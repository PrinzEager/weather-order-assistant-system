"use strict";

/**
 * 多供應商採購草稿確認視窗。
 *
 * 1. 每個商品查詢供貨商與其採購單價（不使用商品售價）。
 * 2. 保留跨頁選擇的供應商和數量；每頁最多十筆。
 * 3. 到貨日以後端台灣時間政策為準；POST 前再檢查一次。
 * 4. 只傳 productId、supplierId、quantity，價格由後端重新驗證及儲存。
 * 5. 一次 POST，由後端交易式地依供應商拆成多張 DRAFT。
 */
import { byId, makeElement, clearElement, showMessage, hideMessage, requestJson, setButtonLoading } from "./common.js";

const OFFER_API = "/api/supplier-products/by-product";

const POLICY_API = "/api/purchase-order-batches/delivery-policy";

const DRAFT_API = "/api/purchase-order-batches/drafts";

const PAGE_SIZE = 10;

const MAX_QUANTITY = 2147483647;

let draftItems = [];

let draftPage = 1;

let isSubmitting = false;

let modalOpen = false;

let isLoading = false;

let onCreated = null;

let openRequestVersion = 0;

let earliestDeliveryDate = "";

let submitError = "";

export function initDraftConfirmation(successCallback) {
    onCreated = successCallback;
    byId("recommendation-order-close").addEventListener("click", closeDraftConfirmation);
    byId("recommendation-order-cancel").addEventListener("click", closeDraftConfirmation);
    byId("recommendation-order-form").addEventListener("submit", submitDrafts);
    byId("recommendation-order-items-body").addEventListener("change", onItemChange);
    byId("recommendation-order-items-body").addEventListener("input", onItemInput);
    byId("recommendation-order-page-prev").addEventListener("click", () => {
        if (draftPage > 1) {
            draftPage -= 1;
            renderDraftPage();
        }
    });
    byId("recommendation-order-page-next").addEventListener("click", () => {
        if (draftPage * PAGE_SIZE < draftItems.length) {
            draftPage += 1;
            renderDraftPage();
        }
    });
    byId("recommendation-delivery-date").addEventListener("change", () => {
        submitError = "";
        refreshDraftSummary();
    });
    // 視窗遮罩區域與 Escape 鍵允許退出，提交中則禁止關閉。
    byId("recommendation-order-panel").addEventListener("click", event => {
        if (event.target === byId("recommendation-order-panel"))
            closeDraftConfirmation();
    });
    document.addEventListener("keydown", event => {
        if (event.key === "Escape" && modalOpen)
            closeDraftConfirmation();
    });
}

/**
 * 主清單傳入所有頁面已選商品；各商品必須有 productId/name/sku/quantity。
 */
