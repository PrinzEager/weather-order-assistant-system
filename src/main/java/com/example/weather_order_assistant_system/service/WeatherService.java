package com.example.weather_order_assistant_system.service;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.example.weather_order_assistant_system.client.CwaApiClient;
import com.example.weather_order_assistant_system.client.CwaDatasetMapping;
import com.example.weather_order_assistant_system.response.WeatherConditionResponse;
import com.example.weather_order_assistant_system.response.WeatherDailyResponse;
import com.example.weather_order_assistant_system.response.WeatherForecastResponse;
import com.example.weather_order_assistant_system.response.WeatherPrecipitationResponse;
import com.example.weather_order_assistant_system.response.WeatherTemperatureResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 中央氣象署天氣預報服務。
 *
 * 負責：
 * 1. 驗證查詢地點及日期。
 * 2. 呼叫中央氣象署 API。
 * 3. 解析溫度、降雨機率及天氣現象。
 * 4. 產生每日天氣摘要。
 * 5. 計算天氣需求倍率。
 * 6. 判斷預報資料完整性。
 *
 * 第一版僅提供未來 3 天預報。
 * 不儲存天氣資料，也不修改商品庫存。
 */
@Service
@RequiredArgsConstructor
public class WeatherService {

    // 台灣時區
    private static final ZoneId TAIWAN_ZONE =
            ZoneId.of("Asia/Taipei");

    // 台灣目前使用的 UTC+08:00 偏移
    private static final ZoneOffset TAIWAN_OFFSET =
            ZoneOffset.ofHours(8);

    // 溫度影響訂貨需求的門檻
    private static final int TEMPERATURE_THRESHOLD = 32;

    // 降雨機率影響訂貨需求的門檻
    private static final int RAIN_THRESHOLD = 60;

    // 每個條件達成時增加的倍率
    private static final BigDecimal FACTOR_INCREMENT =
            new BigDecimal("0.10");

    // 預設天氣倍率
    private static final BigDecimal DEFAULT_FACTOR =
            new BigDecimal("1.00");

    // 依目前確認的預報格式，溫度資料最大預期相隔 3 小時
    // 若未來中央氣象署調整頻率，需重新檢查此規則
    private static final Duration MAX_TEMPERATURE_GAP =
            Duration.ofHours(3);

    // 已建立的中央氣象署 API Client
    private final CwaApiClient cwaApiClient;

    // Spring Boot 4 提供的 Jackson 3 JSON Mapper
    private final JsonMapper jsonMapper;

    /**
     * 查詢指定地點及日期範圍的天氣預報。
     *
     * @param city 縣市名稱
     * @param district 鄉鎮市區名稱
     * @param startDate 查詢起始日期，包含當日
     * @param endDate 查詢結束日期，包含當日
     * @return 完整的天氣預報回傳 DTO
     */
    public WeatherForecastResponse getForecast(
            String city,
            String district,
            LocalDate startDate,
            LocalDate endDate) {

        // 先驗證輸入，避免不必要的 API 呼叫
        validateDates(startDate, endDate);

        if (district == null || district.isBlank()) {
            throw new IllegalArgumentException(
                    "鄉鎮市區名稱不可為空");
        }

        // 根據縣市取得對應的官方資料集
        String datasetId =
                CwaDatasetMapping.getDatasetId(city);

        // 一次取得指定地點的預報 JSON
        String json = fetchForecastJson(datasetId, district);

        // 使用 Jackson 3 解析 JSON
        JsonNode root;

        try {
            root = jsonMapper.readTree(json);
        } catch (JacksonException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "中央氣象署回傳的 JSON 格式錯誤",
                    ex);
        }

