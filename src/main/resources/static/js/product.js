"use strict";

/* 商品管理：對應 ProductController、ProductRequest 與 ProductResponse。 */
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

let productsLoaded = false;

// 商品表單開啟時的庫存快照；用於防止舊畫面覆寫新的庫存異動。
let editingOriginalStock = null;

export function initProducts() {
    // 專案目前已有商品表單；保留補建邏輯以避免舊版 HTML 無法操作。
    installProductForm();
    byId("add-product-button").disabled = false;
    byId("add-product-button").addEventListener("click", () => openProductForm());
    byId("product-form-close").addEventListener("click", closeProductForm);
    byId("product-form-cancel").addEventListener("click", closeProductForm);
    byId("product-form").addEventListener("submit", submitProductForm);
}

export function enterProductsPage() {
    if (!productsLoaded)
        void refreshProducts();
}

export async function refreshProducts() {
    renderMessageRow("載入商品中...");
    try {
        hideMessage();
        const products = await requestJson(API.products);
        if (!Array.isArray(products))
            throw new Error("商品清單回傳格式不正確");
        renderProducts(products);
        productsLoaded = true;
        byId("stat-products").textContent = String(products.length);
        return true;
    }
    catch (error) {
        productsLoaded = false;
        byId("stat-products").textContent = "--";
        renderMessageRow("無法載入商品資料");
        showMessage(error.message, "error");
        console.error("Product API 錯誤：", error);
        return false;
    }
}

function renderProducts(products) {
    const tbody = byId("product-table-body");
    tbody.replaceChildren();
    if (products.length === 0) {
        renderMessageRow("目前沒有啟用中的商品");
        return;
    }

    products.forEach(product => {
        const row = makeElement("tr");
        [
            product.sku,
            `${displayValue(product.name)}（${displayValue(product.unit, "個")}）`,
            product.categoryName,
            product.price == null ? "--" : `NT$ ${formatNumber(product.price)}`,
            product.currentStock,
            product.safetyStock
        ].forEach(value => row.appendChild(makeElement("td", displayValue(value))));

        const statusCell = makeElement("td");
        statusCell.appendChild(makeElement("span", product.status === 1 ? "啟用" : "停用", product.status === 1 ? "status-badge success" : "status-badge danger"));
        row.appendChild(statusCell);

        const actions = makeElement("td");
        actions.append(
            actionButton("編輯", "secondary-button", () => openProductForm(product)),
            actionButton(product.status === 1 ? "停用" : "啟用", "secondary-button", () => updateProductStatus(product)),
            actionButton("刪除", "danger-button", () => deleteProduct(product))
        );
        row.appendChild(actions);
        tbody.appendChild(row);
    });
}

function installProductForm() {
    if (byId("product-form"))
        return;
    const panel = makeElement("div", null, "panel");
    panel.id = "product-form-panel";
    panel.hidden = true;

    const toolbar = makeElement("div", null, "panel-toolbar");
    const title = makeElement("h4", "新增商品");
    title.id = "product-form-title";
    const close = actionButton("關閉", "secondary-button", () => { });
    close.id = "product-form-close";
    toolbar.append(title, close);
    panel.appendChild(toolbar);

    const form = makeElement("form");
    form.id = "product-form";
    const hiddenId = makeElement("input");
    hiddenId.type = "hidden";
    hiddenId.id = "product-id";
    form.appendChild(hiddenId);

    [
        ["product-category-id", "第二層商品分類 *", "select", true],
        ["product-sku", "商品代碼 *", "text", true],
        ["product-name", "商品名稱 *", "text", true],
        ["product-price", "售價 *", "number", true],
        ["product-unit", "單位", "text", false],
        ["product-current-stock", "目前庫存", "number", false],
        ["product-safety-stock", "安全庫存", "number", false]
    ].forEach(([id, labelText, type, required]) => {
        const group = makeElement("div", null, "form-group");
        const label = makeElement("label", labelText);
        const input = makeElement(type === "select" ? "select" : "input");
        label.htmlFor = id;
        input.id = id;
        input.required = required;
        if (type !== "select")
            input.type = type;
        if (type === "number") {
            input.min = "0";
            input.step = id === "product-price" ? "0.01" : "1";
        }
        group.append(label, input);
        form.appendChild(group);
    });

    const buttons = makeElement("div", null, "action-bar");
    const cancel = actionButton("取消", "secondary-button", () => { });
    cancel.id = "product-form-cancel";
    const submit = makeElement("button", "儲存商品", "primary-button");
    submit.id = "product-form-submit";
    submit.type = "submit";
    buttons.append(cancel, submit);
    form.appendChild(buttons);
    panel.appendChild(form);
    byId("page-products").appendChild(panel);
}

