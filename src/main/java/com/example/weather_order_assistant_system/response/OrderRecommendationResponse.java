package com.example.weather_order_assistant_system.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 天氣訂貨建議 API 回傳 DTO。
 *
 * 整合以下資料：
 * 1. 商品基本資料與庫存。
 * 2. 上個月相同天數期間的歷史銷售量。
 * 3. 尚未到貨的採購數量。
 * 4. 中央氣象署天氣需求倍率。
 * 5. 最終建議訂貨數量。
 *
 * 此類別只負責資料傳輸，
 * 實際計算交由 OrderRecommendationService 負責。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderRecommendationResponse {

    // 商品 ID
    private Long productId;

    // 商品名稱
    private String productName;

    // 商品 SKU
    private String sku;

    // 商品目前庫存
    private Integer currentStock;

    // 商品安全庫存
    private Integer safetyStock;

    // 預估需求起始日期（包含當日）
    private LocalDate forecastStartDate;

    // 預估需求結束日期（包含當日）
    private LocalDate forecastEndDate;

    // 歷史銷售比較期間起始日期
    private LocalDate historicalStartDate;

    // 歷史銷售比較期間結束日期
    private LocalDate historicalEndDate;

    // 歷史比較期間的銷售總數量
    private Long historicalSalesQuantity;

    // 已提交但尚未收貨的訂貨數量
    private Long outstandingQuantity;

    // 可用庫存 = 目前庫存 + 尚未到貨數量
    private Long availableStock;

    // 中央氣象署天氣需求倍率
    // 例如：1.00、1.10、1.20
    private BigDecimal weatherFactor;

    // 經過天氣倍率調整後的預估需求量
    // 計算方式：ceil(歷史銷售量 × 天氣倍率)
    private Long weatherAdjustedSalesQuantity;

    // 最終建議訂貨數量
    // max(0, 調整後需求 + 安全庫存 - 目前庫存 - 尚未到貨量)
    private Long recommendedQuantity;

    // 是否需要訂貨
    // 建議訂貨數量大於 0 時為 true
    private Boolean needsReorder;

    // 天氣資料完整性
    // COMPLETE、PARTIAL 或 UNAVAILABLE
    private String forecastStatus;

    // 天氣資料或計算警告訊息
    // 沒有警告時為 null
    private String warning;
}
