"use strict";

/**
 * 採購單明細展開模組。
 *
 * 為避免影響既有 purchase-order.js 的 CRUD、送出、收貨與取消流程，
 * 本模組透過 MutationObserver 觀察訂單列表，每次列表更新後替訂單列加入展開箭頭。
 * 點擊時才呼叫 GET /api/purchase-orders/{id} 取得完整明細。
 *
 * 特別注意：
 * 1. 查詢明細為唯讀操作，不會修改採購單或庫存。
 * 2. 使用 textContent 建立所有後端資料，避免將 API 內容當作 HTML 執行。
 * 3. 資料重新整理時，舊展開內容會隨原列表移除，不會顯示過期資料。
 */
import { API, byId, makeElement, displayValue, formatNumber, requestJson } from "./common.js";

const ORDER_COLSPAN = 7;

const DETAIL_ROW_CLASS = "purchase-order-detail-row";

const TOGGLE_CLASS = "purchase-order-detail-toggle";

function safeText(value, fallback = "--") {
    return displayValue(value, fallback);
}

function appendField(container, label, value) {
    const group = makeElement("div", null, "purchase-order-detail-field");
    group.append(makeElement("span", label, "purchase-order-detail-label"), makeElement("strong", safeText(value), "purchase-order-detail-value"));
    container.appendChild(group);
}

function money(value) {
    return value === undefined || value === null
        ? "--"
        : `NT$ ${formatNumber(value)}`;
}

function appendDetailCell(row, value, className = "") {
    const cell = makeElement("td", safeText(value));
    if (className)
        cell.className = className;
    row.appendChild(cell);
}

function buildDetails(order) {
    const section = makeElement("section", null, "purchase-order-detail-content");
    section.setAttribute("aria-label", `採購單 #${safeText(order.id)} 明細`);

    const title = makeElement("h5", `採購單 #${safeText(order.id)} 的採購明細`);
    section.appendChild(title);

    // 採購單上的供應商快照是建立當下的歷史資訊，不改用目前供應商名稱覆蓋。
    const supplier = order.supplierNameSnapshot || order.supplierName || "--";
    const info = makeElement("div", null, "purchase-order-detail-info");
    appendField(info, "供應商", supplier);
    appendField(info, "聯絡人", order.supplierContactPersonSnapshot);
    appendField(info, "聯絡電話", order.supplierPhoneSnapshot);
    appendField(info, "預計到貨", order.expectedDeliveryDate);
    section.appendChild(info);

    const wrapper = makeElement("div", null, "table-responsive purchase-order-detail-table-wrap");
    const table = makeElement("table", null, "data-table purchase-order-detail-table");
    const thead = makeElement("thead");
    const headerRow = makeElement("tr");
    ["商品名稱／SKU", "採購數量", "採購單價", "明細小計"].forEach(label => {
        headerRow.appendChild(makeElement("th", label));
    });
    thead.appendChild(headerRow);
    table.appendChild(thead);

    const tbody = makeElement("tbody");
    const items = Array.isArray(order.items) ? order.items : null;
    if (!items || items.length === 0) {
        const row = makeElement("tr");
        const cell = makeElement("td", items ? "這張採購單目前沒有商品明細" : "目前無法取得這張採購單的商品明細，請稍後再試", "empty-state");
        cell.colSpan = 4;
        row.appendChild(cell);
        tbody.appendChild(row);
    }
    else {
        items.forEach(item => {
            const row = makeElement("tr");
            const productCell = makeElement("td");
            productCell.appendChild(makeElement("strong", safeText(item.productName)));
            productCell.appendChild(makeElement("small", safeText(item.sku), "purchase-order-detail-sku"));
            row.appendChild(productCell);
            appendDetailCell(row, formatNumber(item.quantity), "purchase-order-detail-number");
            appendDetailCell(row, money(item.unitPrice), "purchase-order-detail-number");
            appendDetailCell(row, money(item.subtotal), "purchase-order-detail-number");
            tbody.appendChild(row);
        });
    }
    table.appendChild(tbody);
    wrapper.appendChild(table);
    section.appendChild(wrapper);

    const footer = makeElement("div", null, "purchase-order-detail-footer");
    footer.appendChild(makeElement("span", `共 ${items?.length ?? 0} 項商品`));
    const total = makeElement("strong", `訂單總額：${money(order.totalAmount)}`);
    footer.appendChild(total);
    section.appendChild(footer);

    if (order.remark) {
        section.appendChild(makeElement("p", `採購備註：${order.remark}`, "purchase-order-detail-remark"));
    }
    return section;
}

