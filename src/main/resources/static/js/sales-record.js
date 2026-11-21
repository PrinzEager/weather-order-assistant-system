"use strict";

/* 銷售紀錄管理：新增銷售、部分退貨，以及查詢每筆退貨歷史。 */
import {
    API, byId, makeElement, showMessage, hideMessage,
    setButtonLoading, displayValue, formatNumber, requestJson
} from "./common.js";
import { refreshProducts } from "./product.js";

let salesLoaded = false;

// 目前正在查看／退貨的銷售紀錄 ID。
let activeReturnSalesId = null;

// 防止使用者在退貨 API 尚未完成時重複提交。
let returnSubmitting = false;

export function initSales() {
    installSalesForm();
    installReturnPanel();
    byId("sale-return-close").addEventListener("click", closeReturnPanel);
    byId("sale-return-cancel").addEventListener("click", closeReturnPanel);
    byId("sale-return-form").addEventListener("submit", submitReturnForm);
    byId("add-sale-button").disabled = false;
    byId("add-sale-button").addEventListener("click", openSalesForm);
    byId("sale-form-close").addEventListener("click", closeSalesForm);
    byId("sale-form-cancel").addEventListener("click", closeSalesForm);
    byId("sale-product-id").addEventListener("change", fillProductPrice);
    byId("sale-form").addEventListener("submit", submitSalesForm);
}

export function enterSalesPage() {
    if (!salesLoaded) void refreshSales();
}

export async function refreshSales() {
    renderMessageRow("載入銷售紀錄中...");
    try {
        hideMessage();
        const records = await requestJson(API.salesRecords);
        if (!Array.isArray(records)) throw new Error("銷售紀錄回傳格式不正確");
        renderSales(records);
        salesLoaded = true;
        return true;
    } catch (error) {
        salesLoaded = false;
        renderMessageRow("無法載入銷售紀錄");
        showMessage(error.message, "error");
        console.error("SalesRecord API 錯誤：", error);
        return false;
    }
}

function renderSales(records) {
    const tbody = byId("sales-table-body");
    tbody.replaceChildren();
    if (records.length === 0) {
        renderMessageRow("目前沒有銷售紀錄");
        return;
    }

    records.forEach(record => {
        const row = makeElement("tr");
        const product = `${displayValue(record.productName)}（${displayValue(record.sku)}）`;
        [
            record.id,
            product,
            formatNumber(record.quantity),
            `NT$ ${formatNumber(record.unitPrice)}`,
            `NT$ ${formatNumber(record.totalAmount)}`,
            formatDateTime(record.dateTime)
        ].forEach(value => row.appendChild(makeElement("td", value)));

        const actions = makeElement("td");
        // 原本的 DELETE 整筆退貨已停用，改為新的 POST 部分退貨 API。
        const remaining = getRemainingQuantity(record);
        const refund = makeElement("button", "辦理退貨", "danger-button");
        refund.type = "button";
        refund.disabled = remaining <= 0 || isReturnExpired(record.dateTime);
        refund.title = remaining <= 0
            ? "這筆銷售已全數退貨"
            : isReturnExpired(record.dateTime)
                ? "一般退貨期限已過，無法辦理退貨"
                : `最多可退 ${remaining} 件`;
        refund.addEventListener("click", () => void openReturnPanel(record.id));

        // 即使已全數退貨或期限已過，仍可查詢歷史資料。
        const history = makeElement("button", "退貨紀錄", "secondary-button");
        history.type = "button";
        history.addEventListener("click", () => void openReturnPanel(record.id));
        actions.append(refund, history);
        row.appendChild(actions);
        tbody.appendChild(row);
    });
}

// 銷售時間與總額由後端產生，前端僅傳 ProductId、Quantity、UnitPrice。
function installSalesForm() {
    if (byId("sale-form")) return;
    const panel = makeElement("div", null, "panel");
    panel.id = "sale-form-panel";
    panel.hidden = true;

    const toolbar = makeElement("div", null, "panel-toolbar");
    const title = makeElement("h4", "新增銷售紀錄");
    const close = makeElement("button", "關閉", "secondary-button");
    close.id = "sale-form-close";
    close.type = "button";
    toolbar.append(title, close);
    panel.appendChild(toolbar);

    const form = makeElement("form");
    form.id = "sale-form";
    form.append(
        formField("sale-product-id", "商品 *", "select"),
        formField("sale-quantity", "銷售數量 *", "number", { min: "1", step: "1", value: "1" }),
        formField("sale-unit-price", "銷售單價 *", "number", { min: "0", step: "0.01" })
    );

    const buttons = makeElement("div", null, "action-bar");
    const cancel = makeElement("button", "取消", "secondary-button");
    cancel.id = "sale-form-cancel";
    cancel.type = "button";
    const submit = makeElement("button", "建立銷售紀錄", "primary-button");
    submit.id = "sale-form-submit";
    submit.type = "submit";
    buttons.append(cancel, submit);
    form.appendChild(buttons);
    panel.appendChild(form);
    byId("page-sales").appendChild(panel);
}

