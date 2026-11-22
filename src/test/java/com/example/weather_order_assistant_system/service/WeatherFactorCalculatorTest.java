package com.example.weather_order_assistant_system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.weather_order_assistant_system.response.WeatherDailyFactorResponse;

/**
 * 冷飲天氣係數邊界測試：不需要資料庫或真的呼叫氣象署 API。
 */
class WeatherFactorCalculatorTest {

    private final WeatherFactorCalculator calculator = new WeatherFactorCalculator();
    private final LocalDate date = LocalDate.of(2026, 11, 22);

    @Test
    void temperatureThresholdsAreLeftClosed() {
        assertFactor("1.00", 24, null, null);
        assertFactor("1.05", 25, null, null);
        assertFactor("1.05", 29, null, null);
        assertFactor("1.10", 30, null, null);
        assertFactor("1.10", 31, null, null);
        assertFactor("1.15", 32, null, null);
        assertFactor("1.15", 33, null, null);
        assertFactor("1.20", 34, null, null);
    }

    @Test
    void humidityOnlyAddsDemandOnHotDays() {
        assertFactor("1.05", 29, 95, null);
        assertFactor("1.10", 30, 59, null);
        assertFactor("1.15", 30, 60, null);
    }

    @Test
    void rainDeductsFivePercentOnlyWhenAvailableAndHigh() {
        assertFactor("1.10", 30, null, null);
        assertFactor("1.10", 30, null, 59);
        assertFactor("1.05", 30, null, 60);
    }

    @Test
    void threeFactorsAddUpWithoutMultiplication() {
        // 33°C +0.15；濕度 75% +0.05；降雨 70% -0.05。
        WeatherDailyFactorResponse day = calculator.calculateDaily(
                date, 33, 75, 70);
        assertEquals(new BigDecimal("1.15"), day.getWeatherFactor());
        assertEquals(new BigDecimal("-0.05"), day.getRainAdjustment());
    }

    @Test
    void missingDataStaysNullAndCountsAsNeutralDay() {
        WeatherDailyFactorResponse missing = calculator.calculateDaily(
                date, null, null, null);
        assertNull(missing.getMaxTemperature());
        assertEquals(new BigDecimal("1.00"), missing.getWeatherFactor());

        WeatherDailyFactorResponse hot = calculator.calculateDaily(
                date.plusDays(1), 34, 80, null);
        // (1.00 + 1.25) / 2 = 1.125，四捨五入為 1.13。
        assertEquals(new BigDecimal("1.13"),
                calculator.calculatePeriodAverage(List.of(missing, hot)));
    }

    @Test
    void emptyPeriodIsNeutral() {
        assertEquals(new BigDecimal("1.00"),
                calculator.calculatePeriodAverage(List.of()));
    }

    private void assertFactor(
            String expected, Integer temperature, Integer humidity, Integer rain) {
        assertEquals(new BigDecimal(expected),
                calculator.calculateDaily(date, temperature, humidity, rain)
                        .getWeatherFactor());
    }
}
