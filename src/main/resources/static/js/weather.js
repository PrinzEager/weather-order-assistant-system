/* 天氣模組：由原 app.js 分離，保留既有功能。 */
import {
    API,
    byId,
    clearElement,
    showEmpty,
    showMessage,
    hideMessage,
    setButtonLoading,
    displayValue,
    formatNumber,
    getSearchParameters,
    apiGet,
    createStatCard
} from "./common.js";

import { initLocationDropdown } from "./taiwan-districts.js";

let weatherLoaded = false;

export function initWeather() {
    // =====================================================
    // 一、初始化縣市與鄉鎮市區連動下拉選單
    // =====================================================
    // 七天天氣預報頁面
    initLocationDropdown("weather");

    // 天氣訂貨建議頁面
    // 共用行政區資料，確保兩個模組選項一致。
    initLocationDropdown("recommendation");

    // =====================================================
    // 二、保留原本天氣查詢事件
    // =====================================================
    byId("weather-search-form").addEventListener("submit", event => {
        event.preventDefault();
        loadWeather();
    });
}

export function enterWeatherPage() {
    if (!weatherLoaded)
        loadWeather();
}

async function loadWeather() {
    const form = byId("weather-search-form");
    const button = form.querySelector('button[type="submit"]');

    const summary = byId("weather-summary");
    const cards = byId("weather-daily-cards");
    const warning = byId("weather-warning");

    try {
        const parameters = getSearchParameters("weather");

        setButtonLoading(button, true, "天氣載入中...");
        hideMessage();

        warning.hidden = true;
        clearElement(summary);
        showEmpty(cards, "正在取得中央氣象署天氣資料...");

        const data = await apiGet(API.weather, parameters);

        if (!data || !Array.isArray(data.dailyForecasts)) {
            throw new Error("天氣資料暫時無法顯示，請稍後重新查詢");
        }

        renderWeatherSummary(data);
        // 七天圖卡直接使用同一次 API 的每日資料與濕度摘要。
        renderSevenDayWeatherCards(data);
        renderWeatherCharts(data);

        // 顯示後端回傳的資料完整性警告。
        if (data.warning) {
            warning.textContent = data.warning;
            warning.hidden = false;
        }
        else if (data.forecastStatus === "UNAVAILABLE") {
            warning.textContent = "目前無法取得完整天氣預報";
            warning.hidden = false;
        }
        else if (data.forecastStatus === "PARTIAL") {
            warning.textContent = "部分天氣資料不足，請留意預報完整性";
            warning.hidden = false;
        }

        weatherLoaded = true;
    }
    catch (error) {
        weatherLoaded = false;

        showEmpty(cards, "無法載入天氣預報");
        clearElement(summary);

        showEmpty(byId("temperature-chart"), "尚無可顯示的溫度資料");

        showEmpty(byId("precipitation-chart"), "尚無可顯示的降雨資料");

        showMessage(error.message, "error");

        console.error("Weather API 錯誤：", error);
    }
    finally {
        setButtonLoading(button, false);
    }
}

function renderWeatherSummary(data) {
    const container = byId("weather-summary");
    clearElement(container);

    const maxTemperature = data.maxTemperature;
    const precipitation = data.maxPrecipitationProbability;
    const factor = data.weatherFactor;

    container.appendChild(createStatCard("預報最高溫", maxTemperature == null
        ? "--"
        : `${formatNumber(maxTemperature)}°C`, "查詢期間"));

    container.appendChild(createStatCard("最高降雨機率", precipitation == null
        ? "--"
        : `${formatNumber(precipitation)}%`, "查詢期間"));

    container.appendChild(createStatCard("天氣需求倍率", factor == null
        ? "--"
        : `${Number(factor).toFixed(2)} ×`, displayValue(data.forecastStatus)));
}

function formatChartTime(value) {
    if (!value)
        return "--";

    // ISO 日期時間：取月/日與小時。
    const match = String(value).match(/(\d{2})-(\d{2})T(\d{2}):/);

    return match
        ? `${match[1]}/${match[2]} ${match[3]}時`
        : String(value).slice(0, 16);
}

