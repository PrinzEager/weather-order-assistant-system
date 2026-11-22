"use strict";

/**
 * 首頁商品與供應商總數。
 *
 * 僅讀取現有後台管理 API，不修改商品、供應商的 CRUD 或啟停流程。
 * 「總數」包含啟用／停用，但不包含已軟刪除的資料。
 * 商品與供應商各自處理錯誤，避免單一 API 故障遮蔽另一項統計。
 */
import { API, requestJson } from "./common.js";

const DASHBOARD_STATS = [
    {
        id: "stat-products",
        path: "/api/management/products?status=all",
        name: "商品"
    },
    {
        id: "stat-suppliers",
        path: "/api/management/suppliers?status=all",
        name: "供應商"
    }
];

let loadingPromise = null;

/**
 * 管理清單可能直接回傳陣列，或使用 Spring Data Page 結構。
 * 若為分頁格式，必須以 totalElements 為準，不能拿當頁 content.length 冒充總數。
 */
function extractTotal(data) {
    if (Array.isArray(data)) return data.length;

    const total = data?.totalElements ?? data?.page?.totalElements;
    if (Number.isSafeInteger(total) && total >= 0) return total;

    throw new Error("管理清單回傳格式不包含可確認的總數");
}

function updateCard(id, count, message) {
    const stat = document.getElementById(id);
    if (!stat) return;

    stat.textContent = count === null
        ? "--"
        : count.toLocaleString("zh-TW");

    // 不新增 DOM ID；沿用原本統計卡片的描述文字。
    const note = stat.closest(".stat-card")?.querySelector("p");
    if (note) note.textContent = message;
}

async function loadOneStat(config) {
    try {
        const data = await requestJson(config.path);
        const count = extractTotal(data);
        updateCard(config.id, count, "含啟用／停用，不含已刪除");
    } catch (error) {
        updateCard(config.id, null, "統計暫時無法取得");
        console.error(`首頁${config.name}總數讀取失敗：`, error);
    }
}

export function refreshDashboardStats() {
    if (loadingPromise) return loadingPromise;

    const pending = Promise.all(DASHBOARD_STATS.map(loadOneStat));
    loadingPromise = pending.finally(() => {
        loadingPromise = null;
    });
    return loadingPromise;
}

function initializeDashboardStats() {
    if (!document.getElementById("page-dashboard")) return;

    // 每次透過導覽列回首頁都更新數字，反映最新新增、停用、重新啟用及軟刪除狀態。
    document.querySelector('[data-page="dashboard"]')
        ?.addEventListener("click", () => void refreshDashboardStats());

    // 直接開首頁時，不必先進入商品或供應商頁才會載入統計。
    void refreshDashboardStats();
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initializeDashboardStats, { once: true });
} else {
    initializeDashboardStats();
}


/* 待處理採購單統計：獨立閉包以隔離 loadingPromise 等狀態。 */
const { refreshDashboardPendingOrders, countPendingOrders, getCompleteOrderList } = (() => {
"use strict";

/**
 * 首頁待處理訂單數量。
 *
 * 只讀取 GET /api/purchase-orders，不建立或更新訂單，也不改動既有採購頁。
 * 本統計包含 DRAFT（待送出）與 SUBMITTED（已送出、待到貨）。
 * RECEIVED／CANCELLED 不計入。若 API 只提供部分分頁資料，寧可顯示 --，
 * 不將當頁數量誤認為全站總數。
 */

const getById = id => document.getElementById(id);
let loadingPromise = null;

/**
 * 兼容既有的陣列清單與可證明完整的 Spring Page 回傳。
 * 若分頁尚未包含全部紀錄，回報格式限制，不臆測缺少的其他頁狀態。
 */
function getCompleteOrderList(data) {
    if (Array.isArray(data)) return data;
    if (!data || !Array.isArray(data.content)) {
        throw new Error("採購單回傳格式不是清單");
    }
    const total = data.totalElements ?? data.page?.totalElements;
    if (Number.isSafeInteger(total) && total >= 0 && total === data.content.length) {
        return data.content;
    }
    throw new Error("採購單清單未包含全部分頁，無法可靠統計");
}

/** 採購狀態依後端 orderStatus 判斷，不使用金額或明細數量推測。 */
function countPendingOrders(data) {
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

function refreshDashboardPendingOrders() {
    if (loadingPromise) return loadingPromise;
    if (!getById("stat-orders")) return Promise.resolve();
    setOrderKpi(null, "正在讀取待處理訂單...");
    const pending = (async () => {
        try {
            const orders = await requestJson(API.purchaseOrders);
            setOrderKpi(countPendingOrders(orders), "待送出草稿＋已送出未到貨");
        } catch (error) {
            setOrderKpi(null, "訂單統計無法取得");
            console.error("首頁待處理訂單讀取失敗：", error);
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

return { refreshDashboardPendingOrders, countPendingOrders, getCompleteOrderList };
})();

// 為頁面刷新及測試保留具名匯出。
export { refreshDashboardPendingOrders, countPendingOrders, getCompleteOrderList };
