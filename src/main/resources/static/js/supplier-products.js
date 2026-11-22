"use strict";

/**
 * 供應商供貨商品表單模組。
 *
 * 與 Supplier 的基本資料表單分開：
 * 1. 載入商品與指定供應商的既有供貨關聯。
 * 2. 讓使用者新增多項供貨商品、設定價格及啟停供貨。
 * 3. 依照 V009 SupplierProduct API 逐筆儲存差異。
 *
 * 提醒：這是前端兩階段儲存，並非跨 API 的資料庫交易。
 * 若其中一筆儲存失敗，重新按下儲存時會先重新讀取後端狀態，避免重複 POST。
 */
import {
    API, byId, makeElement, requestJson
} from "./common.js";

const OFFER_API = `${API.suppliers}`;
const PRICE_PATTERN = /^\d{1,8}(\.\d{1,2})?$/;

let products = [];
let entries = [];
let ready = false;
let loadVersion = 0;

// 建立原本 Supplier 表單內的「供貨商品」區域，不改變既有供應商欄位。
export function installSupplierProductsEditor(form) {
    if (byId("supplier-products-editor")) return;

    const section = makeElement("section", null, "panel");
    section.id = "supplier-products-editor";

    const heading = makeElement("h4", "供貨商品與採購單價");
    const help = makeElement(
        "p",
        "可在新增供應商時一起選擇商品及採購價；既有供貨商品可調價、停用或重新啟用。採購價不同於商品售價。"
    );

    const pickerGroup = makeElement("div", null, "form-group");
    const pickerLabel = makeElement("label", "選擇新增供貨商品");
    pickerLabel.htmlFor = "supplier-product-picker";
    const picker = makeElement("select");
    picker.id = "supplier-product-picker";
    const addButton = makeElement("button", "加入供貨商品", "secondary-button");
    addButton.id = "supplier-product-add";
    addButton.type = "button";
    addButton.addEventListener("click", addSelectedProduct);
    pickerGroup.append(pickerLabel, picker, addButton);

    const tableWrap = makeElement("div", null, "table-responsive");
    const table = makeElement("table", null, "data-table");
    const head = makeElement("thead");
    const headRow = makeElement("tr");
    ["供貨商品", "採購單價（元）", "供貨狀態", "操作"].forEach(label => {
        headRow.appendChild(makeElement("th", label));
    });
    head.appendChild(headRow);

    const body = makeElement("tbody");
    body.id = "supplier-products-body";
    table.append(head, body);
    tableWrap.appendChild(table);

    const state = makeElement("p", "正在載入商品清單…", "empty-state");
    state.id = "supplier-products-state";
    section.append(heading, help, pickerGroup, tableWrap, state);

    // 供貨商品區域放在供應商基本資料與表單操作按鈕之間。
    form.appendChild(section);
    renderRows();
}

// 每次開啟表單皆載入最新商品與供貨關聯。
export async function openSupplierProductsEditor(supplierId = null) {
    const version = ++loadVersion;
    entries = [];
    products = [];
    ready = false;
    const state = byId("supplier-products-state");
    state.textContent = "正在載入商品與供貨資料…";
    byId("supplier-product-picker").disabled = true;
    byId("supplier-product-add").disabled = true;
    renderRows();

    try {
        const [productList, offerList] = await Promise.all([
            requestJson(API.products),
            supplierId ? requestJson(`${OFFER_API}/${supplierId}/products`) : Promise.resolve([])
        ]);
        if (version !== loadVersion) return;
        if (!Array.isArray(productList) || !Array.isArray(offerList)) {
            throw new Error("商品或供貨關聯 API 回傳格式不正確");
        }
        products = productList.filter(p => p.status === 1);
        entries = offerList.map(o => ({
            productId: Number(o.productId),
            productName: o.productName,
            sku: o.sku,
            priceText: String(o.purchasePrice ?? ""),
            active: o.status === 1,
            existed: true
        }));
        ready = true;
        state.textContent = "目前沒有供貨商品；可從上方選擇新增。";
        updatePicker();
        renderRows();
    } catch (error) {
        if (version !== loadVersion) return;
        state.textContent = `載入供貨商品失敗：${error.message}`;
        throw error;
    }
}

export function closeSupplierProductsEditor() {
    // 取消尚未完成的載入，避免切換供應商時舊的 API 結果覆蓋新表單。
    loadVersion++;
    entries = [];
    products = [];
    ready = false;
}

function updatePicker() {
    const picker = byId("supplier-product-picker");
    picker.replaceChildren();
    const empty = makeElement("option", "請選擇要新增的商品");
    empty.value = "";
    picker.appendChild(empty);

    // 已加入的商品不再出現在下拉選單，防止同一供應商重複設定同一商品。
    const used = new Set(entries.map(e => e.productId));
    products.filter(p => !used.has(Number(p.id))).forEach(p => {
        const option = makeElement("option", `${p.name}（${p.sku}）`);
        option.value = String(p.id);
        picker.appendChild(option);
    });
    picker.disabled = !ready || picker.options.length < 2;
    byId("supplier-product-add").disabled = picker.disabled;
}

