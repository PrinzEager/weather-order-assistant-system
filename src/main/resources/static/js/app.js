"use strict";

/* 系統入口：僅負責初始化、選單與頁面切換，不放入各模組 CRUD。 */
import { byId, hideMessage, initializeDates } from "./common.js";
import { initWeather, enterWeatherPage } from "./weather.js";
// 增加版本參數，強制瀏覽器重新取得新版訂貨建議模組。
import { initRecommendations } from "./order-recommendation.js?v=20261121-2";
import { initCategories, enterCategoriesPage } from "./product-category.js";
import { initProducts, enterProductsPage } from "./product.js";
import { initSuppliers, enterSuppliersPage } from "./supplier.js";
import { initSales, enterSalesPage } from "./sales-record.js";
// 使用版本參數，避免瀏覽器載入舊版採購單模組。
import {
    initOrders,
    enterOrdersPage
} from "./purchase-order.js?v=20261121-2";

const PAGE_INFO = {
    dashboard: ["系統儀表板", "查看系統資訊及快速進入主要功能"],
    weather: ["七天天氣預報", "查看中央氣象署未來七天天氣資訊"],
    recommendations: ["天氣訂貨建議", "結合銷售、庫存與天氣計算建議數量"],
    categories: ["商品分類管理", "管理第一層與第二層商品分類"],
    products: ["商品管理", "管理商品資訊與庫存"],
    suppliers: ["供應商管理", "管理合作供應商"],
    sales: ["銷售紀錄", "查詢及維護商品銷售紀錄"],
    orders: ["採購訂貨單", "管理採購草稿及訂貨狀態"]
};

function switchPage(page) {
    if (!PAGE_INFO[page]) return;

    document.querySelectorAll(".page-section").forEach(section => {
        section.hidden = true;
    });

    byId(`page-${page}`).hidden = false;

    document.querySelectorAll(".nav-item").forEach(button => {
        const active = button.dataset.page === page;
        button.classList.toggle("active", active);
        if (active) {
            button.setAttribute("aria-current", "page");
        } else {
            button.removeAttribute("aria-current");
        }
    });

    byId("page-title").textContent = PAGE_INFO[page][0];
    byId("page-description").textContent = PAGE_INFO[page][1];
    hideMessage();

    // 進入頁面時才交由對應模組處理首次載入。
    if (page === "weather") enterWeatherPage();
    if (page === "categories") enterCategoriesPage();
    if (page === "products") enterProductsPage();
    if (page === "suppliers") enterSuppliersPage();
    if (page === "sales") enterSalesPage();
    if (page === "orders") enterOrdersPage();

    window.scrollTo({ top: 0, behavior: "auto" });
}

function initializeNavigation() {
    document.querySelectorAll("[data-page]").forEach(button => {
        button.addEventListener("click", () => switchPage(button.dataset.page));
    });

    document.querySelectorAll("[data-go-page]").forEach(button => {
        button.addEventListener("click", () => switchPage(button.dataset.goPage));
    });
}

document.addEventListener("DOMContentLoaded", () => {
    initializeDates();
    initializeNavigation();
    initWeather();
    initRecommendations();
    initCategories();
    initProducts();
    initSuppliers();
    initSales();
    initOrders();
    switchPage("dashboard");
    console.info("Weather Order Assistant System 前端初始化完成");
});
