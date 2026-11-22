package com.example.weather_order_assistant_system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * 台灣訂貨日期核心規則測試：固定 UTC 時鐘後換算至 Asia/Taipei。
 * 測試與執行主機所在時區無關。
 */
class PurchaseOrderTimePolicyTest {

    private static Clock fixed(String instantUtc) {
        return Clock.fixed(Instant.parse(instantUtc), ZoneOffset.UTC);
    }

    @Test
    void 台灣當天的草稿可以進入後續送出驗證() {
        assertTrue(PurchaseOrderTimePolicy.isDraftCreatedToday(
                LocalDateTime.of(2026, 11, 22, 11, 0), fixed("2026-11-22T05:00:00Z")));
    }

    @Test
    void 台灣跨日後昨天的草稿不可直接送出() {
        // UTC 11/22 16:00 = 台灣 11/23 00:00。
        assertFalse(PurchaseOrderTimePolicy.isDraftCreatedToday(
                LocalDateTime.of(2026, 11, 22, 23, 59), fixed("2026-11-22T16:00:00Z")));
    }

    @Test
    void 未來日期及缺少訂貨日期不可直接送出() {
        Clock clock = fixed("2026-11-22T05:00:00Z");
        assertFalse(PurchaseOrderTimePolicy.isDraftCreatedToday(
                LocalDateTime.of(2026, 11, 23, 0, 1), clock));
        assertFalse(PurchaseOrderTimePolicy.isDraftCreatedToday(null, clock));
    }

    @Test
    void 中午前的批次最早到貨日是隔天() {
        // 台灣 11/22 11:59:59。
        assertEquals(LocalDate.of(2026, 11, 23),
                PurchaseOrderTimePolicy.earliestBatchDeliveryDate(fixed("2026-11-22T03:59:59Z")));
    }

    @Test
    void 中午十二點整開始最早到貨日改成後天() {
        // 台灣 11/22 12:00:00。
        assertEquals(LocalDate.of(2026, 11, 24),
                PurchaseOrderTimePolicy.earliestBatchDeliveryDate(fixed("2026-11-22T04:00:00Z")));
    }

    @Test
    void 批次到貨日排除不合法日期與空值() {
        Clock clock = fixed("2026-11-22T04:00:00Z");
        assertFalse(PurchaseOrderTimePolicy.isValidBatchDeliveryDate(null, clock));
        assertFalse(PurchaseOrderTimePolicy.isValidBatchDeliveryDate(LocalDate.of(2026, 11, 23), clock));
        assertTrue(PurchaseOrderTimePolicy.isValidBatchDeliveryDate(LocalDate.of(2026, 11, 24), clock));
    }

    @Test
    void 月底跨月的批次到貨日正常計算() {
        // 台灣 2026/11/30 12:00 → 12/02。
        assertEquals(LocalDate.of(2026, 12, 2),
                PurchaseOrderTimePolicy.earliestBatchDeliveryDate(fixed("2026-11-30T04:00:00Z")));
    }
}
