"use strict";

/* 銷售紀錄管理：後端只允許新增、查詢與整筆退貨。 */
import {
    API, byId, makeElement, showMessage, hideMessage,
    setButtonLoading, displayValue, formatNumber, requestJson
} from "./common.js";
import { refreshProducts } from "./product.js";

let salesLoaded = false;

export function initSales() {
    installSalesForm();
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
        const refund = makeElement("button", "整筆退貨", "danger-button");
        refund.type = "button";
        refund.addEventListener("click", () => refundSale(record));
        actions.appendChild(refund);
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

async function refundSale(record) {
    if (!window.confirm(`確定將銷售紀錄 #${record.id} 整筆退貨？庫存將恢復。`)) return;
    try {
        await requestJson(`${API.salesRecords}/${record.id}`, "DELETE");
        await refreshSales();
        // 整筆退貨會恢復庫存，因此重新載入商品資料。
        await refreshProducts();
        showMessage("退貨完成，商品庫存已恢復", "success");
    } catch (error) {
        showMessage(error.message, "error");
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
