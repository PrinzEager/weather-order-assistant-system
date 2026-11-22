package com.example.weather_order_assistant_system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/** 透過固定時鐘驗證台灣時間中午 12:00 的邊界。 */
class PurchaseDeliveryDatePolicyTest {
    private static final ZoneId TAIWAN = ZoneId.of("Asia/Taipei");

    private Clock at(int hour, int minute, int second) {
        return Clock.fixed(ZonedDateTime.of(2026, 11, 22,
                hour, minute, second, 0, TAIWAN).toInstant(), TAIWAN);
    }

    @Test
    void beforeNoonAllowsTomorrow() {
        assertEquals(LocalDate.of(2026, 11, 23),
                PurchaseDeliveryDatePolicy.earliestDeliveryDate(at(11, 59, 59)));
    }

    @Test
    void exactlyNoonRequiresDayAfterTomorrow() {
        assertEquals(LocalDate.of(2026, 11, 24),
                PurchaseDeliveryDatePolicy.earliestDeliveryDate(at(12, 0, 0)));
    }

    @Test
    void afterNoonRequiresDayAfterTomorrow() {
        assertEquals(LocalDate.of(2026, 11, 24),
                PurchaseDeliveryDatePolicy.earliestDeliveryDate(at(12, 0, 1)));
    }

    @Test
    void nonTaiwanClockZoneStillUsesTaiwanLocalTime() {
        Clock utcClock = Clock.fixed(ZonedDateTime.of(2026, 11, 22,
                11, 59, 0, 0, TAIWAN).toInstant(), ZoneId.of("UTC"));
        assertEquals(LocalDate.of(2026, 11, 23),
                PurchaseDeliveryDatePolicy.earliestDeliveryDate(utcClock));
    }
}
