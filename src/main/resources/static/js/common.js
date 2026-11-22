"use strict";

// AJAX 與 Spring Security 共用 CSRF header；Token 來自後端渲染的 index.html。
export function csrfHeaders(method = "GET") {
    const safeMethod = String(method).toUpperCase();
    if (["GET", "HEAD", "OPTIONS", "TRACE"].includes(safeMethod)) return {};
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const headerName = document.querySelector('meta[name="_csrf_header"]')?.content;
    return token && headerName ? { [headerName]: token } : {};
}

// Session 過期時，避免將登入頁 HTML 誤當成 JSON。
export function redirectIfSessionExpired(response) {
    if (response.status === 401) {
        window.location.assign("/login?expired");
        throw new Error("登入已逾時，請重新登入");
    }
}

export const API = Object.freeze({
    weather: "/api/weather/forecast",
    recommendations: "/api/order-recommendations",
    categories: "/api/product-categories",
    products: "/api/products",
    suppliers: "/api/suppliers",
    salesRecords: "/api/sales-records",
    purchaseOrders: "/api/purchase-orders"
});

/* 共用工具：由原 app.js 分離，保留既有功能。 */


export function byId(id) {
    return document.getElementById(id);
}

// 建立文字節點，避免直接插入 API 回傳的 HTML。

export function makeElement(tag, text, className) {
    const element = document.createElement(tag);

    if (text !== undefined && text !== null) {
        element.textContent = String(text);
    }

    if (className) {
        element.className = className;
    }

    return element;
}

export function clearElement(element) {
    if (element) {
        element.replaceChildren();
    }
}

export function showEmpty(container, message) {
    clearElement(container);

    if (container) {
        container.appendChild(
            makeElement("p", message, "empty-state")
        );
    }
}

export function showMessage(message, type = "info") {
    const element = byId("global-message");

    element.textContent = message;
    element.className = "global-message";

    if (type === "success" || type === "error") {
        element.classList.add(type);
    }

    element.hidden = false;
}

export function hideMessage() {
    byId("global-message").hidden = true;
}

export function setButtonLoading(button, loading, loadingText) {
    if (!button) return;

    if (loading) {
        button.dataset.originalText = button.textContent.trim();
        button.textContent = loadingText;
        button.disabled = true;
    } else {
        button.textContent =
            button.dataset.originalText || "查詢";
        button.disabled = false;
    }
}

export function displayValue(value, fallback = "--") {
    return value === null ||
           value === undefined ||
           value === ""
        ? fallback
        : String(value);
}

export function formatNumber(value) {
    const number = Number(value);

    return Number.isFinite(number)
        ? number.toLocaleString("zh-TW", {
            maximumFractionDigits: 2
        })
        : "--";
}

// =========================================================
// 3. 台灣日期工具
// =========================================================

// 取得台灣當地日期，不使用 UTC 日期直接截取。

export function getTaiwanToday() {
    const formatter = new Intl.DateTimeFormat("en-US", {
        timeZone: "Asia/Taipei",
        year: "numeric",
        month: "2-digit",
        day: "2-digit"
    });

    const parts = formatter.formatToParts(new Date());

    const values = {};

    parts.forEach(part => {
        values[part.type] = part.value;
    });

    return `${values.year}-${values.month}-${values.day}`;
}

// 以 UTC 日曆計算日期，避免跨日、跨月問題。

export function addDays(dateString, days) {
    const [year, month, day] = dateString
        .split("-")
        .map(Number);

    const date = new Date(
        Date.UTC(year, month - 1, day + days)
    );

    return date.toISOString().slice(0, 10);
}

