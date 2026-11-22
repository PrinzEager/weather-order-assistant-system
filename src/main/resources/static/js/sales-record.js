"use strict";

/* 銷售管理：多商品批次、展開明細；保留原有逐商品退貨與庫存規則。 */
import {
    API,
    byId,
    makeElement,
    showMessage,
    hideMessage,
    setButtonLoading,
    displayValue,
    formatNumber,
    requestJson
} from "./common.js";

import { refreshProducts } from "./product.js";

import { initSalesReport, updateSalesReport } from "./sales-report.js";

let salesLoaded = false;

let formProducts = [];

let saleLineSequence = 0;

let saleSubmitting = false;

// 目前正在查看／退貨的銷售紀錄 ID。
let activeReturnSalesId = null;

let returnSubmitting = false;

export function initSales() {
    installSalesForm();
    installReturnPanel();
    initSalesReport(renderSales);
    byId("sale-return-close").addEventListener("click", closeReturnPanel);
    byId("sale-return-cancel").addEventListener("click", closeReturnPanel);
    byId("sale-return-form").addEventListener("submit", submitReturnForm);
    byId("add-sale-button").disabled = false;
    byId("add-sale-button").addEventListener("click", openSalesForm);
    byId("sale-form-close").addEventListener("click", closeSalesForm);
    byId("sale-form-cancel").addEventListener("click", closeSalesForm);
    byId("sale-add-line").addEventListener("click", () => addSalesLine());
    byId("sale-form").addEventListener("submit", submitSalesForm);
}

export function enterSalesPage() {
    if (!salesLoaded)
        void refreshSales();
}

export async function refreshSales() {
    renderMessageRow("載入銷售紀錄中...");
    try {
        hideMessage();
        const records = await requestJson(API.salesRecords);
        if (!Array.isArray(records))
            throw new Error("銷售紀錄回傳格式不正確");
        updateSalesReport(records);
        salesLoaded = true;
        return true;
    }
    catch (error) {
        salesLoaded = false;
        renderMessageRow("無法載入銷售紀錄");
        showMessage(error.message, "error");
        console.error("SalesRecord API 錯誤：", error);
        return false;
    }
}

