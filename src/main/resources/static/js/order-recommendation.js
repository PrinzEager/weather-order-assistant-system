"use strict";

/*
 * 智慧天氣訂貨建議模組。
 *
 * 功能：
 * 1. 查詢並顯示智慧訂貨建議。
 * 2. 勾選商品及調整實際訂貨數量。
 * 3. 載入有效供應商。
 * 4. 預覽採購草稿明細。
 * 5. 呼叫 PurchaseOrder API 建立 DRAFT 採購單。
 *
 * 注意：
 * 建立草稿不等於正式送出採購單。
 * 本模組不直接修改商品庫存。
 */

import {
    API,
    byId,
    getTaiwanToday,
    makeElement,
    clearElement,
    showEmpty,
    showMessage,
    hideMessage,
    setButtonLoading,
    displayValue,
    formatNumber,
    getSearchParameters,
    apiGet,
    requestJson,
    createStatCard
} from "./common.js";

// 記錄目前是否已成功取得訂貨建議。
let recommendationLoaded = false;

// 暫存本次查詢的訂貨建議。
let recommendationData = [];

// 防止使用者連續送出重複草稿。
let creatingOrder = false;

// Java Integer 可接受的最大正整數。
const MAX_QUANTITY = 2147483647;

/**
 * 初始化智慧訂貨建議頁面。
 *
 * 由 app.js 呼叫一次。
 */
export function initRecommendations() {

    // 查詢訂貨建議。
    byId("recommendation-search-form")
        .addEventListener("submit", event => {

            event.preventDefault();

            loadRecommendations();
        });

    // 勾選商品或取消勾選時，更新按鈕狀態。
    byId("recommendation-table-body")
        .addEventListener("change", event => {

            if (
                event.target.matches(
                    ".recommendation-select, .actual-order-quantity"
                )
            ) {
                updateCreateButtonState();
            }
        });

    // 修改實際訂貨數量時，更新按鈕狀態。
    byId("recommendation-table-body")
        .addEventListener("input", event => {

            if (
                event.target.matches(
                    ".actual-order-quantity"
                )
            ) {
                updateCreateButtonState();
            }
        });

    // 點擊建立採購草稿。
    byId("create-order-from-recommendation")
        .addEventListener("click", openOrderPanel);

    // 關閉表單。
    byId("recommendation-order-close")
        .addEventListener("click", closeOrderPanel);

    // 取消建立。
    byId("recommendation-order-cancel")
        .addEventListener("click", closeOrderPanel);

    // 正式建立 DRAFT 採購草稿。
    byId("recommendation-order-form")
        .addEventListener("submit", createPurchaseOrder);

    // 預設不允許建立草稿。
    updateCreateButtonState();
}

/**
 * 查詢智慧訂貨建議。
 */
async function loadRecommendations() {

    const form = byId("recommendation-search-form");
    const button =
        form.querySelector('button[type="submit"]');

    try {

        const parameters =
            getSearchParameters("recommendation");

        setButtonLoading(
            button,
            true,
            "計算訂貨建議中..."
        );

        hideMessage();

        recommendationLoaded = false;
        recommendationData = [];

        closeOrderPanel();
        updateCreateButtonState();

        showEmpty(
            byId("recommendation-summary"),
            "正在計算訂貨建議..."
        );

        clearElement(
            byId("recommendation-table-body")
        );

        const data = await apiGet(
            API.recommendations,
            parameters
        );

        if (!Array.isArray(data)) {
            throw new Error(
                "訂貨建議 API 回傳格式不正確"
            );
        }

        recommendationData = data;
        recommendationLoaded = true;

        renderRecommendationSummary(data);
        renderRecommendationTable(data);

        updateCreateButtonState();

        showMessage(
            `訂貨建議計算完成，共 ${data.length} 筆商品`,
            "success"
        );

    } catch (error) {

        recommendationLoaded = false;
        recommendationData = [];

        clearElement(
            byId("recommendation-summary")
        );

        const tbody =
            byId("recommendation-table-body");

        clearElement(tbody);

        const row = makeElement("tr");

        const cell = makeElement(
            "td",
            "無法取得訂貨建議",
            "empty-state"
        );

        cell.colSpan = 7;

        row.appendChild(cell);
        tbody.appendChild(row);

        byId("stat-reorder").textContent = "--";

        updateCreateButtonState();

        showMessage(error.message, "error");

        console.error(
            "OrderRecommendation API 錯誤：",
            error
        );

    } finally {

        setButtonLoading(button, false);
    }
}

