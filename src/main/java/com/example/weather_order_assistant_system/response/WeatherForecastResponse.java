package com.example.weather_order_assistant_system.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 天氣預報 API 主要回傳 DTO。
 *
 * 整合中央氣象署未來 3 天天氣預報資料，
 * 提供前端天氣展示及後續訂貨建議計算使用。
 *
 * 主要包含：
 * 1. 查詢地點及日期範圍。
 * 2. 查詢期間最高氣溫及最高降雨機率。
 * 3. 天氣需求調整倍率。
 * 4. 預報資料完整性與警告訊息。
 * 5. 每日天氣摘要及各時段預報資料。
 *
 * 此類別只負責 API 資料傳輸，
 * 實際天氣資料處理及倍率計算由 WeatherService 負責。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeatherForecastResponse {

    // 查詢縣市，例如：高雄市
    private String city;

    // 查詢行政區，例如：路竹區
    private String district;

    // 預報查詢起始日期，包含當日
    private LocalDate startDate;

    // 預報查詢結束日期，包含當日
    private LocalDate endDate;

    // 查詢期間的最高有效氣溫，單位為攝氏度
    // 若沒有有效氣溫資料，保留 null
    private Integer maxTemperature;

    // 查詢期間最高有效的 3 小時降雨機率
    // 數值範圍 0～100，若沒有有效資料則為 null
    private Integer maxPrecipitationProbability;

    // 天氣需求倍率
    //
    // 規則：
    // 最高氣溫 >= 32°C：增加 0.10
    // 最高降雨機率 >= 60%：增加 0.10
    //
    // 最終倍率為 1.00、1.10 或 1.20
    //
    // 若完全沒有有效氣溫及降雨機率資料，
    // 倍率固定為 1.00
    private BigDecimal weatherFactor;

    // 預報資料完整性狀態
    //
    // COMPLETE：查詢的可預報區間資料完整
    // PARTIAL：只有部分預報資料有效
    // UNAVAILABLE：完全沒有有效氣溫及降雨機率資料
    private String forecastStatus;

    // 預報警告訊息
    //
    // COMPLETE 時通常為 null
    // PARTIAL 或 UNAVAILABLE 時提供資料缺失說明
    private String warning;

    // 每日天氣摘要
    // 提供前端顯示未來 3 天天氣卡片
    private List<WeatherDailyResponse> dailyForecasts;

    // 各時間點的溫度預報
    // 提供前端繪製溫度變化圖表
    private List<WeatherTemperatureResponse> temperatureForecasts;

    // 各時段的 3 小時降雨機率預報
    // 提供前端繪製降雨機率圖表
    private List<WeatherPrecipitationResponse> precipitationForecasts;

    // 各時段的天氣現象預報
    // 提供前端對應天氣圖示
    private List<WeatherConditionResponse> weatherConditions;
}
