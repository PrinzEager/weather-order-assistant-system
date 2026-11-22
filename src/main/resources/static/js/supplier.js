"use strict";

/* 供應商管理：對應 SupplierController 與 SupplierRequest。 */
import {
    API,
    byId,
    makeElement,
    showMessage,
    hideMessage,
    setButtonLoading,
    displayValue,
    requestJson
} from "./common.js";

import {
    installSupplierProductsEditor,
    openSupplierProductsEditor,
    closeSupplierProductsEditor,
    validateSupplierProductsEditor,
    saveSupplierProducts
} from "./supplier-products.js";

let suppliersLoaded = false;

let supplierFormVersion = 0;

let supplierFormSaving = false;

export function initSuppliers() {
    installSupplierForm();
    byId("add-supplier-button").disabled = false;
    byId("add-supplier-button").addEventListener("click", () => openSupplierForm());
    byId("supplier-form-close").addEventListener("click", () => closeSupplierForm());
    byId("supplier-form-cancel").addEventListener("click", () => closeSupplierForm());
    byId("supplier-form").addEventListener("submit", submitSupplierForm);
}

export function enterSuppliersPage() {
    if (!suppliersLoaded)
        void refreshSuppliers();
}

export async function refreshSuppliers() {
    renderMessageRow("載入供應商中...");
    try {
        hideMessage();
        const suppliers = await requestJson(API.suppliers);
        if (!Array.isArray(suppliers))
            throw new Error("供應商清單回傳格式不正確");
        renderSuppliers(suppliers);
        suppliersLoaded = true;
        return true;
    }
    catch (error) {
        suppliersLoaded = false;
        renderMessageRow("無法載入供應商資料");
        showMessage(error.message, "error");
        console.error("Supplier API 錯誤：", error);
        return false;
    }
}

function renderSuppliers(suppliers) {
    const tbody = byId("supplier-table-body");
    tbody.replaceChildren();
    if (suppliers.length === 0) {
        renderMessageRow("目前沒有啟用中的供應商");
        return;
    }

    suppliers.forEach(supplier => {
        const row = makeElement("tr");
        [supplier.name, supplier.contactPerson, supplier.phone, supplier.email]
            .forEach(value => row.appendChild(makeElement("td", displayValue(value))));

        const statusCell = makeElement("td");
        statusCell.appendChild(makeElement("span", supplier.status === 1 ? "啟用" : "停用", supplier.status === 1 ? "status-badge success" : "status-badge danger"));
        row.appendChild(statusCell);

        const actions = makeElement("td");
        actions.append(
            actionButton("編輯／供貨商品", "secondary-button", () => openSupplierForm(supplier)),
            actionButton(supplier.status === 1 ? "停用" : "啟用", "secondary-button", () => updateStatus(supplier)),
            actionButton("刪除", "danger-button", () => deleteSupplier(supplier))
        );
        row.appendChild(actions);
        tbody.appendChild(row);
    });
}

// 原始 HTML 只有列表，因此由模組補上與商品分類相同用途的編輯面板。
function installSupplierForm() {
    if (byId("supplier-form"))
        return;
    const panel = makeElement("div", null, "panel");
    panel.id = "supplier-form-panel";
    panel.hidden = true;

    const toolbar = makeElement("div", null, "panel-toolbar");
    const title = makeElement("h4", "新增供應商");
    title.id = "supplier-form-title";
    const close = makeElement("button", "關閉", "secondary-button");
    close.type = "button";
    close.id = "supplier-form-close";
    toolbar.append(title, close);
    panel.appendChild(toolbar);

    const form = makeElement("form");
    form.id = "supplier-form";
    const idInput = makeElement("input");
    idInput.type = "hidden";
    idInput.id = "supplier-id";
    form.appendChild(idInput);

    [
        ["supplier-name", "供應商名稱 *", "text", true, 100],
        ["supplier-contact-person", "聯絡人", "text", false, 50],
        ["supplier-phone", "電話", "tel", false, 30],
        ["supplier-email", "電子郵件", "email", false, 100],
        ["supplier-address", "地址", "text", false, 255]
    ].forEach(([id, text, type, required, maxLength]) => {
        const group = makeElement("div", null, "form-group");
        const label = makeElement("label", text);
        const input = makeElement("input");
        label.htmlFor = id;
        input.id = id;
        input.type = type;
        input.required = required;
        input.maxLength = maxLength;
        group.append(label, input);
        form.appendChild(group);
    });

    const buttons = makeElement("div", null, "action-bar");
    const cancel = makeElement("button", "取消", "secondary-button");
    cancel.type = "button";
    cancel.id = "supplier-form-cancel";
    const submit = makeElement("button", "儲存供應商", "primary-button");
    submit.id = "supplier-form-submit";
    submit.type = "submit";
    buttons.append(cancel, submit);
    // 供貨商品區塊由獨立模組建立，避免 Supplier 基本資料程式變得過長。
    installSupplierProductsEditor(form);
    form.appendChild(buttons);
    panel.appendChild(form);
    byId("page-suppliers").appendChild(panel);
}

