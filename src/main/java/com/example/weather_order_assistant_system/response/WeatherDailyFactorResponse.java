package com.example.weather_order_assistant_system.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 冷飲業務情境：每日天氣需求係數及各項調整值。
 *
 * 注意：係數是可解釋的 Demo 假設值，非業界實證標準。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeatherDailyFactorResponse {

    // 對應日期（臺灣時間）
    private LocalDate date;

    // 當日有效預報的最高氣溫，無資料為 null
    private Integer maxTemperature;

    // 當日有效時段的加權平均相對濕度，無資料為 null
    private Integer averageRelativeHumidity;

    // 當日可用預報時段中的最高降雨機率，無資料為 null
    private Integer maxPrecipitationProbability;

    // 氣溫區間造成的調整（例如 0.15）
    private BigDecimal temperatureAdjustment;

    // 高溫且高濕造成的調整（例如 0.05）
    private BigDecimal humidityAdjustment;

    // 高降雨機率造成的負向調整（例如 -0.05）
    private BigDecimal rainAdjustment;

    // 1.00 + 上述三項調整
    private BigDecimal weatherFactor;
}