export async function openDraftConfirmation(items) {
    if (modalOpen || isSubmitting || !items.length)
        return;
    openRequestVersion += 1;
    const thisVersion = openRequestVersion;
    modalOpen = true;
    isLoading = true;
    draftPage = 1;
    draftItems = [];
    earliestDeliveryDate = "";
    submitError = "";

    const modal = byId("recommendation-order-panel");
    modal.hidden = false;
    document.body.classList.add("rec-modal-open");
    byId("recommendation-order-form").reset();
    setDraftMessage("正在載入有效供應商與採購單價...");
    byId("recommendation-order-warning").textContent = "";
    byId("recommendation-order-submit").disabled = true;
    byId("recommendation-order-total").textContent = "0.00";
    byId("recommendation-order-page-info").textContent = "";
    modal.querySelector(".recommendation-modal-card")?.focus();

    try {
        // 每項商品皆獨立查詢有效供貨商；不讓使用者選到未供貨的廠商。
        const [policy, ...offerLists] = await Promise.all([
            requestJson(POLICY_API),
            ...items.map(item => requestJson(`${OFFER_API}/${item.productId}`))
        ]);
        if (!modalOpen || thisVersion !== openRequestVersion)
            return;
        if (!policy?.earliestDeliveryDate)
            throw new Error("目前無法確認預計到貨日期，請稍後再試");
        earliestDeliveryDate = policy.earliestDeliveryDate;
        const delivery = byId("recommendation-delivery-date");
        delivery.min = earliestDeliveryDate;
        delivery.value = earliestDeliveryDate;
        byId("recommendation-delivery-policy").textContent =
            `台灣時間 12:00 前最快隔天到貨，12:00（含）後最快後天；最早可選 ${earliestDeliveryDate}`;

        draftItems = items.map((item, index) => {
            const offers = offerLists[index];
            if (!Array.isArray(offers)) {
                throw new Error(`「${item.productName}」的供應商回傳格式不正確`);
            }
            return {
                productId: Number(item.productId),
                productName: item.productName,
                sku: item.sku,
                quantityRaw: String(item.quantity),
                supplierId: "", // 不預選供應商，使用者必須親自決定。
                offers: offers.filter(offer => Number(offer.status) === 1
                    && Number(offer.supplierId) > 0
                    && Number.isFinite(Number(offer.purchasePrice))
                    && Number(offer.purchasePrice) >= 0)
            };
        });
        isLoading = false;
        renderDraftPage();
    }
    catch (error) {
        if (!modalOpen || thisVersion !== openRequestVersion)
            return;
        showMessage(`無法開啟採購草稿確認視窗：${error.message}`, "error");
        closeDraftConfirmation();
    }
}

export function closeDraftConfirmation() {
    if (isSubmitting)
        return;
    openRequestVersion += 1; // 若尚有查價回應，忽略過期結果。
    modalOpen = false;
    isLoading = false;
    draftItems = [];
    draftPage = 1;
    submitError = "";
    const modal = byId("recommendation-order-panel");
    if (modal)
        modal.hidden = true;
    document.body.classList.remove("rec-modal-open");
}

function setDraftMessage(message) {
    const td = makeElement("td", message, "empty-state");
    td.colSpan = 5;
    const tr = makeElement("tr");
    tr.appendChild(td);
    byId("recommendation-order-items-body").replaceChildren(tr);
}

