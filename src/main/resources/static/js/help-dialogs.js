"use strict";

/**
 * 使用者說明視窗：原本原生 dialog 的開關方式保留。
 * 本檔另負責「天氣訂貨建議」的即時計算明細顯示。
 *
 * 不重新呼叫天氣 API，不在前端重新決定倍率；
 * 僅呈現後端同一次回傳的每日係數、期間倍率及計算過程。
 */
function bindDialog(openId, dialogId, closeId) {
    const open = document.getElementById(openId);
    const dialog = document.getElementById(dialogId);
    const close = document.getElementById(closeId);
    if (!open || !dialog || !close)
        return;

    open.addEventListener("click", () => {
        if (!dialog.open)
            dialog.showModal();
    });
    close.addEventListener("click", () => dialog.close());
    dialog.addEventListener("click", event => {
        // 只有點到 dialog 自身背景，不是內文，才關閉。
        if (event.target === dialog)
            dialog.close();
    });
}

function initHelpDialogs() {
    bindDialog("recommendation-formula-open", "recommendation-formula-dialog", "recommendation-formula-close");
    bindDialog("user-guide-open", "user-guide-dialog", "user-guide-close");
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initHelpDialogs, { once: true });
}
else {
    initHelpDialogs();
}

function text(id, value) {
    const element = document.getElementById(id);
    if (element)
        element.textContent = String(value);
}

/** 只在數值有效時格式化，null／undefined 不可誤顯示為 0%。 */
function validNumber(value) {
    if (value === null || value === undefined || value === "")
        return null;
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
}

function rate(value) {
    const number = validNumber(value);
    return number === null ? "--" : number.toFixed(2);
}

function adjustment(value) {
    const number = validNumber(value);
    if (number === null)
        return "--";
    if (number > 0)
        return `+${number.toFixed(2)}`;
    return number.toFixed(2);
}

function dateLabel(value) {
    const match = String(value ?? "").match(/^\d{4}-(\d{2})-(\d{2})$/);
    return match ? `${match[1]}/${match[2]}` : String(value ?? "--");
}

/**
 * 建立 table cell 時一律以 textContent 插入，避免 API 字串被當成 HTML。
 */
function addCell(row, value, className = "") {
    const cell = document.createElement("td");
    cell.textContent = String(value);
    if (className)
        cell.className = className;
    row.appendChild(cell);
}

/**
 * 由訂貨建議模組於成功載入時傳入同一個 API 回應。
 * response=null 表示尚未查詢、條件變更或查詢失敗。
 */
