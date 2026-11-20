package com.example.weather_order_assistant_system.response;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 每日天氣預報摘要回傳 DTO。
 *
 * 用於提供前端未來 3 天的每日天氣卡片資料。
 *
 * 包含：
 * 1. 預報日期。
 * 2. 當日最高氣溫。
 * 3. 當日最低氣溫。
 * 4. 代表性天氣現象。
 * 5. 天氣現象代碼。
 *
 * 此類別只負責資料傳輸，
 * 每日最高、最低氣溫及代表性天氣的計算，
 * 將由 WeatherService 負責處理。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeatherDailyResponse {

    // 預報日期，以台灣當地日期表示
    // 例如：2026-11-21
    private LocalDate date;

    // 當日預報最高氣溫，單位為攝氏度（°C）
    // 若沒有有效氣溫資料，保留 null
    private Integer maxTemperature;

    // 當日預報最低氣溫，單位為攝氏度（°C）
    // 若沒有有效氣溫資料，保留 null
    private Integer minTemperature;

    // 當日代表性天氣現象
    // 例如：晴、多雲、陰、短暫雨
    //
    // 預計選擇當地時間 12:00 所對應的天氣預報時段。
    // 若沒有符合條件的有效資料，保留 null。
    private String weather;

    // 當日代表性天氣現象代碼
    // 例如："01"、"04"、"07"
    //
    // 使用 String 保留前置零，
    // 由前端依照代碼決定顯示的天氣圖示。
    private String weatherCode;
}
