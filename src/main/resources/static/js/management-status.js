"use strict";

/**
 * 商品／供應商後台狀態管理。
 *
 * 為避免影響已驗收的商品、供應商編輯、供貨價格與採購功能：
 * - 「啟用中」沿用原本的管理列表與 CRUD 按鈕。
 * - 「全部」或「已停用」顯示獨立的管理查詢表格，可停用／重新啟用。
 * - 永遠不修改 /api/products、/api/suppliers 的有效資料清單定義。
 */
import {
    API, byId, makeElement, displayValue, formatNumber,
    requestJson, showMessage
} from "./common.js";

const VIEWS = [
    {
        kind: "products",
        pageId: "page-products",
        tableBodyId: "product-table-body",
        title: "商品",
        columns: 8,
        endpoint: "/api/management/products",
        entityApi: API.products
    },
    {
        kind: "suppliers",
        pageId: "page-suppliers",
        tableBodyId: "supplier-table-body",
        title: "供應商",
        columns: 6,
        endpoint: "/api/management/suppliers",
        entityApi: API.suppliers
    }
];

function option(value, label) {
    const item = makeElement("option", label);
    item.value = value;
    return item;
}

function td(text, className = "") {
    return makeElement("td", displayValue(text), className);
}

function button(label, className, handler) {
    const result = makeElement("button", label, className);
    result.type = "button";
    result.addEventListener("click", handler);
    return result;
}

function badge(status) {
    return makeElement(
        "span",
        Number(status) === 1 ? "啟用" : "停用",
        `status-badge ${Number(status) === 1 ? "success" : "danger"}`
    );
}

// 將原有啟用列表重新讀取。未具備重新整理 export 時不影響狀態 API 成功結果。
async function refreshOriginalList(kind) {
    try {
        if (kind === "suppliers") {
            const module = await import("./supplier.js");
            return typeof module.refreshSuppliers === "function"
                ? (await module.refreshSuppliers()) !== false
                : false;
        }

        const module = await import("./product.js");
        return typeof module.refreshProducts === "function"
            ? (await module.refreshProducts()) !== false
            : false;
    } catch (error) {
        console.warn("原有管理列表暫時無法重新整理；可重新整理網頁：", error);
        return false;
    }
}

/**
 * 根據管理查詢建立獨立表格；避免插手原有模組私有的 render 函式。
 */
function renderManagementRows(view, entries) {
    view.altBody.replaceChildren();

    view.count.textContent = `目前顯示 ${entries.length} 筆${view.config.title}`;

    if (entries.length === 0) {
        const row = makeElement("tr");
        const cell = makeElement("td", "沒有符合狀態的資料", "empty-state");
        cell.colSpan = view.config.columns;
        row.appendChild(cell);
        view.altBody.appendChild(row);
        return;
    }

    entries.forEach(entry => {
        const row = makeElement("tr");
        const active = Number(entry.status) === 1;

        if (view.config.kind === "products") {
            row.appendChild(td(entry.sku));

            const name = makeElement("td");
            name.appendChild(makeElement("strong", displayValue(entry.name)));
            name.appendChild(makeElement(
                "div", `單位：${displayValue(entry.unit)}`, "weather-description"
            ));
            row.appendChild(name);

            row.appendChild(td(entry.categoryName));
            row.appendChild(td(entry.price == null ? "--" : `NT$ ${formatNumber(entry.price)}`));
            row.appendChild(td(formatNumber(entry.currentStock)));
            row.appendChild(td(formatNumber(entry.safetyStock)));
        } else {
            row.appendChild(td(entry.name));
            row.appendChild(td(entry.contactPerson));
            row.appendChild(td(entry.phone));
            row.appendChild(td(entry.email));
        }

        const statusCell = makeElement("td");
        statusCell.appendChild(badge(entry.status));
        row.appendChild(statusCell);

        const actions = makeElement("td", null, "management-status-actions");
        const change = button(
            active ? "停用" : "重新啟用",
            active ? "secondary-button" : "primary-button",
            () => void toggleStatus(view, entry, change)
        );
        actions.appendChild(change);

        if (active) {
            actions.appendChild(button(
                "切換至編輯清單", "secondary-button",
                () => void changeMode(view, "active")
            ));
        }
        row.appendChild(actions);
        view.altBody.appendChild(row);
    });
}

function renderError(view, message) {
    const row = makeElement("tr");
    const cell = makeElement("td", message, "empty-state");
    cell.colSpan = view.config.columns;
    row.appendChild(cell);
    view.altBody.replaceChildren(row);
    view.count.textContent = "查詢失敗";
}

