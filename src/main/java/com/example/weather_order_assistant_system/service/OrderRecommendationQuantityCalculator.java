package com.example.weather_order_assistant_system.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 訂貨建議的純計算工具，與天氣 API、資料庫、HTTP 無關。
 * 將商業公式集中管理，方便用 JUnit 驗證與未來維護。
 */
public final class OrderRecommendationQuantityCalculator {

    private OrderRecommendationQuantityCalculator() {
    }

    /**
     * 根據所選歷史淨銷量與倍率計算預期需求，並無條件進位。
     */
    public static long adjustedSales(long netSales, BigDecimal factor) {
        if (netSales < 0 || factor == null || factor.signum() < 0) {
            throw new IllegalArgumentException("歷史淨銷量與天氣倍率必須有效且不可為負數");
        }
        return BigDecimal.valueOf(netSales)
                .multiply(factor)
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
    }

    /**
     * max(0, ceil(歷史淨銷量 × 倍率) + 安全庫存 - 現貨 - 未到貨量)
     * 未勾天氣時，請將 factor 設為 1.00。
     */
    public static long calculate(long netSales, BigDecimal factor,
            long safetyStock, long currentStock, long outstanding) {

        long demand = adjustedSales(netSales, factor);
        long required = Math.addExact(demand, safetyStock);
        long available = Math.addExact(currentStock, outstanding);
        return Math.max(0L, Math.subtractExact(required, available));
    }
}
