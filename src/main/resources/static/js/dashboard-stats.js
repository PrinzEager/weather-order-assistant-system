"use strict";

/**
 * Phase 7.1｜首頁商品與供應商總數。
 *
 * 僅讀取現有後台管理 API，不修改商品、供應商的 CRUD 或啟停流程。
 * 「總數」包含啟用／停用，但不包含已軟刪除的資料。
 * 商品與供應商各自處理錯誤，避免單一 API 故障遮蔽另一項統計。
 */
import { requestJson } from "./common.js";

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
