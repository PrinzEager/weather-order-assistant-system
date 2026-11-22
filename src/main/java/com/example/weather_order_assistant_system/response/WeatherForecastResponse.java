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
 * 整合中央氣象署未來 7 天天氣預報資料，
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

    // 查詢期間最高有效的 12 小時降雨機率
    // 數值範圍 0～100，若沒有有效資料則為 null
    private Integer maxPrecipitationProbability;

    // 冷飲業務情境的七天平均天氣需求倍率。
    // 每日：1.00 + 氣溫調整 + 高溫濕度調整 + 降雨調整。
    // 缺值採中性調整，平均結果四捨五入到小數 2 位。
    // 此係數不應直接套用到非冷飲商品。
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
    // 提供前端顯示未來 7 天天氣卡片
    private List<WeatherDailyResponse> dailyForecasts;

    // 逐 12 小時的平均溫度預報（資料點時間為該時段中點）
    // 以原本 WeatherTemperatureResponse 的格式供前端繪圖
    private List<WeatherTemperatureResponse> temperatureForecasts;

    // 各時段的 12 小時降雨機率預報（主要只有前三天）
    // 缺值不當成零，供前端繪製降雨機率圖表
    private List<WeatherPrecipitationResponse> precipitationForecasts;

    // 各時段的天氣現象預報
    // 提供前端對應天氣圖示
    private List<WeatherConditionResponse> weatherConditions;

    // 官方逐 12 小時的平均相對濕度預報，無值為 null。
    private List<WeatherHumidityResponse> humidityForecasts;

    // 各日冷飲需求係數明細：供前端解釋七天平均係數。
    private List<WeatherDailyFactorResponse> dailyWeatherFactors;
}