function money(value) {
    const n = Number(value);
    return Number.isFinite(n)
        ? n.toLocaleString("zh-TW", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : "--";
}

function parsePositiveQuantity(raw) {
    if (!/^[0-9]+$/.test(String(raw ?? "").trim()))
        return null;
    const n = Number(raw);
    return Number.isSafeInteger(n) && n > 0 && n <= MAX_QUANTITY ? n : null;
}

function chosenOffer(item) {
    return item.offers.find(offer => String(offer.supplierId) === String(item.supplierId));
}

function renderDraftPage() {
    const body = byId("recommendation-order-items-body");
    clearElement(body);
    const pages = Math.max(1, Math.ceil(draftItems.length / PAGE_SIZE));
    draftPage = Math.min(Math.max(1, draftPage), pages);
    for (const item of draftItems.slice((draftPage - 1) * PAGE_SIZE, draftPage * PAGE_SIZE)) {
        body.appendChild(buildItemRow(item));
    }
    byId("recommendation-order-page-info").textContent = `第 ${draftPage}／${pages} 頁，共 ${draftItems.length} 項`;
    byId("recommendation-order-page-prev").disabled = draftPage <= 1;
    byId("recommendation-order-page-next").disabled = draftPage >= pages;
    refreshDraftSummary();
}

function buildItemRow(item) {
    const tr = makeElement("tr");
    tr.dataset.productId = String(item.productId);
    const name = makeElement("td");
    name.append(makeElement("strong", item.productName), makeElement("div", item.sku ?? "--", "weather-description"));
    tr.appendChild(name);

    const qty = makeElement("td");
    const input = makeElement("input");
    input.type = "number";
    input.min = "1";
    input.max = String(MAX_QUANTITY);
    input.step = "1";
    input.required = true;
    input.className = "recommendation-preview-quantity";
    input.value = item.quantityRaw;
    input.dataset.productId = String(item.productId);
    input.setAttribute("aria-label", `${item.productName} 採購數量`);
    qty.appendChild(input);
    tr.appendChild(qty);

    const supplierCell = makeElement("td");
    const supplierSelect = makeElement("select");
    supplierSelect.className = "recommendation-preview-supplier";
    supplierSelect.dataset.productId = String(item.productId);
    supplierSelect.required = true;
    supplierSelect.setAttribute("aria-label", `${item.productName} 供應商`);
    const placeholder = makeElement("option", item.offers.length ? "請選擇供應商" : "無有效供應商");
    placeholder.value = "";
    supplierSelect.appendChild(placeholder);
    for (const offer of item.offers) {
        const option = makeElement("option", offer.supplierName);
        option.value = String(offer.supplierId);
        supplierSelect.appendChild(option);
    }
    supplierSelect.value = item.supplierId;
    supplierSelect.disabled = item.offers.length === 0;
    supplierCell.appendChild(supplierSelect);
    tr.appendChild(supplierCell);

    const offer = chosenOffer(item);
    tr.appendChild(makeElement("td", offer ? `NT$ ${money(offer.purchasePrice)}` : "--", "rec-price"));
    const quantity = parsePositiveQuantity(item.quantityRaw);
    tr.appendChild(makeElement("td", offer && quantity
        ? `NT$ ${money(Number(offer.purchasePrice) * quantity)}` : "--", "rec-subtotal"));
    return tr;
}

function findItem(input) {
    const id = Number(input.dataset?.productId);
    return draftItems.find(item => item.productId === id);
}

function onItemChange(event) {
    const input = event.target;
    const item = findItem(input);
    if (!item)
        return;
    if (input.matches(".recommendation-preview-supplier")) {
        submitError = "";
        item.supplierId = input.value;
        renderDraftPage(); // 價格與小計立即連動；保留其他頁面的選擇。
    }
    else if (input.matches(".recommendation-preview-quantity")) {
        submitError = "";
        item.quantityRaw = input.value;
        renderDraftPage();
    }
}

function onItemInput(event) {
    const input = event.target;
    if (!input.matches(".recommendation-preview-quantity"))
        return;
    const item = findItem(input);
    if (!item)
        return;
    item.quantityRaw = input.value;
    submitError = "";
    const tr = input.closest("tr");
    const offer = chosenOffer(item);
    const quantity = parsePositiveQuantity(item.quantityRaw);
    tr.querySelector(".rec-subtotal").textContent = offer && quantity
        ? `NT$ ${money(Number(offer.purchasePrice) * quantity)}` : "--";
    refreshDraftSummary();
}

function refreshDraftSummary() {
    if (!modalOpen || isLoading)
        return;
    let total = 0;
    let missingSupplier = 0;
    let invalidQuantity = 0;
    let missingOffers = 0;
    const supplierIds = new Set();
    for (const item of draftItems) {
        const offer = chosenOffer(item);
        const quantity = parsePositiveQuantity(item.quantityRaw);
        if (item.offers.length === 0)
            missingOffers += 1;
        if (!offer)
            missingSupplier += 1;
        else
            supplierIds.add(String(offer.supplierId));
        if (quantity === null)
            invalidQuantity += 1;
        if (offer && quantity)
            total += Number(offer.purchasePrice) * quantity;
    }
    byId("recommendation-order-total").textContent = money(total);
    byId("recommendation-order-count").textContent =
        `預估將建立 ${supplierIds.size} 張供應商草稿（${draftItems.length} 項商品）`;
    const date = byId("recommendation-delivery-date").value;
    const validDate = !!date && date >= earliestDeliveryDate;
    let warning = "";
    if (missingOffers)
        warning = `${missingOffers} 項商品沒有有效供應商，請先設定供貨關聯或移除商品後重試。`;
    else if (missingSupplier)
        warning = `尚有 ${missingSupplier} 項商品未選供應商，請逐頁完成選擇。`;
    else if (invalidQuantity)
        warning = `${invalidQuantity} 項商品的採購數量不合法，必須大於 0。`;
    else if (!validDate)
        warning = `預計到貨日不可早於 ${earliestDeliveryDate}。`;
    else if (!Number.isSafeInteger(Math.round(total * 100)))
        warning = "採購總額超過前端可安全計算範圍。";
    byId("recommendation-order-warning").textContent = warning || submitError;
    byId("recommendation-order-submit").disabled =
        !!warning || isSubmitting || !draftItems.length;
}

async function submitDrafts(event) {
    event.preventDefault();
    if (isSubmitting || isLoading || !modalOpen)
        return;
    const button = byId("recommendation-order-submit");
    // 送出一開始就鎖定，包含重新查詢日期政策期間，阻止連點重複建單。
    isSubmitting = true;
    submitError = "";
    setButtonLoading(button, true, "確認日期與建立採購草稿中...");
    try {
        const items = draftItems.map(item => {
            const quantity = parsePositiveQuantity(item.quantityRaw);
            if (!chosenOffer(item))
                throw new Error(`「${item.productName}」尚未選擇有效供應商`);
            if (quantity === null)
                throw new Error(`「${item.productName}」採購數量必須大於 0`);
            return { productId: item.productId, supplierId: Number(item.supplierId), quantity };
        });
        if (!items.length)
            throw new Error("請至少選擇一項商品");

        // 送出前再次向後端查詢台灣時間日期政策，避免視窗開著跨過中午截單時間。
        const policy = await requestJson(POLICY_API);
        const minDate = policy?.earliestDeliveryDate;
        if (!minDate)
            throw new Error("無法確認最早預計到貨日期");
        earliestDeliveryDate = minDate;
        byId("recommendation-delivery-date").min = minDate;
        byId("recommendation-delivery-policy").textContent =
            `台灣時間 12:00 前最快隔天到貨，12:00（含）後最快後天；最早可選 ${minDate}`;
        const deliveryDate = byId("recommendation-delivery-date").value;
        if (!deliveryDate || deliveryDate < minDate) {
            refreshDraftSummary();
            throw new Error(`依目前台灣時間，預計到貨日不可早於 ${minDate}`);
        }
        const remark = byId("recommendation-order-remark").value.trim();
        if (remark.length > 500)
            throw new Error("採購備註不可超過 500 字");

        button.textContent = "正在建立採購草稿...";
        hideMessage();
        // 不傳採購單價，確保採購價格與歷史快照由後端自行查詢及保存。
        const result = await requestJson(DRAFT_API, "POST", {
            expectedDeliveryDate: deliveryDate,
            remark: remark || null,
            items
        });
        const ids = Array.isArray(result?.orders)
            ? result.orders.map(order => `#${order.orderId}`).join("、") : "";
        const count = Number(result?.createdOrderCount ?? 0);
        const amount = money(result?.totalAmount ?? 0);
        // 先恢復可關閉狀態；清除跨頁選購，避免使用者再次建立同一批草稿。
        isSubmitting = false;
        closeDraftConfirmation();
        if (typeof onCreated === "function")
            onCreated(result);
        showMessage(`成功建立 ${count} 張 DRAFT 草稿${ids ? `（${ids}）` : ""}，總金額 NT$ ${amount}。請至採購訂單頁面確認。`, "success");
    }
    catch (error) {
        submitError = error.message;
        showMessage(error.message, "error");
        console.error("批次建立採購草稿失敗：", error);
    }
    finally {
        isSubmitting = false;
        setButtonLoading(button, false);
        refreshDraftSummary();
    }
}