async function loadManagementRows(view) {
    const status = view.filter.value;
    if (status === "active") return false;

    const version = ++view.version;
    renderError(view, "正在查詢管理資料...");

    try {
        const data = await requestJson(
            `${view.config.endpoint}?status=${encodeURIComponent(status)}`
        );
        // 避免快速切換篩選時，舊請求覆寫目前畫面。
        if (view.version !== version || view.filter.value !== status) return;
        if (!Array.isArray(data)) throw new Error("後台管理 API 回傳格式不正確");
        renderManagementRows(view, data);
        return true;
    } catch (error) {
        if (view.version !== version || view.filter.value !== status) return;
        renderError(view, "無法讀取管理資料");
        showMessage(`${view.config.title}狀態查詢失敗：${error.message}`, "error");
        console.error("Management Status API：", error);
        return false;
    }
}

async function changeMode(view, value) {
    view.filter.value = value;
    view.version++;

    const showActiveList = value === "active";
    view.originalWrapper.hidden = !showActiveList;
    view.altWrapper.hidden = showActiveList;
    view.count.hidden = showActiveList;

    if (showActiveList) {
        // 回到原始列表時，恢復已驗收的新增／編輯／刪除等操作。
        const refreshed = await refreshOriginalList(view.config.kind);
        if (!refreshed) {
            view.count.hidden = false;
            view.count.textContent = "若列表未更新，請重新整理網頁";
        }
    } else {
        await loadManagementRows(view);
    }
}

async function toggleStatus(view, entry, actionButton) {
    if (actionButton.disabled) return;

    const nextStatus = Number(entry.status) === 1 ? 0 : 1;
    const action = nextStatus === 1 ? "重新啟用" : "停用";
    if (!window.confirm(`確定要${action}${view.config.title}「${entry.name}」？`)) return;

    actionButton.disabled = true;
    try {
        // 不新增狀態更新 API，沿用現有 Controller 驗收過的 PATCH。
        await requestJson(
            `${view.config.entityApi}/${entry.id}/status?status=${nextStatus}`,
            "PATCH"
        );
        const originalRefreshed = await refreshOriginalList(view.config.kind);
        const listLoaded = await loadManagementRows(view);
        if (!listLoaded) {
            showMessage(
                `${view.config.title}「${entry.name}」狀態已更新，但管理清單重新載入失敗，請重新查詢`,
                "error"
            );
        } else {
            showMessage(
                `${view.config.title}「${entry.name}」已${action}` +
                (originalRefreshed ? "" : "；切回啟用清單後若未更新請重新整理頁面"),
                "success"
            );
        }
    } catch (error) {
        // 失敗時保留資料列，讓使用者可再次操作。
        showMessage(`${action}失敗：${error.message}`, "error");
    } finally {
        // 列表可能已重繪，因此只解除仍存在的舊按鈕。
        if (actionButton.isConnected) actionButton.disabled = false;
    }
}

function installView(config) {
    const page = byId(config.pageId);
    const tbody = byId(config.tableBodyId);
    if (!page || !tbody || byId(`management-${config.kind}-filter`)) return;

    const originalWrapper = tbody.closest(".table-responsive");
    const panel = originalWrapper?.closest(".panel");
    const toolbar = panel?.querySelector(".panel-toolbar");
    const originalTable = tbody.closest("table");
    const thead = originalTable?.querySelector("thead");
    if (!originalWrapper || !toolbar || !thead) {
        console.error(`${config.title}狀態管理掛載失敗：找不到原有列表`);
        return;
    }

    // 原有 toolbar 直接新增篩選，不覆寫既有「新增」按鈕。
    const filterGroup = makeElement("div", null, "management-status-filter");
    const label = makeElement("label", "顯示狀態：");
    label.htmlFor = `management-${config.kind}-filter`;
    const filter = makeElement("select");
    filter.id = `management-${config.kind}-filter`;
    filter.append(
        option("active", "啟用中（原有管理清單）"),
        option("all", "全部（含停用）"),
        option("inactive", "已停用（可重新啟用）")
    );
    filter.value = "active";
    filterGroup.append(label, filter);
    toolbar.insertBefore(filterGroup, toolbar.lastElementChild);

    // 新增第二個「狀態管理檢視」：只在選擇全部／停用時顯示。
    const altWrapper = makeElement("div", null, "table-responsive management-status-view");
    altWrapper.hidden = true;
    const altTable = makeElement("table", null, "data-table");
    altTable.appendChild(thead.cloneNode(true));
    const altBody = makeElement("tbody");
    altTable.appendChild(altBody);
    altWrapper.appendChild(altTable);
    originalWrapper.insertAdjacentElement("afterend", altWrapper);

    const count = makeElement("p", "", "management-status-count");
    count.hidden = true;
    altWrapper.insertAdjacentElement("beforebegin", count);

    const view = {
        config, filter, originalWrapper, altWrapper, altBody, count, version: 0
    };
    filter.addEventListener("change", () => void changeMode(view, filter.value));
}

function initializeManagementStatus() {
    VIEWS.forEach(installView);
    console.info("[Management Status] 商品／供應商全部與停用篩選已安裝");
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initializeManagementStatus, { once: true });
} else {
    initializeManagementStatus();
}