function money(cents) {
    return (cents / 100).toLocaleString("zh-TW", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function cents(value) { return Math.round(Number(value ?? 0) * 100); }

/**
 * 舊資料 batchId 為 null，每筆獨立顯示；新資料依批次分組。
 * 透過原 salesRecord.id 逐項辦理退貨，不使用批次 ID 作為退貨對象。
 */
function groupSales(records) {
    const groups = new Map();
    records.forEach(record => {
        const key = record.batchId || `single:${record.id}`;
        if (!groups.has(key))
            groups.set(key, []);
        groups.get(key).push(record);
    });
    return [...groups.values()].sort((a, b) => String(b[0].dateTime ?? "").localeCompare(String(a[0].dateTime ?? "")));
}

function renderSales(records) {
    const tbody = byId("sales-table-body");
    tbody.replaceChildren();
    if (!records.length) {
        renderMessageRow("此日期區間沒有銷售紀錄");
        return;
    }
    groupSales(records).forEach(group => {
        const first = group[0];
        const grossQty = group.reduce((sum, row) => sum + Number(row.quantity || 0), 0);
        const totalCents = group.reduce((sum, row) => sum + cents(row.totalAmount), 0);
        const row = makeElement("tr");
        const expandCell = makeElement("td");
        const toggle = makeElement("button", "▸", "sales-detail-toggle");
        toggle.type = "button";
        toggle.setAttribute("aria-expanded", "false");
        toggle.setAttribute("aria-label", "展開銷售明細");
        expandCell.appendChild(toggle);
        row.appendChild(expandCell);
        const summary = group.length === 1
            ? `${displayValue(first.productName)}（${displayValue(first.sku)}）`
            : `${displayValue(first.productName)} 等 ${group.length} 項商品`;
        [summary, formatNumber(grossQty), `${group.length} 項`, `NT$ ${money(totalCents)}`, formatDateTime(first.dateTime)]
            .forEach(value => row.appendChild(makeElement("td", value)));
        row.appendChild(makeElement("td", "展開後可逐項退貨"));

        const detailRow = makeElement("tr", null, "sales-batch-detail");
        detailRow.hidden = true;
        const detailCell = makeElement("td");
        detailCell.colSpan = 7;
        const table = makeElement("table", null, "sales-detail-table");
        const heading = makeElement("thead");
        const headingsRow = makeElement("tr");
        ["商品", "銷售量", "已退貨", "剩餘可退", "成交單價", "原始小計", "操作"]
            .forEach(text => headingsRow.appendChild(makeElement("th", text)));
        heading.appendChild(headingsRow);
        table.appendChild(heading);
        const body = makeElement("tbody");
        group.forEach(record => {
            const line = makeElement("tr");
            const remaining = getRemainingQuantity(record);
            [`${displayValue(record.productName)}（${displayValue(record.sku)}）`,
                `${record.quantity} 件`, `${record.returnedQuantity ?? 0} 件`, `${remaining} 件`,
                `NT$ ${money(cents(record.unitPrice))}`, `NT$ ${money(cents(record.totalAmount))}`]
                .forEach(value => line.appendChild(makeElement("td", value)));
            const actions = makeElement("td");
            const buttons = makeElement("div", null, "sales-detail-actions");
            const refund = makeElement("button", "辦理退貨", "danger-button");
            refund.type = "button";
            refund.disabled = remaining <= 0 || isReturnExpired(record.dateTime);
            refund.title = remaining <= 0 ? "已全數退貨" : isReturnExpired(record.dateTime)
                ? "已超過一般七天退貨期限" : `最多可退 ${remaining} 件`;
            refund.addEventListener("click", () => void openReturnPanel(record.id));
            const history = makeElement("button", "退貨紀錄", "secondary-button");
            history.type = "button";
            history.addEventListener("click", () => void openReturnPanel(record.id));
            buttons.append(refund, history);
            actions.appendChild(buttons);
            line.appendChild(actions);
            body.appendChild(line);
        });
        table.appendChild(body);
        detailCell.appendChild(table);
        detailRow.appendChild(detailCell);
        toggle.addEventListener("click", () => {
            detailRow.hidden = !detailRow.hidden;
            const expanded = !detailRow.hidden;
            toggle.textContent = expanded ? "▾" : "▸";
            toggle.setAttribute("aria-expanded", String(expanded));
            toggle.setAttribute("aria-label", expanded ? "收合銷售明細" : "展開銷售明細");
        });
        tbody.append(row, detailRow);
    });
}

function installSalesForm() {
    if (byId("sale-form"))
        return;
    const panel = makeElement("div", null, "panel");
    panel.id = "sale-form-panel";
    panel.hidden = true;
    const toolbar = makeElement("div", null, "panel-toolbar");
    const title = makeElement("h4", "新增多商品銷售");
    const close = makeElement("button", "關閉", "secondary-button");
    close.id = "sale-form-close";
    close.type = "button";
    toolbar.append(title, close);
    panel.appendChild(toolbar);
    panel.appendChild(makeElement("p", "一次新增多項商品，全部成功才會扣除庫存；同種商品請合併為一列。", "sales-entry-hint"));
    const form = makeElement("form");
    form.id = "sale-form";
    const lines = makeElement("div");
    lines.id = "sale-line-items";
    form.appendChild(lines);
    const footer = makeElement("div", null, "sales-entry-footer");
    const add = makeElement("button", "+ 新增商品", "secondary-button");
    add.type = "button";
    add.id = "sale-add-line";
    const summary = makeElement("div", "合計：0 件／NT$ 0.00", "sales-entry-total");
    summary.id = "sale-form-summary";
    footer.append(add, summary);
    form.appendChild(footer);
    const buttons = makeElement("div", null, "action-bar");
    const cancel = makeElement("button", "取消", "secondary-button");
    cancel.id = "sale-form-cancel";
    cancel.type = "button";
    const submit = makeElement("button", "確認建立銷售", "primary-button");
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
    if (type !== "select")
        input.type = type;
    Object.entries(attributes).forEach(([name, value]) => input.setAttribute(name, value));
    group.append(label, input);
    return group;
}

function createOption(value, text) {
    const option = makeElement("option", text);
    option.value = String(value);
    return option;
}

function addSalesLine() {
    const container = byId("sale-line-items");
    if (container.children.length >= 50) {
        showMessage("一次銷售最多選擇 50 項商品", "error");
        return;
    }
    const seq = ++saleLineSequence;
    const line = makeElement("div", null, "sales-entry-line");
    line.dataset.saleLine = String(seq);
    const productField = formField(`sale-product-${seq}`, "商品 *", "select");
    const select = productField.querySelector("select");
    select.appendChild(createOption("", "請選擇商品"));
    formProducts.forEach(product => select.appendChild(createOption(product.id, `${product.name}（${product.sku}，庫存 ${product.currentStock}）`)));
    const qtyField = formField(`sale-quantity-${seq}`, "數量 *", "number", { min: "1", step: "1", value: "1" });
    const priceField = formField(`sale-price-${seq}`, "單價（NT$）*", "number", { min: "0", step: "0.01", max: "99999999.99" });
    const price = priceField.querySelector("input");
    const qty = qtyField.querySelector("input");
    select.addEventListener("change", () => {
        const product = formProducts.find(item => String(item.id) === select.value);
        price.value = product?.price ?? "";
        updateSalesPreview();
    });
    qty.addEventListener("input", updateSalesPreview);
    price.addEventListener("input", updateSalesPreview);
    const remove = makeElement("button", "移除", "secondary-button sales-entry-remove");
    remove.type = "button";
    remove.addEventListener("click", () => {
        if (container.children.length <= 1) {
            showMessage("至少需保留一項商品", "error");
            return;
        }
        line.remove();
        updateSalesPreview();
    });
    line.append(productField, qtyField, priceField, remove);
    container.appendChild(line);
    updateSalesPreview();
}

function readSalesLines() {
    return [...byId("sale-line-items").children].map(line => {
        const numbers = line.querySelectorAll('input[type="number"]');
        return {
            productId: Number(line.querySelector("select").value),
            quantity: Number(numbers[0]?.value),
            unitPriceText: numbers[1]?.value.trim() ?? ""
        };
    });
}

function updateSalesPreview() {
    const items = readSalesLines();
    const quantity = items.reduce((sum, item) => sum + (Number.isSafeInteger(item.quantity) && item.quantity > 0 ? item.quantity : 0), 0);
    const totalCents = items.reduce((sum, item) => sum + (Number.isFinite(Number(item.unitPriceText)) && Number(item.unitPriceText) >= 0
        ? Math.round(Number(item.unitPriceText) * 100) * Math.max(0, item.quantity || 0) : 0), 0);
    byId("sale-form-summary").textContent = `合計：${quantity} 件／NT$ ${money(totalCents)}`;
}

async function openSalesForm() {
    try {
        const products = await requestJson(API.products);
        if (!Array.isArray(products))
            throw new Error("商品清單回傳格式不正確");
        formProducts = products;
        if (!products.length) {
            showMessage("目前沒有可銷售的啟用商品", "error");
            return;
        }
        byId("sale-form").reset();
        byId("sale-line-items").replaceChildren();
        addSalesLine();
        byId("sale-form-panel").hidden = false;
        byId("sale-form-panel").scrollIntoView({ behavior: "smooth", block: "start" });
    }
    catch (error) {
        showMessage(error.message, "error");
    }
}

function closeSalesForm() {
    if (saleSubmitting)
        return;
    byId("sale-form-panel").hidden = true;
    byId("sale-form").reset();
}

async function submitSalesForm(event) {
    event.preventDefault();
    if (saleSubmitting)
        return;
    const items = readSalesLines();
    const seen = new Set();
    for (const item of items) {
        if (!Number.isSafeInteger(item.productId) || item.productId <= 0 ||
            !Number.isSafeInteger(item.quantity) || item.quantity <= 0) {
            showMessage("每項銷售都需要選擇商品與正整數數量", "error");
            return;
        }
        if (seen.has(item.productId)) {
            showMessage("同次銷售不可重複選擇商品，請合併數量", "error");
            return;
        }
        seen.add(item.productId);
        if (!/^\d{1,8}(\.\d{1,2})?$/.test(item.unitPriceText)) {
            showMessage("單價最多 8 位整數及 2 位小數", "error");
            return;
        }
    }
    if (!items.length || items.length > 50) {
        showMessage("一次銷售請選擇 1 至 50 項商品", "error");
        return;
    }
    const submit = byId("sale-form-submit");
    saleSubmitting = true;
    try {
        setButtonLoading(submit, true, "建立中...");
        // 多筆銷售只送一次後端 API，由 Service 同一交易扣庫存，避免部分成功。
        await requestJson(`${API.salesRecords}/batch`, "POST", {
            items: items.map(item => ({
                productId: item.productId, quantity: item.quantity, unitPrice: Number(item.unitPriceText)
            }))
        });
        // 建立成功後先關閉，以免後續刷新失敗導致誤操作重複提交。
        byId("sale-form-panel").hidden = true;
        let refreshed = true;
        try {
            const salesOk = await refreshSales();
            await refreshProducts();
            refreshed = salesOk;
        }
        catch (refreshError) {
            refreshed = false;
            console.error("銷售已建立，但部分資料刷新失敗", refreshError);
        }
        showMessage(refreshed
            ? `銷售建立成功，共 ${items.length} 項商品，庫存已扣除`
            : "銷售已建立並扣除庫存，但部分畫面刷新失敗，請重新整理確認", refreshed ? "success" : "error");
    }
    catch (error) {
        showMessage(error.message, "error");
    }
    finally {
        saleSubmitting = false;
        setButtonLoading(submit, false);
    }
}

// =====================================================
// 部分退貨：動態新增表單與退貨歷史，不必修改 index.html。
// =====================================================
function installReturnPanel() {
    if (byId("sale-return-panel"))
        return;

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
    form.appendChild(formField("sale-return-quantity", "本次退貨數量 *", "number", { min: "1", step: "1", value: "1" }));

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
    if (!saleTime)
        return null;
    const date = new Date(String(saleTime));
    if (Number.isNaN(date.getTime()))
        return null;
    return new Date(date.getTime() + 7 * 24 * 60 * 60 * 1000);
}

function isReturnExpired(saleTime) {
    const deadline = getReturnDeadline(saleTime);
    return deadline ? Date.now() > deadline.getTime() : false;
}

function formatDeadline(deadline) {
    if (!deadline)
        return "無法判斷退貨截止時間";
    const two = value => String(value).padStart(2, "0");
    return `${deadline.getFullYear()}-${two(deadline.getMonth() + 1)}-${two(deadline.getDate())} ` +
        `${two(deadline.getHours())}:${two(deadline.getMinutes())}:${two(deadline.getSeconds())}`;
}

// 每次開啟都向後端取得最新累計數量，避免使用過期的列表資料。
async function openReturnPanel(salesRecordId) {
    if (returnSubmitting)
        return;
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
    }
    catch (error) {
        showMessage(error.message, "error");
        byId("sale-return-info").textContent = "無法讀取銷售資料";
        renderReturnHistoryMessage("無法載入退貨紀錄");
    }
}

async function refreshReturnPanel(salesRecordId) {
    // 依序讀取銷售主檔和退貨歷史；API 不改動現有後端。
    const record = await requestJson(`${API.salesRecords}/${salesRecordId}`);
    const returns = await requestJson(`${API.salesRecords}/${salesRecordId}/returns`);
    if (activeReturnSalesId !== salesRecordId)
        return;
    if (!Array.isArray(returns))
        throw new Error("退貨紀錄回傳格式不正確");

    const remaining = getRemainingQuantity(record);
    byId("sale-return-info").textContent =
        `銷售 #${record.id}｜${displayValue(record.productName)}｜原始銷售 ${record.quantity} 件` +
            `｜累計已退 ${record.returnedQuantity ?? 0} 件｜剩餘可退 ${remaining} 件`;

    const deadline = getReturnDeadline(record.dateTime);
    const expired = isReturnExpired(record.dateTime);
    byId("sale-return-deadline").textContent =
        `一般退貨期限：${formatDeadline(deadline)}${expired ? "（已逾期）" : ""}。` +
            "僅受理可重新販售的商品；送出時仍會確認退貨條件。";

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
    if (returnSubmitting)
        return;
    activeReturnSalesId = null;
    byId("sale-return-panel").hidden = true;
    byId("sale-return-form").reset();
}

async function submitReturnForm(event) {
    event.preventDefault();
    if (returnSubmitting || activeReturnSalesId == null)
        return;

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

    if (!window.confirm(`確定退貨 ${quantity} 件？成功後會回補商品庫存。`))
        return;

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
        }
        catch (refreshError) {
            console.error("退貨成功，但部分畫面刷新失敗：", refreshError);
            showMessage("退貨已成功，但部分畫面未更新，請重新整理頁面", "error");
        }
    }
    catch (error) {
        showMessage(error.message, "error");
    }
    finally {
        returnSubmitting = false;
        setButtonLoading(submit, false);
    }
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
