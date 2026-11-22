"use strict";

/**
 * 銷售報表：由既有 /api/sales-records 結果繪圖與匯出，不增加後端資料查詢。
 * 銷售毛額/件數以原始紀錄統計；淨額/件數另外扣除已退貨，避免混淆。
 * 篩選日期的起訖皆包含當日；超過 14 天僅圖表顯示最近 14 天，報表仍完整。
 */
import { byId, makeElement, getTaiwanToday, addDays, showMessage } from "./common.js";

let allRecords = [];
let selectedRecords = [];
let onFilter = null;
const MONEY = new Intl.NumberFormat("zh-TW", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const SVG = "http://www.w3.org/2000/svg";

function yuan(cents) { return MONEY.format(cents / 100); }
function recordCents(record) { return Math.round(Number(record.totalAmount ?? 0) * 100); }
function returnedCents(record) {
    return Math.round(Number(record.unitPrice ?? 0) * 100) * Number(record.returnedQuantity ?? 0);
}
function datePart(record) { return String(record.dateTime ?? "").slice(0, 10); }
function dateRange() {
    const start = byId("sales-start-date").value;
    const end = byId("sales-end-date").value;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(start) || !/^\d{4}-\d{2}-\d{2}$/.test(end) || start > end) {
        throw new Error("請選擇有效的起始與結束日期，且起始日期不可晚於結束日期");
    }
    return { start, end };
}

export function initSalesReport(callback) {
    onFilter = callback;
    const today = getTaiwanToday();
    byId("sales-start-date").value = addDays(today, -6);
    byId("sales-end-date").value = today;
    byId("sales-filter-form").addEventListener("submit", event => {
        event.preventDefault();
        try { applyDateFilter(); } catch (error) { showMessage(error.message, "error"); }
    });
    byId("sales-last-seven-days").addEventListener("click", () => {
        const todayNow = getTaiwanToday();
        byId("sales-start-date").value = addDays(todayNow, -6);
        byId("sales-end-date").value = todayNow;
        applyDateFilter();
    });
    byId("sales-export-csv").addEventListener("click", downloadCsv);
    byId("sales-print-report").addEventListener("click", printReport);
}

export function updateSalesReport(records) {
    allRecords = records;
    applyDateFilter();
}

function applyDateFilter() {
    const { start, end } = dateRange();
    selectedRecords = allRecords.filter(item => {
        const date = datePart(item);
        return date >= start && date <= end;
    });
    renderMetrics();
    renderChart(start, end);
    if (typeof onFilter === "function") onFilter(selectedRecords);
}

function reportTotals() {
    const grossCents = selectedRecords.reduce((sum, row) => sum + recordCents(row), 0);
    const returnedTotal = selectedRecords.reduce((sum, row) => sum + returnedCents(row), 0);
    const grossQty = selectedRecords.reduce((sum, row) => sum + Number(row.quantity || 0), 0);
    const returnedQty = selectedRecords.reduce((sum, row) => sum + Number(row.returnedQuantity || 0), 0);
    const groups = new Set(selectedRecords.map(row => row.batchId || `legacy:${row.id}`));
    return { grossCents, returnedTotal, grossQty, returnedQty, batches: groups.size };
}

function renderMetrics() {
    const totals = reportTotals();
    const summary = byId("sales-report-summary");
    summary.replaceChildren();
    const values = [
        ["銷售筆數", `${totals.batches} 筆`],
        ["銷售毛數量", `${totals.grossQty} 件`],
        ["退貨數量", `${totals.returnedQty} 件`],
        ["淨銷售數量", `${totals.grossQty - totals.returnedQty} 件`],
        ["銷售毛額", `NT$ ${yuan(totals.grossCents)}`],
        ["退貨金額", `NT$ ${yuan(totals.returnedTotal)}`],
        ["淨銷售金額", `NT$ ${yuan(totals.grossCents - totals.returnedTotal)}`]
    ];
    values.forEach(([label, value]) => {
        const card = makeElement("div", null, "sales-report-metric");
        card.append(makeElement("span", label), makeElement("strong", value));
        summary.appendChild(card);
    });
}