// =========================================================
// 七天預報日期範圍：含當日共七天（今天至今天 + 6 天）。
// 舊版以今天 + 2 天當上限，會阻擋已完成的七天天氣 API。
// 訂貨建議的「預設三天」仍保留，避免改變使用者既有操作習慣；
// 但兩個模組都可自行選擇最多七天，與新版後端規則一致。
// =========================================================
export function initializeDates() {
    const today = getTaiwanToday();
    const lastDay = addDays(today, 6);
    const recommendationDefaultEnd = addDays(today, 2);

    [
        ["weather-start-date", today],
        ["weather-end-date", lastDay],
        ["recommendation-start-date", today],
        ["recommendation-end-date", recommendationDefaultEnd]
    ].forEach(([id, value]) => {
        const input = byId(id);
        if (!input) return;
        input.value = value;
        input.min = today;
        input.max = lastDay;
    });
}

export function validateDateRange(startDate, endDate) {
    const today = getTaiwanToday();
    const lastDay = addDays(today, 6);

    if (!startDate || !endDate) {
        throw new Error("請選擇起始日期與結束日期");
    }

    if (startDate > endDate) {
        throw new Error("起始日期不可晚於結束日期");
    }

    if (startDate < today || endDate > lastDay) {
        throw new Error("目前僅支援台灣當天至第七天（含起迄日）的天氣預報");
    }
}

export async function apiGet(path, parameters) {
    const query = new URLSearchParams(parameters);
    const url = `${path}?${query.toString()}`;

    const response = await fetch(url, {
        method: "GET",
        headers: {
            "Accept": "application/json"
        }
    });

    redirectIfSessionExpired(response);

    // 避免伺服器回傳非 JSON 時直接造成解析例外。
    const contentType =
        response.headers.get("content-type") || "";

    let data = null;

    if (contentType.includes("json")) {
        data = await response.json();
    } else {
        const text = await response.text();

        if (!response.ok) {
            throw new Error(
                `HTTP ${response.status}：${text.slice(0, 100)}`
            );
        }

        throw new Error("後端未回傳預期的 JSON 資料");
    }

    if (!response.ok) {
        throw new Error(
            data?.message ||
            data?.error ||
            `請求失敗（HTTP ${response.status}）`
        );
    }

    return data;
}

export function getSearchParameters(prefix) {
    const city = byId(`${prefix}-city`).value.trim();
    const district = byId(`${prefix}-district`).value.trim();

    const startDate =
        byId(`${prefix}-start-date`).value;

    const endDate =
        byId(`${prefix}-end-date`).value;

    if (!city || !district) {
        throw new Error("請輸入縣市與鄉鎮市區");
    }

    validateDateRange(startDate, endDate);

    return { city, district, startDate, endDate };
}

// =========================================================
// 6. 天氣圖示
// =========================================================

// 中央氣象署 weatherCode 為字串，需保留前導零。
// 此處以簡化圖示分類顯示，詳細代碼可後續補齊。

export function createStatCard(title, value, note = "") {
    const article = makeElement("article", null, "stat-card");

    article.appendChild(makeElement("h4", title));
    article.appendChild(makeElement("strong", value));
    article.appendChild(makeElement("p", note));

    return article;
}

// 通用請求：可處理 JSON 回應與沒有 Body 的 HTTP 204。
export async function requestJson(url, method = "GET", body) {
    const response = await fetch(url, {
        method,
        headers: {
            "Accept": "application/json",
            ...(body === undefined
                ? {}
                : { "Content-Type": "application/json" }),
            ...csrfHeaders(method)
        },
        ...(body === undefined
            ? {}
            : { body: JSON.stringify(body) })
    });

    redirectIfSessionExpired(response);

    const raw = await response.text();
    let data = null;

    try {
        data = raw ? JSON.parse(raw) : null;
    } catch {
        // 非 JSON 的錯誤回覆由 HTTP 狀態處理。
    }

    if (!response.ok) {
        const validationMessage = Array.isArray(data?.errors)
            ? data.errors
                .map(error => error.defaultMessage || error.message)
                .filter(Boolean)
                .join("、")
            : null;

        throw new Error(
            validationMessage ||
            data?.message ||
            data?.detail ||
            data?.error ||
            `HTTP ${response.status}: ${raw.slice(0, 100)}`
        );
    }

    return data;
}