        if (root == null || !root.isObject()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "中央氣象署回傳的資料格式不正確");
        }

        // 檢查中央氣象署回應是否成功
        String success = readText(root.path("success"));

        if (!"true".equalsIgnoreCase(success)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "中央氣象署回報預報資料取得失敗");
        }

        // 尋找指定縣市、指定行政區的 Location
        JsonNode location = findLocation(root, city, district);

        // 分別儲存三種不同時間粒度的預報資料
        List<WeatherTemperatureResponse> temperatures =
                new ArrayList<>();

        List<WeatherPrecipitationResponse> precipitations =
                new ArrayList<>();

        List<WeatherConditionResponse> conditions =
                new ArrayList<>();

        // 解析中央氣象署各天氣元素
        parseWeatherElements(
                location,
                temperatures,
                precipitations,
                conditions);

        // 依時間排序，讓前端圖表及後續判斷更穩定
        temperatures.sort(
                Comparator.comparing(
                        WeatherTemperatureResponse::getDataTime));

        precipitations.sort(
                Comparator.comparing(
                        WeatherPrecipitationResponse::getStartTime));

        conditions.sort(
                Comparator.comparing(
                        WeatherConditionResponse::getStartTime));

        // 查詢日期採左閉右開：
        // [startDate 00:00, endDate + 1 00:00)
        OffsetDateTime rangeStart =
                startDate.atStartOfDay(TAIWAN_ZONE)
                        .toOffsetDateTime();

        OffsetDateTime rangeEnd =
                endDate.plusDays(1)
                        .atStartOfDay(TAIWAN_ZONE)
                        .toOffsetDateTime();

        // 預報有效範圍不得早於現在
        // 查詢今日時，已過去的時段不列入倍率與完整性判斷
        OffsetDateTime now =
                OffsetDateTime.now(TAIWAN_ZONE);

        OffsetDateTime effectiveStart =
                rangeStart.isAfter(now) ? rangeStart : now;

        // 篩選有效的未來預報
        List<WeatherTemperatureResponse> selectedTemperatures =
                temperatures.stream()
                        .filter(item -> !item.getDataTime()
                                .isBefore(effectiveStart))
                        .filter(item -> item.getDataTime()
                                .isBefore(rangeEnd))
                        .toList();

        List<WeatherPrecipitationResponse> selectedPrecipitations =
                precipitations.stream()
                        .filter(item -> overlaps(
                                item.getStartTime(),
                                item.getEndTime(),
                                effectiveStart,
                                rangeEnd))
                        .toList();

        List<WeatherConditionResponse> selectedConditions =
                conditions.stream()
                        .filter(item -> overlaps(
                                item.getStartTime(),
                                item.getEndTime(),
                                effectiveStart,
                                rangeEnd))
                        .toList();

        // 計算查詢期間最高有效氣溫
        Integer maxTemperature =
                selectedTemperatures.stream()
                        .map(WeatherTemperatureResponse::getTemperature)
                        .filter(value -> value != null)
                        .max(Integer::compareTo)
                        .orElse(null);

        // 計算查詢期間最高有效降雨機率
        Integer maxRain =
                selectedPrecipitations.stream()
                        .map(
                                WeatherPrecipitationResponse::
                                        getPrecipitationProbability)
                        .filter(value -> value != null)
                        .max(Integer::compareTo)
                        .orElse(null);

        // 計算天氣需求倍率
        BigDecimal weatherFactor =
                calculateWeatherFactor(maxTemperature, maxRain);

        // 建立每日天氣卡片摘要
        // 每日卡片保留當日有效的完整預報資料，
        // 不將已過去的時間點誤判為資料缺失
        List<WeatherDailyResponse> dailyForecasts =
                buildDailyForecasts(
                        startDate,
                        endDate,
                        temperatures,
                        conditions);

        // 判定預報資料完整性
        String forecastStatus = calculateForecastStatus(
                selectedTemperatures,
                selectedPrecipitations,
                selectedConditions,
                effectiveStart,
                rangeEnd);

        String warning = switch (forecastStatus) {
            case "PARTIAL" ->
                    "部分氣象預報資料缺失，天氣倍率僅依有效資料計算";
            case "UNAVAILABLE" ->
                    "沒有可用的氣溫及降雨機率資料，天氣倍率使用 1.00";
            default -> null;
        };

        // 組合最終 API 回傳 DTO
        WeatherForecastResponse response =
                new WeatherForecastResponse();

        response.setCity(city);
        response.setDistrict(district);
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        response.setMaxTemperature(maxTemperature);
        response.setMaxPrecipitationProbability(maxRain);
        response.setWeatherFactor(weatherFactor);
        response.setForecastStatus(forecastStatus);
        response.setWarning(warning);
        response.setDailyForecasts(dailyForecasts);
        response.setTemperatureForecasts(selectedTemperatures);
        response.setPrecipitationForecasts(selectedPrecipitations);
        response.setWeatherConditions(selectedConditions);

        return response;
    }

    /**
     * 驗證日期。
     *
     * 第一版限制今天至後天，共 3 個日曆日。
     */
    private void validateDates(
            LocalDate startDate,
            LocalDate endDate) {

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException(
                    "起始日期與結束日期不可為空");
        }

        LocalDate today = LocalDate.now(TAIWAN_ZONE);

        if (startDate.isBefore(today)) {
            throw new IllegalArgumentException(
                    "預報起始日期不可早於今天");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "預報結束日期不可早於起始日期");
        }

        if (endDate.isAfter(today.plusDays(2))) {
            throw new IllegalArgumentException(
                    "目前僅支援今天至後天的天氣預報");
        }
    }

    /**
     * 呼叫中央氣象署 API，區分上游 HTTP 與網路錯誤。
     */
    private String fetchForecastJson(
            String datasetId,
            String district) {

        try {
            return cwaApiClient.getForecastJson(
                    datasetId, district);

        } catch (RestClientResponseException ex) {

            // 上游授權失敗仍以 502 呈現，
            // 避免誤導前端認為使用者沒有登入權限
            if (ex.getStatusCode().value() == 401
                    || ex.getStatusCode().value() == 403) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "中央氣象署 API 授權失敗",
                        ex);
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "中央氣象署 API 回應異常",
                    ex);

        } catch (ResourceAccessException ex) {

            // 只有實際逾時才使用 HTTP 504
            if (isTimeout(ex)) {
                throw new ResponseStatusException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "中央氣象署 API 連線逾時",
                        ex);
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "無法連線至中央氣象署 API",
                    ex);

        } catch (RestClientException ex) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "中央氣象署 API 請求失敗",
                    ex);

        } catch (IllegalStateException ex) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "中央氣象署回傳空白或無效資料",
                    ex);
        }
    }

    /**
     * 判斷例外原因中是否包含連線逾時。
     */
    private boolean isTimeout(Throwable exception) {

        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof java.net.http.HttpTimeoutException) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    /**
     * 從官方 JSON 尋找指定縣市與行政區。
     *
     * 必須同時比對 LocationsName 與 LocationName，
     * 避免不同縣市的同名行政區造成誤判。
     */
    private JsonNode findLocation(
            JsonNode root,
            String city,
            String district) {

        JsonNode groups =
                root.path("records").path("Locations");

        if (!groups.isArray()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "中央氣象署 JSON 缺少 Locations 資料");
        }

        for (JsonNode group : groups) {

            String groupCity =
                    readText(group.path("LocationsName"));

            if (!city.equals(groupCity)) {
                continue;
            }

            JsonNode locations = group.path("Location");

            if (!locations.isArray()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "中央氣象署 JSON 缺少 Location 資料");
            }

            for (JsonNode location : locations) {

                String locationName =
                        readText(location.path("LocationName"));

                if (district.equals(locationName)) {
                    return location;
                }
            }

            throw new IllegalArgumentException(
                    "指定行政區不存在於該縣市：" + district);
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "中央氣象署回傳的縣市與查詢地點不一致");
    }

    /**
     * 依 ElementName 分別解析三種天氣預報。
     *
     * 不依賴 WeatherElement 的陣列順序。
     */
    private void parseWeatherElements(
            JsonNode location,
            List<WeatherTemperatureResponse> temperatures,
            List<WeatherPrecipitationResponse> precipitations,
            List<WeatherConditionResponse> conditions) {

        JsonNode elements = location.path("WeatherElement");

        if (!elements.isArray()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "中央氣象署 JSON 缺少 WeatherElement");
        }

        for (JsonNode element : elements) {

            String elementName =
                    readText(element.path("ElementName"));

            JsonNode times = element.path("Time");

            if (!times.isArray()) {
                continue;
            }

            for (JsonNode time : times) {

                JsonNode values = time.path("ElementValue");

                // 取得第一個氣象元素值
                JsonNode value = values.isArray()
                        && !values.isEmpty()
                        ? values.get(0)
                        : null;

                if (value == null) {
                    continue;
                }

                try {
                    if ("溫度".equals(elementName)) {

                        OffsetDateTime dataTime = parseTime(
                                time.path("DataTime"));

                        if (dataTime != null) {
                            temperatures.add(
                                    new WeatherTemperatureResponse(
                                            dataTime,
                                            parseTemperature(
                                                    value.path("Temperature"))));
                        }

                    } else if ("3小時降雨機率".equals(elementName)) {

                        OffsetDateTime start = parseTime(
                                time.path("StartTime"));

                        OffsetDateTime end = parseTime(
                                time.path("EndTime"));

                        if (validPeriod(start, end)) {
                            precipitations.add(
                                    new WeatherPrecipitationResponse(
                                            start,
                                            end,
                                            parseRain(
                                                    value.path(
                                                            "ProbabilityOfPrecipitation"))));
                        }

                    } else if ("天氣現象".equals(elementName)) {

                        OffsetDateTime start = parseTime(
                                time.path("StartTime"));

                        OffsetDateTime end = parseTime(
                                time.path("EndTime"));

                        if (validPeriod(start, end)) {
                            conditions.add(
                                    new WeatherConditionResponse(
                                            start,
                                            end,
                                            readText(value.path("Weather")),
                                            readText(value.path("WeatherCode"))));
                        }
                    }

                } catch (DateTimeParseException ex) {
                    // 單一時段的日期格式異常時跳過該筆。
                    // 後續完整性檢查會反映資料缺口。
                    // 不讓單筆資料破壞其他有效預報。
                }
            }
        }
    }

    /**
     * 讀取 JSON 字串。
     * 無值、空字串或 null 時保留缺值。
     */
    private String readText(JsonNode node) {

        if (node == null
                || node.isMissingNode()
                || node.isNull()) {
            return null;
        }

        String text = node.asText();

        if (text == null || text.isBlank()) {
            return null;
        }

        return text.trim();
    }

    /**
     * 安全解析氣象數值。
     * 不可把缺值或異常文字轉為 0。
     */
    private Integer parseNumber(JsonNode node) {

        String value = readText(node);

        if (value == null) {
            return null;
        }

        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 解析溫度。
     *
     * 使用中央氣象署提供的攝氏整數值。
     */
    private Integer parseTemperature(JsonNode node) {
        return parseNumber(node);
    }

    /**
     * 解析降雨機率。
     *
     * 合法數值為 0～100。
     * 0 是有效降雨機率，不等於缺值。
     */
    private Integer parseRain(JsonNode node) {

        Integer value = parseNumber(node);

        if (value == null || value < 0 || value > 100) {
            return null;
        }

        return value;
    }

    /**
     * 解析 ISO 8601 時間，保留時區偏移。
     */
    private OffsetDateTime parseTime(JsonNode node) {

        String text = readText(node);

        if (text == null) {
            return null;
        }

        return OffsetDateTime.parse(text)
                .withOffsetSameInstant(TAIWAN_OFFSET);
    }

    /**
     * 驗證時段的開始時間必須早於結束時間。
     */
    private boolean validPeriod(
            OffsetDateTime start,
            OffsetDateTime end) {

        return start != null
                && end != null
                && start.isBefore(end);
    }

    /**
     * 判斷兩個左閉右開時段是否重疊。
     *
     * [start, end) 與 [rangeStart, rangeEnd)
     */
    private boolean overlaps(
            OffsetDateTime start,
            OffsetDateTime end,
            OffsetDateTime rangeStart,
            OffsetDateTime rangeEnd) {

        return validPeriod(start, end)
                && start.isBefore(rangeEnd)
                && end.isAfter(rangeStart);
    }

    /**
     * 計算天氣需求倍率。
     *
     * 最高氣溫 >= 32°C：增加 0.10
     * 最高降雨機率 >= 60%：增加 0.10
     *
     * 缺值不視為達標。
     */
    private BigDecimal calculateWeatherFactor(
            Integer maxTemperature,
            Integer maxRain) {

        BigDecimal factor = DEFAULT_FACTOR;

        if (maxTemperature != null
                && maxTemperature >= TEMPERATURE_THRESHOLD) {
            factor = factor.add(FACTOR_INCREMENT);
        }

        if (maxRain != null
                && maxRain >= RAIN_THRESHOLD) {
            factor = factor.add(FACTOR_INCREMENT);
        }

        return factor;
    }

    /**
     * 建立每日天氣摘要。
     *
     * 每日最高、最低溫取當日有效溫度值。
     * 代表性天氣使用當地 12:00 所屬預報時段。
     *
     * 若無有效資料，相關欄位保留 null。
     */
    private List<WeatherDailyResponse> buildDailyForecasts(
            LocalDate startDate,
            LocalDate endDate,
            List<WeatherTemperatureResponse> temperatures,
            List<WeatherConditionResponse> conditions) {

        List<WeatherDailyResponse> dailyForecasts =
                new ArrayList<>();

        for (LocalDate date = startDate;
                !date.isAfter(endDate);
                date = date.plusDays(1)) {

            OffsetDateTime dayStart =
                    date.atStartOfDay(TAIWAN_ZONE)
                            .toOffsetDateTime();

            OffsetDateTime dayEnd =
                    date.plusDays(1)
                            .atStartOfDay(TAIWAN_ZONE)
                            .toOffsetDateTime();

            OffsetDateTime noon =
                    date.atTime(12, 0)
                            .atZone(TAIWAN_ZONE)
                            .toOffsetDateTime();

            Integer max = null;
            Integer min = null;

            // 計算每日最高及最低氣溫
            for (WeatherTemperatureResponse item : temperatures) {

                OffsetDateTime time = item.getDataTime();
                Integer value = item.getTemperature();

                if (value == null
                        || time.isBefore(dayStart)
                        || !time.isBefore(dayEnd)) {
                    continue;
                }

                if (max == null || value > max) {
                    max = value;
                }

                if (min == null || value < min) {
                    min = value;
                }
            }

            String weather = null;
            String weatherCode = null;

            // 取得涵蓋當日中午 12:00 的代表性天氣
            for (WeatherConditionResponse item : conditions) {

                if (!noon.isBefore(item.getStartTime())
                        && noon.isBefore(item.getEndTime())) {

                    weather = item.getWeather();
                    weatherCode = item.getWeatherCode();
                    break;
                }
            }

            dailyForecasts.add(
                    new WeatherDailyResponse(
                            date,
                            max,
                            min,
                            weather,
                            weatherCode));
        }

        return dailyForecasts;
    }

    /**
     * 判定天氣預報完整性。
     *
     * UNAVAILABLE：
     * 氣溫與降雨機率都沒有有效資料。
     *
     * COMPLETE：
     * 氣溫資料符合預期的最大 3 小時間距，
     * 降雨及天氣現象的有效時段完整覆蓋查詢區間。
     *
     * 其餘可使用部分資料的情況為 PARTIAL。
     */
    private String calculateForecastStatus(
            List<WeatherTemperatureResponse> temperatures,
            List<WeatherPrecipitationResponse> precipitations,
            List<WeatherConditionResponse> conditions,
            OffsetDateTime start,
            OffsetDateTime end) {

        boolean hasTemperature = temperatures.stream()
                .anyMatch(item -> item.getTemperature() != null);

        boolean hasRain = precipitations.stream()
                .anyMatch(item ->
                        item.getPrecipitationProbability() != null);

        if (!hasTemperature && !hasRain) {
            return "UNAVAILABLE";
        }

        boolean temperatureComplete =
                isTemperatureComplete(temperatures, start, end);

        // 降雨資料只有數值有效的時段才能算作覆蓋
        List<WeatherPrecipitationResponse> validRain =
                precipitations.stream()
                        .filter(item ->
                                item.getPrecipitationProbability() != null)
                        .toList();

        boolean rainComplete =
                isPeriodCoverageComplete(
                        validRain.stream()
                                .map(item -> new TimePeriod(
                                        item.getStartTime(),
                                        item.getEndTime()))
                                .toList(),
                        start,
                        end);

        // 天氣現象與代碼皆有效，才算完整覆蓋
        boolean conditionComplete =
                isPeriodCoverageComplete(
                        conditions.stream()
                                .filter(item ->
                                        item.getWeather() != null
                                        && item.getWeatherCode() != null)
                                .map(item -> new TimePeriod(
                                        item.getStartTime(),
                                        item.getEndTime()))
                                .toList(),
                        start,
                        end);

        if (temperatureComplete
                && rainComplete
                && conditionComplete) {
            return "COMPLETE";
        }

        return "PARTIAL";
    }

    /**
     * 檢查溫度資料時間點是否連續到足以覆蓋預報期間。
     *
     * 依已確認的官方樣本，溫度前段為逐小時，
     * 後段為每 3 小時，因此最大容許間距為 3 小時。
     *
     * 這是時間點資料的完整性近似判斷，
     * 並非將溫度當成連續時段資料。
     */
    private boolean isTemperatureComplete(
            List<WeatherTemperatureResponse> temperatures,
            OffsetDateTime start,
            OffsetDateTime end) {

        List<OffsetDateTime> times = temperatures.stream()
                .filter(item -> item.getTemperature() != null)
                .map(WeatherTemperatureResponse::getDataTime)
                .sorted()
                .toList();

        if (times.isEmpty()) {
            return false;
        }

        OffsetDateTime first = times.get(0);
        OffsetDateTime last = times.get(times.size() - 1);

        if (Duration.between(start, first)
                .compareTo(MAX_TEMPERATURE_GAP) > 0) {
            return false;
        }

        if (Duration.between(last, end)
                .compareTo(MAX_TEMPERATURE_GAP) > 0) {
            return false;
        }

        for (int i = 1; i < times.size(); i++) {

            Duration gap =
                    Duration.between(times.get(i - 1), times.get(i));

            if (gap.compareTo(MAX_TEMPERATURE_GAP) > 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * 檢查時段型預報是否完整覆蓋指定區間。
     *
     * 即使不同預報時段彼此重疊，
     * 也必須確定沒有未覆蓋的時間缺口。
     */
    private boolean isPeriodCoverageComplete(
            List<TimePeriod> periods,
            OffsetDateTime start,
            OffsetDateTime end) {

        List<TimePeriod> sorted = periods.stream()
                .filter(item -> validPeriod(
                        item.start(), item.end()))
                .sorted(Comparator.comparing(TimePeriod::start))
                .toList();

        OffsetDateTime coveredUntil = start;

        for (TimePeriod period : sorted) {

            // 如果下一個時段開始時間晚於已覆蓋時間，
            // 代表中間存在缺口
            if (period.start().isAfter(coveredUntil)) {
                return false;
            }

            if (period.end().isAfter(coveredUntil)) {
                coveredUntil = period.end();
            }

            if (!coveredUntil.isBefore(end)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 內部使用的時間區間資料。
     * 不作為資料庫 Entity，也不回傳給前端。
     */
    private record TimePeriod(
            OffsetDateTime start,
            OffsetDateTime end) {
    }
}
