"use strict";

/**
 * Phase 9｜首頁待處理訂貨單數量。
 *
 * 只讀取 GET /api/purchase-orders，不建立或更新訂單，也不改動既有採購頁。
 * 本統計包含 DRAFT（待送出）與 SUBMITTED（已送出、待到貨）。
 * RECEIVED／CANCELLED 不計入。若 API 只提供部分分頁資料，寧可顯示 --，
 * 不將當頁數量誤認為全站總數。
 */
import { API, requestJson } from "./common.js";

const getById = id => document.getElementById(id);
let loadingPromise = null;

/**
 * 兼容既有的陣列清單與可證明完整的 Spring Page 回傳。
 * 若分頁尚未包含全部紀錄，回報格式限制，不臆測缺少的其他頁狀態。
 */
export function getCompleteOrderList(data) {
    if (Array.isArray(data)) return data;
    if (!data || !Array.isArray(data.content)) {
        throw new Error("採購單 API 回傳格式不是清單");
    }
    const total = data.totalElements ?? data.page?.totalElements;
    if (Number.isSafeInteger(total) && total >= 0 && total === data.content.length) {
        return data.content;
    }
    throw new Error("採購單清單未包含全部分頁，無法可靠統計");
}

/** 採購狀態依後端 orderStatus 判斷，不使用金額或明細數量推測。 */
export function countPendingOrders(data) {
    const orders = getCompleteOrderList(data);
    let count = 0;
    for (const order of orders) {
        if (!order || typeof order.orderStatus !== "string") {
            throw new Error("採購單狀態資料不完整");
        }
        if (order.orderStatus === "DRAFT" || order.orderStatus === "SUBMITTED") {
            count++;
        }
    }
    return count;
}

function setOrderKpi(count, message) {
    const element = getById("stat-orders");
    if (!element) return;
    element.textContent = Number.isSafeInteger(count) && count >= 0
        ? count.toLocaleString("zh-TW") : "--";
    const note = element.closest(".stat-card")?.querySelector("p");
    if (note) note.textContent = message;
}

export function refreshDashboardPendingOrders() {
    if (loadingPromise) return loadingPromise;
    if (!getById("stat-orders")) return Promise.resolve();
    setOrderKpi(null, "正在讀取待處理訂單...");
    const pending = (async () => {
        try {
            const orders = await requestJson(API.purchaseOrders);
            setOrderKpi(countPendingOrders(orders), "待送出草稿＋已送出未到貨");
        } catch (error) {
            setOrderKpi(null, "訂貨單統計無法取得");
            console.error("首頁待處理訂貨單讀取失敗：", error);
        }
    })();
    loadingPromise = pending.finally(() => { loadingPromise = null; });
    return loadingPromise;
}

function initPendingOrdersKpi() {
    if (!getById("page-dashboard")) return;
    // 與現有首頁模組一致：進入首頁與從採購頁返回時都重新整理。
    document.querySelector('[data-page="dashboard"]')?.addEventListener(
        "click", () => void refreshDashboardPendingOrders()
    );
    void refreshDashboardPendingOrders();
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initPendingOrdersKpi, { once: true });
} else {
    initPendingOrdersKpi();
}