function addSelectedProduct() {
    if (!ready) return;
    const picker = byId("supplier-product-picker");
    const productId = Number(picker.value);
    const product = products.find(p => Number(p.id) === productId);
    if (!product || entries.some(e => e.productId === productId)) return;

    entries.push({
        productId,
        productName: product.name,
        sku: product.sku,
        priceText: "", // 不拿商品售價當採購成本；必須由使用者填寫。
        active: true,
        existed: false
    });
    updatePicker();
    renderRows();
    const addedPriceInput = byId(`supplier-offer-price-${productId}`);
    if (addedPriceInput) addedPriceInput.focus();
}

function renderRows() {
    const body = byId("supplier-products-body");
    if (!body) return;
    body.replaceChildren();
    byId("supplier-products-state").hidden = entries.length > 0;

    for (const entry of entries) {
        const row = makeElement("tr");
        const productCell = makeElement("td");
        productCell.appendChild(makeElement("strong", entry.productName || "商品"));
        productCell.appendChild(makeElement("div", entry.sku ?? "", "empty-state"));

        const priceCell = makeElement("td");
        const input = makeElement("input");
        // 商品若已停用，仍保留原本報價供歷史管理，但後端不允許再調價。
        const productAvailable = products.some(p => Number(p.id) === entry.productId);
        input.disabled = entry.existed && !productAvailable;
        input.title = input.disabled ? "商品已停用，無法修改採購價格" : "";
        input.id = `supplier-offer-price-${entry.productId}`;
        input.type = "number";
        input.min = "0";
        input.step = "0.01";
        input.placeholder = "請輸入採購單價";
        input.required = true;
        input.value = entry.priceText;
        input.style.width = "150px";
        input.addEventListener("input", () => { entry.priceText = input.value.trim(); });
        priceCell.appendChild(input);

        const statusCell = makeElement("td");
        const statusLabel = makeElement("label");
        const checkbox = makeElement("input");
        checkbox.type = "checkbox";
        checkbox.checked = entry.active;
        // 產品停用後，允許停用舊供貨關聯，但不得重新啟用。
        checkbox.disabled = entry.existed && !productAvailable && !entry.active;
        checkbox.addEventListener("change", () => {
            entry.active = checkbox.checked;
            statusText.textContent = entry.active ? " 啟用" : " 停止供貨";
        });
        const statusText = makeElement("span", entry.active ? " 啟用" : " 停止供貨");
        statusLabel.append(checkbox, statusText);
        statusCell.appendChild(statusLabel);

        const actionCell = makeElement("td");
        if (!entry.existed) {
            const remove = makeElement("button", "移除", "secondary-button");
            remove.type = "button";
            remove.addEventListener("click", () => {
                entries = entries.filter(e => e !== entry);
                updatePicker();
                renderRows();
            });
            actionCell.appendChild(remove);
        } else {
            actionCell.appendChild(makeElement("small", "既有資料：可修改價格或停用"));
        }
        row.append(productCell, priceCell, statusCell, actionCell);
        body.appendChild(row);
    }
}

// 建立供應商之前先檢查所有輸入，避免基本資料已建立後才發現採購價格不合法。
export function validateSupplierProductsEditor() {
    if (!ready) throw new Error("商品資料尚未載入完成，請重新開啟供應商表單");
    const seen = new Set();
    for (const entry of entries) {
        if (!Number.isSafeInteger(entry.productId) || entry.productId <= 0 || seen.has(entry.productId)) {
            throw new Error("供貨商品重複或商品 ID 不合法");
        }
        seen.add(entry.productId);
        if (!PRICE_PATTERN.test(entry.priceText)) {
            throw new Error(`「${entry.productName}」採購單價最多 8 位整數及 2 位小數，且不可為負數`);
        }
        if (!entry.existed && !entry.active) {
            throw new Error(`新商品「${entry.productName}」請先啟用供貨，或按移除`);
        }
    }
}

function normalizedPrice(value) {
    return Number(value).toFixed(2);
}

/**
 * 儲存商品差異。
 * 每次先 GET 後端當前資料，處理「基本資料儲存成功、供貨資料部分失敗」後重試的情況。
 */
export async function saveSupplierProducts(supplierId) {
    validateSupplierProductsEditor();
    const url = `${OFFER_API}/${supplierId}/products`;
    // 建立操作快照：避免使用者在等待 HTTP 回應期間更動表單造成儲存範圍飄移。
    const desiredEntries = entries.map(entry => ({ ...entry }));
    const existing = await requestJson(url);
    if (!Array.isArray(existing)) throw new Error("供貨關聯清單回傳格式不正確");
    const byProductId = new Map(existing.map(o => [Number(o.productId), o]));

    // 僅透過現在表單中的商品更動供貨設定；未出現在表單的其他商品不會被刪除。
    for (const entry of desiredEntries) {
        const old = byProductId.get(entry.productId);
        try {
            if (!old) {
                if (!entry.active) continue;
                await requestJson(url, "POST", {
                    productId: entry.productId,
                    purchasePrice: Number(entry.priceText)
                });
            } else {
                const productUrl = `${url}/${entry.productId}`;
                if (normalizedPrice(old.purchasePrice) !== normalizedPrice(entry.priceText)) {
                    await requestJson(productUrl, "PUT", {
                        purchasePrice: Number(entry.priceText)
                    });
                }
                if ((old.status === 1) !== entry.active) {
                    await requestJson(`${productUrl}/status`, "PUT", {
                        status: entry.active ? 1 : 0
                    });
                }
            }
        } catch (error) {
            throw new Error(`商品「${entry.productName}」儲存失敗：${error.message}`);
        }
    }
}
