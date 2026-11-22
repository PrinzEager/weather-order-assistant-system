package com.example.weather_order_assistant_system.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import com.example.weather_order_assistant_system.response.WeatherDailyFactorResponse;
import com.example.weather_order_assistant_system.response.WeatherHumidityResponse;
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
 * 此版本提供今天至第七天（含）之 7 個日曆日預報。
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

    // 官方一週預報中，12 小時降雨機率通常僅涵蓋前三個日曆日。
    // 第四至第七天未提供的降雨資料，不當成缺失或 0%。
    private static final int RAIN_FORECAST_DAYS = 3;

    // 已建立的中央氣象署 API Client
    private final CwaApiClient cwaApiClient;

    // Spring Boot 4 提供的 Jackson 3 JSON Mapper
    private final JsonMapper jsonMapper;

    // 獨立的冷飲天氣係數計算器：與 API 解析邏輯分開。
    private final WeatherFactorCalculator weatherFactorCalculator;

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
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "鄉鎮市區名稱不可為空");
        }

        // 將不支援的縣市視為使用者輸入錯誤，而非伺服器 500。
        String datasetId;
        try {
            datasetId = CwaDatasetMapping.getDatasetId(city);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

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

        // 分別儲存逐 12 小時溫度、濕度、降雨及天氣預報。
        // 平均溫度轉為時段中點供原本的前端折線圖使用。
        List<WeatherTemperatureResponse> temperatures =
                new ArrayList<>();

        // 最高、最低溫度採官方逐 12 小時時段資料。
        // 不用「平均溫度」推算極值，以免影響天氣倍率。
        List<TemperaturePeriod> maximumTemperatures = new ArrayList<>();
        List<TemperaturePeriod> minimumTemperatures = new ArrayList<>();

        List<WeatherHumidityResponse> humidities =
                new ArrayList<>();

        List<WeatherPrecipitationResponse> precipitations =
                new ArrayList<>();

        List<WeatherConditionResponse> conditions =
                new ArrayList<>();

        // 解析中央氣象署各天氣元素
        parseWeatherElements(
                location,
                temperatures,
                maximumTemperatures,
                minimumTemperatures,
                humidities,
                precipitations,
                conditions);

        // 依時間排序，讓前端圖表及後續判斷更穩定
        temperatures.sort(
                Comparator.comparing(
                        WeatherTemperatureResponse::getDataTime));

        humidities.sort(
                Comparator.comparing(WeatherHumidityResponse::getStartTime));

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

        // 只取與有效查詢期間重疊的最高／最低溫時段。
        List<TemperaturePeriod> selectedMaximums = maximumTemperatures.stream()
                .filter(item -> overlaps(item.start(), item.end(),
                        effectiveStart, rangeEnd))
                .toList();

        List<TemperaturePeriod> selectedMinimums = minimumTemperatures.stream()
                .filter(item -> overlaps(item.start(), item.end(),
                        effectiveStart, rangeEnd))
                .toList();

        List<WeatherHumidityResponse> selectedHumidities = humidities.stream()
                .filter(item -> overlaps(item.getStartTime(), item.getEndTime(),
                        effectiveStart, rangeEnd))
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

        // 官方「最高溫度」元素才是天氣倍率的溫度依據。
        // 不使用逐 12 小時平均溫度誤當最高溫。
        Integer maxTemperature = selectedMaximums.stream()
                .map(TemperaturePeriod::value)
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

        // 建立每日天氣卡片摘要
        // 每日卡片呈現與該日重疊的官方 12 小時溫度區間，
        // 日期範圍內可能跨午夜，詳見 buildDailyForecasts。
        List<WeatherDailyResponse> dailyForecasts =
                buildDailyForecasts(
                        startDate,
                        endDate,
                        maximumTemperatures,
                        minimumTemperatures,
                        conditions);

        // 每天獨立計算冷飲係數，然後取所選日期的算術平均。
        // 今日只計入目前以後仍有效的預報時段。
        List<WeatherDailyFactorResponse> dailyWeatherFactors =
                buildDailyWeatherFactors(
                        startDate, endDate, effectiveStart,
                        selectedMaximums, selectedHumidities,
                        selectedPrecipitations);

        BigDecimal weatherFactor =
                weatherFactorCalculator.calculatePeriodAverage(dailyWeatherFactors);

        // 降雨機率官方通常只提供前三天；未提供的遠期降雨不當成異常。
        OffsetDateTime officialRainEnd = LocalDate.now(TAIWAN_ZONE)
                .plusDays(RAIN_FORECAST_DAYS)
                .atStartOfDay(TAIWAN_ZONE)
                .toOffsetDateTime();

        OffsetDateTime requiredRainEnd = rangeEnd.isBefore(officialRainEnd)
                ? rangeEnd : officialRainEnd;

        // 判定預報完整性：溫度與天氣覆蓋查詢範圍，
        // 降雨只在官方提供前三天的範圍內檢查。
        String forecastStatus = calculateForecastStatus(
                selectedMaximums,
                selectedMinimums,
                selectedHumidities,
                selectedPrecipitations,
                selectedConditions,
                effectiveStart,
                rangeEnd,
                requiredRainEnd);

        String warning = switch (forecastStatus) {
            case "PARTIAL" ->
                    "部分氣象預報資料缺失，缺值採中性調整，天氣倍率僅供參考";
            case "UNAVAILABLE" ->
                    "沒有可用的最高／最低氣溫與降雨機率資料，天氣倍率使用 1.00";
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
        response.setHumidityForecasts(selectedHumidities);
        response.setDailyWeatherFactors(dailyWeatherFactors);

        return response;
    }

    /**
     * 驗證日期。
     *
     * 限制今天起至第七天，共 7 個日曆日（含起迄）。
     */
    private void validateDates(
            LocalDate startDate,
            LocalDate endDate) {

        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "起始日期與結束日期不可為空");
        }

        LocalDate today = LocalDate.now(TAIWAN_ZONE);

        if (startDate.isBefore(today)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "預報起始日期不可早於今天");
        }

        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "預報結束日期不可早於起始日期");
        }

        if (endDate.isAfter(today.plusDays(6))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "目前僅支援今天起七天內的天氣預報");
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

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "指定行政區不存在於該縣市：" + district);
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "中央氣象署回傳的縣市與查詢地點不一致");
    }

    /**
     * 解析一週逐 12 小時的天氣元素。
     *
     * 元素名稱不依陣列順序，依中央氣象署官方 ElementName 比對：
     * 平均溫度、最高溫度、最低溫度、平均相對濕度、
     * 12小時降雨機率、天氣現象。
     *
     * 平均溫度屬「時段平均值」，轉成時段中點供既有圖表顯示。
     * 每日最高／最低與整體天氣倍率則讀取專屬的時段極值。
     */
    private void parseWeatherElements(
            JsonNode location,
            List<WeatherTemperatureResponse> temperatures,
            List<TemperaturePeriod> maximumTemperatures,
            List<TemperaturePeriod> minimumTemperatures,
            List<WeatherHumidityResponse> humidities,
            List<WeatherPrecipitationResponse> precipitations,
            List<WeatherConditionResponse> conditions) {

        JsonNode elements = location.path("WeatherElement");

        if (!elements.isArray()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "中央氣象署 JSON 缺少 WeatherElement");
        }

        for (JsonNode element : elements) {
            String elementName = readText(element.path("ElementName"));
            JsonNode times = element.path("Time");

            if (!times.isArray()) {
                continue;
            }

            // 七天資料均以 StartTime / EndTime 表示有效時段。
            for (JsonNode time : times) {
                JsonNode values = time.path("ElementValue");
                JsonNode value = values.isArray() && !values.isEmpty()
                        ? values.get(0) : null;

                if (value == null) {
                    continue;
                }

                try {
                    OffsetDateTime start = parseTime(time.path("StartTime"));
                    OffsetDateTime end = parseTime(time.path("EndTime"));

                    if (!validPeriod(start, end)) {
                        continue;
                    }

                    if ("平均溫度".equals(elementName)) {
                        // 舊版 DTO 使用資料點；以時段中點表示 12 小時平均溫。
                        OffsetDateTime midpoint = start.plusSeconds(
                                Duration.between(start, end).getSeconds() / 2);
                        temperatures.add(new WeatherTemperatureResponse(
                                midpoint,
                                parseTemperature(value.path("Temperature"))));

                    } else if ("最高溫度".equals(elementName)) {
                        maximumTemperatures.add(new TemperaturePeriod(
                                start, end,
                                parseTemperature(value.path("MaxTemperature"))));

                    } else if ("最低溫度".equals(elementName)) {
                        minimumTemperatures.add(new TemperaturePeriod(
                                start, end,
                                parseTemperature(value.path("MinTemperature"))));

                    } else if ("平均相對濕度".equals(elementName)) {
                        // 七天資料使用 RelativeHumidity，無效數值保留 null。
                        humidities.add(new WeatherHumidityResponse(
                                start, end,
                                parsePercent(value.path("RelativeHumidity"))));

                    } else if ("12小時降雨機率".equals(elementName)) {
                        // 有效值 0～100；"-" 等缺值保留 null。
                        precipitations.add(new WeatherPrecipitationResponse(
                                start, end,
                                parseRain(value.path("ProbabilityOfPrecipitation"))));

                    } else if ("天氣現象".equals(elementName)) {
                        conditions.add(new WeatherConditionResponse(
                                start, end,
                                readText(value.path("Weather")),
                                readText(value.path("WeatherCode"))));
                    }

                } catch (DateTimeParseException ex) {
                    // 單一不合法時間略過，完整性檢查會反映缺口。
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
        return parsePercent(node);
    }

    /**
     * 濕度與降雨機率都是百分比，數值只允許 0～100。
     */
    private Integer parsePercent(JsonNode node) {

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
     * 逐日計算冷飲天氣係數，將氣溫、濕度與降雨分開處理。
     *
     * 氣溫：取當日有效預報的最高溫。
     * 濕度：依與該日重疊的有效時段分鐘數加權平均。
     * 降雨：取當日有效降雨機率最高值；遠期缺值維持 null。
     */
    private List<WeatherDailyFactorResponse> buildDailyWeatherFactors(
            LocalDate startDate,
            LocalDate endDate,
            OffsetDateTime effectiveStart,
            List<TemperaturePeriod> maximumTemperatures,
            List<WeatherHumidityResponse> humidities,
            List<WeatherPrecipitationResponse> precipitations) {

        List<WeatherDailyFactorResponse> result = new ArrayList<>();

        for (LocalDate date = startDate;
                !date.isAfter(endDate);
                date = date.plusDays(1)) {

            OffsetDateTime dayStart = date.atStartOfDay(TAIWAN_ZONE)
                    .toOffsetDateTime();
            OffsetDateTime dayEnd = date.plusDays(1)
                    .atStartOfDay(TAIWAN_ZONE).toOffsetDateTime();

            // 今天已經過去的時間不參與倍率計算。
            OffsetDateTime validStart = dayStart.isAfter(effectiveStart)
                    ? dayStart : effectiveStart;

            Integer dayMaxTemperature = maximumTemperatures.stream()
                    .filter(item -> overlaps(item.start(), item.end(),
                            validStart, dayEnd))
                    .map(TemperaturePeriod::value)
                    .filter(value -> value != null)
                    .max(Integer::compareTo)
                    .orElse(null);

            Integer dayHumidity = calculateDayAverageHumidity(
                    humidities, validStart, dayEnd);

            Integer dayMaxRain = precipitations.stream()
                    .filter(item -> overlaps(item.getStartTime(), item.getEndTime(),
                            validStart, dayEnd))
                    .map(WeatherPrecipitationResponse::getPrecipitationProbability)
                    .filter(value -> value != null)
                    .max(Integer::compareTo)
                    .orElse(null);

            result.add(weatherFactorCalculator.calculateDaily(
                    date, dayMaxTemperature, dayHumidity, dayMaxRain));
        }
        return result;
    }

    /**
     * 依時段重疊分鐘數取得每日平均相對濕度。
     *
     * 例如 18:00～隔日 06:00 的濕度值，
     * 前一天與隔一天各貢獻 6 小時，不會都當成完整 12 小時。
     */
    private Integer calculateDayAverageHumidity(
            List<WeatherHumidityResponse> humidities,
            OffsetDateTime dayStart,
            OffsetDateTime dayEnd) {

        long totalMinutes = 0L;
        BigDecimal weightedSum = BigDecimal.ZERO;

        for (WeatherHumidityResponse item : humidities) {
            if (item.getRelativeHumidity() == null || !overlaps(
                    item.getStartTime(), item.getEndTime(), dayStart, dayEnd)) {
                continue;
            }

            OffsetDateTime overlapStart = item.getStartTime().isAfter(dayStart)
                    ? item.getStartTime() : dayStart;
            OffsetDateTime overlapEnd = item.getEndTime().isBefore(dayEnd)
                    ? item.getEndTime() : dayEnd;

            long minutes = Duration.between(overlapStart, overlapEnd).toMinutes();
            if (minutes <= 0) {
                continue;
            }

            totalMinutes = Math.addExact(totalMinutes, minutes);
            weightedSum = weightedSum.add(
                    BigDecimal.valueOf(item.getRelativeHumidity())
                            .multiply(BigDecimal.valueOf(minutes)));
        }

        if (totalMinutes == 0L) {
            return null;
        }
        return weightedSum.divide(
                BigDecimal.valueOf(totalMinutes), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    /**
     * 依官方逐 12 小時最高／最低氣溫產生每日卡片。
     *
     * 因為預報時段可能跨過午夜（18:00～隔天 06:00），
     * 這裡採「與當日有重疊」的時段極值計算。
     * 此值是預報時段的近似日摘要，不是逐時觀測極值。
     */
    private List<WeatherDailyResponse> buildDailyForecasts(
            LocalDate startDate,
            LocalDate endDate,
            List<TemperaturePeriod> maximumTemperatures,
            List<TemperaturePeriod> minimumTemperatures,
            List<WeatherConditionResponse> conditions) {

        List<WeatherDailyResponse> dailyForecasts = new ArrayList<>();

        for (LocalDate date = startDate;
                !date.isAfter(endDate);
                date = date.plusDays(1)) {

            OffsetDateTime dayStart = date.atStartOfDay(TAIWAN_ZONE)
                    .toOffsetDateTime();
            OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(TAIWAN_ZONE)
                    .toOffsetDateTime();
            OffsetDateTime noon = date.atTime(12, 0)
                    .atZone(TAIWAN_ZONE).toOffsetDateTime();

            Integer max = maximumTemperatures.stream()
                    .filter(item -> overlaps(item.start(), item.end(), dayStart, dayEnd))
                    .map(TemperaturePeriod::value)
                    .filter(value -> value != null)
                    .max(Integer::compareTo)
                    .orElse(null);

            Integer min = minimumTemperatures.stream()
                    .filter(item -> overlaps(item.start(), item.end(), dayStart, dayEnd))
                    .map(TemperaturePeriod::value)
                    .filter(value -> value != null)
                    .min(Integer::compareTo)
                    .orElse(null);

            String weather = null;
            String weatherCode = null;

            // 延續原本以當日 12:00 預報作為代表性天氣的規則。
            for (WeatherConditionResponse item : conditions) {
                if (!noon.isBefore(item.getStartTime())
                        && noon.isBefore(item.getEndTime())) {
                    weather = item.getWeather();
                    weatherCode = item.getWeatherCode();
                    break;
                }
            }

            dailyForecasts.add(new WeatherDailyResponse(
                    date, max, min, weather, weatherCode));
        }

        return dailyForecasts;
    }

    /**
     * 判定未來七天天氣預報的資料完整性。
     *
     * COMPLETE：最高／最低溫、相對濕度與天氣現象覆蓋有效查詢期間；
     *           降雨只需覆蓋官方前三天的有效查詢期間。
     * PARTIAL：有可用資料，但查詢期間內存在需要的缺口。
     * UNAVAILABLE：最高／最低溫及降雨機率皆無有效數值。
     *
     * 不把官方第四～第七天未提供的降雨機率當成資料缺失。
     */
    private String calculateForecastStatus(
            List<TemperaturePeriod> maximumTemperatures,
            List<TemperaturePeriod> minimumTemperatures,
            List<WeatherHumidityResponse> humidities,
            List<WeatherPrecipitationResponse> precipitations,
            List<WeatherConditionResponse> conditions,
            OffsetDateTime start,
            OffsetDateTime end,
            OffsetDateTime requiredRainEnd) {

        boolean hasTemperature = maximumTemperatures.stream()
                .anyMatch(item -> item.value() != null)
                || minimumTemperatures.stream()
                .anyMatch(item -> item.value() != null);

        boolean hasRain = precipitations.stream()
                .anyMatch(item -> item.getPrecipitationProbability() != null);

        if (!hasTemperature && !hasRain) {
            return "UNAVAILABLE";
        }

        boolean maximumComplete = isPeriodCoverageComplete(
                maximumTemperatures.stream()
                        .filter(item -> item.value() != null)
                        .map(item -> new TimePeriod(item.start(), item.end()))
                        .toList(),
                start, end);

        boolean minimumComplete = isPeriodCoverageComplete(
                minimumTemperatures.stream()
                        .filter(item -> item.value() != null)
                        .map(item -> new TimePeriod(item.start(), item.end()))
                        .toList(),
                start, end);

        // 相對濕度是冷飲倍率的新依據，缺失時顯示 PARTIAL。
        boolean humidityComplete = isPeriodCoverageComplete(
                humidities.stream()
                        .filter(item -> item.getRelativeHumidity() != null)
                        .map(item -> new TimePeriod(
                                item.getStartTime(), item.getEndTime()))
                        .toList(),
                start, end);

        boolean conditionComplete = isPeriodCoverageComplete(
                conditions.stream()
                        .filter(item -> item.getWeather() != null
                                && item.getWeatherCode() != null)
                        .map(item -> new TimePeriod(
                                item.getStartTime(), item.getEndTime()))
                        .toList(),
                start, end);

        // 若選擇的期間全部在第 4 天以後，就不要求降雨覆蓋。
        boolean rainComplete = !requiredRainEnd.isAfter(start)
                || isPeriodCoverageComplete(
                        precipitations.stream()
                                .filter(item -> item.getPrecipitationProbability() != null)
                                .map(item -> new TimePeriod(
                                        item.getStartTime(), item.getEndTime()))
                                .toList(),
                        start, requiredRainEnd);

        return maximumComplete && minimumComplete
                && humidityComplete && conditionComplete && rainComplete
                ? "COMPLETE" : "PARTIAL";
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
     * 官方逐 12 小時的最高／最低氣溫資料。
     * 僅供本服務計算，不直接暴露到 API DTO。
     */
    private record TemperaturePeriod(
            OffsetDateTime start,
            OffsetDateTime end,
            Integer value) {
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