function svgNode(tag, attrs = {}, text = null) {
    const node = document.createElementNS(SVG, tag);
    for (const [key, value] of Object.entries(attrs)) node.setAttribute(key, String(value));
    if (text != null) node.textContent = String(text);
    return node;
}

function renderChart(start, end) {
    const container = byId("sales-trend-chart");
    const note = byId("sales-chart-note");
    container.replaceChildren();
    const chartStart = start > addDays(end, -13) ? start : addDays(end, -13);
    const dateKeys = [];
    for (let date = chartStart; date <= end; date = addDays(date, 1)) dateKeys.push(date);
    note.textContent = dateKeys.length < Number((Date.parse(end) - Date.parse(start)) / 86400000 + 1)
        ? `長條圖僅顯示 ${chartStart} ～ ${end}（最近 14 天）；列表與匯出仍包含整個篩選期間。`
        : `${start} ～ ${end}；柱狀為原始售出件數，折線為銷售毛額，未扣除退貨。`;
    if (dateKeys.length === 0 || selectedRecords.length === 0) {
        container.appendChild(makeElement("p", "這個日期範圍尚無銷售紀錄", "sales-trend-empty"));
        return;
    }
    const byDate = new Map(dateKeys.map(date => [date, { count: 0, cents: 0 }]));
    selectedRecords.forEach(record => {
        const day = byDate.get(datePart(record));
        if (!day) return;
        day.count += Number(record.quantity ?? 0);
        day.cents += recordCents(record);
    });
    const days = [...byDate.entries()].map(([date, amount]) => ({ date, ...amount }));
    const width = 900, height = 295, left = 60, right = 830, top = 25, bottom = 228;
    const plot = bottom - top, step = (right - left) / days.length;
    const maxQty = Math.max(1, ...days.map(day => day.count));
    const maxCents = Math.max(1, ...days.map(day => day.cents));
    const chart = svgNode("svg", { viewBox: `0 0 ${width} ${height}`, role: "img", "aria-label": "每日銷售量柱狀圖及每日銷售毛額折線圖" });
    chart.appendChild(svgNode("title", {}, "銷售數量與銷售金額趨勢"));
    for (let tick = 0; tick <= 4; tick++) {
        const y = bottom - plot * tick / 4;
        chart.appendChild(svgNode("line", { x1:left, x2:right, y1:y, y2:y, stroke:"#ecded0" }));
        chart.appendChild(svgNode("text", { x:left - 8, y:y + 5, "text-anchor":"end", fill:"#826956", "font-size":12 }, Math.round(maxQty * tick / 4)));
        chart.appendChild(svgNode("text", { x:right + 8, y:y + 5, fill:"#826956", "font-size":11 }, Math.round(maxCents / 100 * tick / 4).toLocaleString("zh-TW")));
    }
    const points = [];
    days.forEach((day, index) => {
        const center = left + (index + 0.5) * step;
        const yBar = bottom - day.count / maxQty * plot;
        const bar = svgNode("rect", { x:center - step * .30, y:yBar, width:step * .6, height:bottom - yBar, rx:5, fill:"#ebb580" });
        bar.appendChild(svgNode("title", {}, `${day.date}：銷售 ${day.count} 件`));
        chart.appendChild(bar);
        points.push(`${center},${bottom - day.cents / maxCents * plot}`);
        chart.appendChild(svgNode("text", { x:center, y:bottom + 22, "text-anchor":"middle", fill:"#78624f", "font-size":11 }, day.date.slice(5).replace("-", "/")));
    });
    chart.appendChild(svgNode("polyline", { points:points.join(" "), fill:"none", stroke:"#ce6b2b", "stroke-width":3, "stroke-linejoin":"round", "stroke-linecap":"round" }));
    days.forEach((day, index) => {
        const circle = svgNode("circle", { cx:left + (index + .5) * step, cy:bottom - day.cents / maxCents * plot, r:4, fill:"#fff", stroke:"#ce6b2b", "stroke-width":2 });
        circle.appendChild(svgNode("title", {}, `${day.date}：銷售毛額 NT$ ${yuan(day.cents)}`));
        chart.appendChild(circle);
    });
    container.appendChild(chart);
}

