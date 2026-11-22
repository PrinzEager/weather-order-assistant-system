"use strict";

/**
 * 訂貨日期有效性保護（前端輔助層）。
 *
 * 1. 用台灣日期／中午 12:00 作為前端檢查時點；跨日提醒重新選擇日期。
 * 2. 當訂貨預覽跨日或跨截止時間時，攔截本次提交，保留輸入以供重新確認。
 * 3. 過去日期的 DRAFT 不讓使用者從列表直接按「送出」。
 * 4. 只攔截 UI 事件，不自動 DELETE／CANCEL 已建立訂單。
 *
 * 安全邊界：前端驗證無法代替 Spring Boot Service 的交易內驗證。
 * 本檔不改寫任何原有訂貨／採購模組的事件處理函式。
 */
import { addDays, getTaiwanToday, showMessage } from "./common.js";

const ZONE = "Asia/Taipei";

let observedDay = getTaiwanToday();

let observedCutoffSlot = null;

let queriedRecommendationDay = null;

let staleRecommendations = false;

let batchOpenedAt = null;

let singleFormOpenedDay = null;

let lastNoticeKey = "";

function currentSlot() {
    const today = getTaiwanToday();
    const parts = new Intl.DateTimeFormat("en-GB", {
        timeZone: ZONE, hour: "2-digit", hourCycle: "h23"
    }).formatToParts(new Date());
    const hour = Number(parts.find(part => part.type === "hour")?.value ?? 0);
    return { today, slot: hour < 12 ? "before-noon" : "after-noon" };
}

function isRelevantPage() {
    return Boolean(document.querySelector('.nav-item.active[data-page="recommendations"], .nav-item.active[data-page="orders"]'));
}

function showOrderWarning(message, noticeId = message) {
    showMessage(message, "error");
    const detail = document.getElementById("recommendation-order-warning");
    const panel = document.getElementById("recommendation-order-panel");
    if (detail && panel && !panel.hidden)
        detail.textContent = message;
    // 同一個日期或同一個狀態不連續彈出多次提示。
    if (lastNoticeKey !== noticeId) {
        lastNoticeKey = noticeId;
        window.alert(message);
    }
}

function stop(event, message, key) {
    event.preventDefault();
    event.stopImmediatePropagation();
    showOrderWarning(message, key);
}

function checkClock(showNotice = true) {
    const { today, slot } = currentSlot();
    // 非跨日但已跨越 12:00：訂貨確認視窗若仍開啟，提醒重新取得政策。
    const cutoffCrossed = today === observedDay &&
        observedCutoffSlot === "before-noon" && slot === "after-noon";
    observedCutoffSlot = slot;
    if (today === observedDay) {
        const panel = document.getElementById("recommendation-order-panel");
        if (cutoffCrossed && panel && !panel.hidden && showNotice && isRelevantPage()) {
            showOrderWarning("目前已越過中午 12:00 的訂貨截止時點，請關閉後重新開啟確認視窗，以取得最新的最早到貨日期。", `noon:${today}`);
        }
        return false;
    }
    const previous = observedDay;
    observedDay = today;
    // 已取得的訂貨建議屬舊日資料；必須在新日重新查詢。
    staleRecommendations = true;

    // 只更新有效的選取上下限，不覆寫使用者已選的日期／數量。
    for (const id of ["recommendation-start-date", "recommendation-end-date"]) {
        const field = document.getElementById(id);
        if (!field)
            continue;
        field.min = today;
        field.max = addDays(today, 6);
    }
    const dateField = document.getElementById("recommendation-delivery-date");
    if (dateField)
        dateField.min = today; // 最終最早到貨日仍以後端政策為準。
    const singleDate = document.getElementById("order-expected-delivery-date");
    if (singleDate)
        singleDate.min = today;

    if (showNotice && isRelevantPage()) {
        showOrderWarning(`頁面已由 ${previous} 跨日至 ${today}。舊訂貨資料不能直接提交，請重新選擇日期並查詢訂貨建議。`, `day:${today}`);
    }
    return true;
}

function validNewRecommendationRange() {
    const today = getTaiwanToday();
    const start = document.getElementById("recommendation-start-date")?.value;
    const end = document.getElementById("recommendation-end-date")?.value;
    return Boolean(start && end && start <= end && start >= today && end <= addDays(today, 6));
}

