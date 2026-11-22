package com.example.weather_order_assistant_system.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.weather_order_assistant_system.response.WeatherDailyFactorResponse;

/**
 * 冷飲的規則式天氣需求係數計算器。
 *
 * 本版僅為冷飲 Demo 的商業假設，不是所有商品的通用倍率。
 * 對單日先計算「1 + 溫度調整 + 濕度調整 + 降雨調整」，
 * 再對查詢範圍內的每一天取算術平均。
 *
 * 無資料時該項調整為 0；沒有資料「不等於」濕度或降雨為 0。
 */
@Component
public class WeatherFactorCalculator {

    // 冷飲需求基準倍率
    private static final BigDecimal BASE_FACTOR = new BigDecimal("1.00");

    private static final BigDecimal FIVE_PERCENT = new BigDecimal("0.05");
    private static final BigDecimal TEN_PERCENT = new BigDecimal("0.10");
    private static final BigDecimal FIFTEEN_PERCENT = new BigDecimal("0.15");
    private static final BigDecimal TWENTY_PERCENT = new BigDecimal("0.20");
    private static final BigDecimal NEGATIVE_FIVE_PERCENT = new BigDecimal("-0.05");

    /**
     * 依單日氣象條件計算冷飲的需求係數。
     *
     * @param date 日期
     * @param maxTemperature 當日有效最高氣溫，允許 null
     * @param averageHumidity 當日加權平均相對濕度，允許 null
     * @param maxRain 當日有效降雨機率最高值，允許 null
     */
    public WeatherDailyFactorResponse calculateDaily(
            LocalDate date,
            Integer maxTemperature,
            Integer averageHumidity,
            Integer maxRain) {

        BigDecimal temperatureAdjustment = calculateTemperature(maxTemperature);

        // 濕度僅於氣溫達 30°C、濕度達 60% 時增加 5%。
        // 避免低溫高濕被誤判成冷飲旺季。
        BigDecimal humidityAdjustment =
                maxTemperature != null && maxTemperature >= 30
                && averageHumidity != null && averageHumidity >= 60
                ? FIVE_PERCENT : BigDecimal.ZERO.setScale(2);

        // 降雨 >= 60% 時，假設冷飲現場來客下降 5%。
        // 官方第 4～7 天未提供降雨機率時，使用中性調整。
        BigDecimal rainAdjustment =
                maxRain != null && maxRain >= 60
                ? NEGATIVE_FIVE_PERCENT : BigDecimal.ZERO.setScale(2);

        BigDecimal factor = BASE_FACTOR
                .add(temperatureAdjustment)
                .add(humidityAdjustment)
                .add(rainAdjustment);

        return new WeatherDailyFactorResponse(
                date,
                maxTemperature,
                averageHumidity,
                maxRain,
                temperatureAdjustment,
                humidityAdjustment,
                rainAdjustment,
                factor);
    }

    /**
     * 查詢期間的天氣係數：每天算一次，再取平均。
     *
     * 即使某一天資料不足，該日仍以中性調整計入分母，
     * 不會因為跳過缺值日而高估整段期間的熱度。
     * 資料是否不完整由 WeatherService 的 forecastStatus 提醒。
     */
    public BigDecimal calculatePeriodAverage(
            List<WeatherDailyFactorResponse> days) {

        if (days == null || days.isEmpty()) {
            return BASE_FACTOR;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (WeatherDailyFactorResponse day : days) {
            total = total.add(day.getWeatherFactor());
        }
        return total.divide(
                BigDecimal.valueOf(days.size()),
                2,
                RoundingMode.HALF_UP);
    }

    /**
     * 氣溫的連續區間使用左閉右開；34°C 以上為最高區間。
     */
    private BigDecimal calculateTemperature(Integer temperature) {

        if (temperature == null || temperature < 25) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (temperature < 30) {
            return FIVE_PERCENT;
        }
        if (temperature < 32) {
            return TEN_PERCENT;
        }
        if (temperature < 34) {
            return FIFTEEN_PERCENT;
        }
        return TWENTY_PERCENT;
    }
}
