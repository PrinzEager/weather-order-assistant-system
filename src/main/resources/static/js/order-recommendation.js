"use strict";

/**
 * 天氣訂貨建議：兩層商品分類、歷史基準、天氣套用、跨頁選購。
 *
 * 後端一次回傳每項商品的「正常建議量」與「天氣建議量」。
 * 這裡只根據勾選狀態切換數量，不自行重新計算天氣係數。
 * 舊 /api/order-recommendations 保留，不改動既有後端 API。
 */
import {
    API, byId, makeElement, clearElement, showMessage,
    hideMessage, setButtonLoading, formatNumber,
    getTaiwanToday, addDays, apiGet, createStatCard
} from "./common.js";
import {
    initDraftConfirmation, openDraftConfirmation, closeDraftConfirmation
} from "./recommendation-order-draft.js";
import {
    setWeatherFactorExplanation
} from "./help-dialogs.js";

const OPTION_API = "/api/order-recommendation-options/with-weather-details";
const PAGE_SIZE = 10;
const MAX_QUANTITY = 2147483647;

let recommendationLoaded = false;
let loading = false;
let recommendationData = [];
let categories = [];
let productCategoryIds = new Map();

// 以商品 ID 保存狀態，不依賴目前畫面有哪些列，因此跨頁及跨分類不會遺失選擇。
// value = { selected:boolean, applyWeather:boolean, quantityRaw:string|null }
let productStates = new Map();
let listPage = 1;

export function initRecommendations() {
    // 沒有成功查詢前先顯示操作提示，切勿把教學範例當成真實倍率。
    setWeatherFactorExplanation(null);

    // 查詢條件改變後，舊明細不可繼續宣稱屬於「本次查詢」。
    for (const id of [
        "recommendation-city", "recommendation-district",
        "recommendation-start-date", "recommendation-end-date",
        "recommendation-history-basis"
    ]) {
        byId(id).addEventListener("change", () => {
            setWeatherFactorExplanation(null, "查詢條件已變更，請重新產生訂貨建議以查看對應的天氣倍率。");
        });
    }

    // 即使共用工具仍保留三天限制，也讓訂貨建議日期可以選擇未來七天。
    const today = getTaiwanToday();
    const lastDay = addDays(today, 6);
    for (const id of ["recommendation-start-date", "recommendation-end-date"]) {
        byId(id).min = today;
        byId(id).max = lastDay;
    }
    // 舊版共用初始化預設三天，訂貨建議頁改為預設完整七天。
    if (byId("recommendation-end-date").value === addDays(today, 2)) {
        byId("recommendation-end-date").value = lastDay;
    }
    byId("recommendation-search-form").addEventListener("submit", event => {
        event.preventDefault();
        void loadRecommendations();
    });

    byId("recommendation-history-basis").addEventListener("change", () => {
        // 換歷史基準必須由後端重新計算：兩種來源不相加。
        if (recommendationLoaded && !loading) void loadRecommendations();
    });

    byId("recommendation-parent-category").addEventListener("change", () => {
        populateChildCategories();
        listPage = 1;
        renderRecommendationTable();
    });
    byId("recommendation-child-category").addEventListener("change", () => {
        listPage = 1;
        renderRecommendationTable();
    });
    byId("recommendation-page-prev").addEventListener("click", () => {
        if (listPage > 1) {
            listPage -= 1;
            renderRecommendationTable();
        }
    });
    byId("recommendation-page-next").addEventListener("click", () => {
        listPage += 1;
        renderRecommendationTable();
    });

    const body = byId("recommendation-table-body");
    body.addEventListener("change", handleTableChange);
    body.addEventListener("input", handleTableInput);

    byId("create-order-from-recommendation").addEventListener("click", () => {
        try {
            openDraftConfirmation(getSelectedItems());
        } catch (error) {
            showMessage(error.message, "error");
        }
    });

    initDraftConfirmation(() => {
        // 成功建立草稿後清空選購（避免再次送出）；保留計算結果供參考。
        productStates.clear();
        renderRecommendationTable();
        renderRecommendationSummary();
    });
    updateCreateButtonState();
}