/**
 * 顯示訂貨建議摘要。
 */
function renderRecommendationSummary(data) {

    const container =
        byId("recommendation-summary");

    clearElement(container);

    const needsReorder = data.filter(
        item => item.needsReorder === true
    ).length;

    const totalQuantity = data.reduce(
        (sum, item) =>
            sum +
            (Number(item.recommendedQuantity) || 0),
        0
    );

    container.appendChild(
        createStatCard(
            "有效商品數",
            data.length,
            "本次查詢"
        )
    );

    container.appendChild(
        createStatCard(
            "需要補貨商品",
            needsReorder,
            "建議數量大於零"
        )
    );

    container.appendChild(
        createStatCard(
            "建議訂貨總數",
            formatNumber(totalQuantity),
            "所有商品建議量合計"
        )
    );

    byId("stat-reorder").textContent =
        needsReorder;
}

/**
 * 顯示訂貨建議商品表格。
 */
function renderRecommendationTable(data) {

    const tbody =
        byId("recommendation-table-body");

    clearElement(tbody);

    if (data.length === 0) {

        const row = makeElement("tr");

        const cell = makeElement(
            "td",
            "目前沒有符合條件的有效商品",
            "empty-state"
        );

        cell.colSpan = 7;

        row.appendChild(cell);
        tbody.appendChild(row);

        return;
    }

    data.forEach(item => {

        const row = makeElement("tr");

        // 第一欄：商品選取。
        const selectCell = makeElement("td");

        const checkbox = makeElement("input");

        checkbox.type = "checkbox";
        checkbox.className =
            "recommendation-select";

        checkbox.dataset.productId =
            String(item.productId);

        checkbox.setAttribute(
            "aria-label",
            `選擇商品 ${displayValue(item.productName)}`
        );

        selectCell.appendChild(checkbox);
        row.appendChild(selectCell);

        // 第二欄：商品名稱與 SKU。
        const productCell = makeElement("td");

        productCell.appendChild(
            makeElement(
                "strong",
                item.productName
            )
        );

        productCell.appendChild(
            makeElement(
                "div",
                displayValue(item.sku),
                "weather-description"
            )
        );

        row.appendChild(productCell);

        // 第三欄：目前庫存。
        row.appendChild(
            makeElement(
                "td",
                formatNumber(item.currentStock)
            )
        );

        // 第四欄：歷史銷售。
        row.appendChild(
            makeElement(
                "td",
                formatNumber(
                    item.historicalSalesQuantity
                )
            )
        );

        // 第五欄：天氣倍率。
        const factor = item.weatherFactor;

        row.appendChild(
            makeElement(
                "td",
                factor == null
                    ? "--"
                    : `${Number(factor).toFixed(2)} ×`
            )
        );

        // 第六欄：建議訂貨量。
        const quantityCell = makeElement("td");

        quantityCell.appendChild(
            makeElement(
                "span",
                formatNumber(item.recommendedQuantity),
                item.needsReorder
                    ? "status-badge warning"
                    : "status-badge success"
            )
        );

        row.appendChild(quantityCell);

        // 第七欄：使用者實際訂貨數量。
        const actualCell = makeElement("td");

        const quantityInput = makeElement("input");

        quantityInput.type = "number";
        quantityInput.min = "0";
        quantityInput.max = String(MAX_QUANTITY);
        quantityInput.step = "1";

        quantityInput.value = String(
            item.recommendedQuantity ?? 0
        );

        quantityInput.className =
            "actual-order-quantity";

        quantityInput.dataset.productId =
            String(item.productId);

        quantityInput.style.width = "90px";

        quantityInput.setAttribute(
            "aria-label",
            `${displayValue(item.productName)}實際訂貨量`
        );

        actualCell.appendChild(quantityInput);
        row.appendChild(actualCell);

        // 保留詳細計算依據。
        row.title =
            `歷史銷售：${displayValue(item.historicalSalesQuantity)}\n` +
            `天氣倍率：${displayValue(item.weatherFactor)}\n` +
            `調整後需求：${displayValue(item.weatherAdjustedSalesQuantity)}\n` +
            `安全庫存：${displayValue(item.safetyStock)}\n` +
            `目前庫存：${displayValue(item.currentStock)}\n` +
            `未到貨數量：${displayValue(item.outstandingQuantity)}\n` +
            `可用庫存：${displayValue(item.availableStock)}`;

        tbody.appendChild(row);
    });
}