async function openSupplierForm(supplier = null) {
    if (supplierFormSaving)
        return;
    const version = ++supplierFormVersion;
    byId("supplier-form").reset();
    byId("supplier-id").value = supplier?.id ?? "";
    byId("supplier-name").value = supplier?.name ?? "";
    byId("supplier-contact-person").value = supplier?.contactPerson ?? "";
    byId("supplier-phone").value = supplier?.phone ?? "";
    byId("supplier-email").value = supplier?.email ?? "";
    byId("supplier-address").value = supplier?.address ?? "";
    byId("supplier-form-title").textContent = supplier ? "編輯供應商與供貨商品" : "新增供應商與供貨商品";
    byId("supplier-form-panel").hidden = false;
    byId("supplier-form-panel").scrollIntoView({ behavior: "smooth", block: "start" });

    const submit = byId("supplier-form-submit");
    submit.disabled = true;
    try {
        // 開啟表單時同步取得可供貨商品及既有報價。
        await openSupplierProductsEditor(supplier?.id ?? null);
    }
    catch (error) {
        showMessage(error.message, "error");
        return;
    }
    finally {
        // 舊視窗的非同步請求不得將新視窗的按鈕提前啟用。
        if (version === supplierFormVersion)
            submit.disabled = false;
    }
    if (version === supplierFormVersion)
        byId("supplier-name").focus();
}

function closeSupplierForm(force = false) {
    if (supplierFormSaving && !force)
        return;
    supplierFormVersion++;
    closeSupplierProductsEditor();
    byId("supplier-form-panel").hidden = true;
    byId("supplier-form").reset();
    byId("supplier-id").value = "";
}

async function submitSupplierForm(event) {
    event.preventDefault();
    if (supplierFormSaving)
        return;
    const originalId = byId("supplier-id").value;
    const valueOrNull = id => byId(id).value.trim() || null;
    const payload = {
        name: byId("supplier-name").value.trim(),
        contactPerson: valueOrNull("supplier-contact-person"),
        phone: valueOrNull("supplier-phone"),
        email: valueOrNull("supplier-email"),
        address: valueOrNull("supplier-address")
    };
    if (!payload.name) {
        showMessage("請輸入供應商名稱", "error");
        return;
    }

    // 先驗證供貨設定，避免供應商基本資料已儲存才發現價格有誤。
    try {
        validateSupplierProductsEditor();
    }
    catch (error) {
        showMessage(error.message, "error");
        return;
    }

    const submit = byId("supplier-form-submit");
    let basicSaved = false;
    let savedId = originalId ? Number(originalId) : null;
    try {
        supplierFormSaving = true;
        hideMessage();
        setButtonLoading(submit, true, "儲存中...");

        // 現有 Supplier API 先建立／修改基本資料，維持原本 CRUD 行為。
        const saved = await requestJson(originalId ? `${API.suppliers}/${originalId}` : API.suppliers, originalId ? "PUT" : "POST", payload);
        savedId = Number(saved?.id ?? savedId);
        if (!Number.isSafeInteger(savedId) || savedId <= 0) {
            throw new Error("無法確認供應商資料，請重新查詢供應商清單後再試");
        }
        basicSaved = true;

        // 新增供應商後才有 supplierId，可用來建立每筆供貨關聯。
        // 供貨 API 各筆獨立；若部分失敗，保留 supplierId 讓使用者修正重試。
        byId("supplier-id").value = String(savedId);
        await saveSupplierProducts(savedId);

        closeSupplierForm(true);
        await refreshSuppliers();
        showMessage(`供應商及供貨商品已${originalId ? "更新" : "新增"}`, "success");
    }
    catch (error) {
        if (basicSaved) {
            byId("supplier-form-title").textContent = "編輯供應商與供貨商品";
            await refreshSuppliers();
            showMessage(`供應商基本資料已儲存（ID：${savedId}），但供貨商品尚未全部完成：${error.message}。請修正後再次按儲存，系統會比對已成功的資料。`, "error");
        }
        else {
            showMessage(error.message, "error");
        }
    }
    finally {
        supplierFormSaving = false;
        setButtonLoading(submit, false);
    }
}

async function updateStatus(supplier) {
    const status = supplier.status === 1 ? 0 : 1;
    const action = status === 1 ? "啟用" : "停用";
    if (!window.confirm(`確定要${action}供應商「${supplier.name}」？`))
        return;
    try {
        await requestJson(`${API.suppliers}/${supplier.id}/status?status=${status}`, "PATCH");
        await refreshSuppliers();
        showMessage(`供應商已${action}`, "success");
    }
    catch (error) {
        showMessage(error.message, "error");
    }
}

async function deleteSupplier(supplier) {
    if (!window.confirm(`確定要刪除供應商「${supplier.name}」？`))
        return;
    try {
        await requestJson(`${API.suppliers}/${supplier.id}`, "DELETE");
        await refreshSuppliers();
        showMessage("供應商已刪除", "success");
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

function renderMessageRow(message) {
    const row = makeElement("tr");
    const cell = makeElement("td", message, "empty-state");
    cell.colSpan = 6;
    row.appendChild(cell);
    byId("supplier-table-body").replaceChildren(row);
}
