/* 天氣模組：由原 app.js 分離，保留既有功能。 */
import { API, byId, makeElement, clearElement, showEmpty, showMessage, hideMessage, setButtonLoading, displayValue, formatNumber, getSearchParameters, apiGet, createStatCard } from "./common.js";

let weatherLoaded = false;

export function initWeather() {
    byId("weather-search-form").addEventListener("submit", event => {
        event.preventDefault();
        loadWeather();
    });
}

export function enterWeatherPage() {
    if (!weatherLoaded) loadWeather();
}

function getWeatherIcon(code, description = "") {
    const weatherCode = String(code ?? "").padStart(2, "0");
    const text = String(description);

    if (text.includes("雷")) return "⛈️";
    if (text.includes("雪")) return "❄️";

    if (text.includes("雨") ||
        ["08", "09", "10", "11", "12",
         "13", "14", "15", "16", "17",
         "18", "19", "20", "21", "22"].includes(weatherCode)) {
        return "🌧️";
    }

    if (weatherCode === "01" || text === "晴") {
        return "☀️";
    }

    if (weatherCode === "02" ||
        weatherCode === "03" ||
        text.includes("晴時多雲")) {
        return "🌤️";
    }

    return "☁️";
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
            throw new Error("天氣 API 回傳格式不正確");
        }

        renderWeatherSummary(data);
        renderDailyWeather(data.dailyForecasts);
        renderWeatherCharts(data);

        // 顯示後端回傳的資料完整性警告。
        if (data.warning) {
            warning.textContent = data.warning;
            warning.hidden = false;
        } else if (data.forecastStatus === "UNAVAILABLE") {
            warning.textContent = "目前無法取得完整天氣預報";
            warning.hidden = false;
        } else if (data.forecastStatus === "PARTIAL") {
            warning.textContent = "部分天氣資料不足，請留意預報完整性";
            warning.hidden = false;
        }

        weatherLoaded = true;

    } catch (error) {
        weatherLoaded = false;

        showEmpty(cards, "無法載入天氣預報");
        clearElement(summary);

        showEmpty(
            byId("temperature-chart"),
            "尚無可顯示的溫度資料"
        );

        showEmpty(
            byId("precipitation-chart"),
            "尚無可顯示的降雨資料"
        );

        showMessage(error.message, "error");

        console.error("Weather API 錯誤：", error);

    } finally {
        setButtonLoading(button, false);
    }
}

function renderWeatherSummary(data) {
    const container = byId("weather-summary");
    clearElement(container);

    const maxTemperature = data.maxTemperature;
    const precipitation = data.maxPrecipitationProbability;
    const factor = data.weatherFactor;

    container.appendChild(
        createStatCard(
            "預報最高溫",
            maxTemperature == null
                ? "--"
                : `${formatNumber(maxTemperature)}°C`,
            "查詢期間"
        )
    );

    container.appendChild(
        createStatCard(
            "最高降雨機率",
            precipitation == null
                ? "--"
                : `${formatNumber(precipitation)}%`,
            "查詢期間"
        )
    );

    container.appendChild(
        createStatCard(
            "天氣需求倍率",
            factor == null
                ? "--"
                : `${Number(factor).toFixed(2)} ×`,
            displayValue(data.forecastStatus)
        )
    );
}

function renderDailyWeather(dailyForecasts) {
    const container = byId("weather-daily-cards");
    clearElement(container);

    if (dailyForecasts.length === 0) {
        showEmpty(container, "目前沒有每日天氣資料");
        return;
    }

    dailyForecasts.forEach(day => {
        const card = makeElement("article", null, "weather-card");

        // 兼容每日 DTO 常見日期命名。
        const date = day.date ?? day.forecastDate;

        const weather = day.weather ?? "";
        const icon = getWeatherIcon(day.weatherCode, weather);

        const max = day.maxTemperature;
        const min = day.minTemperature;

        card.appendChild(
            makeElement("h4", displayValue(date, "預報日期"))
        );

        card.appendChild(
            makeElement("div", icon, "weather-icon")
        );

        card.appendChild(
            makeElement(
                "div",
                `${displayValue(max)}° / ${displayValue(min)}°`,
                "weather-temperature"
            )
        );

        card.appendChild(
            makeElement(
                "div",
                displayValue(weather, "無天氣描述"),
                "weather-description"
            )
        );

        container.appendChild(card);
    });
}

function formatChartTime(value) {
    if (!value) return "--";

    // ISO 日期時間：取月/日與小時。
    const match = String(value).match(
        /(\d{2})-(\d{2})T(\d{2}):/
    );

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
        .filter(item =>
            item.value !== null &&
            Number.isFinite(item.value)
        );

    if (points.length === 0) {
        showEmpty(container, "目前沒有可顯示的圖表資料");
        return;
    }

    const width = Math.max(600, points.length * 55);
    const height = 230;
    const padding = 40;

    const values = points.map(point => point.value);

    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = Math.max(max - min, 1);

    const svg = document.createElementNS(
        "http://www.w3.org/2000/svg",
        "svg"
    );

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
    const polyline = document.createElementNS(
        "http://www.w3.org/2000/svg",
        "polyline"
    );

    polyline.setAttribute(
        "points",
        coordinates.map(p => `${p.x},${p.y}`).join(" ")
    );

    polyline.setAttribute("fill", "none");
    polyline.setAttribute("stroke", color);
    polyline.setAttribute("stroke-width", "3");
    polyline.setAttribute("stroke-linejoin", "round");
    polyline.setAttribute("stroke-linecap", "round");

    svg.appendChild(polyline);

    coordinates.forEach((point, index) => {
        const circle = document.createElementNS(
            "http://www.w3.org/2000/svg",
            "circle"
        );

        circle.setAttribute("cx", point.x);
        circle.setAttribute("cy", point.y);
        circle.setAttribute("r", "4");
        circle.setAttribute("fill", color);

        const tooltip = document.createElementNS(
            "http://www.w3.org/2000/svg",
            "title"
        );

        tooltip.textContent =
            `${point.label}：${point.value}${suffix}`;

        circle.appendChild(tooltip);
        svg.appendChild(circle);

        // 控制標籤數量，避免三天每小時資料擠在一起。
        const labelInterval = Math.max(
            1,
            Math.ceil(points.length / 7)
        );

        if (index % labelInterval === 0 ||
            index === points.length - 1) {

            const label = document.createElementNS(
                "http://www.w3.org/2000/svg",
                "text"
            );

            label.setAttribute("x", point.x);
            label.setAttribute("y", height - 10);
            label.setAttribute("font-size", "10");
            label.setAttribute("text-anchor", "middle");
            label.setAttribute("fill", "#64748b");

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

    renderLineChart(
        byId("temperature-chart"),
        temperature.map(item => ({
            label: formatChartTime(item.dataTime),
            value: item.temperature
        })),
        "#2563eb",
        "°C"
    );

    renderLineChart(
        byId("precipitation-chart"),
        precipitation.map(item => ({
            label: formatChartTime(item.startTime),
            value: item.precipitationProbability
        })),
        "#0891b2",
        "%"
    );
}