/**
 * 對既有表格的每張採購單注入展開按鈕與隱藏明細列。
 * 不更動原本 7 欄的位置，保留原本列表與全部操作按鈕。
 */
function decorateOrderRow(row) {
    if (row.classList.contains(DETAIL_ROW_CLASS) ||
        row.querySelector(`.${TOGGLE_CLASS}`))
        return;

    const cells = row.children;
    // 只有實際訂單資料列才有七個欄位；載入中／錯誤提示列只會有一欄。
    if (cells.length !== ORDER_COLSPAN ||
        !Array.from(cells).every(cell => cell.tagName === "TD"))
        return;

    const idText = cells[0].textContent.trim();
    if (!/^[1-9]\d*$/.test(idText))
        return;
    const orderId = Number(idText);
    if (!Number.isSafeInteger(orderId))
        return;

    const detailsRow = makeElement("tr", null, DETAIL_ROW_CLASS);
    detailsRow.hidden = true;
    detailsRow.id = `purchase-order-details-${orderId}`;
    const detailsCell = makeElement("td");
    detailsCell.colSpan = ORDER_COLSPAN;
    detailsRow.appendChild(detailsCell);

    const toggle = makeElement("button", "▸", TOGGLE_CLASS);
    toggle.type = "button";
    toggle.setAttribute("aria-label", `展開採購單 #${orderId} 明細`);
    toggle.setAttribute("aria-expanded", "false");
    toggle.setAttribute("aria-controls", detailsRow.id);
    toggle.title = `展開採購單 #${orderId} 明細`;
    cells[0].prepend(toggle);

    let detailLoaded = false;
    let loading = false;
    toggle.addEventListener("click", async () => {
        if (loading)
            return;

        if (!detailsRow.hidden) {
            detailsRow.hidden = true;
            toggle.textContent = "▸";
            toggle.setAttribute("aria-expanded", "false");
            toggle.setAttribute("aria-label", `展開採購單 #${orderId} 明細`);
            return;
        }

        detailsRow.hidden = false;
        toggle.textContent = "▾";
        toggle.setAttribute("aria-expanded", "true");
        toggle.setAttribute("aria-label", `收合採購單 #${orderId} 明細`);
        if (detailLoaded)
            return;

        // 單筆查詢：清單不會為了顯示折疊明細而額外載入所有商品。
        loading = true;
        toggle.disabled = true;
        detailsCell.replaceChildren(makeElement("p", "正在載入採購明細...", "empty-state"));
        try {
            const order = await requestJson(`${API.purchaseOrders}/${orderId}`);
            if (!detailsRow.isConnected)
                return;
            if (!order || typeof order !== "object" || Array.isArray(order)) {
                throw new Error("採購單明細回傳格式不正確");
            }
            detailsCell.replaceChildren(buildDetails(order));
            detailLoaded = true;
        }
        catch (error) {
            // 如果明細列已經從畫面移除，就不再更新畫面。
            if (!detailsRow.isConnected)
                return;
            // 顯示 API 載入失敗的原因。
            // 使用模板字串，讓錯誤訊息可以包含 error.message。
            detailsCell.replaceChildren(makeElement("p", `載入失敗：${error.message}。請收合後再點一次重試。`, "purchase-order-detail-error"));
        }
        finally {
            // 不論成功或失敗，都恢復按鈕狀態。
            loading = false;
            toggle.disabled = false;
        }
    });

    // 每張主單下方緊接其對應的細項列，並將狀態控制交給按鈕。
    row.after(detailsRow);
}

function decorateOrderTable(tbody) {
    Array.from(tbody.children).forEach(decorateOrderRow);
}

function initializePurchaseOrderDetails() {
    const tbody = byId("order-table-body");
    if (!tbody)
        return;

    // 原本的 refreshOrders() 可能整批重畫表格，因此需持續觀察列表更新。
    const observer = new MutationObserver(() => decorateOrderTable(tbody));
    observer.observe(tbody, { childList: true });
    decorateOrderTable(tbody);
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initializePurchaseOrderDetails, { once: true });
}
else {
    initializePurchaseOrderDetails();
}