function formField(id, labelText, type, attributes = {}) {
    const group = makeElement("div", null, "form-group");
    const label = makeElement("label", labelText);
    const input = makeElement(type === "select" ? "select" : "input");
    label.htmlFor = id;
    input.id = id;
    input.required = true;
    if (type !== "select") input.type = type;
    Object.entries(attributes).forEach(([name, value]) => input.setAttribute(name, value));
    group.append(label, input);
    return group;
}

async function openSalesForm() {
    const form = byId("sale-form");
    form.reset();
    byId("sale-quantity").value = "1";
    const select = byId("sale-product-id");
    select.replaceChildren(createOption("", "請選擇商品"));

    try {
        const products = await requestJson(API.products);
        if (!Array.isArray(products)) throw new Error("商品清單回傳格式不正確");
        products.forEach(product => {
            select.appendChild(createOption(product.id, `${product.name}（${product.sku}，庫存 ${product.currentStock}）`));
        });
        byId("sale-form-panel").hidden = false;
        byId("sale-form-panel").scrollIntoView({ behavior: "smooth", block: "start" });
    } catch (error) {
        showMessage(error.message, "error");
    }
}

async function fillProductPrice() {
    const productId = Number(byId("sale-product-id").value);
    if (!productId) return;
    try {
        const product = await requestJson(`${API.products}/${productId}`);
        byId("sale-unit-price").value = product.price ?? "";
    } catch (error) {
        showMessage(error.message, "error");
    }
}

function closeSalesForm() {
    byId("sale-form-panel").hidden = true;
    byId("sale-form").reset();
}

async function submitSalesForm(event) {
    event.preventDefault();
    const productId = Number(byId("sale-product-id").value);
    const quantity = Number(byId("sale-quantity").value);
    const unitPriceText = byId("sale-unit-price").value.trim();
    if (!Number.isSafeInteger(productId) || productId <= 0 ||
        !Number.isSafeInteger(quantity) || quantity <= 0) {
        showMessage("請選擇商品並輸入正整數銷售數量", "error");
        return;
    }
    if (!/^\d{1,8}(\.\d{1,2})?$/.test(unitPriceText)) {
        showMessage("單價最多 8 位整數及 2 位小數", "error");
        return;
    }

    const submit = byId("sale-form-submit");
    try {
        setButtonLoading(submit, true, "建立中...");
        await requestJson(API.salesRecords, "POST", {
            productId,
            quantity,
            unitPrice: Number(unitPriceText)
        });
        closeSalesForm();
        await refreshSales();
        // 銷售會扣除庫存，同步更新商品頁與首頁商品統計。
        await refreshProducts();
        showMessage("銷售紀錄已建立，商品庫存已扣除", "success");
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        setButtonLoading(submit, false);
    }
}