export function setWeatherFactorExplanation(response, emptyMessage = "尚未產生訂貨建議。請先按「產生訂貨建議」，即可查看本次實際倍率。") {
    const details = document.getElementById("guidance-factor-details");
    const tbody = document.getElementById("guidance-factor-rows");
    const note = document.getElementById("guidance-factor-missing");
    const status = document.getElementById("guidance-factor-status");
    if (!details || !tbody || !note || !status)
        return;

    tbody.replaceChildren();
    details.hidden = true;
    note.hidden = true;
    note.textContent = "";
    status.dataset.state = "normal";
    text("guidance-factor-equation", "");
    text("guidance-factor-rounding", "");

    if (!response) {
        text("guidance-factor-location", "尚無已產生的建議");
        text("guidance-factor-date-range", "請先選定地點與日期，並產生訂貨建議");
        text("guidance-factor-value", "--");
        text("guidance-factor-change", "等待計算結果");
        status.textContent = emptyMessage;
        return;
    }

    // 不使用目前表單欄位，避免使用者更改條件時把舊資料誤認為新查詢。
    const backendFactor = validNumber(response.weatherFactor);
    text("guidance-factor-location", `${response.city ?? "--"}・${response.district ?? "--"}`);
    text("guidance-factor-date-range", `${response.startDate ?? "--"} ～ ${response.endDate ?? "--"}（含首尾）`);
    text("guidance-factor-value", backendFactor === null ? "--" : `${backendFactor.toFixed(2)} ×`);
    if (backendFactor === null) {
        text("guidance-factor-change", "沒有可用的期間倍率");
    }
    else {
        const percent = (backendFactor - 1) * 100;
        const label = Math.abs(percent) < 0.00001
            ? "與歷史需求基準相同"
            : percent > 0 ? `預估需求增加 ${percent.toFixed(0)}%` : `預估需求減少 ${Math.abs(percent).toFixed(0)}%`;
        text("guidance-factor-change", label);
    }

    const forecastStatus = String(response.forecastStatus ?? "");
    const statusText = forecastStatus === "COMPLETE" ? "預報查詢完成"
        : forecastStatus === "PARTIAL" ? "部分預報資料不足"
            : forecastStatus === "UNAVAILABLE" ? "暫無有效預報（使用中性倍率）"
                : "預報狀態未知";
    status.dataset.state = forecastStatus === "COMPLETE" ? "normal" : "warning";
    status.textContent = response.warning ? `${statusText}：${response.warning}` : statusText;

    const days = Array.isArray(response.dailyWeatherFactors)
        ? [...response.dailyWeatherFactors].filter(Boolean).sort((a, b) => String(a.date).localeCompare(String(b.date)))
        : [];
    if (days.length === 0) {
        // 天氣服務逾時而使用 1.00 時，沒有每日明細就不能虛構加總過程。
        status.textContent += "。本次沒有逐日明細可顯示。";
        return;
    }

    // 顯示從後端送來的每日值，這裡只計算加總供解釋，不覆寫建議用倍率。
    let sumHundredths = 0;
    let completeFactors = true;
    let missingRainDays = 0;
    let missingTemperatureDays = 0;
    let missingHumidityDays = 0;
    for (const day of days) {
        const row = document.createElement("tr");
        const temp = validNumber(day.maxTemperature);
        const humidity = validNumber(day.averageRelativeHumidity);
        const rain = validNumber(day.maxPrecipitationProbability);
        const factor = validNumber(day.weatherFactor);
        addCell(row, dateLabel(day.date));
        addCell(row, temp === null ? "無資料" : `${temp}°C`, temp === null ? "guidance-factor-unavailable" : "");
        addCell(row, humidity === null ? "無資料" : `${humidity}%`, humidity === null ? "guidance-factor-unavailable" : "");
        addCell(row, rain === null ? "未提供" : `${rain}%`, rain === null ? "guidance-factor-unavailable" : "");
        addCell(row, adjustment(day.temperatureAdjustment));
        addCell(row, adjustment(day.humidityAdjustment));
        addCell(row, adjustment(day.rainAdjustment));
        addCell(row, rate(factor));
        tbody.appendChild(row);

        if (temp === null)
            missingTemperatureDays += 1;
        if (humidity === null)
            missingHumidityDays += 1;
        if (rain === null)
            missingRainDays += 1;
        if (factor === null)
            completeFactors = false;
        else
            sumHundredths += Math.round(factor * 100);
    }

    details.hidden = false;
    if (completeFactors) {
        const sum = sumHundredths / 100;
        const average = sum / days.length;
        text("guidance-factor-equation", `（${days.map(d => rate(d.weatherFactor)).join(" + ")}）÷ ${days.length} 天 = ${sum.toFixed(2)} ÷ ${days.length}`);
        text("guidance-factor-rounding", `四捨五入前平均約 ${average.toFixed(6)} → 本次實際採用 ${rate(backendFactor)} 倍（小數第二位）`);
        // 後端使用 BigDecimal HALF_UP；以「整數百分位」比較，
        // 避免 JS 以浮點 .toFixed(2) 計算 1.005 時可能誤判為 1.00。
        const roundedHalfUp = Math.round(sumHundredths / days.length) / 100;
        if (backendFactor !== null && Math.abs(roundedHalfUp - backendFactor) > 0.001) {
            status.dataset.state = "warning";
            status.textContent += "。每日平均與本次訂貨倍率不同，請重新產生訂貨建議。";
        }
    }
    else {
        text("guidance-factor-equation", "部分每日倍率缺失，無法完整還原加總過程。");
        text("guidance-factor-rounding", `商品訂貨建議仍使用本次已計算的 ${rate(backendFactor)} 倍。`);
    }

    const notices = [];
    if (missingRainDays > 0)
        notices.push(`${missingRainDays} 天沒有降雨機率預報（本項調整 0，並不代表降雨機率為 0%）`);
    if (missingTemperatureDays > 0)
        notices.push(`${missingTemperatureDays} 天缺少最高氣溫`);
    if (missingHumidityDays > 0)
        notices.push(`${missingHumidityDays} 天缺少平均濕度`);
    if (notices.length > 0) {
        note.textContent = `資料提醒：${notices.join("；")}。遠期降雨機率未提供時，可能仍屬官方預報範圍的正常情況。`;
        note.hidden = false;
    }
}
