"use strict";

/**
 * 系統前端網址路由（History API 版本）。
 *
 * 1. 以 /home、/orders 等不帶 # 的網址代表功能頁。
 * 2. 不變更既有 app.js 的 switchPage() 或 data-page / DOM ID。
 * 3. 保留上一頁、下一頁、F5 及直接貼上網址的功能。
 * 4. 舊版 /#/home、/#/orders、/#/dashboard 書籤自動改為新網址。
 * 5. 離開已修改且尚未儲存的表單前，保留既有的確認提醒。
 *
 * 請搭配 FrontendRouteController：直接開啟 /orders 時由後端回傳 index 模板。
 */
const PAGE_BY_ROUTE = Object.freeze({
    home: "dashboard",
    weather: "weather",
    recommendations: "recommendations",
    categories: "categories",
    products: "products",
    suppliers: "suppliers",
    sales: "sales",
    orders: "orders"
});

const ROUTE_BY_PAGE = Object.freeze(Object.fromEntries(
    Object.entries(PAGE_BY_ROUTE).map(([route, page]) => [page, route])
));

// Thymeleaf 的 @{} 會產生包含 context-path 的 JS 連結。
// 例如部署在 /store，載入路徑為 /store/js/router.js，網址就應為 /store/orders。
function getContextPath() {
    const script = [...document.querySelectorAll('script[src]')].find(element => {
        try {
            return new URL(element.getAttribute("src"), window.location.href)
                .pathname.endsWith("/js/router.js");
        } catch (_) {
            return false;
        }
    });
    if (!script) return "";
    const path = new URL(script.getAttribute("src"), window.location.href).pathname;
    return path.slice(0, -"/js/router.js".length);
}

const CONTEXT_PATH = getContextPath();
let currentPage = "dashboard";
let syncingHistory = false;
let formHasUnsavedChanges = false;

/**
 * 網址解析：優先將舊版 #/route 當作書籤相容路由；否則讀取 pathname。
 * 僅處理固定功能名稱，不將任何未知網址轉成動態 HTML 模板路徑。
 */
function pageFromAddress() {
    const oldHash = window.location.hash.match(/^#\/([^/?#]+)/);
    if (oldHash) {
        const name = oldHash[1] === "dashboard" ? "home" : oldHash[1];
        return PAGE_BY_ROUTE[name] || "dashboard";
    }

    const path = window.location.pathname;
    let relativePath = path;
    if (CONTEXT_PATH && (path === CONTEXT_PATH || path.startsWith(`${CONTEXT_PATH}/`))) {
        relativePath = path.slice(CONTEXT_PATH.length);
    }
    const routeName = relativePath.replace(/^\/+|\/+$/g, "");
    const name = routeName === "dashboard" ? "home" : routeName;
    return PAGE_BY_ROUTE[name] || "dashboard";
}

function canonicalUrl(page) {
    return `${CONTEXT_PATH}/${ROUTE_BY_PAGE[page] || "home"}`;
}

function updateUrl(page, replace = false) {
    const target = canonicalUrl(page);
    // 路徑一致但仍含舊 hash / query / 末尾斜線時，也要規範為乾淨網址。
    if (window.location.pathname === target && !window.location.hash && !window.location.search) return;
    if (replace) {
        window.history.replaceState({ page }, "", target);
    } else {
        window.history.pushState({ page }, "", target);
    }
}

function isFormVisible(form) {
    if (!form) return false;
    const panel = form.closest(
        "#recommendation-order-panel, #order-form-panel, #sale-form-panel, [role='dialog']"
    );
    return Boolean(panel && !panel.hidden);
}

function hasOpenUnsavedForm() {
    if (!formHasUnsavedChanges) return false;
    return ["recommendation-order-form", "order-form", "sale-form"]
        .some(id => isFormVisible(document.getElementById(id)));
}

function confirmLeavingPage() {
    if (!hasOpenUnsavedForm()) return true;
    const accepted = window.confirm("目前有尚未儲存的表單修改，確定要離開此功能頁嗎？");
    if (accepted) formHasUnsavedChanges = false;
    return accepted;
}

/**
 * 上一頁／下一頁／首次載入時，同步目前網址與既有功能頁。
 */
function syncFromAddress() {
    const destination = pageFromAddress();
    if (destination === currentPage) {
        updateUrl(destination, true);
        return;
    }
    if (!confirmLeavingPage()) {
        // 取消離開時，把網址回復到實際顯示的頁面。
        updateUrl(currentPage, true);
        return;
    }

    const menu = document.querySelector(`.nav-item[data-page="${destination}"]`);
    if (!menu) {
        updateUrl("dashboard", true);
        return;
    }

    syncingHistory = true;
    try {
        // 保留 app.js 原本的首次載入、頁面顯示與導覽事件處理。
        menu.click();
        currentPage = destination;
        updateUrl(destination, true);
    } finally {
        syncingHistory = false;
    }
}

function onNavigationClick(event) {
    const button = event.target instanceof Element
        ? event.target.closest("[data-page], [data-go-page]") : null;
    if (!button || syncingHistory) return;

    const destination = button.dataset.page || button.dataset.goPage;
    if (!ROUTE_BY_PAGE[destination]) return;

    if (destination !== currentPage && !confirmLeavingPage()) {
        event.preventDefault();
        event.stopImmediatePropagation();
        return;
    }

    currentPage = destination;
    updateUrl(destination);
    // 不攔截事件，繼續讓原有 app.js 處理切換。
}

function initRouter() {
    const active = document.querySelector(".nav-item.active[data-page]");
    currentPage = active?.dataset.page || "dashboard";

    document.addEventListener("click", onNavigationClick, true);
    window.addEventListener("popstate", syncFromAddress);
    // 舊版網址或使用者手動修改 # 時也能轉換，不產生新的 # 路由。
    window.addEventListener("hashchange", syncFromAddress);

    const markDirty = event => {
        if (event.target instanceof Element && event.target.closest(
            "#recommendation-order-form, #order-form, #sale-form"
        )) formHasUnsavedChanges = true;
    };
    document.addEventListener("input", markDirty, true);
    document.addEventListener("change", markDirty, true);

    document.addEventListener("click", event => {
        const button = event.target instanceof Element ? event.target.closest("button") : null;
        if (button && [
            "recommendation-order-close", "recommendation-order-cancel",
            "order-form-close", "order-form-cancel", "sale-form-close", "sale-form-cancel"
        ].includes(button.id)) formHasUnsavedChanges = false;
    });

    window.addEventListener("beforeunload", event => {
        if (hasOpenUnsavedForm()) {
            event.preventDefault();
            event.returnValue = "";
        }
    });

    // app.js 先完成首頁選單初始化，再根據網址切換至目標功能。
    syncFromAddress();
}

document.addEventListener("DOMContentLoaded", initRouter);

export { pageFromAddress, canonicalUrl };
