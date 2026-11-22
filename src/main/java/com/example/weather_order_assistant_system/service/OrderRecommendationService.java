package com.example.weather_order_assistant_system.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.weather_order_assistant_system.entity.Product;
import com.example.weather_order_assistant_system.entity.PurchaseOrder;
import com.example.weather_order_assistant_system.entity.PurchaseOrderItem;
import com.example.weather_order_assistant_system.repository.ProductRepository;
import com.example.weather_order_assistant_system.repository.PurchaseOrderRepository;
import com.example.weather_order_assistant_system.repository.SalesRecordRepository;
import com.example.weather_order_assistant_system.response.OrderRecommendationResponse;
import com.example.weather_order_assistant_system.response.OrderRecommendationOptionResponse;
import java.util.Locale;
import com.example.weather_order_assistant_system.response.WeatherForecastResponse;

import lombok.RequiredArgsConstructor;

/**
 * 天氣訂貨建議服務。
 *
 * 整合：
 * 1. 商品目前庫存及安全庫存。
 * 2. 上個月相同天數期間的歷史銷售量。
 * 3. 已提交但尚未到貨的訂貨數量。
 * 4. 中央氣象署提供的天氣需求倍率。
 *
 * 計算公式：
 * max(0, ceil(歷史銷售量 × 天氣倍率)
 *        + 安全庫存 - 目前庫存 - 尚未到貨量)
 *
 * 本服務只產生建議，不直接建立訂貨單。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderRecommendationService {

    // 商品資料查詢
    private final ProductRepository productRepository;

    // 歷史銷售數量統計
    private final SalesRecordRepository salesRecordRepository;

    // 尚未到貨的訂貨單查詢
    private final PurchaseOrderRepository purchaseOrderRepository;

    // 已完成的天氣預報服務
    private final WeatherService weatherService;

    // 商品啟用狀態
    private static final Integer ACTIVE_STATUS = 1;

    // 已提交但尚未到貨的訂貨單狀態
    private static final String SUBMITTED = "SUBMITTED";

    // 天氣 API 無法使用時的預設倍率
    private static final BigDecimal DEFAULT_FACTOR =
            new BigDecimal("1.00");

    /**
     * 查詢指定商品的天氣訂貨建議。
     *
     * @param productId 商品 ID
     * @param city 縣市名稱
     * @param district 鄉鎮市區名稱
     * @param startDate 預估需求起始日
     * @param endDate 預估需求結束日
     * @return 指定商品的訂貨建議
     */
    public OrderRecommendationResponse getRecommendation(
            Long productId,
            String city,
            String district,
            LocalDate startDate,
            LocalDate endDate) {

        // 僅允許查詢有效且啟用的商品
        Product product = productRepository
                .findByIdAndDeletedAtIsNull(productId)
                .filter(p -> ACTIVE_STATUS.equals(p.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "商品不存在、已停售或已刪除"
                ));

        // 取得一次天氣資料
        WeatherInfo weather = loadWeather(
                city, district, startDate, endDate);

        // 取得目前已提交、尚未到貨的訂貨單
        List<PurchaseOrder> submittedOrders =
                purchaseOrderRepository
                        .findByOrderStatusAndDeletedAtIsNull(SUBMITTED);

        // 計算並回傳指定商品建議
        return buildRecommendation(
                product,
                startDate,
                endDate,
                weather,
                submittedOrders);
    }

    /**
     * 查詢全部有效商品的天氣訂貨建議。
     *
     * 同一次查詢只呼叫一次 WeatherService，
     * 所有商品共用相同的天氣倍率。
     */
    public List<OrderRecommendationResponse> getAllRecommendations(
            String city,
            String district,
            LocalDate startDate,
            LocalDate endDate) {

        // 取得所有啟用且未軟刪除的商品
        List<Product> products = productRepository
                .findByStatusAndDeletedAtIsNullOrderByIdAsc(
                        ACTIVE_STATUS);

        // 即使沒有商品，也先驗證日期、地點與天氣查詢
        // 避免無效請求被當成正常結果
        WeatherInfo weather = loadWeather(
                city, district, startDate, endDate);

        // 一次取得所有已提交但尚未到貨的訂貨單
        List<PurchaseOrder> submittedOrders =
                purchaseOrderRepository
                        .findByOrderStatusAndDeletedAtIsNull(SUBMITTED);

        List<OrderRecommendationResponse> result =
                new ArrayList<>();

        // 所有商品共用同一份天氣資料及訂貨單資料
        for (Product product : products) {

            result.add(buildRecommendation(
                    product,
                    startDate,
                    endDate,
                    weather,
                    submittedOrders));
        }

        return result;
    }

    /**
     * 查詢全部商品的「兩種補貨建議」。
     *
     * 只向天氣服務查詢一次資料；每項商品另外計算上週與上月同期淨銷量。
     * 前端的「套用天氣」與「選擇訂貨」保持獨立，並不儲存於資料庫。
     *
     * @param basis WEEK：上週同期；MONTH：上月同期（預設）
     */
    public List<OrderRecommendationOptionResponse> getAllRecommendationOptions(
            String city,
            String district,
            LocalDate startDate,
            LocalDate endDate,
            String basis) {

        String selectedBasis = validateHistoricalBasis(basis);

        List<Product> products = productRepository
                .findByStatusAndDeletedAtIsNullOrderByIdAsc(ACTIVE_STATUS);

        // 同一次查詢中的全部商品共用一份天氣資料。
        WeatherInfo weather = loadWeather(city, district, startDate, endDate);
        List<PurchaseOrder> submittedOrders = purchaseOrderRepository
                .findByOrderStatusAndDeletedAtIsNull(SUBMITTED);

        List<OrderRecommendationOptionResponse> results = new ArrayList<>();
        for (Product product : products) {
            results.add(buildRecommendationOption(
                    product, startDate, endDate, selectedBasis,
                    weather, submittedOrders));
        }
        return results;
    }

    /**
     * 查詢指定商品的兩種補貨建議，便於 Postman 驗證單一商品數值。
     */
    public OrderRecommendationOptionResponse getRecommendationOptions(
            Long productId,
            String city,
            String district,
            LocalDate startDate,
            LocalDate endDate,
            String basis) {

        String selectedBasis = validateHistoricalBasis(basis);
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .filter(p -> ACTIVE_STATUS.equals(p.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "商品不存在、已停售或已刪除"));

        WeatherInfo weather = loadWeather(city, district, startDate, endDate);
        List<PurchaseOrder> submittedOrders = purchaseOrderRepository
                .findByOrderStatusAndDeletedAtIsNull(SUBMITTED);

        return buildRecommendationOption(
                product, startDate, endDate, selectedBasis,
                weather, submittedOrders);
    }

    /**
     * 限定歷史銷售基準只有 WEEK / MONTH。
     * 先檢查參數，再呼叫外部天氣 API，避免無效請求浪費 API 額度。
     */
    private String validateHistoricalBasis(String basis) {
        String value = basis == null ? "MONTH"
                : basis.trim().toUpperCase(Locale.ROOT);
        if (!"WEEK".equals(value) && !"MONTH".equals(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "basis 只能是 WEEK 或 MONTH");
        }
        return value;
    }

    /**
     * 計算一項商品的上週／上月淨銷售量，以及一般／天氣兩種建議量。
     *
     * 此方法不負責把商品加入採購單，也不會修改商品庫存。
     */
    private OrderRecommendationOptionResponse buildRecommendationOption(
            Product product,
            LocalDate startDate,
            LocalDate endDate,
            String basis,
            WeatherInfo weather,
            List<PurchaseOrder> submittedOrders) {

        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        // 上週同期：日期平移七天，因此星期幾完全一致。
        LocalDate lastWeekStart = startDate.minusWeeks(1);
        LocalDate lastWeekEnd = endDate.minusWeeks(1);

        // 上月同期：延續原專案的「上月起始日＋相同天數」政策。
        LocalDate lastMonthStart = startDate.minusMonths(1);
        LocalDate lastMonthEnd = lastMonthStart.plusDays(days - 1);

        long lastWeekSales = sumNetSales(
                product.getId(), lastWeekStart, lastWeekEnd);
        long lastMonthSales = sumNetSales(
                product.getId(), lastMonthStart, lastMonthEnd);
        long selectedSales = "WEEK".equals(basis) ? lastWeekSales : lastMonthSales;

        long currentStock = product.getCurrentStock() == null
                ? 0L : product.getCurrentStock().longValue();
        long safetyStock = product.getSafetyStock() == null
                ? 0L : product.getSafetyStock().longValue();
        long outstanding = calculateOutstandingQuantity(
                product.getId(), submittedOrders);
        long availableStock = Math.addExact(currentStock, outstanding);

        // 方案 A：不勾天氣時倍率固定 1.00，仍保留歷史淨銷量。
        long normalQuantity = OrderRecommendationQuantityCalculator.calculate(
                selectedSales, DEFAULT_FACTOR,
                safetyStock, currentStock, outstanding);

        // 勾選天氣：沿用 WeatherService 的期間平均冷飲係數。
        long weatherQuantity = OrderRecommendationQuantityCalculator.calculate(
                selectedSales, weather.factor(),
                safetyStock, currentStock, outstanding);
        long adjustedSales = OrderRecommendationQuantityCalculator.adjustedSales(
                selectedSales, weather.factor());

        OrderRecommendationOptionResponse response =
                new OrderRecommendationOptionResponse();
        response.setProductId(product.getId());
        response.setProductName(product.getName());
        response.setSku(product.getSku());
        response.setForecastStartDate(startDate);
        response.setForecastEndDate(endDate);
        response.setLastWeekStartDate(lastWeekStart);
        response.setLastWeekEndDate(lastWeekEnd);
        response.setLastMonthStartDate(lastMonthStart);
        response.setLastMonthEndDate(lastMonthEnd);
        response.setLastWeekNetSalesQuantity(lastWeekSales);
        response.setLastMonthNetSalesQuantity(lastMonthSales);
        response.setHistoricalBasis(basis);
        response.setSelectedHistoricalSalesQuantity(selectedSales);
        response.setCurrentStock(product.getCurrentStock());
        response.setSafetyStock(product.getSafetyStock());
        response.setOutstandingQuantity(outstanding);
        response.setAvailableStock(availableStock);
        response.setWeatherFactor(weather.factor());
        response.setWeatherAdjustedSalesQuantity(adjustedSales);
        response.setNormalRecommendedQuantity(normalQuantity);
        response.setWeatherRecommendedQuantity(weatherQuantity);
        response.setForecastStatus(weather.status());
        response.setWarning(weather.warning());
        return response;
    }

    /**
     * 沿用 SalesReturn 已完成的淨銷量 SUM 查詢。
     * 左閉右開：[起始日 00:00, 結束日次日 00:00)。
     */
    private long sumNetSales(Long productId, LocalDate start, LocalDate end) {
        Long quantity = salesRecordRepository.sumQuantityByProductAndDateRange(
                productId,
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay());
        return quantity == null ? 0L : quantity;
    }

    /**
     * 取得天氣需求資訊。
     *
     * 若中央氣象署發生上游服務錯誤或逾時，
     * 訂貨建議仍可使用預設倍率 1.00。
     *
     * 日期錯誤、縣市錯誤或行政區錯誤不降級，
     * 必須讓使用者修正輸入。
     */
    private WeatherInfo loadWeather(
            String city,
            String district,
            LocalDate startDate,
            LocalDate endDate) {

        try {

            WeatherForecastResponse forecast =
                    weatherService.getForecast(
                            city,
                            district,
                            startDate,
                            endDate);

            BigDecimal factor = forecast.getWeatherFactor();

            if (factor == null) {
                factor = DEFAULT_FACTOR;
            }

            return new WeatherInfo(
                    factor,
                    forecast.getForecastStatus(),
                    forecast.getWarning());

        } catch (ResponseStatusException ex) {

            int status = ex.getStatusCode().value();

            // 只有上游氣象服務問題才允許降級
            // 400 等使用者輸入錯誤必須繼續向外拋出
            if (status != 502 && status != 504) {
                throw ex;
            }

            return new WeatherInfo(
                    DEFAULT_FACTOR,
                    "UNAVAILABLE",
                    "中央氣象署服務暫時無法使用，"
                            + "已使用天氣倍率 1.00 產生訂貨建議");
        }
    }

    /**
     * 建立單一商品的訂貨建議。
     *
     * 注意：
     * 日期起訖皆包含，但 Repository 使用左閉右開區間。
     */
    private OrderRecommendationResponse buildRecommendation(
            Product product,
            LocalDate startDate,
            LocalDate endDate,
            WeatherInfo weather,
            List<PurchaseOrder> submittedOrders) {

        // 預估期間包含的總天數
        long days = ChronoUnit.DAYS.between(
                startDate, endDate) + 1;

        // 上個月相同天數期間
        // 例如 11/21～11/23 對應 10/21～10/23
        LocalDate historicalStartDate =
                startDate.minusMonths(1);

        LocalDate historicalEndDate =
                historicalStartDate.plusDays(days - 1);

        // 歷史銷售統計使用左閉右開時間範圍
        LocalDateTime historyStart =
                historicalStartDate.atStartOfDay();

        LocalDateTime historyEndExclusive =
                historicalEndDate.plusDays(1).atStartOfDay();

        // 計算歷史銷售總數量
        Long historicalSales =
                salesRecordRepository.sumQuantityByProductAndDateRange(
                        product.getId(),
                        historyStart,
                        historyEndExclusive);

        if (historicalSales == null) {
            historicalSales = 0L;
        }

        // 計算尚未到貨的訂貨數量
        long outstanding = calculateOutstandingQuantity(
                product.getId(),
                submittedOrders);

        // 取得目前庫存及安全庫存
        long currentStock = product.getCurrentStock() == null
                ? 0L
                : product.getCurrentStock().longValue();

        long safetyStock = product.getSafetyStock() == null
                ? 0L
                : product.getSafetyStock().longValue();

        // 天氣調整後需求量
        // 使用 BigDecimal 避免小數計算誤差
        // CEILING 表示無條件進位
        long adjustedSales = BigDecimal
                .valueOf(historicalSales)
                .multiply(weather.factor())
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();

        // 可用庫存 = 目前庫存 + 尚未到貨數量
        long availableStock =
                Math.addExact(currentStock, outstanding);

        // 建議訂貨數量不可小於 0
        long requiredStock =
                Math.addExact(adjustedSales, safetyStock);

        long recommendedQuantity = Math.max(
                0L,
                Math.subtractExact(requiredStock, availableStock));

        // 建立 API 回傳資料
        OrderRecommendationResponse response =
                new OrderRecommendationResponse();

        response.setProductId(product.getId());
        response.setProductName(product.getName());
        response.setSku(product.getSku());

        response.setCurrentStock(product.getCurrentStock());
        response.setSafetyStock(product.getSafetyStock());

        response.setForecastStartDate(startDate);
        response.setForecastEndDate(endDate);

        response.setHistoricalStartDate(historicalStartDate);
        response.setHistoricalEndDate(historicalEndDate);

        response.setHistoricalSalesQuantity(historicalSales);
        response.setOutstandingQuantity(outstanding);
        response.setAvailableStock(availableStock);

        response.setWeatherFactor(weather.factor());
        response.setWeatherAdjustedSalesQuantity(adjustedSales);

        response.setRecommendedQuantity(recommendedQuantity);
        response.setNeedsReorder(recommendedQuantity > 0);

        response.setForecastStatus(weather.status());
        response.setWarning(weather.warning());

        return response;
    }

    /**
     * 計算指定商品尚未到貨的訂貨數量。
     *
     * 只統計 SUBMITTED 且未軟刪除的訂貨單。
     *
     * 目前透過 PurchaseOrder.getItems() 取得明細，
     * 並利用外層唯讀交易處理 LAZY 關聯。
     */
    private long calculateOutstandingQuantity(
            Long productId,
            List<PurchaseOrder> submittedOrders) {

        long total = 0L;

        for (PurchaseOrder order : submittedOrders) {

            for (PurchaseOrderItem item : order.getItems()) {

                if (item.getProduct() == null
                        || item.getProduct().getId() == null) {
                    continue;
                }

                if (!productId.equals(item.getProduct().getId())) {
                    continue;
                }

                if (item.getQuantity() != null) {
                    total = Math.addExact(
                            total,
                            item.getQuantity().longValue());
                }
            }
        }

        return total;
    }

    /**
     * 服務內部傳遞天氣資料使用。
     *
     * 不建立資料表，也不作為 API 回傳 DTO。
     */
    private record WeatherInfo(
            BigDecimal factor,
            String status,
            String warning) {
    }
}
