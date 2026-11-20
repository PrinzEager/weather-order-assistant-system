package com.example.weather_order_assistant_system.response;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 天氣降雨機率預報回傳 DTO。
 *
 * 用於回傳中央氣象署每 3 小時的降雨機率預報。
 *
 * 提供前端繪製未來 3 天的降雨機率圖表，
 * 並讓後端天氣服務取得各時段的降雨機率資料。
 *
 * 此類別僅負責 API 資料傳輸，
 * 不負責天氣需求倍率計算。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeatherPrecipitationResponse {

    // 降雨機率預報時段的開始時間
    // 例如：2026-11-21T12:00:00+08:00
    private OffsetDateTime startTime;

    // 降雨機率預報時段的結束時間
    // 例如：2026-11-21T15:00:00+08:00
    private OffsetDateTime endTime;

    // 該時段的降雨機率，單位為百分比（%）
    //
    // 數值範圍為 0～100。
    // 0 表示有效的 0% 降雨機率。
    // null 表示中央氣象署沒有提供有效資料。
    //
    // 注意：不可將 null 自動轉換為 0。
    private Integer precipitationProbability;
}