// =====================================================
// 部分退貨：動態新增表單與退貨歷史，不必修改 index.html。
// =====================================================
function installReturnPanel() {
    if (byId("sale-return-panel")) return;

    const panel = makeElement("div", null, "panel");
    panel.id = "sale-return-panel";
    panel.hidden = true;

    const toolbar = makeElement("div", null, "panel-toolbar");
    const title = makeElement("h4", "銷售退貨管理");
    const close = makeElement("button", "關閉", "secondary-button");
    close.id = "sale-return-close";
    close.type = "button";
    toolbar.append(title, close);
    panel.appendChild(toolbar);

    // 原始銷售數量、累計已退貨、剩餘可退貨數量由後端提供。
    const info = makeElement("p", "正在載入銷售資料...");
    info.id = "sale-return-info";
    panel.appendChild(info);

    const deadline = makeElement("p");
    deadline.id = "sale-return-deadline";
    panel.appendChild(deadline);

    const form = makeElement("form");
    form.id = "sale-return-form";
    form.appendChild(formField(
        "sale-return-quantity", "本次退貨數量 *", "number",
        { min: "1", step: "1", value: "1" }
    ));

    const reasonGroup = makeElement("div", null, "form-group");
    const reasonLabel = makeElement("label", "退貨原因");
    reasonLabel.htmlFor = "sale-return-reason";
    const reason = makeElement("input");
    reason.id = "sale-return-reason";
    reason.type = "text";
    reason.maxLength = 100;
    reason.placeholder = "例如：購買數量過多，不超過 100 字";
    reasonGroup.append(reasonLabel, reason);
    form.appendChild(reasonGroup);

    const actions = makeElement("div", null, "action-bar");
    const cancel = makeElement("button", "取消", "secondary-button");
    cancel.type = "button";
    cancel.id = "sale-return-cancel";
    const submit = makeElement("button", "確認退貨", "primary-button");
    submit.type = "submit";
    submit.id = "sale-return-submit";
    actions.append(cancel, submit);
    form.appendChild(actions);
    panel.appendChild(form);

    const historyTitle = makeElement("h4", "歷史退貨紀錄");
    panel.appendChild(historyTitle);
    const tableWrap = makeElement("div", null, "table-responsive");
    const table = makeElement("table", null, "data-table");
    const thead = makeElement("thead");
    const headRow = makeElement("tr");
    ["退貨日期", "退貨數量", "退款金額", "退貨原因", "退貨後累計", "當時剩餘可退"]
        .forEach(text => headRow.appendChild(makeElement("th", text)));
    thead.appendChild(headRow);
    const tbody = makeElement("tbody");
    tbody.id = "sale-return-history-body";
    table.append(thead, tbody);
    tableWrap.appendChild(table);
    panel.appendChild(tableWrap);
    byId("page-sales").appendChild(panel);
}

// 回傳銷售主檔目前的剩餘可退數量。
function getRemainingQuantity(record) {
    const original = Number(record.quantity ?? 0);
    const returned = Number(record.returnedQuantity ?? 0);
    const remaining = Number(record.remainingReturnableQuantity ?? original - returned);
    return Number.isFinite(remaining) ? Math.max(0, remaining) : 0;
}

// 將後端的 LocalDateTime 視為使用者當地時間（本 Demo 以臺灣時區運行）。
// 期限的最終判斷仍以後端為準，前端只做操作提示與防止明顯無效請求。
function getReturnDeadline(saleTime) {
    if (!saleTime) return null;
    const date = new Date(String(saleTime));
    if (Number.isNaN(date.getTime())) return null;
    return new Date(date.getTime() + 7 * 24 * 60 * 60 * 1000);
}

function isReturnExpired(saleTime) {
    const deadline = getReturnDeadline(saleTime);
    return deadline ? Date.now() > deadline.getTime() : false;
}

function formatDeadline(deadline) {
    if (!deadline) return "無法判斷退貨截止時間";
    const two = value => String(value).padStart(2, "0");
    return `${deadline.getFullYear()}-${two(deadline.getMonth() + 1)}-${two(deadline.getDate())} ` +
        `${two(deadline.getHours())}:${two(deadline.getMinutes())}:${two(deadline.getSeconds())}`;
}

// 每次開啟都向後端取得最新累計數量，避免使用過期的列表資料。
async function openReturnPanel(salesRecordId) {
    if (returnSubmitting) return;
    activeReturnSalesId = salesRecordId;
    byId("sale-return-panel").hidden = false;
    byId("sale-return-form").reset();
    byId("sale-return-quantity").value = "1";
    byId("sale-return-info").textContent = "正在載入銷售資料...";
    renderReturnHistoryMessage("正在載入退貨紀錄...");
    byId("sale-return-form").hidden = true;
    byId("sale-return-panel").scrollIntoView({ behavior: "smooth", block: "start" });
    try {
        await refreshReturnPanel(salesRecordId);
    } catch (error) {
        showMessage(error.message, "error");
        byId("sale-return-info").textContent = "無法讀取銷售資料";
        renderReturnHistoryMessage("無法載入退貨紀錄");
    }
}

