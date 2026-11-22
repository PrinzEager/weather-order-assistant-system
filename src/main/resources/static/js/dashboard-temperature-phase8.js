"use strict";

/**
 * Phase 8｜首頁「未來 48 小時氣溫趨勢」。
 *
 * 1. 只使用 dashboard-overview.js 已經取得的 /api/weather/forecast 回傳資料。
 * 2. 只畫出 temperatureForecasts 真正提供的時間點，不插補任何逐小時溫度。
 * 3. 依時間區間 [startTime, endTime) 配對天氣現象與降雨機率。
 * 4. null 降雨機率為「未提供」，不可視為 0%。
 * 5. 不變更 WeatherService、訂貨倍率、商品或訂單資料。
 */
const SVG_NS = "http://www.w3.org/2000/svg";
const HORIZON_MS = 48 * 60 * 60 * 1000;

function svgNode(tag, attrs = {}, text = null) {
    const node = document.createElementNS(SVG_NS, tag);
    for (const [key, value] of Object.entries(attrs)) {
        node.setAttribute(key, String(value));
    }
    if (text !== null) node.textContent = String(text);
    return node;
}

function validTemperature(value) {
    if (value === null || value === undefined || value === "") return null;
    const num = Number(value);
    // API 回傳攝氏溫度；保留合理的非整數值，不自行補造。
    return Number.isFinite(num) ? num : null;
}

function validRain(value) {
    if (value === null || value === undefined || value === "") return null;
    const num = Number(value);
    return Number.isFinite(num) && num >= 0 && num <= 100 ? num : null;
}

function timeInsideInterval(timeMs, period) {
    if (!period || !period.startTime || !period.endTime) return false;
    const start = Date.parse(period.startTime);
    const end = Date.parse(period.endTime);
    return Number.isFinite(start) && Number.isFinite(end) &&
        timeMs >= start && timeMs < end;
}

function weatherIcon(code, description = "") {
    const c = String(code ?? "").padStart(2, "0");
    const text = String(description ?? "");
    if (text.includes("雷")) return "⛈️";
    if (text.includes("雪")) return "❄️";
    if (text.includes("雨") || (Number.isFinite(Number(c)) && Number(c) >= 8)) return "🌧️";
    if (c === "01" || text === "晴") return "☀️";
    if (c === "02" || c === "03" || text.includes("晴")) return "🌤️";
    if (c === "04" || c === "05" || c === "06" || text.includes("雲") || text.includes("陰")) return "☁️";
    return "◌"; // 沒有對應現象時使用中性圖示，避免誤判晴雨。
}

/**
 * 將 API 回傳整理成可繪圖的資料點。
 * nowMs 可由測試指定；正式使用時採瀏覽器的現在時間。
 *
 * 同一個時間點若重複回傳，僅保留第一筆有效溫度；
 * 不補值、不重新計算雨量與天氣倍率。
 */
export function buildDashboardTemperaturePoints(data, nowMs = Date.now()) {
    const temperatures = Array.isArray(data?.temperatureForecasts)
        ? data.temperatureForecasts : [];
    const conditions = Array.isArray(data?.weatherConditions)
        ? data.weatherConditions : [];
    const rains = Array.isArray(data?.precipitationForecasts)
        ? data.precipitationForecasts : [];
    const found = new Set();
    const points = [];

    for (const item of temperatures) {
        const timeMs = Date.parse(item?.dataTime);
        const temp = validTemperature(item?.temperature);
        if (!Number.isFinite(timeMs) || temp === null ||
            timeMs < nowMs || timeMs > nowMs + HORIZON_MS || found.has(timeMs)) {
            continue;
        }
        found.add(timeMs);
        const condition = conditions.find(p => timeInsideInterval(timeMs, p));
        const rainPeriod = rains.find(p => timeInsideInterval(timeMs, p));
        points.push({
            timeMs,
            temperature: temp,
            weather: condition?.weather ?? null,
            weatherCode: condition?.weatherCode ?? null,
            rain: validRain(rainPeriod?.precipitationProbability)
        });
    }
    return points.sort((a, b) => a.timeMs - b.timeMs);
}

function formatTime(ms) {
    // 官方 ISO 含 +08:00；仍指定 Asia/Taipei，避免瀏覽器所在時區影響顯示。
    const p = new Intl.DateTimeFormat("zh-TW", {
        timeZone: "Asia/Taipei", month: "2-digit", day: "2-digit",
        hour: "2-digit", hourCycle: "h23"
    }).formatToParts(new Date(ms));
    const fields = Object.fromEntries(p.map(part => [part.type, part.value]));
    return `${Number(fields.month)}/${Number(fields.day)} ${fields.hour}時`;
}

function temperatureLabel(value) {
    return `${Number(value.toFixed(1)).toLocaleString("zh-TW")}°`;
}

export function setDashboardTemperatureState(message, isError = false) {
    const container = document.getElementById("dashboard-temperature-timeline");
    if (!container) return;
    const p = document.createElement("p");
    p.className = isError
        ? "dashboard-temperature-message dashboard-temperature-error"
        : "dashboard-temperature-message";
    p.textContent = message;
    container.replaceChildren(p);
    container.setAttribute("aria-busy", isError ? "false" : "true");
    // 更新狀態時同步清除上一輪備註，避免資料重新整理期間顯示舊時間點數量。
    const note = document.getElementById("dashboard-temperature-note");
    if (note) note.textContent = isError
        ? "目前無法繪製氣溫趨勢，請稍後重新整理天氣摘要。"
        : "正在讀取未來 48 小時內的實際預報時間點...";
}

