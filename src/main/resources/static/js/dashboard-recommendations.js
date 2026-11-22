"use strict";

/**
 * 儀表板智慧訂貨建議摘要（只讀）。
 *
 * 1. 使用新版 GET /api/order-recommendation-options，不能誤用舊 /api/order-recommendations。
 * 2. 直接採用後端 normalRecommendedQuantity / weatherRecommendedQuantity，絕不重算採購公式。
 * 3. 本頁的天氣勾選只切換「顯示模式」；不修改正式訂貨頁的使用者選擇或實際訂貨量。
 * 4. 不建立採購單、不觸碰供應商報價或跨頁選購邏輯。
 */
import { API, apiGet, requestJson, getTaiwanToday, addDays } from "./common.js";

const OPTIONS_API = "/api/order-recommendation-options";

const MAX_PREVIEW = 5;

const el = id => document.getElementById(id);

let recommendationRows = null;

let productsById = new Map();

let currentInfo = null;

let latestRequestId = 0;

let currentRequest = null;

let productInfoWarning = false;

function create(tag, text, className) {
    const element = document.createElement(tag);
    if (text !== null && text !== undefined)
        element.textContent = String(text);
    if (className)
        element.className = className;
    return element;
}

function validQuantity(value) {
    // null/undefined/空字串代表缺值，不能誤當 0；也拒絕非整數及負數。
    if (value === null || value === undefined || value === "")
        return null;
    const number = Number(value);
    return Number.isSafeInteger(number) && number >= 0 ? number : null;
}

function quantityFor(item, useWeather) {
    return validQuantity(useWeather
        ? item.weatherRecommendedQuantity
        : item.normalRecommendedQuantity);
}

/**
 * 首頁「需要補貨商品」KPI，直接共用 訂貨摘要已取得的資料。
 * 只讀取後端 normalRecommendedQuantity／weatherRecommendedQuantity，絕不重算建議公式。
 * 數量缺值時不擅自當成 0，避免顯示錯誤的商品總數。
 */
function updateReorderKpi(total = null, message = "訂貨建議暫時無法取得") {
    const stat = el("stat-reorder");
    if (!stat)
        return;
    stat.textContent = Number.isSafeInteger(total) && total >= 0
        ? total.toLocaleString("zh-TW") : "--";
    const note = stat.closest(".stat-card")?.querySelector("p");
    if (note)
        note.textContent = message;
}

function updateReorderKpiFromCurrentRows(useWeather, basisName) {
    if (!Array.isArray(recommendationRows)) {
        updateReorderKpi();
        return;
    }
    // 若其中一筆的所選建議量沒有有效數值，就不能認定統計總數完整。
    const incomplete = recommendationRows.some(item => item == null || quantityFor(item, useWeather) === null);
    if (incomplete) {
        updateReorderKpi(null, "部分商品建議量缺值，無法確認總數");
        return;
    }
    const total = pickDashboardSuggestions(recommendationRows, useWeather).total;
    updateReorderKpi(total, `${basisName} · ${useWeather ? "天氣情境" : "一般建議"} · 建議量 > 0`);
}

function formatQuantity(value) {
    const n = validQuantity(value);
    return n === null ? "未提供" : n.toLocaleString("zh-TW");
}

/**
 * 依所選模式由後端建議量排序；只挑出建議量大於 0 的商品。
 * 永遠不依商品庫存自行推導建議量，也不以缺值 0 代替。
 */
export function pickDashboardSuggestions(items, useWeather, maxPreview = MAX_PREVIEW) {
    const validRows = (Array.isArray(items) ? items : [])
        .filter(item => item && validQuantity(item.productId) !== null)
        .map(item => ({ item, quantity: quantityFor(item, useWeather) }))
        .filter(row => row.quantity !== null && row.quantity > 0)
        .sort((a, b) => b.quantity - a.quantity || Number(a.item.productId) - Number(b.item.productId));
    return {
        total: validRows.length,
        top: validRows.slice(0, maxPreview)
    };
}

function getLocation() {
    // 延用天氣模組的縣市／鄉鎮選擇，與 首頁天氣摘要一致。
    return {
        city: el("weather-city")?.value?.trim() || "高雄市",
        district: el("weather-district")?.value?.trim() || "路竹區"
    };
}

function queryParameters() {
    const startDate = getTaiwanToday();
    const basis = el("dashboard-rec-basis")?.value === "MONTH" ? "MONTH" : "WEEK";
    return {
        ...getLocation(), startDate, endDate: addDays(startDate, 6), basis
    };
}