async function openProductForm(product = null) {
    try {
        const categories = await requestJson(API.categories);
        if (!Array.isArray(categories))
            throw new Error("商品分類回傳格式不正確");
        byId("product-form").reset();
        byId("product-id").value = product?.id ?? "";
        editingOriginalStock = product ? product.currentStock : null;
        byId("product-form-title").textContent = product ? "編輯商品" : "新增商品";

        const select = byId("product-category-id");
        select.replaceChildren(createOption("", "請選擇分類"));
        categories
            .filter(category => category.parentId != null && category.status === 1)
            .forEach(category => {
            const label = category.parentName
                ? `${category.parentName} / ${category.name}`
                : category.name;
            select.appendChild(createOption(category.id, label));
        });
        select.value = product?.categoryId ?? "";

        [
            ["product-sku", "sku", ""],
            ["product-name", "name", ""],
            ["product-price", "price", ""],
            ["product-unit", "unit", ""],
            ["product-current-stock", "currentStock", "0"],
            ["product-safety-stock", "safetyStock", "0"]
        ].forEach(([id, key, fallback]) => {
            byId(id).value = product?.[key] ?? fallback;
        });

        const panel = byId("product-form-panel");
        panel.hidden = false;
        panel.scrollIntoView({ behavior: "smooth", block: "start" });
        byId("product-sku").focus();
    }
    catch (error) {
        showMessage(error.message, "error");
    }
}

function closeProductForm() {
    byId("product-form-panel").hidden = true;
    byId("product-form").reset();
    byId("product-id").value = "";
    editingOriginalStock = null;
}

async function submitProductForm(event) {
    event.preventDefault();
    const id = byId("product-id").value;
    const categoryId = Number(byId("product-category-id").value);
    const sku = byId("product-sku").value.trim();
    const name = byId("product-name").value.trim();
    const priceText = byId("product-price").value.trim();
    const unit = byId("product-unit").value.trim();

    try {
        if (!Number.isSafeInteger(categoryId) || categoryId <= 0)
            throw new Error("請選擇第二層分類");
        if (!sku || sku.length > 50 || !name || name.length > 100)
            throw new Error("商品名稱或 SKU 不符合規則");
        if (!/^\d+(\.\d{1,2})?$/.test(priceText))
            throw new Error("售價必須是非負金額，最多兩位小數");
        if (unit.length > 20)
            throw new Error("單位最多 20 個字元");

        const payload = {
            categoryId,
            sku,
            name,
            price: Number(priceText),
            unit: unit || null,
            currentStock: parseStock("product-current-stock"),
            safetyStock: parseStock("product-safety-stock")
        };
        // 修改商品時帶上開啟表單當下的庫存，讓伺服器比對是否過期。
        if (id) {
            payload.expectedCurrentStock = editingOriginalStock;
        }

        const submit = byId("product-form-submit");
        try {
            setButtonLoading(submit, true, "儲存中...");
            await requestJson(id ? `${API.products}/${id}` : API.products, id ? "PUT" : "POST", payload);
            closeProductForm();
            await refreshProducts();
            showMessage(`商品已${id ? "更新" : "新增"}`, "success");
        }
        finally {
            setButtonLoading(submit, false);
        }
    }
    catch (error) {
        showMessage(error.message, "error");
    }
}

function parseStock(inputId) {
    const value = byId(inputId).value.trim();
    if (value === "")
        return null;
    if (!/^\d+$/.test(value) || !Number.isSafeInteger(Number(value))) {
        throw new Error("庫存必須是非負整數");
    }
    return Number(value);
}

async function updateProductStatus(product) {
    const status = product.status === 1 ? 0 : 1;
    const action = status === 1 ? "啟用" : "停用";
    if (!window.confirm(`確定要${action}商品「${product.name}」？`))
        return;
    try {
        await requestJson(`${API.products}/${product.id}/status?status=${status}`, "PATCH");
        await refreshProducts();
        showMessage(`商品已${action}`, "success");
    }
    catch (error) {
        showMessage(error.message, "error");
    }
}

async function deleteProduct(product) {
    if (!window.confirm(`確定要刪除商品「${product.name}」？`))
        return;
    try {
        await requestJson(`${API.products}/${product.id}`, "DELETE");
        await refreshProducts();
        showMessage("商品已刪除", "success");
    }
    catch (error) {
        showMessage(error.message, "error");
    }
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

function renderMessageRow(message) {
    const row = makeElement("tr");
    const cell = makeElement("td", message, "empty-state");
    cell.colSpan = 8;
    row.appendChild(cell);
    byId("product-table-body").replaceChildren(row);
}