// 防止 CSV 公式注入與逗號、換行造成欄位錯位；Excel 可直接開啟 UTF-8 BOM 檔案。
function csvCell(value) {
    let text = String(value ?? "");
    if (/^[\s]*[=+\-@]/.test(text)) text = "'" + text;
    return `"${text.replaceAll('"', '""')}"`;
}
function exportRows() {
    const rows = [["銷售時間", "批次識別碼", "明細編號", "商品名稱", "SKU", "銷售數量", "已退貨量", "淨售數量", "單價(NT$)", "銷售毛額(NT$)", "退貨金額(NT$)", "淨銷售額(NT$)"]];
    selectedRecords.forEach(row => {
        const returned = Number(row.returnedQuantity || 0);
        const gross = recordCents(row);
        const refund = returnedCents(row);
        rows.push([String(row.dateTime ?? "").replace("T", " "), row.batchId || "單品銷售", row.id,
            row.productName, row.sku, row.quantity, returned, Number(row.quantity || 0) - returned,
            yuan(Math.round(Number(row.unitPrice) * 100)), yuan(gross), yuan(refund), yuan(gross - refund)]);
    });
    const total = reportTotals();
    rows.push([], ["合計", "", "", "", "", total.grossQty, total.returnedQty,
        total.grossQty - total.returnedQty, "", yuan(total.grossCents), yuan(total.returnedTotal),
        yuan(total.grossCents - total.returnedTotal)]);
    return rows;
}
function downloadCsv() {
    if (!selectedRecords.length) { showMessage("指定範圍沒有銷售紀錄可匯出", "error"); return; }
    const rows = exportRows();
    const csv = "\uFEFF" + rows.map(row => row.map(csvCell).join(",")).join("\r\n");
    const blob = new Blob([csv], { type:"text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `銷售報表_${byId("sales-start-date").value}_${byId("sales-end-date").value}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function htmlEscape(value) {
    return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}
function printReport() {
    if (!selectedRecords.length) { showMessage("指定範圍沒有銷售紀錄可列印", "error"); return; }
    const popup = window.open("", "_blank");
    if (!popup) { showMessage("瀏覽器阻擋報表視窗，請允許本站開啟彈出視窗", "error"); return; }
    const totals = reportTotals();
    const rows = exportRows();
    // 使用 HTML escaping 建立單獨的列印畫面，不插入未經處理的商品名稱。
    popup.document.write(`<!doctype html><html lang="zh-Hant"><head><meta charset="UTF-8"><title>銷售報表</title>
      <style>body{font-family:Arial,'Microsoft JhengHei',sans-serif;color:#3a3129;padding:26px}h1{color:#91562f}p{color:#6b6159}table{border-collapse:collapse;width:100%;font-size:11px}th,td{border:1px solid #d9c5b2;padding:7px;text-align:left}th{background:#f7eadb}small{color:#8b745f}@page{size:A4 landscape;margin:13mm}@media print{.print-button{display:none}}</style></head><body>
      <button class="print-button">列印／另存 PDF</button><h1>銷售紀錄報表</h1>
      <p>統計期間：${htmlEscape(byId("sales-start-date").value)} ～ ${htmlEscape(byId("sales-end-date").value)}（含起訖日）</p>
      <p>銷售筆數：${totals.batches}｜銷售毛額：NT$ ${yuan(totals.grossCents)}｜退貨金額：NT$ ${yuan(totals.returnedTotal)}｜淨銷售額：NT$ ${yuan(totals.grossCents - totals.returnedTotal)}</p>
      <table><thead><tr>${rows[0].map(text => `<th>${htmlEscape(text)}</th>`).join("")}</tr></thead><tbody>${rows.slice(1).filter(row => row.length).map(row => `<tr>${row.map(text => `<td>${htmlEscape(text)}</td>`).join("")}</tr>`).join("")}</tbody></table>
      <p><small>本報表為銷售原始紀錄及其累計退貨，不包含其他期間或已軟刪除之紀錄。</small></p></body></html>`);
    popup.document.close();
    popup.document.querySelector(".print-button")?.addEventListener("click", () => popup.print());
    popup.focus();
}
