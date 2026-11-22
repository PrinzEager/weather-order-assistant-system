package com.example.weather_order_assistant_system.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 採購草稿的台灣時間截單日期規則。
 * 11:59:59 前最早隔天；12:00:00（含）以後最早後天。
 * 提供 Clock 參數是為了讓 JUnit 能穩定測試 12 點邊界，不需修改系統時間。
 */
public final class PurchaseDeliveryDatePolicy {

    public static final ZoneId TAIWAN_ZONE = ZoneId.of("Asia/Taipei");
    private static final LocalTime CUTOFF_TIME = LocalTime.NOON;

    private PurchaseDeliveryDatePolicy() {
        // 工具類別不允許直接建立實例。
    }

    /** 正式執行時，使用台灣時區的現在時間。 */
    public static LocalDate earliestDeliveryDate() {
        return earliestDeliveryDate(Clock.system(TAIWAN_ZONE));
    }

    /** 可測試版本：一律轉成台灣當地時間，避免系統預設時區影響結果。 */
    public static LocalDate earliestDeliveryDate(Clock clock) {
        Objects.requireNonNull(clock, "clock 不可為 null");
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(TAIWAN_ZONE));
        return now.toLocalTime().isBefore(CUTOFF_TIME)
                ? now.toLocalDate().plusDays(1)
                : now.toLocalDate().plusDays(2);
    }
}