function renderState(message, isError = false) {
    const body = el("dashboard-rec-body");
    if (!body)
        return;
    const row = create("tr");
    const cell = create("td", message, `dashboard-rec-state${isError ? " error" : ""}`);
    cell.colSpan = 7;
    row.appendChild(cell);
    body.replaceChildren(row);
}

function showWarning(text) {
    const warning = el("dashboard-rec-warning");
    if (!warning)
        return;
    warning.textContent = text || "";
    warning.hidden = !text;
}

function summaryReason(item, useWeather) {
    // 這是對後端輸入欄位的簡述，不是模擬「AI 預測理由」。
    const history = validQuantity(item.historicalSalesQuantity);
    const safety = validQuantity(item.safetyStock);
    const stock = validQuantity(item.currentStock);
    const outstanding = validQuantity(item.outstandingQuantity);
    const parts = [];
    if (history !== null)
        parts.push(`同期淨售 ${formatQuantity(history)}`);
    if (safety !== null)
        parts.push(`安全庫存 ${formatQuantity(safety)}`);
    if (stock !== null)
        parts.push(`現庫 ${formatQuantity(stock)}`);
    if (outstanding !== null)
        parts.push(`未到貨 ${formatQuantity(outstanding)}`);
    const factors = parts.length ? parts.join(" · ") : "依歷史淨銷量、庫存與安全庫存評估";
    if (!useWeather)
        return factors;
    const factor = Number(item.weatherFactor);
    const factorLabel = item.weatherFactor !== null && item.weatherFactor !== undefined && Number.isFinite(factor)
        ? `天氣倍率 ${factor.toFixed(2)}×；` : "天氣情境；";
    return factorLabel + factors;
}

function navigateFullRecommendations() {
    // 透過現有 app.js 的導覽監聽器切頁；不寫入選購狀態。
    const nav = document.querySelector('[data-page="recommendations"]');
    nav?.click();
}

function renderRecommendationTable() {
    const body = el("dashboard-rec-body");
    const meta = el("dashboard-rec-meta");
    if (!body || !meta || !Array.isArray(recommendationRows) || !currentInfo)
        return;

    const weather = Boolean(el("dashboard-rec-use-weather")?.checked);
    const { total, top } = pickDashboardSuggestions(recommendationRows, weather);
    const basisName = currentInfo.basis === "MONTH" ? "上月同期" : "上週同期";
    updateReorderKpiFromCurrentRows(weather, basisName);
    const modeName = weather ? "天氣情境" : "一般建議";
    meta.textContent = `${currentInfo.city} · ${currentInfo.district}｜${currentInfo.startDate} ～ ${currentInfo.endDate}｜${basisName} · ${modeName}｜需補貨 ${total} 項（最多顯示 ${MAX_PREVIEW} 項）`;
    body.replaceChildren();

    if (total === 0) {
        renderState(`目前「${basisName}／${modeName}」沒有建議需補貨的商品；可切換比較基準或天氣情境查看。`);
    }
    else {
        top.forEach(({ item, quantity }, index) => {
            const product = productsById.get(Number(item.productId)) || {};
            const row = create("tr");

            const rankCell = create("td");
            rankCell.appendChild(create("span", index + 1, "dashboard-rec-rank"));
            row.appendChild(rankCell);

            const nameCell = create("td");
            nameCell.appendChild(create("strong", item.productName || product.name || "商品名稱未提供", "dashboard-rec-product-name"));
            nameCell.appendChild(create("span", item.sku || product.sku || "SKU 未提供", "dashboard-rec-sku"));
            row.appendChild(nameCell);

            const categoryCell = create("td");
            categoryCell.appendChild(create("span", product.categoryName || "分類未提供", "dashboard-rec-category"));
            row.appendChild(categoryCell);

            const quantityCell = create("td");
            const unit = typeof product.unit === "string" ? product.unit.trim() : "";
            quantityCell.appendChild(create("strong", `${formatQuantity(quantity)}${unit ? ` ${unit}` : ""}`, "dashboard-rec-quantity"));
            quantityCell.appendChild(create("span", modeName, "dashboard-rec-quantity-note"));
            row.appendChild(quantityCell);

            const stockCell = create("td", `${formatQuantity(item.currentStock)}${unit ? ` ${unit}` : ""}`, "dashboard-rec-number");
            row.appendChild(stockCell);

            row.appendChild(create("td", summaryReason(item, weather), "dashboard-rec-reason"));

            const action = create("td");
            const button = create("button", "查看訂貨頁", "dashboard-rec-row-button");
            button.type = "button";
            button.setAttribute("aria-label", `前往完整訂貨頁查看 ${item.productName || product.name || "商品"}`);
            button.addEventListener("click", navigateFullRecommendations);
            action.appendChild(button);
            row.appendChild(action);
            body.appendChild(row);
        });
    }
    body.setAttribute("aria-busy", "false");
}