/**
 * 驗證數量是否為正整數。
 *
 * 回傳 null 代表無效。
 */
function parsePositiveQuantity(value) {

    const text = String(value).trim();

    // 不允許空字串、小數、負數及科學記號。
    if (!/^[0-9]+$/.test(text)) {
        return null;
    }

    const quantity = Number(text);

    if (
        !Number.isSafeInteger(quantity) ||
        quantity <= 0 ||
        quantity > MAX_QUANTITY
    ) {
        return null;
    }

    return quantity;
}

/**
 * 收集訂貨建議表格中已勾選的商品。
 *
 * 使用者可以自行修改訂貨量。
 * 但真正送出時，每筆數量必須大於 0。
 */
function getSelectedItems() {

    const checkboxes = document.querySelectorAll(
        "#recommendation-table-body " +
        ".recommendation-select:checked"
    );

    const selectedItems = [];

    checkboxes.forEach(checkbox => {

        const productId =
            Number(checkbox.dataset.productId);

        const quantityInput = Array.from(
            document.querySelectorAll(
                "#recommendation-table-body " +
                ".actual-order-quantity"
            )
        ).find(input =>
            Number(input.dataset.productId) ===
            productId
        );

        if (!quantityInput) {
            throw new Error(
                "找不到商品訂貨數量欄位"
            );
        }

        const quantity = parsePositiveQuantity(
            quantityInput.value
        );

        if (quantity === null) {
            throw new Error(
                `商品 ID ${productId} 的訂貨數量必須為大於 0 的整數`
            );
        }

        if (
            !Number.isSafeInteger(productId) ||
            productId <= 0
        ) {
            throw new Error(
                "商品編號不正確"
            );
        }

        selectedItems.push({
            productId,
            quantity
        });
    });

    return selectedItems;
}

/**
 * 根據商品選取狀態與實際訂貨數量，
 * 決定是否啟用建立採購草稿按鈕。
 *
 * 暫時加入 Console 診斷訊息，
 * 用來找出按鈕持續停用的原因。
 */
function updateCreateButtonState() {

    const button = byId(
        "create-order-from-recommendation"
    );

    if (!button) {
        console.error("找不到建立採購草稿按鈕");
        return;
    }

    // 顯示模組目前的內部狀態。
    console.log("【訂貨建議】按鈕狀態檢查：", {
        recommendationLoaded,
        creatingOrder
    });

    // 尚未載入成功，或正在建立訂單時停用按鈕。
    if (!recommendationLoaded || creatingOrder) {

        button.disabled = true;

        console.warn(
            "【訂貨建議】按鈕停用：資料未載入或正在建立訂單"
        );

        return;
    }

    try {

        // 取得已勾選且數量合法的商品。
        const items = getSelectedItems();

        console.log(
            "【訂貨建議】已選取商品：",
            items
        );

        // 至少選擇一項商品才允許建立採購草稿。
        button.disabled = items.length === 0;

        console.log(
            "【訂貨建議】最終按鈕狀態：",
            button.disabled
        );

    } catch (error) {

        // 將原本被隱藏的錯誤原因顯示出來。
        console.error(
            "【訂貨建議】商品驗證失敗：",
            error
        );

        button.disabled = true;
    }
}

/**
 * 從 Supplier API 載入有效供應商。
 */
