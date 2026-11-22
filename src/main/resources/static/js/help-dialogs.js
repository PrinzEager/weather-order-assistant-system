"use strict";

/**
 * 公式說明與操作手冊共用原生 dialog。
 * 只在按鈕點擊時開啟，不在切頁或登入後自動跳出。
 * 原生 dialog 支援 Esc、焦點管理與遮罩；按背景也能關閉。
 */
function bindDialog(openId, dialogId, closeId) {
    const open = document.getElementById(openId);
    const dialog = document.getElementById(dialogId);
    const close = document.getElementById(closeId);
    if (!open || !dialog || !close) return;

    open.addEventListener("click", () => {
        if (!dialog.open) dialog.showModal();
    });
    close.addEventListener("click", () => dialog.close());
    dialog.addEventListener("click", event => {
        // 只有點到 dialog 自身背景，不是內文，才關閉。
        if (event.target === dialog) dialog.close();
    });
}

function initHelpDialogs() {
    bindDialog("recommendation-formula-open", "recommendation-formula-dialog", "recommendation-formula-close");
    bindDialog("user-guide-open", "user-guide-dialog", "user-guide-close");
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initHelpDialogs, { once: true });
} else {
    initHelpDialogs();
}
