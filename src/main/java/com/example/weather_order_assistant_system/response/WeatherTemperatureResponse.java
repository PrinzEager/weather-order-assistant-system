package com.example.weather_order_assistant_system.response;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 天氣溫度預報回傳 DTO。
 *
 * 用於回傳中央氣象署各時間點的溫度預報，
 * 提供前端繪製未來 3 天的溫度變化圖表。
 *
 * 此類別僅負責 API 資料傳輸，
 * 不處理天氣倍率計算或其他商業邏輯。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeatherTemperatureResponse {

    // 預報時間，保留中央氣象署提供的 +08:00 時區偏移
    // 例如：2026-11-21T12:00:00+08:00
    private OffsetDateTime dataTime;

    // 該時間點的預報溫度，單位為攝氏度（°C）
    // 若中央氣象署沒有提供有效數值，允許為 null
    private Integer temperature;
}
