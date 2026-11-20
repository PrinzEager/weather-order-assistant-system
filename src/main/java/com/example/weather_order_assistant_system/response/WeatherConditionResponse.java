package com.example.weather_order_assistant_system.response;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 天氣現象預報回傳 DTO。
 *
 * 用於回傳中央氣象署各時段的天氣現象與天氣代碼。
 *
 * 提供前端顯示未來 3 天的天氣描述及天氣圖示，
 * 例如：晴、多雲、陰、短暫雨等。
 *
 * 此類別只負責 API 資料傳輸，
 * 不負責天氣圖示映射或天氣需求倍率計算。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeatherConditionResponse {

    // 天氣現象預報時段的開始時間
    // 例如：2026-11-21T12:00:00+08:00
    private OffsetDateTime startTime;

    // 天氣現象預報時段的結束時間
    // 例如：2026-11-21T15:00:00+08:00
    private OffsetDateTime endTime;

    // 中央氣象署提供的天氣現象描述
    // 例如：晴、多雲、陰、短暫雨
    private String weather;

    // 中央氣象署提供的天氣現象代碼
    //
    // 必須使用 String，保留前置零。
    // 例如："01"、"04"、"07"。
    //
    // 前端未來可依此代碼對應不同天氣圖示。
    // 若中央氣象署未提供有效代碼，保留 null。
    private String weatherCode;
}
