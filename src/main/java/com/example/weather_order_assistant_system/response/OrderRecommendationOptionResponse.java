package com.example.weather_order_assistant_system.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 訂貨建議「可選方案」DTO。
 *
 * 同時回傳上週／上月淨銷量、一般建議與天氣建議；
 * 是否勾選天氣及是否加入草稿由前端逐商品自行決定。
 * 不影響原本的 OrderRecommendationResponse 與 API。
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderRecommendationOptionResponse {

    // 商品基本資料
    private Long productId;
    private String productName;
    private String sku;

    // 預估需求日期區間（起訖皆包含）
    private LocalDate forecastStartDate;
    private LocalDate forecastEndDate;

    // 同星期幾的上週同期日期
    private LocalDate lastWeekStartDate;
    private LocalDate lastWeekEndDate;

    // 上月同期：上月對應起始日，往後取相同日數
    private LocalDate lastMonthStartDate;
    private LocalDate lastMonthEndDate;

    // 兩組歷史淨銷量 = 原始銷量 - 累計退貨量
    private Long lastWeekNetSalesQuantity;
    private Long lastMonthNetSalesQuantity;

    // WEEK 或 MONTH；只決定本次計算要採用哪組歷史銷售
    private String historicalBasis;
    private Long selectedHistoricalSalesQuantity;

    // 庫存、未到貨與可用庫存
    private Integer currentStock;
    private Integer safetyStock;
    private Long outstandingQuantity;
    private Long availableStock;

    // 冷飲天氣模型的期間係數，只在使用者勾選時採用
    private BigDecimal weatherFactor;
    private Long weatherAdjustedSalesQuantity;

    // 未勾選天氣：選定歷史淨銷量 + 安全庫存 - 可用庫存
    private Long normalRecommendedQuantity;

    // 勾選天氣：ceil(選定歷史淨銷量 × 天氣係數) + 安全庫存 - 可用庫存
    private Long weatherRecommendedQuantity;

    // 保留氣象資料品質提示
    private String forecastStatus;
    private String warning;
}
