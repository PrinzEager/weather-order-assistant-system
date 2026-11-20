"use strict";

/* 採購單管理：對應草稿 CRUD 以及送出、到貨、取消狀態流程。 */
import {
    API, byId, makeElement, showMessage, hideMessage,
    setButtonLoading, displayValue, formatNumber, requestJson
} from "./common.js";
import { refreshProducts } from "./product.js";

let ordersLoaded = false;
let availableProducts = [];

export function initOrders() {
    installOrderForm();
    byId("add-order-button").disabled = false;
    byId("add-order-button").addEventListener("click", () => openOrderForm());
    byId("order-form-close").addEventListener("click", closeOrderForm);
    byId("order-form-cancel").addEventListener("click", closeOrderForm);
    byId("order-add-item").addEventListener("click", () => appendItemRow());
    byId("order-form").addEventListener("submit", submitOrderForm);
}

export function enterOrdersPage() {
    if (!ordersLoaded) void refreshOrders();
}

export async function refreshOrders() {
    renderMessageRow("載入採購單中...");
    try {
        hideMessage();
        const orders = await requestJson(API.purchaseOrders);
        if (!Array.isArray(orders)) throw new Error("採購單清單回傳格式不正確");
        renderOrders(orders);
        ordersLoaded = true;
        return true;
    } catch (error) {
        ordersLoaded = false;
        renderMessageRow("無法載入採購單");
        showMessage(error.message, "error");
        console.error("PurchaseOrder API 錯誤：", error);
        return false;
    }
}

function renderOrders(orders) {
    const tbody = byId("order-table-body");
    tbody.replaceChildren();
    if (orders.length === 0) {
        renderMessageRow("目前沒有採購單");
        return;
    }

    orders.forEach(order => {
        const row = makeElement("tr");
        [
            order.id,
            order.supplierNameSnapshot,
            formatDateTime(order.orderDate),
            displayValue(order.expectedDeliveryDate),
            `NT$ ${formatNumber(order.totalAmount)}`
        ].forEach(value => row.appendChild(makeElement("td", displayValue(value))));

        const statusCell = makeElement("td");
        statusCell.appendChild(createStatusBadge(order.orderStatus));
        row.appendChild(statusCell);

        const actions = makeElement("td");
        if (order.orderStatus === "DRAFT") {
            actions.append(
                actionButton("編輯", "secondary-button", () => openOrderForm(order)),
                actionButton("送出", "primary-button", () => changeOrderState(order, "submit")),
                actionButton("取消", "secondary-button", () => changeOrderState(order, "cancel")),
                actionButton("刪除", "danger-button", () => deleteOrder(order))
            );
        } else if (order.orderStatus === "SUBMITTED") {
            actions.append(
                actionButton("確認到貨", "primary-button", () => changeOrderState(order, "receive")),
                actionButton("取消", "danger-button", () => changeOrderState(order, "cancel"))
            );
        } else if (order.orderStatus === "CANCELLED") {
            actions.appendChild(actionButton("刪除", "danger-button", () => deleteOrder(order)));
        } else {
            actions.appendChild(makeElement("span", "已完成", "status-badge neutral"));
        }
        row.appendChild(actions);
        tbody.appendChild(row);
    });
}

// index.html 原先只有列表；此處建立符合 Request DTO 的採購單表單。
function installOrderForm() {
    if (byId("order-form")) return;
    const panel = makeElement("div", null, "panel");
    panel.id = "order-form-panel";
    panel.hidden = true;

    const toolbar = makeElement("div", null, "panel-toolbar");
    const title = makeElement("h4", "建立採購單");
    title.id = "order-form-title";
    const close = actionButton("關閉", "secondary-button", () => {});
    close.id = "order-form-close";
    toolbar.append(title, close);
    panel.appendChild(toolbar);

    const form = makeElement("form");
    form.id = "order-form";
    const idInput = makeElement("input");
    idInput.type = "hidden";
    idInput.id = "order-id";
    form.appendChild(idInput);
    form.append(
        formField("order-supplier-id", "供應商 *", "select"),
        formField("order-expected-delivery-date", "預計到貨日期", "date"),
        formField("order-remark", "備註", "textarea")
    );

    const itemToolbar = makeElement("div", null, "panel-toolbar");
    itemToolbar.appendChild(makeElement("h5", "採購商品明細"));
    const addItem = actionButton("新增商品", "secondary-button", () => {});
    addItem.id = "order-add-item";
    itemToolbar.appendChild(addItem);
    form.appendChild(itemToolbar);
    const items = makeElement("div");
    items.id = "order-items";
    form.appendChild(items);

    const buttons = makeElement("div", null, "action-bar");
    const cancel = actionButton("取消", "secondary-button", () => {});
    cancel.id = "order-form-cancel";
    const submit = makeElement("button", "儲存草稿", "primary-button");
    submit.id = "order-form-submit";
    submit.type = "submit";
    buttons.append(cancel, submit);
    form.appendChild(buttons);
    panel.appendChild(form);
    byId("page-orders").appendChild(panel);
}

function formField(id, labelText, type) {
    const group = makeElement("div", null, "form-group");
    const label = makeElement("label", labelText);
    const input = makeElement(type === "select" ? "select" : type === "textarea" ? "textarea" : "input");
    label.htmlFor = id;
    input.id = id;
    if (type !== "select" && type !== "textarea") input.type = type;
    if (id === "order-supplier-id") input.required = true;
    if (id === "order-remark") input.maxLength = 500;
    group.append(label, input);
    return group;
}