function updateWarnings() {
    const messages = [];
    if (Array.isArray(recommendationRows)) {
        const warnings = recommendationRows
            .map(item => String(item?.warning || "").trim()).filter(Boolean);
        if (warnings.length)
            messages.push(warnings[0]);
        else if (recommendationRows.some(item => item?.forecastStatus === "PARTIAL" || item?.forecastStatus === "UNAVAILABLE")) {
            messages.push("部分天氣資料不完整，天氣建議量僅供參考；缺值由後端按原規則處理。");
        }
    }
    if (productInfoWarning)
        messages.push("商品分類或單位資料暫時無法取得；建議數量仍使用後端結果。");
    showWarning(messages.join("；"));
}

async function loadDashboardRecommendations() {
    const body = el("dashboard-rec-body");
    const meta = el("dashboard-rec-meta");
    const refresh = el("dashboard-rec-refresh");
    if (!body || !meta || !refresh)
        return;
    const params = queryParameters();
    const key = JSON.stringify(params);
    // 若同一組查詢正在執行，切回首頁時不發送重複 API 請求。
    if (currentRequest?.key === key)
        return currentRequest.promise;

    const requestId = ++latestRequestId;
    updateReorderKpi(null, "正在重新計算需補貨商品...");
    refresh.disabled = true;
    body.setAttribute("aria-busy", "true");
    renderState("正在取得真實訂貨建議...");
    meta.textContent = `${params.city} · ${params.district}｜${params.startDate} ～ ${params.endDate}｜正在查詢...`;
    showWarning("");

    const promise = (async () => {
        try {
            // 建議 API 一次查全部商品並重用同一次天氣預報；商品 API 僅用於顯示分類與單位。
            const [options, productInfo] = await Promise.allSettled([
                apiGet(OPTIONS_API, params), requestJson(API.products)
            ]);
            if (requestId !== latestRequestId)
                return;
            if (options.status !== "fulfilled")
                throw options.reason;
            if (!Array.isArray(options.value))
                throw new Error("訂貨建議資料暫時無法使用，請稍後再試");

            recommendationRows = options.value;
            currentInfo = params;
            productInfoWarning = productInfo.status !== "fulfilled" || !Array.isArray(productInfo.value);
            productsById = new Map((productInfoWarning ? [] : productInfo.value)
                .filter(product => validQuantity(product?.id) !== null)
                .map(product => [Number(product.id), product]));
            renderRecommendationTable();
            updateWarnings();
        }
        catch (error) {
            if (requestId !== latestRequestId)
                return;
            recommendationRows = null;
            currentInfo = null;
            updateReorderKpi(null, "訂貨建議查詢失敗，請更新重試");
            meta.textContent = "訂貨建議查詢失敗；可按「更新」重試或進入完整訂貨頁。";
            renderState(`無法載入訂貨建議：${error?.message || "未知錯誤"}`, true);
            showWarning("");
            console.error("首頁訂貨建議載入失敗：", error);
        }
        finally {
            if (requestId === latestRequestId) {
                refresh.disabled = false;
                body.setAttribute("aria-busy", "false");
                currentRequest = null;
            }
        }
    })();
    currentRequest = { key, promise };
    return promise;
}

function initializeDashboardRecommendations() {
    if (!el("page-dashboard") || !el("dashboard-rec-body"))
        return;
    el("dashboard-rec-refresh")?.addEventListener("click", () => void loadDashboardRecommendations());
    el("dashboard-rec-basis")?.addEventListener("change", () => void loadDashboardRecommendations());
    // 切換天氣只重繪已取得的兩種後端數值，不重新查氣象署、不覆蓋選購內容。
    el("dashboard-rec-use-weather")?.addEventListener("change", renderRecommendationTable);
    document.querySelector('[data-page="dashboard"]')?.addEventListener("click", () => void loadDashboardRecommendations());
    if (!el("page-dashboard").hidden)
        void loadDashboardRecommendations();
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initializeDashboardRecommendations, { once: true });
}
else {
    initializeDashboardRecommendations();
}