function onSubmitCapture(event) {
    const form = event.target;
    if (!(form instanceof HTMLFormElement))
        return;
    checkClock();
    const today = getTaiwanToday();

    if (form.id === "recommendation-search-form") {
        if (validNewRecommendationRange()) {
            queriedRecommendationDay = today;
            staleRecommendations = false;
            batchOpenedAt = null;
            lastNoticeKey = "";
        }
        return; // 原本模組繼續負責查詢與日期驗證。
    }

    if (form.id === "recommendation-order-form") {
        const current = currentSlot();
        if (staleRecommendations ||
            (queriedRecommendationDay && queriedRecommendationDay !== today) ||
            (batchOpenedAt && (batchOpenedAt.today !== current.today || batchOpenedAt.slot !== current.slot))) {
            stop(event, "訂貨期間已跨日，或已越過中午 12:00 的訂貨截止時點。本次建立草稿已中止，請重新查詢或關閉後重新開啟訂貨確認視窗，確認到貨日期與供應商價格。", `batch:${current.today}:${current.slot}`);
            return;
        }
        const date = document.getElementById("recommendation-delivery-date");
        if (date?.value && date.min && date.value < date.min) {
            stop(event, "預計到貨日期已不符合目前限制，請重新選擇日期後再送出。", `batch-date:${date.value}:${date.min}`);
        }
        // 正確的最早到貨日必須由現有 delivery-policy API 及後端 Service 最終驗證。
    }

    if (form.id === "order-form") {
        const date = document.getElementById("order-expected-delivery-date");
        if (singleFormOpenedDay && singleFormOpenedDay !== today) {
            stop(event, "採購單編輯視窗已跨日，本次儲存已中止。請先關閉並重新開啟，重新確認訂貨及預計到貨日期。", `single:${today}`);
            return;
        }
        if (date?.value && date.value < today) {
            stop(event, "預計到貨日期不可早於台灣今天，請重新選擇。", `single-date:${date.value}:${today}`);
        }
    }
}

function onClickCapture(event) {
    if (!(event.target instanceof Element))
        return;
    checkClock();
    const button = event.target.closest("button");
    if (!button)
        return;
    const today = getTaiwanToday();

    if (button.id === "create-order-from-recommendation") {
        if (staleRecommendations || (queriedRecommendationDay && queriedRecommendationDay !== today)) {
            stop(event, "已跨日，請重新選擇預測期間並產生訂貨建議，確認商品數量後再建立採購草稿。", `re-query:${today}`);
            return;
        }
        batchOpenedAt = currentSlot();
    }

    if (button.id === "add-order-button" ||
        (button.closest("#order-table-body") && button.textContent.trim() === "編輯")) {
        singleFormOpenedDay = today;
    }

    // 本守衛只中止跨日舊草稿的「送出」，不擅自變更資料庫狀態。
    if (button.closest("#order-table-body") && button.textContent.trim() === "送出") {
        const row = button.closest("tr");
        // 現有採購單清單第 3 欄為後端建立的 orderDate。
        const orderDate = row?.cells?.[2]?.textContent?.trim()?.slice(0, 10) ?? "";
        if (/^\d{4}-\d{2}-\d{2}$/.test(orderDate) && orderDate < today) {
            stop(event, `這張採購草稿建立於 ${orderDate}，與今天 ${today} 不同。本次送出已取消，但不會自動刪除資料庫草稿。請取消原草稿並重新下訂。`, `draft:${row?.cells?.[0]?.textContent}:${today}`);
        }
    }
}

function startGuard() {
    // 在 capture 階段驗證，確保原訂貨 JS 的 submit/click handler 不先呼叫 API。
    observedCutoffSlot = currentSlot().slot;
    document.addEventListener("submit", onSubmitCapture, true);
    document.addEventListener("click", onClickCapture, true);
    document.addEventListener("visibilitychange", () => {
        if (!document.hidden)
            checkClock();
    });
    window.addEventListener("focus", () => checkClock());
    // 使用者長時間停在同一頁面沒有操作時，仍能在跨日附近看到提醒。
    window.setInterval(() => {
        if (!document.hidden)
            checkClock();
    }, 30000);
}

document.addEventListener("DOMContentLoaded", startGuard);

export { currentSlot, validNewRecommendationRange };
