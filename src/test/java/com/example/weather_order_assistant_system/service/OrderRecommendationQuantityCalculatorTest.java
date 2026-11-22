package com.example.weather_order_assistant_system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 不依賴資料庫或中央氣象署，單獨驗證兩種訂貨公式。
 */
class OrderRecommendationQuantityCalculatorTest {

    @Test
    void normalFormulaKeepsHistoricalSales() {
        // 方案 A：100 + 20 - 30 - 10 = 80。
        assertEquals(80L, OrderRecommendationQuantityCalculator.calculate(
                100L, new BigDecimal("1.00"), 20L, 30L, 10L));
    }

    @Test
    void weatherFormulaAdjustsOnlyDemand() {
        // 套用 1.05：ceil(100 × 1.05) + 20 - 30 - 10 = 85。
        assertEquals(85L, OrderRecommendationQuantityCalculator.calculate(
                100L, new BigDecimal("1.05"), 20L, 30L, 10L));
    }

    @Test
    void fractionalWeatherSalesAreRoundedUp() {
        // ceil(21 × 1.05) = ceil(22.05) = 23。
        assertEquals(23L, OrderRecommendationQuantityCalculator.adjustedSales(
                21L, new BigDecimal("1.05")));
    }

    @Test
    void recommendationNeverDropsBelowZero() {
        assertEquals(0L, OrderRecommendationQuantityCalculator.calculate(
                10L, new BigDecimal("1.00"), 5L, 200L, 0L));
    }

    @Test
    void weatherFactorMustNotBeNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> OrderRecommendationQuantityCalculator.adjustedSales(
                        10L, new BigDecimal("-0.10")));
    }
}