function renderLineChart(container, items, color, suffix) {
    clearElement(container);

    const points = items
        .map(item => ({
        label: item.label,
        value: item.value == null
            ? null
            : Number(item.value)
    }))
        .filter(item => item.value !== null &&
        Number.isFinite(item.value));

    if (points.length === 0) {
        showEmpty(container, "目前沒有可顯示的圖表資料");
        return;
    }

    // 七天可能含大量時段資料；限制最大寬度，避免數千像素橫向捲軸。
    const width = Math.max(600, Math.min(1200, points.length * 18));
    const height = 230;
    const padding = 40;

    const values = points.map(point => point.value);

    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = Math.max(max - min, 1);

    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");

    svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
    svg.setAttribute("width", width);
    svg.setAttribute("height", height);
    svg.setAttribute("role", "img");
    svg.setAttribute("aria-label", "天氣數值變化圖表");

    const coordinates = points.map((point, index) => {
        const x = padding +
            index * (width - padding * 2) /
                Math.max(points.length - 1, 1);

        const y = height - padding -
            (point.value - min) / range *
                (height - padding * 2);

        return { x, y, ...point };
    });

    // 繪製資料折線。
    const polyline = document.createElementNS("http://www.w3.org/2000/svg", "polyline");

    polyline.setAttribute("points", coordinates.map(p => `${p.x},${p.y}`).join(" "));

    polyline.setAttribute("fill", "none");
    polyline.setAttribute("stroke", color);
    polyline.setAttribute("stroke-width", "3");
    polyline.setAttribute("stroke-linejoin", "round");
    polyline.setAttribute("stroke-linecap", "round");

    svg.appendChild(polyline);

    coordinates.forEach((point, index) => {
        const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");

        circle.setAttribute("cx", point.x);
        circle.setAttribute("cy", point.y);
        circle.setAttribute("r", "4");
        circle.setAttribute("fill", color);

        const tooltip = document.createElementNS("http://www.w3.org/2000/svg", "title");

        tooltip.textContent =
            `${point.label}：${point.value}${suffix}`;

        circle.appendChild(tooltip);
        svg.appendChild(circle);

        // 控制標籤數量，避免三天每小時資料擠在一起。
        const labelInterval = Math.max(1, Math.ceil(points.length / 7));

        if (index % labelInterval === 0 ||
            index === points.length - 1) {
            const label = document.createElementNS("http://www.w3.org/2000/svg", "text");

            label.setAttribute("x", point.x);
            label.setAttribute("y", height - 10);
            label.setAttribute("font-size", "10");
            label.setAttribute("text-anchor", "middle");
            label.setAttribute("fill", "#80685c");

            label.textContent = point.label;

            svg.appendChild(label);
        }
    });

    container.appendChild(svg);
}

function renderWeatherCharts(data) {
    const temperature = Array.isArray(data.temperatureForecasts)
        ? data.temperatureForecasts
        : [];

    const precipitation = Array.isArray(data.precipitationForecasts)
        ? data.precipitationForecasts
        : [];

    renderLineChart(byId("temperature-chart"), temperature.map(item => ({
        label: formatChartTime(item.dataTime),
        value: item.temperature
    })), "#c96522", "°C");

    renderLineChart(byId("precipitation-chart"), precipitation.map(item => ({
        label: formatChartTime(item.startTime),
        value: item.precipitationProbability
    })), "#5d9dc7", "%");
}

// ===== 每日預報圖卡（原獨立視圖模組） =====
/* =========================================================
   七天天氣卡片渲染模組
   - 僅負責把「既有天氣 API 的同一份回應」呈現成每日卡片。
   - 不另行呼叫 API，不改日期查詢、不改訂貨建議計算。
   - 與 weather.js 放在同一模組內，取得 data 後呼叫。
   - 注意：正式整合前仍需比對使用者本機最新版 weather.js。
   ========================================================= */
/**
 * 轉換 API 的數字欄位。null／空字串代表沒有資料，絕對不可視為 0。
 * 為了防止顯示 NaN，只有有限數值才算有效。
 */
function readNumber(value) {
    if (value === null || value === undefined || value === "") {
        return null;
    }

    const number = Number(value);
    return Number.isFinite(number) ? number : null;
}

/**
 * 百分比只接受 0～100，真正的 0% 是有效數值。
 */
