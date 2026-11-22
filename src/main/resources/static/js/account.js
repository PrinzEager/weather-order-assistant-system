"use strict";

// 我的帳號：僅串接已驗收的 /api/account/me 與 /api/account/change-password。
// 不更動既有導覽、採購、商品等模組，也不保存密碼或 Session ID。
import { csrfHeaders, redirectIfSessionExpired } from "./common.js";

const byId = id => document.getElementById(id);
let submitting = false;

// 後端回傳 LocalDateTime 不包含時區；直接以原始時間格式顯示，避免瀏覽器自行換算。
function formatLocalDateTime(value) {
    return value ? String(value).replace("T", " ").slice(0, 19) : "--";
}

// API 錯誤會以 JSON.message 優先顯示；不把密碼或 Token 輸出到 Console。
async function readApiError(response) {
    const type = response.headers.get("content-type") || "";
    if (type.includes("json")) {
        try {
            const data = await response.json();
            return data?.message || data?.detail || data?.error || `請求失敗（HTTP ${response.status}）`;
        } catch (_) {
            // JSON 格式異常時提供基本訊息，不洩漏完整 HTML 錯誤頁。
        }
    }
    return `請求失敗（HTTP ${response.status}）`;
}

function showFormMessage(message, isError = true) {
    const element = byId("account-form-message");
    element.textContent = message;
    element.hidden = false;
    element.classList.toggle("account-form-success", !isError);
}

function resetFormMessage() {
    const element = byId("account-form-message");
    element.textContent = "";
    element.hidden = true;
    element.classList.remove("account-form-success");
}

async function loadMyAccount() {
    const info = byId("account-info-message");
    const details = byId("account-details");
    info.hidden = false;
    info.textContent = "正在讀取帳號資訊...";
    details.hidden = true;

    try {
        const response = await fetch("/api/account/me", {
            method: "GET",
            credentials: "same-origin",
            headers: { "Accept": "application/json" },
            cache: "no-store"
        });
        redirectIfSessionExpired(response);
        if (!response.ok) throw new Error(await readApiError(response));

        const user = await response.json();
        byId("account-name").textContent = user.username ?? "--";
        byId("account-status").textContent = user.status === 1 ? "啟用" : "停用";
        byId("account-created-at").textContent = formatLocalDateTime(user.createdAt);
        byId("account-updated-at").textContent = formatLocalDateTime(user.updatedAt);
        info.hidden = true;
        details.hidden = false;
    } catch (error) {
        info.textContent = `帳號資訊讀取失敗：${error.message || "請稍後重試"}`;
    }
}

function openAccountDialog() {
    const dialog = byId("account-dialog");
    if (!dialog || dialog.open) return;
    resetFormMessage();
    byId("account-password-form").reset();
    dialog.showModal();
    void loadMyAccount();
}

function closeAccountDialog() {
    if (submitting) return;
    byId("account-dialog")?.close();
}

async function submitChangePassword(event) {
    event.preventDefault();
    if (submitting) return;

    const currentPassword = byId("account-current-password").value;
    const newPassword = byId("account-new-password").value;
    const confirmPassword = byId("account-confirm-password").value;
    resetFormMessage();

    // 前端僅協助提早提示；所有規則仍由已完成的後端 Service 再次驗證。
    if (newPassword !== confirmPassword) {
        showFormMessage("兩次輸入的新密碼不一致");
        return;
    }
    if (newPassword.length < 12) {
        showFormMessage("新密碼至少需要 12 個字元");
        return;
    }
    if (new TextEncoder().encode(newPassword).length > 72) {
        showFormMessage("新密碼 UTF-8 編碼長度不可超過 72 bytes");
        return;
    }
    if (newPassword === currentPassword) {
        showFormMessage("新密碼不可與目前密碼相同");
        return;
    }

    submitting = true;
    const button = byId("account-submit-button");
    button.disabled = true;
    button.textContent = "正在更新密碼...";

    try {
        const response = await fetch("/api/account/change-password", {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Accept": "application/json",
                "Content-Type": "application/json",
                ...csrfHeaders("POST")
            },
            body: JSON.stringify({ currentPassword, newPassword, confirmPassword })
        });
        redirectIfSessionExpired(response);
        if (!response.ok) throw new Error(await readApiError(response));
        if (response.status !== 204) throw new Error("伺服器回應與預期不符，請重新確認登入狀態");

        // 後端已使原本 Session 失效；不可沿用舊 Cookie，直接導回登入頁。
        byId("account-password-form").reset();
        window.location.assign("/login?passwordChanged");
    } catch (error) {
        showFormMessage(error.message || "更新失敗，請稍後重試");
    } finally {
        submitting = false;
        button.disabled = false;
        button.textContent = "確認修改密碼";
    }
}

function initializeAccountUI() {
    const openButton = byId("account-open-button");
    const dialog = byId("account-dialog");
    const form = byId("account-password-form");
    if (!openButton || !dialog || !form) return;

    openButton.addEventListener("click", openAccountDialog);
    byId("account-close-button").addEventListener("click", closeAccountDialog);
    byId("account-cancel-button").addEventListener("click", closeAccountDialog);
    form.addEventListener("submit", submitChangePassword);
    dialog.addEventListener("cancel", event => {
        if (submitting) event.preventDefault();
    });
    dialog.addEventListener("close", () => {
        form.reset();
        resetFormMessage();
    });
}

document.addEventListener("DOMContentLoaded", initializeAccountUI);