/** 訂貨建議專用的七天日期驗證，不影響原本天氣頁的日期工具。 */
function getRecommendationSearchParameters() {
    const city = byId("recommendation-city").value.trim();
    const district = byId("recommendation-district").value.trim();
    const startDate = byId("recommendation-start-date").value;
    const endDate = byId("recommendation-end-date").value;
    const today = getTaiwanToday();
    const lastDay = addDays(today, 6);
    if (!city || !district) throw new Error("請選擇縣市與鄉鎮市區");
    if (!startDate || !endDate) throw new Error("請選擇起始日期與結束日期");
    if (startDate > endDate) throw new Error("起始日期不可晚於結束日期");
    if (startDate < today || endDate > lastDay) {
        throw new Error(`訂貨建議日期範圍為 ${today} 至 ${lastDay}（含首尾）`);
    }
    return {city, district, startDate, endDate};
}

async function loadRecommendations() {
    if (loading) return;
    const button = byId("recommendation-search-form").querySelector('button[type="submit"]');
    loading = true;
    recommendationLoaded = false;
    recommendationData = [];
    categories = [];
    productCategoryIds.clear();
    productStates.clear();
    listPage = 1;
    closeDraftConfirmation();
    setWeatherFactorExplanation(null, "正在計算訂貨建議，取得資料後即可查看每日倍率。");
    resetFilters();
    updateCreateButtonState();
    clearElement(byId("recommendation-summary"));
    showTableMessage("正在計算訂貨建議...");
    try {
        hideMessage();
        setButtonLoading(button, true, "計算訂貨建議中...");
        const params = getRecommendationSearchParameters();
        params.basis = byId("recommendation-history-basis").value;

        // 一次取得全部有效商品的兩種建議量、分類、商品關聯。
        // 查天氣的呼叫只會發生在後端訂貨建議服務內，不為每個商品重複查天氣。
        const [detailResponse, categoryResponse, productResponse] = await Promise.all([
            apiGet(OPTION_API, params),
            apiGet(API.categories, {}),
            apiGet(API.products, {})
        ]);
        const options = detailResponse?.recommendations;
        const productList = Array.isArray(productResponse)
            ? productResponse : productResponse?.content;
        if (!Array.isArray(options) ||
                !Array.isArray(detailResponse?.dailyWeatherFactors) ||
                detailResponse?.weatherFactor == null ||
                !Array.isArray(categoryResponse)
                || !Array.isArray(productList)) {
            throw new Error("訂貨建議、商品或分類回傳格式不正確");
        }
        // 避免 API 版本不符時，把缺少的建議數量誤當成 0 件。
        const incomplete = options.find(item =>
            item.productId == null ||
            item.normalRecommendedQuantity == null ||
            item.weatherRecommendedQuantity == null ||
            item.selectedHistoricalSalesQuantity == null ||
            item.weatherFactor == null
        );
        if (incomplete) {
            throw new Error("訂貨建議缺少一般／天氣建議量或歷史淨銷量，請檢查後端版本");
        }
        // 如果 Product API 改成分頁，只拿第一頁可能造成部分商品找不到分類。
        if (!Array.isArray(productResponse) &&
                Number(productResponse?.totalElements) > productList.length) {
            throw new Error("商品已啟用分頁，請先取得完整商品分類對應資料");
        }

        recommendationData = options;
        categories = categoryResponse.filter(c => Number(c.status) === 1 && c.deletedAt == null);
        productCategoryIds = new Map(productList
            .filter(p => p.id != null && p.categoryId != null)
            .map(p => [Number(p.id), Number(p.categoryId)]));
        recommendationLoaded = true;
        // 這份說明與商品建議來自同一次後端計算，開啟視窗不會重查 API。
        setWeatherFactorExplanation(detailResponse);
        populateParentCategories();
        renderRecommendationTable();
        renderRecommendationSummary();
        showMessage(`已產生 ${options.length} 筆商品建議，請選擇第一層與第二層分類`, "success");
    } catch (error) {
        recommendationLoaded = false;
        recommendationData = [];
        categories = [];
        productStates.clear();
        setWeatherFactorExplanation(null, "訂貨建議查詢失敗，請重新產生後再查看實際天氣倍率。");
        showTableMessage("訂貨建議載入失敗，請檢查後端資料");
        byId("stat-reorder").textContent = "--";
        showMessage(error.message, "error");
        console.error("訂貨建議查詢失敗：", error);
    } finally {
        loading = false;
        setButtonLoading(button, false);
        updateCreateButtonState();
    }
}

function resetFilters() {
    const parent = byId("recommendation-parent-category");
    const child = byId("recommendation-child-category");
    setPlaceholder(parent, "請先產生訂貨建議");
    setPlaceholder(child, "請先選擇第一層分類");
    parent.disabled = true;
    child.disabled = true;
    byId("recommendation-list-meta").textContent = "請先產生訂貨建議";
    updatePaging(0, 0, 0);
}