async function openOrderForm(order = null) {
    try {
        const [suppliers, products] = await Promise.all([
            requestJson(API.suppliers),
            requestJson(API.products)
        ]);
        if (!Array.isArray(suppliers) || !Array.isArray(products)) {
            throw new Error("供應商或商品清單格式不正確");
        }
        availableProducts = products;
        byId("order-form").reset();
        byId("order-id").value = order?.id ?? "";
        byId("order-form-title").textContent = order ? `編輯採購單 #${order.id}` : "建立採購單";

        const supplierSelect = byId("order-supplier-id");
        supplierSelect.replaceChildren(createOption("", "請選擇供應商"));
        suppliers.forEach(supplier => supplierSelect.appendChild(createOption(supplier.id, supplier.name)));
        supplierSelect.value = order?.supplierId ?? "";
        byId("order-expected-delivery-date").value = order?.expectedDeliveryDate ?? "";
        byId("order-remark").value = order?.remark ?? "";

        const items = byId("order-items");
        items.replaceChildren();
        (order?.items?.length ? order.items : [null]).forEach(item => appendItemRow(item));
        byId("order-form-panel").hidden = false;
        byId("order-form-panel").scrollIntoView({ behavior: "smooth", block: "start" });
    } catch (error) {
        showMessage(error.message, "error");
    }
}

function appendItemRow(item = null) {
    const row = makeElement("div", null, "form-group order-item-row");
    row.style.display = "flex";
    row.style.gap = "8px";
    row.style.alignItems = "end";

    const select = makeElement("select");
    select.className = "order-item-product";
    select.required = true;
    select.appendChild(createOption("", "請選擇商品"));
    availableProducts.forEach(product => {
        select.appendChild(createOption(product.id, `${product.name}（${product.sku}）`));
    });
    select.value = item?.productId ?? "";

    const quantity = makeElement("input");
    quantity.className = "order-item-quantity";
    quantity.type = "number";
    quantity.min = "1";
    quantity.step = "1";
    quantity.required = true;
    quantity.value = item?.quantity ?? "1";

    const remove = actionButton("移除", "danger-button", () => row.remove());
    row.append(select, quantity, remove);
    byId("order-items").appendChild(row);
}

function closeOrderForm() {
    byId("order-form-panel").hidden = true;
    byId("order-form").reset();
    byId("order-items").replaceChildren();
}

async function submitOrderForm(event) {
    event.preventDefault();
    const id = byId("order-id").value;
    const supplierId = Number(byId("order-supplier-id").value);
    const rows = [...document.querySelectorAll("#order-items .order-item-row")];
    const items = rows.map(row => ({
        productId: Number(row.querySelector(".order-item-product").value),
        quantity: Number(row.querySelector(".order-item-quantity").value)
    }));

    const validItems = items.every(item =>
        Number.isSafeInteger(item.productId) && item.productId > 0 &&
        Number.isSafeInteger(item.quantity) && item.quantity > 0
    );
    if (!Number.isSafeInteger(supplierId) || supplierId <= 0 || !items.length || !validItems) {
        showMessage("請選擇供應商，並填寫至少一筆有效商品明細", "error");
        return;
    }
    if (new Set(items.map(item => item.productId)).size !== items.length) {
        showMessage("同一張採購單不可重複選擇商品", "error");
        return;
    }

    const payload = {
        supplierId,
        expectedDeliveryDate: byId("order-expected-delivery-date").value || null,
        remark: byId("order-remark").value.trim() || null,
        items
    };
    const submit = byId("order-form-submit");
    try {
        setButtonLoading(submit, true, "儲存中...");
        await requestJson(id ? `${API.purchaseOrders}/${id}` : API.purchaseOrders, id ? "PUT" : "POST", payload);
        closeOrderForm();
        await refreshOrders();
        showMessage(`採購單草稿已${id ? "更新" : "建立"}`, "success");
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        setButtonLoading(submit, false);
    }
}

async function changeOrderState(order, action) {
    const labels = { submit: "送出", receive: "確認到貨", cancel: "取消" };
    if (!window.confirm(`確定要${labels[action]}採購單 #${order.id}？`)) return;
    try {
        await requestJson(`${API.purchaseOrders}/${order.id}/${action}`, "POST");
        await refreshOrders();
        // 確認到貨後端會增加庫存，需同步刷新商品頁。
        if (action === "receive") await refreshProducts();
        showMessage(`採購單已${labels[action]}`, "success");
    } catch (error) {
        showMessage(error.message, "error");
    }
}

async function deleteOrder(order) {
    if (!window.confirm(`確定要刪除採購單 #${order.id}？`)) return;
    try {
        await requestJson(`${API.purchaseOrders}/${order.id}`, "DELETE");
        await refreshOrders();
        showMessage("採購單已刪除", "success");
    } catch (error) {
        showMessage(error.message, "error");
    }
}

function createStatusBadge(status) {
    const labels = { DRAFT: "草稿", SUBMITTED: "已送出", RECEIVED: "已到貨", CANCELLED: "已取消" };
    const classes = { DRAFT: "neutral", SUBMITTED: "warning", RECEIVED: "success", CANCELLED: "danger" };
    return makeElement("span", labels[status] || status || "--", `status-badge ${classes[status] || "neutral"}`);
}

function actionButton(text, className, handler) {
    const button = makeElement("button", text, className);
    button.type = "button";
    button.style.marginRight = "6px";
    button.addEventListener("click", handler);
    return button;
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
    byId("order-table-body").replaceChildren(row);
}