async function loadSuppliersForOrder() {

    const select = byId(
        "recommendation-supplier-id"
    );

    clearElement(select);

    const placeholder = makeElement(
        "option",
        "請選擇供應商"
    );

    placeholder.value = "";
    select.appendChild(placeholder);

    const response = await requestJson(
        API.suppliers
    );

    // 相容一般陣列或分頁回傳格式。
    const suppliers = Array.isArray(response)
        ? response
        : response?.content;

    if (!Array.isArray(suppliers)) {
        throw new Error(
            "供應商 API 回傳格式不正確"
        );
    }

    // 排除已停用或已軟刪除的供應商。
    const activeSuppliers = suppliers.filter(
        supplier =>
            Number(supplier.status) === 1 &&
            supplier.deletedAt == null
    );

    activeSuppliers.forEach(supplier => {

        const option = makeElement(
            "option",
            supplier.name
        );

        option.value = String(supplier.id);

        select.appendChild(option);
    });

    if (activeSuppliers.length === 0) {
        throw new Error(
            "目前沒有可使用的有效供應商，請先建立或啟用供應商"
        );
    }
}

/**
 * 開啟採購草稿表單。
 *
 * 不會在此步驟呼叫 POST API。
 */
async function openOrderPanel() {

    if (creatingOrder) return;

    const button = byId(
        "create-order-from-recommendation"
    );

    try {

        if (!recommendationLoaded) {
            throw new Error(
                "請先產生訂貨建議"
            );
        }

        const selectedItems = getSelectedItems();

        if (selectedItems.length === 0) {
            throw new Error(
                "請先勾選至少一項商品"
            );
        }

        hideMessage();

        button.disabled = true;

        // 每次開啟時清除前一次填寫內容。
        byId("recommendation-order-form").reset();

        // =====================================================
        // 限制預計到貨日期不可早於台灣今天
        // =====================================================

        // 使用既有台灣日期工具，避免 UTC 時差問題。
        const today = getTaiwanToday();

        // 取得預計到貨日期欄位。
        const deliveryDateInput = byId(
            "recommendation-delivery-date"
        );

        // 不允許選擇今天以前的日期。
        deliveryDateInput.min = today;

        // 每次開啟表單時重新設定最小日期，
        // 避免頁面跨日後仍使用昨天的限制。

        // 先載入有效供應商。
        await loadSuppliersForOrder();

        // 產生確認表單的商品明細。
        renderOrderPreview(selectedItems);

        // 顯示草稿表單。
        byId("recommendation-order-panel").hidden =
            false;

        byId("recommendation-order-panel")
            .scrollIntoView({
                behavior: "smooth",
                block: "start"
            });

    } catch (error) {

        byId("recommendation-order-panel").hidden =
            true;

        showMessage(error.message, "error");

        console.error(
            "開啟採購草稿失敗：",
            error
        );

    } finally {

        updateCreateButtonState();
    }
}

/**
 * 將勾選的商品顯示於採購草稿確認表格。
 *
 * 目前後端 Request 不接收採購單價，
 * 因此不自行推算採購價格。
 */
function renderOrderPreview(items) {

    const tbody = byId(
        "recommendation-order-items-body"
    );

    clearElement(tbody);

    items.forEach(item => {

        const product = recommendationData.find(
            data =>
                Number(data.productId) ===
                item.productId
        );

        if (!product) {
            throw new Error(
                "找不到選取商品的訂貨建議資料"
            );
        }

        const row = makeElement("tr");

        row.dataset.productId =
            String(item.productId);

        // 商品名稱與 SKU。
        const productCell = makeElement("td");

        productCell.appendChild(
            makeElement(
                "strong",
                product.productName
            )
        );

        productCell.appendChild(
            makeElement(
                "div",
                displayValue(product.sku),
                "weather-description"
            )
        );

        row.appendChild(productCell);

        // 讓使用者再次確認實際訂貨數量。
        const quantityCell = makeElement("td");

        const quantityInput = makeElement("input");

        quantityInput.type = "number";
        quantityInput.min = "1";
        quantityInput.max = String(MAX_QUANTITY);
        quantityInput.step = "1";
        quantityInput.required = true;
        quantityInput.value = String(item.quantity);

        quantityInput.className =
            "recommendation-preview-quantity";

        quantityInput.style.width = "100px";

        quantityCell.appendChild(quantityInput);
        row.appendChild(quantityCell);

        // 目前 Request DTO 不提供採購單價。
        row.appendChild(
            makeElement("td", "—")
        );

        // 金額由後端建立訂單時處理。
        row.appendChild(
            makeElement("td", "由後端計算")
        );

        tbody.appendChild(row);
    });

    // 不顯示虛構的預估採購金額。
    byId("recommendation-order-total")
        .textContent = "由後端計算";
}