function setPlaceholder(select, label) {
    clearElement(select);
    const option = makeElement("option", label);
    option.value = "";
    select.appendChild(option);
    select.value = "";
}

function populateParentCategories() {
    const select = byId("recommendation-parent-category");
    setPlaceholder(select, "請選擇第一層分類");
    const parents = categories.filter(c => c.parentId == null)
        .sort((a, b) => Number(a.id) - Number(b.id));
    for (const parent of parents) {
        const option = makeElement("option", parent.name);
        option.value = String(parent.id);
        select.appendChild(option);
    }
    select.disabled = parents.length === 0;
    populateChildCategories();
}

function populateChildCategories() {
    const parentId = Number(byId("recommendation-parent-category").value);
    const child = byId("recommendation-child-category");
    setPlaceholder(child, "請選擇第二層分類");
    if (!parentId) {
        child.disabled = true;
        return;
    }
    const children = categories.filter(c => Number(c.parentId) === parentId)
        .sort((a, b) => Number(a.id) - Number(b.id));
    for (const category of children) {
        const option = makeElement("option", category.name);
        option.value = String(category.id);
        child.appendChild(option);
    }
    child.disabled = children.length === 0;
}

function getFilteredItems() {
    const childId = Number(byId("recommendation-child-category").value);
    if (!childId) return [];
    return recommendationData.filter(p => productCategoryIds.get(Number(p.productId)) === childId);
}

function stateFor(id) {
    const productId = Number(id);
    if (!productStates.has(productId)) {
        productStates.set(productId, { selected: false, applyWeather: false, quantityRaw: null });
    }
    return productStates.get(productId);
}

function recommendedQuantity(item, state) {
    const number = state.applyWeather
        ? item.weatherRecommendedQuantity : item.normalRecommendedQuantity;
    return Number(number ?? 0);
}

function actualValue(item, state) {
    return state.quantityRaw === null
        ? String(recommendedQuantity(item, state)) : state.quantityRaw;
}

/**
 * 說明為什麼一般建議與天氣建議可能相同。
 * 即使天氣倍率不是 1.00，也可能因歷史淨銷量為零，
 * 或現有庫存已足夠，導致兩個最終補貨數量相同。
 *
 * 此方法僅供畫面解釋；真正建議量仍完全採用後端回傳值。
 */
function explainSameRecommendation(item) {
    const sales = Number(item.selectedHistoricalSalesQuantity ?? 0);
    const factor = Number(item.weatherFactor ?? 1);
    const adjusted = Number(item.weatherAdjustedSalesQuantity ?? sales);
    const normal = Number(item.normalRecommendedQuantity);
    const weather = Number(item.weatherRecommendedQuantity);

    if (normal !== weather) return "天氣調整後的補貨數量不同";
    if (sales === 0) return "選定的歷史期間淨銷量為 0，無可調整的銷售需求";
    if (factor === 1) return "天氣倍率為 1.00，未增加或減少預期需求";
    if (adjusted === sales) return "歷史需求乘倍率後經無條件進位仍相同";
    if (normal === 0 && weather === 0) {
        return "現有庫存與未到貨量足夠，兩種計算最後都不需要補貨";
    }
    return "經安全庫存、現貨與未到貨量計算後，最終補貨數量相同";
}

/**
 * 建議量格子的提示文字會隨「套用天氣」勾選狀態改變。
 * 同時列出後端兩個原始結果，方便直接核對 Postman。
 */
function buildRecommendationTooltip(item, state) {
    const sales = Number(item.selectedHistoricalSalesQuantity ?? 0);
    const factor = Number(item.weatherFactor ?? 1);
    const normal = Number(item.normalRecommendedQuantity);
    const weather = Number(item.weatherRecommendedQuantity);
    const basis = item.historicalBasis === "WEEK" ? "上週同期" : "上月同期";
    const mode = state.applyWeather ? "已勾選：套用天氣係數" : "未勾選：一般建議（天氣倍率視為 1.00）";
    const difference = weather - normal;
    const sign = difference > 0 ? "+" : "";

    return [
        mode,
        `歷史基準：${basis}；歷史淨銷量：${formatNumber(sales)} 件`,
        `當前預報倍率：${factor.toFixed(2)} ×`,
        `一般建議：${formatNumber(normal)} 件`,
        `天氣建議：${formatNumber(weather)} 件`,
        `天氣對訂貨量差異：${sign}${formatNumber(difference)} 件`,
        `安全庫存：${formatNumber(item.safetyStock)}；目前庫存：${formatNumber(item.currentStock)}；未到貨：${formatNumber(item.outstandingQuantity)}`,
        ...(difference === 0 ? [`數量相同原因：${explainSameRecommendation(item)}`] : []),
        ...(state.quantityRaw !== null ? ["※ 實際訂貨量已手動修改，不隨建議量自動覆蓋"] : [])
    ].join("\n");
}