export function renderDashboardTemperatureTimeline(data) {
    const container = document.getElementById("dashboard-temperature-timeline");
    const note = document.getElementById("dashboard-temperature-note");
    if (!container) return;

    // 一次性使用這份已抓取的 API 結果；無額外 fetch。
    const points = buildDashboardTemperaturePoints(data);
    container.replaceChildren();
    container.setAttribute("aria-busy", "false");

    if (points.length === 0) {
        setDashboardTemperatureState("未來 48 小時內沒有可繪製的氣溫時間點", true);
        if (note) note.textContent = "目前 API 沒有此時段的有效溫度資料；不會以假資料補線。";
        return;
    }

    const W = Math.max(520, 130 * (points.length - 1) + 120);
    const H = 327, left = 67, right = W - 60;
    const minT = points[0].timeMs;
    const maxT = points[points.length - 1].timeMs;
    const span = maxT - minT;
    const x = (point, index) => span === 0
        ? (left + right) / 2
        : left + ((point.timeMs - minT) / span) * (right - left);
    const temps = points.map(p => p.temperature);
    const lowest = Math.min(...temps) - 2;
    const highest = Math.max(...temps) + 2;
    const y = temp => 282 - ((temp - lowest) / (highest - lowest)) * 78;

    const svg = svgNode("svg", {
        viewBox: `0 0 ${W} ${H}`, width: W, height: H,
        role: "img", "aria-label": "未來 48 小時的預報時間點、天氣圖示、降雨機率與氣溫折線圖"
    });
    svg.appendChild(svgNode("title", {}, "未來 48 小時氣溫趨勢"));
    svg.appendChild(svgNode("desc", {}, points.map(p =>
        `${formatTime(p.timeMs)}，${p.weather || "天氣現象未提供"}，` +
        `${temperatureLabel(p.temperature)}C，降雨機率${p.rain === null ? "未提供" : `${p.rain}%`}`
    ).join("；")));

    // 淡色分隔線與時間軸，讓圖表閱讀順序貼近使用者提供的參考截圖。
    svg.appendChild(svgNode("line", {
        x1: 22, x2: W - 22, y1: 39, y2: 39,
        stroke: "#EAD8C9", "stroke-width": 1
    }));
    svg.appendChild(svgNode("line", {
        x1: 22, x2: W - 22, y1: 176, y2: 176,
        stroke: "#F2E4D7", "stroke-width": 1, "stroke-dasharray": "4 6"
    }));

    // 按預報時間差繪製折線；並非把不同間隔誤畫成逐小時距離。
    if (points.length > 1) {
        svg.appendChild(svgNode("polyline", {
            points: points.map((p, i) => `${x(p, i)},${y(p.temperature)}`).join(" "),
            fill: "none", stroke: "#DA8736", "stroke-width": 3.5,
            "stroke-linecap": "round", "stroke-linejoin": "round"
        }));
    }

    points.forEach((point, index) => {
        const px = x(point, index);
        svg.appendChild(svgNode("text", {
            x: px, y: 27, "text-anchor": "middle", "font-size": 13,
            fill: "#714C38", "font-weight": 700
        }, formatTime(point.timeMs)));
        svg.appendChild(svgNode("text", {
            x: px, y: 95, "text-anchor": "middle", "font-size": 30
        }, weatherIcon(point.weatherCode, point.weather)));
        svg.appendChild(svgNode("text", {
            x: px, y: 130, "text-anchor": "middle", "font-size": 12,
            fill: "#82675A"
        }, point.weather || "現象未提供"));
        svg.appendChild(svgNode("text", {
            x: px, y: 158, "text-anchor": "middle", "font-size": 12,
            fill: "#6E7282"
        }, `☂ ${point.rain === null ? "未提供" : `${point.rain}%`}`));
        const circle = svgNode("circle", {
            cx: px, cy: y(point.temperature), r: 5.5,
            fill: "#FFFCF5", stroke: "#CC6B2F", "stroke-width": 3
        });
        circle.appendChild(svgNode("title", {},
            `${formatTime(point.timeMs)} 氣溫 ${temperatureLabel(point.temperature)}`));
        svg.appendChild(circle);
        svg.appendChild(svgNode("text", {
            x: px, y: y(point.temperature) - 14,
            "text-anchor": "middle", "font-size": 18,
            "font-weight": 800, fill: "#623D2A"
        }, temperatureLabel(point.temperature)));
    });

    const scroller = document.createElement("div");
    scroller.className = "dashboard-temperature-scroller";
    scroller.setAttribute("role", "region");
    scroller.setAttribute("aria-label", "可左右捲動查看各個預報時段的氣溫趨勢");
    scroller.setAttribute("tabindex", "0");
    scroller.appendChild(svg);
    container.appendChild(scroller);
    if (note) {
        note.textContent = `已顯示 ${points.length} 個真實預報時間點；` +
            "時間為臺灣時間；可左右滑動查看時段。缺少降雨資料顯示「未提供」，未進行逐小時插值。";
    }
}