/**
 * 關閉採購草稿表單。
 *
 * 若正在送出請求，不允許關閉，
 * 避免使用者誤以為請求已取消。
 */
function closeOrderPanel() {

    if (creatingOrder) return;

    const panel = byId(
        "recommendation-order-panel"
    );

    if (panel) {
        panel.hidden = true;
    }
}

/**
 * 讀取採購草稿預覽表格中的商品明細。
 */
function getPreviewItems() {

    const rows = document.querySelectorAll(
        "#recommendation-order-items-body " +
        "tr[data-product-id]"
    );

    const items = [];

    rows.forEach(row => {

        const productId =
            Number(row.dataset.productId);

        const quantityInput = row.querySelector(
            ".recommendation-preview-quantity"
        );

        const quantity = parsePositiveQuantity(
            quantityInput?.value ?? ""
        );

        if (
            !Number.isSafeInteger(productId) ||
            productId <= 0 ||
            quantity === null
        ) {
            throw new Error(
                "採購明細的商品編號或訂貨數量不正確"
            );
        }

        items.push({
            productId,
            quantity
        });
    });

    if (items.length === 0) {
        throw new Error(
            "採購單至少需要一筆商品明細"
        );
    }

    return items;
}

/**
 * 建立真正的採購草稿。
 *
 * POST /api/purchase-orders
 *
 * 成功時後端會建立 DRAFT 採購單，
 * 不會在此自動送出成 SUBMITTED。
 */
async function createPurchaseOrder(event) {

    event.preventDefault();

    if (creatingOrder) return;

    const form = byId(
        "recommendation-order-form"
    );

    const button = byId(
        "recommendation-order-submit"
    );

    try {

        const supplierId = Number(
            byId("recommendation-supplier-id").value
        );

        if (
            !Number.isSafeInteger(supplierId) ||
            supplierId <= 0
        ) {
            throw new Error(
                "請選擇有效的供應商"
            );
        }

        const items = getPreviewItems();

        const deliveryDate = byId(
            "recommendation-delivery-date"
        ).value;

        // =====================================================
        // 再次驗證預計到貨日期
        // =====================================================

        // 預計到貨日可以不填。
        // 若有填寫，就不可早於台灣今天。
        if (
            deliveryDate &&
            deliveryDate < getTaiwanToday()
        ) {
            throw new Error(
                "預計到貨日期不可早於今天"
            );
        }
        
        const remark = byId(
            "recommendation-order-remark"
        ).value.trim();

        if (remark.length > 500) {
            throw new Error(
                "採購備註不可超過 500 個字元"
            );
        }

        // 完全符合目前 PurchaseOrderRequest、
        // PurchaseOrderItemRequest 的欄位結構。
        const payload = {
            supplierId,
            expectedDeliveryDate:
                deliveryDate || null,
            remark: remark || null,
            items
        };

        creatingOrder = true;

        setButtonLoading(
            button,
            true,
            "建立採購草稿中..."
        );

        updateCreateButtonState();

        hideMessage();

        // 呼叫目前已存在的採購單建立 API。
        const createdOrder = await requestJson(
            API.purchaseOrders,
            "POST",
            payload
        );

        // 建立成功後關閉表單。
        byId("recommendation-order-panel").hidden =
            true;

        // 清除已勾選商品，降低重複建立風險。
        document.querySelectorAll(
            "#recommendation-table-body " +
            ".recommendation-select"
        ).forEach(checkbox => {
            checkbox.checked = false;
        });

        const orderId = createdOrder?.id;

        showMessage(
            orderId != null
                ? `採購草稿 #${orderId} 建立成功，請至採購訂貨單頁面確認並送出`
                : "採購草稿建立成功，請至採購訂貨單頁面確認",
            "success"
        );

        form.reset();

    } catch (error) {

        showMessage(error.message, "error");

        console.error(
            "建立採購草稿失敗：",
            error
        );

    } finally {

        creatingOrder = false;

        setButtonLoading(
            button,
            false
        );

        updateCreateButtonState();
    }
}