function parsePositiveQuantity(value) {
    const text = String(value ?? "").trim();
    if (!/^[0-9]+$/.test(text)) return null;
    const n = Number(text);
    return Number.isSafeInteger(n) && n > 0 && n <= MAX_QUANTITY ? n : null;
}

function showTableMessage(message) {
    const row = makeElement("tr");
    const td = makeElement("td", message, "empty-state");
    td.colSpan = 8;
    row.appendChild(td);
    byId("recommendation-table-body").replaceChildren(row);
}

function renderRecommendationTable() {
    const child = byId("recommendation-child-category").value;
    if (!recommendationLoaded) {
        showTableMessage("尚未產生訂貨建議");
        updatePaging(0, 0, 0);
        return;
    }
    if (!child) {
        showTableMessage("請選擇第一層及第二層商品分類");
        byId("recommendation-list-meta").textContent = `共 ${recommendationData.length} 筆有效商品，請選擇第二層分類`;
        updatePaging(0, 0, 0);
        updateCreateButtonState();
        return;
    }
    const filtered = getFilteredItems();
    const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
    listPage = Math.min(Math.max(1, listPage), pageCount);
    const items = filtered.slice((listPage - 1) * PAGE_SIZE, listPage * PAGE_SIZE);
    const body = byId("recommendation-table-body");
    clearElement(body);
    if (!items.length) {
        showTableMessage("此分類沒有有效商品的訂貨建議");
    } else {
        for (const item of items) body.appendChild(createRecommendationRow(item));
    }
    byId("recommendation-list-meta").textContent = `此分類 ${filtered.length} 筆商品，每頁 ${PAGE_SIZE} 筆`;
    updatePaging(filtered.length, listPage, pageCount);
    updateCreateButtonState();
}

function createRecommendationRow(item) {
    const id = Number(item.productId);
    const state = stateFor(id);
    const row = makeElement("tr");
    row.dataset.productId = String(id);

    const selectedCell = makeElement("td");
    const select = makeElement("input");
    select.type = "checkbox";
    select.className = "recommendation-select";
    select.checked = state.selected;
    select.dataset.productId = String(id);
    select.setAttribute("aria-label", `選擇訂貨商品 ${item.productName}`);
    selectedCell.appendChild(select);
    row.appendChild(selectedCell);

    const nameCell = makeElement("td");
    nameCell.append(makeElement("strong", item.productName),
        makeElement("div", item.sku ?? "--", "weather-description"));
    row.appendChild(nameCell);
    row.appendChild(makeElement("td", formatNumber(item.currentStock)));
    row.appendChild(makeElement("td", formatNumber(item.selectedHistoricalSalesQuantity)));
    row.appendChild(makeElement("td", `${Number(item.weatherFactor ?? 1).toFixed(2)} ×`));

    const weatherCell = makeElement("td");
    const weather = makeElement("input");
    weather.type = "checkbox";
    weather.className = "recommendation-weather-check";
    weather.dataset.productId = String(id);
    weather.checked = state.applyWeather;
    weather.setAttribute("aria-label", `套用 ${item.productName} 的天氣係數`);
    weatherCell.appendChild(weather);
    row.appendChild(weatherCell);

    const suggested = recommendedQuantity(item, state);
    const suggestedCell = makeElement("td");
    const suggestedText = makeElement("strong", formatNumber(suggested),
        suggested > 0 ? "status-badge warning" : "status-badge success");
    suggestedCell.appendChild(suggestedText);

    // 明確標示目前採用哪個建議，即使兩種建議數量相同，勾選仍有回饋。
    suggestedCell.appendChild(makeElement("div",
        state.applyWeather ? "已套用天氣" : "一般建議", "weather-description"));

    // 同時顯示兩種數量的差距，避免誤以為勾選事件沒有反應。
    const difference = Number(item.weatherRecommendedQuantity)
        - Number(item.normalRecommendedQuantity);
    const diffLabel = difference === 0
        ? `差異 0 件｜${explainSameRecommendation(item)}`
        : `天氣差異 ${difference > 0 ? "+" : ""}${formatNumber(difference)} 件`;
    suggestedCell.appendChild(makeElement("small", diffLabel, "weather-description"));

    // 提示需綁定到實際滑鼠指向的元素，而不只是外層 tr。
    const tooltip = buildRecommendationTooltip(item, state);
    suggestedCell.title = tooltip;
    suggestedText.title = tooltip;
    row.appendChild(suggestedCell);

    const qtyCell = makeElement("td");
    const quantity = makeElement("input");
    quantity.type = "number";
    quantity.min = "0";
    quantity.step = "1";
    quantity.max = String(MAX_QUANTITY);
    quantity.value = actualValue(item, state);
    quantity.className = "actual-order-quantity";
    quantity.dataset.productId = String(id);
    quantity.setAttribute("aria-label", `${item.productName} 的實際訂貨量`);
    qtyCell.appendChild(quantity);
    row.appendChild(qtyCell);

    row.title = `上週淨銷量：${formatNumber(item.lastWeekNetSalesQuantity)}\n` +
        `上月淨銷量：${formatNumber(item.lastMonthNetSalesQuantity)}\n` +
        buildRecommendationTooltip(item, state);
    return row;
}

