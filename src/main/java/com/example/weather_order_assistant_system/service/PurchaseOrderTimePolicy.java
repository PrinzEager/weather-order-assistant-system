package com.example.weather_order_assistant_system.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 訂貨日期規則：所有比較均以台灣（Asia/Taipei）時間為準。
 *
 * 這個類別只負責日期判斷，不存取資料庫，也不會擅自取消或刪除採購單。
 * 可供既有 PurchaseOrderService、批次建單 Service 共用。
 *
 * 注意：資料表 order_date 是 LocalDateTime（沒有時區資訊）。
 * 因此系統建立訂單時也必須以「台灣當地時間」寫入 order_date，
 * 否則上線到 UTC 時區的主機後，跨日判斷可能有誤。
 */
public final class PurchaseOrderTimePolicy {

    public static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private static final LocalTime DELIVERY_CUTOFF = LocalTime.NOON;

    private PurchaseOrderTimePolicy() {
        // 純工具類別，不允許建立實例。
    }

    /**
     * 判斷草稿是否於台灣當天建立。
     *
     * 用途：DRAFT -> SUBMITTED 前的日期驗證。
     * 對於 null、昨天或未來日期，都視為不可直接送出，避免時鐘或資料異常被忽略。
     *
     * @param orderDate 資料庫儲存的台灣當地訂貨時間
     * @param clock 可注入的時鐘，測試時可固定跨日時間點
     * @return 當天建立則 true；否則 false
     */
    public static boolean isDraftCreatedToday(LocalDateTime orderDate, Clock clock) {
        Objects.requireNonNull(clock, "clock 不可為 null");
        if (orderDate == null) {
            return false;
        }

        LocalDate todayInTaiwan = LocalDate.now(clock.withZone(TAIPEI));
        return orderDate.toLocalDate().equals(todayInTaiwan);
    }

    /**
     * 計算批次草稿的最早到貨日。
     *
     * 台灣當天 12:00 前（不含 12:00）可選隔天；
     * 12:00 起（包含 12:00）最早只能選後天。
     *
     * 此處僅集中日期算法，不會自行替換既有批次 Service 的強制驗證。
     */
    public static LocalDate earliestBatchDeliveryDate(Clock clock) {
        Objects.requireNonNull(clock, "clock 不可為 null");

        ZonedDateTime nowTaiwan = clock.instant().atZone(TAIPEI);
        int earliestDays = nowTaiwan.toLocalTime().isBefore(DELIVERY_CUTOFF) ? 1 : 2;
        return nowTaiwan.toLocalDate().plusDays(earliestDays);
    }

    /**
     * 批次建單預計到貨日是否合法。
     * 批次草稿日期屬必填，未傳、過去日期或截止政策不允許的日期皆回傳 false。
     *
     * 注意：此規則只適用於已要求日期必填的「批次草稿 API」。
     * 既有單張採購 API 的到貨日期可能可留空，不應在未核對原始碼前直接套用。
     */
    public static boolean isValidBatchDeliveryDate(LocalDate expectedDeliveryDate, Clock clock) {
        return expectedDeliveryDate != null
                && !expectedDeliveryDate.isBefore(earliestBatchDeliveryDate(clock));
    }
}