async function refreshReturnPanel(salesRecordId) {
    // 依序讀取銷售主檔和退貨歷史；API 不改動現有後端。
    const record = await requestJson(`${API.salesRecords}/${salesRecordId}`);
    const returns = await requestJson(`${API.salesRecords}/${salesRecordId}/returns`);
    if (activeReturnSalesId !== salesRecordId) return;
    if (!Array.isArray(returns)) throw new Error("退貨紀錄回傳格式不正確");

    const remaining = getRemainingQuantity(record);
    byId("sale-return-info").textContent =
        `銷售 #${record.id}｜${displayValue(record.productName)}｜原始銷售 ${record.quantity} 件` +
        `｜累計已退 ${record.returnedQuantity ?? 0} 件｜剩餘可退 ${remaining} 件`;

    const deadline = getReturnDeadline(record.dateTime);
    const expired = isReturnExpired(record.dateTime);
    byId("sale-return-deadline").textContent =
        `一般退貨期限：${formatDeadline(deadline)}${expired ? "（已逾期）" : ""}。` +
        "僅受理可重新販售的商品；最終以後端驗證為準。";

    // 剩餘數量為零或已超過 7 天時仍顯示歷史，但不顯示退貨表單。
    byId("sale-return-form").hidden = remaining <= 0 || expired;
    byId("sale-return-quantity").max = String(remaining);
    renderReturnHistory(returns);
}

function renderReturnHistory(records) {
    const tbody = byId("sale-return-history-body");
    tbody.replaceChildren();
    if (records.length === 0) {
        renderReturnHistoryMessage("此筆銷售尚無退貨紀錄");
        return;
    }
    records.forEach(record => {
        const row = makeElement("tr");
        [
            formatDateTime(record.returnedAt),
            formatNumber(record.quantity),
            `NT$ ${formatNumber(record.returnAmount)}`,
            displayValue(record.reason, "未填寫"),
            formatNumber(record.returnedQuantity),
            formatNumber(record.remainingReturnableQuantity)
        ].forEach(value => row.appendChild(makeElement("td", value)));
        tbody.appendChild(row);
    });
}

function renderReturnHistoryMessage(message) {
    const row = makeElement("tr");
    const cell = makeElement("td", message, "empty-state");
    cell.colSpan = 6;
    row.appendChild(cell);
    byId("sale-return-history-body").replaceChildren(row);
}

function closeReturnPanel() {
    if (returnSubmitting) return;
    activeReturnSalesId = null;
    byId("sale-return-panel").hidden = true;
    byId("sale-return-form").reset();
}

async function submitReturnForm(event) {
    event.preventDefault();
    if (returnSubmitting || activeReturnSalesId == null) return;

    const quantity = Number(byId("sale-return-quantity").value);
    const max = Number(byId("sale-return-quantity").max);
    const reason = byId("sale-return-reason").value.trim();
    if (!Number.isSafeInteger(quantity) || quantity <= 0 || quantity > max) {
        showMessage("退貨數量必須是正整數，且不可超過剩餘可退數量", "error");
        return;
    }
    if (reason.length > 100) {
        showMessage("退貨原因不可超過 100 字", "error");
        return;
    }

    if (!window.confirm(`確定退貨 ${quantity} 件？成功後會回補商品庫存。`)) return;

    const salesRecordId = activeReturnSalesId;
    const submit = byId("sale-return-submit");
    returnSubmitting = true;
    try {
        setButtonLoading(submit, true, "退貨處理中...");
        await requestJson(`${API.salesRecords}/${salesRecordId}/returns`, "POST", {
            quantity,
            reason: reason || null
        });
        // 退貨成功後重新載入銷售資料、商品庫存與歷史紀錄。
        byId("sale-return-form").reset();
        // 即使後續畫面刷新失敗，也不能把已成功的退貨誤認為失敗而重送。
        showMessage("退貨成功，商品庫存已回補", "success");
        try {
            await refreshReturnPanel(salesRecordId);
            await refreshSales();
            await refreshProducts();
        } catch (refreshError) {
            console.error("退貨成功，但部分畫面刷新失敗：", refreshError);
            showMessage("退貨已成功，但部分畫面未更新，請重新整理頁面", "error");
        }
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        returnSubmitting = false;
        setButtonLoading(submit, false);
    }
}

function createOption(value, text) {
    const option = makeElement("option", text);
    option.value = String(value);
    return option;
}

function formatDateTime(value) {
    return value ? String(value).replace("T", " ").slice(0, 19) : "--";
}

function renderMessageRow(message) {
    const row = makeElement("tr");
    const cell = makeElement("td", message, "empty-state");
    cell.colSpan = 7;
    row.appendChild(cell);
    byId("sales-table-body").replaceChildren(row);
}
