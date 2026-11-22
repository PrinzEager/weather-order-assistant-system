package com.example.weather_order_assistant_system.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 天氣訂貨建議頁面專用的整合回傳 DTO。
 *
 * 商品補貨建議與每天的天氣調整明細共用一次預報結果，
 * 避免重新查詢後因預報更新而出現畫面倍率不一致。
 * 不需新增資料表，也不修改原有訂貨計算公式。
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderRecommendationExplanationResponse {

    // 本次已成功產生訂貨建議的查詢條件
    private String city;
    private String district;
    private LocalDate startDate;
    private LocalDate endDate;

    // 後端真正用來計算商品建議的「四捨五入後」期間倍率
    private BigDecimal weatherFactor;
    private String forecastStatus;
    private String warning;

    // 每日倍率拆解：日最高溫、濕度、降雨與三種調整值
    private List<WeatherDailyFactorResponse> dailyWeatherFactors;

    // 每項商品的一般補貨建議與天氣補貨建議
    private List<OrderRecommendationOptionResponse> recommendations;
}