function readPercentage(value) {
    const number = readNumber(value);
    return number !== null && number >= 0 && number <= 100
        ? number
        : null;
}

/**
 * 使用台灣時間取今天日期；不以瀏覽器所在地推算天氣日期。
 */
function getTaiwanToday() {
    const parts = new Intl.DateTimeFormat("en-US", {
        timeZone: "Asia/Taipei",
        year: "numeric",
        month: "2-digit",
        day: "2-digit"
    }).formatToParts(new Date());

    const field = type => parts.find(part => part.type === type)?.value;
    return `${field("year")}-${field("month")}-${field("day")}`;
}

/**
 * 統一日期顯示，例如 2026-11-22 -> 11/22（日）。
 */
function formatDay(dateValue) {
    const iso = typeof dateValue === "string"
        ? dateValue.slice(0, 10)
        : "";

    if (!/^\d{4}-\d{2}-\d{2}$/.test(iso)) {
        return { iso: "", label: "預報日期", fullLabel: "預報日期" };
    }

    const date = new Date(`${iso}T12:00:00+08:00`);
    if (Number.isNaN(date.getTime())) {
        return { iso: "", label: "預報日期", fullLabel: "預報日期" };
    }

    const weekday = new Intl.DateTimeFormat("zh-TW", {
        timeZone: "Asia/Taipei",
        weekday: "short"
    }).format(date).replace(/^週|^星期/, "");

    return {
        iso,
        label: `${iso.slice(5, 7)}/${iso.slice(8, 10)}（${weekday}）`,
        fullLabel: `${iso}（星期${weekday}）`
    };
}

/**
 * 以官方天氣描述及代碼選擇圖示。只做顯示，不影響天氣倍率計算。
 */
function getWeatherIcon(code, description) {
    const text = String(description ?? "");
    const numericCode = readNumber(code);

    if (text.includes("雷"))
        return "⛈️";
    if (text.includes("雪"))
        return "❄️";
    if (text.includes("雨") || (numericCode !== null && numericCode >= 8)) {
        return "🌧️";
    }
    if (text.includes("陰") || text.includes("多雲時陰") ||
        numericCode === 6 || numericCode === 7) {
        return "☁️";
    }
    if (text.includes("晴") && text.includes("雲"))
        return "🌤️";
    if (text.includes("晴") || numericCode === 1)
        return "☀️";
    if (text.includes("雲") || (numericCode !== null && numericCode >= 2 && numericCode <= 5)) {
        return "🌤️";
    }
    return "🌥️";
}

/**
 * 檢查 API 時段是否和台灣某個自然日相交。
 * 使用左閉右開 [startTime, endTime) 的規則，避免跨日重複判定邊界。
 */
function overlapsTaiwanDay(period, isoDay) {
    if (!period || !isoDay)
        return false;

    const start = period.startTime ?? period.dataTime;
    const end = period.endTime;
    if (!start)
        return false;

    const dayStart = Date.parse(`${isoDay}T00:00:00+08:00`);
    const dayEnd = dayStart + 24 * 60 * 60 * 1000;
    const startMillis = Date.parse(start);
    if (Number.isNaN(startMillis))
        return false;

    if (end == null) {
        return startMillis >= dayStart && startMillis < dayEnd;
    }

    const endMillis = Date.parse(end);
    return !Number.isNaN(endMillis) && startMillis < dayEnd && endMillis > dayStart;
}

/**
 * 若每日 DTO 尚未含降雨摘要，從同一份 API 回傳的時段降雨資料中
 * 取出該日「有值」的最大值；全部缺失時回傳 null（不是 0）。
 */
function getDayMaxRain(day, data, date) {
    const direct = readPercentage(day.maxPrecipitationProbability ?? day.precipitationProbability);
    if (direct !== null)
        return direct;

    const periods = Array.isArray(data.precipitationForecasts)
        ? data.precipitationForecasts
        : [];

    const values = periods
        .filter(period => overlapsTaiwanDay(period, date))
        .map(period => readPercentage(period.precipitationProbability))
        .filter(value => value !== null);

    return values.length > 0 ? Math.max(...values) : null;
}

