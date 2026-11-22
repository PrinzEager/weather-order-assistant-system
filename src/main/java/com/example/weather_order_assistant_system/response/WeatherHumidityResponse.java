package com.example.weather_order_assistant_system.response;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 一週預報中的平均相對濕度時段資料。
 *
 * 缺值使用 null，絕不以 0% 代替。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeatherHumidityResponse {

    // 這筆預報的開始時間（包含）
    private OffsetDateTime startTime;

    // 這筆預報的結束時間（不包含）
    private OffsetDateTime endTime;

    // 平均相對濕度（0～100%），無資料時為 null
    private Integer relativeHumidity;
}