function handleTableChange(event) {
    const input = event.target;
    const id = Number(input.dataset?.productId);
    if (!id || !recommendationData.some(p => Number(p.productId) === id)) return;
    const state = stateFor(id);
    if (input.matches(".recommendation-select")) {
        state.selected = input.checked;
        renderRecommendationSummary();
    } else if (input.matches(".recommendation-weather-check")) {
        state.applyWeather = input.checked;
        // 使用者未自行修改訂貨量時，實際數量才跟著預設建議量切換。
        renderRecommendationTable();
        renderRecommendationSummary();
    } else if (input.matches(".actual-order-quantity")) {
        state.quantityRaw = input.value;
    }
    updateCreateButtonState();
}

function handleTableInput(event) {
    const input = event.target;
    if (!input.matches(".actual-order-quantity")) return;
    const id = Number(input.dataset.productId);
    if (!id) return;
    stateFor(id).quantityRaw = input.value;
    updateCreateButtonState();
}

function getSelectedItems() {
    if (!recommendationLoaded) throw new Error("請先產生訂貨建議");
    const selected = [];
    for (const item of recommendationData) {
        const state = productStates.get(Number(item.productId));
        if (!state?.selected) continue;
        const quantity = parsePositiveQuantity(actualValue(item, state));
        if (quantity === null) {
            throw new Error(`「${item.productName}」訂貨數量必須是大於 0 的整數`);
        }
        selected.push({
            productId: Number(item.productId),
            productName: item.productName,
            sku: item.sku,
            quantity
        });
    }
    return selected;
}

function updateCreateButtonState() {
    const button = byId("create-order-from-recommendation");
    const count = Array.from(productStates.values()).filter(s => s.selected).length;
    byId("recommendation-selected-count").textContent = `已選擇 ${count} 項商品（跨分類／頁面保留）`;
    let valid = false;
    if (recommendationLoaded && !loading && count > 0) {
        try { valid = getSelectedItems().length > 0; } catch (_) { valid = false; }
    }
    button.disabled = !valid;
}

function renderRecommendationSummary() {
    const container = byId("recommendation-summary");
    clearElement(container);
    if (!recommendationLoaded) return;
    let needsReorder = 0;
    let totalQuantity = 0;
    for (const item of recommendationData) {
        const q = recommendedQuantity(item,
            productStates.get(Number(item.productId)) ?? {applyWeather:false});
        if (q > 0) needsReorder += 1;
        totalQuantity += q;
    }
    container.append(
        createStatCard("有效商品數", recommendationData.length, "全部分類"),
        createStatCard("需要補貨商品", needsReorder, "依目前天氣勾選狀態"),
        createStatCard("建議訂貨總數", formatNumber(totalQuantity), "未勾選天氣時使用一般建議")
    );
    byId("stat-reorder").textContent = String(needsReorder);
}

function updatePaging(total, page, pages) {
    const visible = pages > 0 && total > 0;
    byId("recommendation-pagination").hidden = !visible;
    byId("recommendation-page-info").textContent = visible ? `第 ${page}／${pages} 頁` : "";
    byId("recommendation-page-prev").disabled = !visible || page <= 1;
    byId("recommendation-page-next").disabled = !visible || page >= pages;
}