/**
 * 讀取「當日平均相對濕度」。
 *
 * 目前後端回應的 dailyForecasts 僅含日期、溫度與天氣現象；
 * 每日平均濕度實際放在 dailyWeatherFactors[].averageRelativeHumidity。
 * 因此優先讀取每日卡片本身欄位（相容未來 DTO 擴充），
 * 若不存在，再依「相同日期」從 dailyWeatherFactors 找資料。
 *
 * 全程只使用同一次天氣 API 回傳，不重算濕度、不更動天氣倍率，
 * 也不拿其他日期資料補值；沒有有效數字時保留 null。
 */
function getDayHumidity(day, data, date) {
    const direct = readPercentage(day.averageRelativeHumidity ?? day.relativeHumidity ?? day.avgRelativeHumidity);

    if (direct !== null)
        return direct;

    const dailyFactors = Array.isArray(data?.dailyWeatherFactors)
        ? data.dailyWeatherFactors
        : [];

    const matched = dailyFactors.find(item => String(item?.date ?? "").slice(0, 10) === date);

    return readPercentage(matched?.averageRelativeHumidity);
}

function makeElement(tagName, className, value) {
    const element = document.createElement(tagName);
    if (className)
        element.className = className;
    if (value !== undefined && value !== null)
        element.textContent = String(value);
    return element;
}

function addMetric(container, label, value) {
    const row = makeElement("div", "weather-card-metric");
    row.appendChild(makeElement("span", "weather-card-metric-label", label));

    const result = makeElement("strong", `weather-card-metric-value${value === null ? " is-unavailable" : ""}`, value === null ? "未提供" : `${Number(value).toLocaleString("zh-TW")}%`);

    row.appendChild(result);
    container.appendChild(row);
}

/**
 * 新版每日圖卡。傳入 loadWeather() 已取得的「完整 API 回傳 data」。
 * 每筆 dailyForecasts 對應一張卡，使用者查詢 3 天就顯示 3 張，
 * 查詢 7 天就顯示 7 張，不強制產生不存在的預報。
 */
export function renderSevenDayWeatherCards(data) {
    const container = document.getElementById("weather-daily-cards");
    if (!container)
        return;
    container.replaceChildren();

    const days = Array.isArray(data?.dailyForecasts) ? data.dailyForecasts : [];
    if (days.length === 0) {
        container.appendChild(makeElement("p", "empty-state", "目前沒有每日天氣預報資料"));
        return;
    }

    const today = getTaiwanToday();
    const fragment = document.createDocumentFragment();

    days.forEach((day, index) => {
        const date = formatDay(day.date ?? day.forecastDate);
        const isToday = date.iso === today;
        const weather = day.weather || "未提供天氣描述";
        const max = readNumber(day.maxTemperature);
        const min = readNumber(day.minTemperature);
        const rain = getDayMaxRain(day, data, date.iso);
        const humidity = getDayHumidity(day, data, date.iso);

        const card = makeElement("article", `weather-card${isToday ? " is-today" : ""}`);
        card.setAttribute("aria-label", `${date.fullLabel}，${weather}`);

        const header = makeElement("div", "weather-card-header");
        header.appendChild(makeElement("h4", "weather-card-date", date.label));
        header.appendChild(makeElement("span", "weather-card-day-index", isToday ? "今天" : `第 ${index + 1} 天`));
        card.appendChild(header);

        const condition = makeElement("div", "weather-card-condition");
        const icon = makeElement("span", "weather-icon", getWeatherIcon(day.weatherCode, weather));
        icon.setAttribute("aria-hidden", "true");
        condition.appendChild(icon);
        condition.appendChild(makeElement("div", "weather-description", weather));
        card.appendChild(condition);

        card.appendChild(makeElement("span", "weather-temperature-label", "最高／最低氣溫"));
        card.appendChild(makeElement("div", "weather-temperature", `${max === null ? "--" : max}° / ${min === null ? "--" : min}°C`));

        const metrics = makeElement("div", "weather-card-metrics");
        addMetric(metrics, "最高降雨機率", rain);
        addMetric(metrics, "平均相對濕度", humidity);
        card.appendChild(metrics);
        fragment.appendChild(card);
    });

    container.appendChild(fragment);
}
