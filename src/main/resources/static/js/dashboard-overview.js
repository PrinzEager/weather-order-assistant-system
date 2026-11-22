"use strict";

/**
 * 儀表板七天天氣概覽、近七日銷售趨勢。
 *
 * 使用已有的 REST API，不更動訂貨倍率、銷售、退貨或採購規則。
 * 不修改 weather.js（完整天氣頁）、sales-record.js（銷售 CRUD）與 app.js。
 */
import {
    API, apiGet, requestJson, getTaiwanToday, addDays
} from "./common.js";
// 天氣趨勢與每日圖卡共用同一份 API 回應，不增加 HTTP 呼叫。


/* 48 小時氣溫圖元件：局部命名空間，避免與首頁天氣圖卡共用名稱衝突。 */
const { renderDashboardTemperatureTimeline, setDashboardTemperatureState, buildDashboardTemperaturePoints } = (() => {
"use strict";

/**
 * 首頁「未來 48 小時氣溫趨勢」。
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
function buildDashboardTemperaturePoints(data, nowMs = Date.now()) {
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

function setDashboardTemperatureState(message, isError = false) {
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

function renderDashboardTemperatureTimeline(data) {
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

return { renderDashboardTemperatureTimeline, setDashboardTemperatureState, buildDashboardTemperaturePoints };
})();
export { buildDashboardTemperaturePoints };

const byId = id => document.getElementById(id);
const SVG_NS = "http://www.w3.org/2000/svg";
const WEEKDAYS = ["日", "一", "二", "三", "四", "五", "六"];

// 使用者切回儀表板時重新取得最新資料；同一時間的請求不重複送出。
let weatherRequest = null;
let salesRequest = null;

function create(tag, text, className) {
    const element = document.createElement(tag);
    if (text !== undefined && text !== null) element.textContent = String(text);
    if (className) element.className = className;
    return element;
}

function renderState(container, message, className = "dashboard-widget-empty") {
    container.replaceChildren(create("p", message, className));
}

// 日期以 Asia/Taipei 的日曆日處理，避免 UTC 換日造成資料歸屬錯誤。
function dateLabel(iso) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(String(iso))) return "日期未提供";
    const d = new Date(`${iso}T12:00:00+08:00`);
    if (!Number.isFinite(d.getTime())) return "日期未提供";
    const weekday = WEEKDAYS[d.getUTCDay()];
    return `${Number(iso.slice(5, 7))}/${Number(iso.slice(8, 10))}（${weekday}）`;
}

function percent(value) {
    // null 是缺值；0 才是有效的 0%，兩者不可混淆。
    if (value === null || value === undefined || value === "") return "未提供";
    const n = Number(value);
    return Number.isFinite(n) && n >= 0 && n <= 100
        ? `${Math.round(n)}%` : "未提供";
}

function temperature(value) {
    if (value === null || value === undefined || value === "") return "--";
    const n = Number(value);
    return Number.isFinite(n) ? `${Math.round(n)}°` : "--";
}

function weatherIcon(code, description = "") {
    const c = String(code ?? "").padStart(2, "0");
    const text = String(description);
    if (text.includes("雷")) return "⛈️";
    if (text.includes("雪")) return "❄️";
    if (text.includes("雨") || (/^\d+$/.test(c) && Number(c) >= 8)) return "🌧️";
    if (c === "01" || text === "晴") return "☀️";
    if (["02", "03"].includes(c) || text.includes("晴")) return "🌤️";
    return "☁️";
}

function todayRange() {
    const today = getTaiwanToday();
    return {
        startDate: today,
        endDate: addDays(today, 6)
    };
}

function getSelectedLocation() {
    // 直接讀取天氣頁已初始化的行政區選單，不建立第二套地點資料。
    const city = byId("weather-city")?.value?.trim() || "高雄市";
    const district = byId("weather-district")?.value?.trim() || "路竹區";
    return { city, district };
}

async function loadDashboardWeather() {
    if (weatherRequest) return weatherRequest;

    const cards = byId("dashboard-weather-cards");
    const status = byId("dashboard-weather-status");
    const locationText = byId("dashboard-weather-location");
    const warning = byId("dashboard-weather-warning");
    const refresh = byId("dashboard-weather-refresh");
    if (!cards || !status || !locationText || !warning || !refresh) return;

    const location = getSelectedLocation();
    const range = todayRange();
    locationText.textContent = `${location.city} · ${location.district}`;
    warning.hidden = true;
    status.textContent = "載入中";
    status.removeAttribute("data-status");
    cards.setAttribute("aria-busy", "true");
    refresh.disabled = true;
    renderState(cards, "正在載入中央氣象署七天天氣...", "dashboard-widget-loading");
    setDashboardTemperatureState("正在載入氣溫趨勢...");

    weatherRequest = (async () => {
        try {
            const data = await apiGet(API.weather, { ...location, ...range });
            if (!data || !Array.isArray(data.dailyForecasts)) {
                throw new Error("天氣 API 回傳格式不正確");
            }
            locationText.textContent = `${data.city || location.city} · ${data.district || location.district}`;
            const forecastStatus = String(data.forecastStatus || "");
            const label = {
                COMPLETE: "資料完整", PARTIAL: "部分資料", UNAVAILABLE: "資料不可用"
            }[forecastStatus] || "預報資訊";
            status.textContent = label;
            status.dataset.status = forecastStatus;
            renderWeatherCards(data, cards);
            // 使用上述同一份 data：每日摘要與時段氣溫必須來自相同查詢期間與地點。
            renderDashboardTemperatureTimeline(data);
            if (data.warning || forecastStatus === "PARTIAL" || forecastStatus === "UNAVAILABLE") {
                warning.textContent = data.warning || "預報資料可能不完整，請以完整天氣頁為準。";
                warning.hidden = false;
            }
        } catch (error) {
            status.textContent = "讀取失敗";
            status.dataset.status = "UNAVAILABLE";
            renderState(cards, `天氣載入失敗：${error.message}`, "dashboard-widget-error");
            setDashboardTemperatureState("天氣 API 讀取失敗，無法顯示氣溫趨勢", true);
            console.error("儀表板天氣摘要載入失敗：", error);
        } finally {
            cards.setAttribute("aria-busy", "false");
            refresh.disabled = false;
            weatherRequest = null;
        }
    })();
    return weatherRequest;
}

function renderWeatherCards(data, container) {
    container.replaceChildren();
    if (data.dailyForecasts.length === 0) {
        renderState(container, "目前沒有可用的每日天氣預報");
        return;
    }

    // 後端已依有效預報時段計算每日平均濕度、最高降雨，直接以日期配對。
    // 不要把全期間 weatherFactor 或單一時段濕度誤用成每日平均濕度。
    const factors = new Map(
        (Array.isArray(data.dailyWeatherFactors) ? data.dailyWeatherFactors : [])
            .filter(day => day?.date)
            .map(day => [String(day.date), day])
    );

    const days = data.dailyForecasts.slice().sort(
        (a, b) => String(a.date || a.forecastDate).localeCompare(String(b.date || b.forecastDate))
    );

    for (const day of days) {
        const date = String(day.date ?? day.forecastDate ?? "");
        const factor = factors.get(date) || {};
        const humidity = day.averageRelativeHumidity ?? factor.averageRelativeHumidity;
        const rain = day.maxPrecipitationProbability ?? factor.maxPrecipitationProbability;
        const weather = day.weather || "天氣未提供";
        const card = create("article", null, "dashboard-mini-weather");
        card.append(
            create("div", dateLabel(date), "dashboard-mini-date"),
            create("div", weatherIcon(day.weatherCode, weather), "dashboard-mini-icon"),
            create("div", weather, "dashboard-mini-description"),
            create("div", `${temperature(day.maxTemperature)} / ${temperature(day.minTemperature)}`, "dashboard-mini-temperature")
        );
        const metrics = create("div", null, "dashboard-mini-metrics");
        metrics.append(
            create("span", `降雨：${percent(rain)}`),
            create("span", `平均濕度：${percent(humidity)}`)
        );
        card.appendChild(metrics);
        card.setAttribute("aria-label", `${dateLabel(date)} ${weather}，高溫${temperature(day.maxTemperature)}、低溫${temperature(day.minTemperature)}，降雨機率${percent(rain)}，平均相對濕度${percent(humidity)}`);
        container.appendChild(card);
    }
}

function money(amount) {
    return `NT$ ${amount.toLocaleString("zh-TW", { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
}

function axisAmount(amount) {
    if (amount >= 10000) return `${Number((amount / 10000).toFixed(1))}萬`;
    return Math.round(amount).toLocaleString("zh-TW");
}

function lastSevenDays(today) {
    return Array.from({ length: 7 }, (_, index) => addDays(today, index - 6));
}

function aggregateSales(records, dates) {
    const byDate = new Map(dates.map(date => [date, { date, quantity: 0, cents: 0 }]));
    let invalidCount = 0;
    for (const record of records) {
        // 後端 LocalDateTime 沒有時區尾綴；取 YYYY-MM-DD，按台灣業務日期彙總。
        const date = String(record?.dateTime ?? "").slice(0, 10);
        const row = byDate.get(date);
        if (!row) continue;
        const quantity = Number(record?.quantity);
        const amount = Number(record?.totalAmount);
        if (!Number.isInteger(quantity) || quantity < 0 || !Number.isFinite(amount) || amount < 0) {
            invalidCount++;
            continue;
        }
        row.quantity += quantity;
        // 金額以整數分累計，降低 JavaScript 小數加總的浮點誤差。
        row.cents += Math.round(amount * 100);
    }
    const days = dates.map(date => byDate.get(date));
    return {
        days,
        invalidCount,
        totalCents: days.reduce((sum, day) => sum + day.cents, 0),
        totalQuantity: days.reduce((sum, day) => sum + day.quantity, 0)
    };
}

async function loadDashboardSales() {
    if (salesRequest) return salesRequest;
    const container = byId("dashboard-sales-chart");
    const total = byId("dashboard-sales-total");
    const quantity = byId("dashboard-sales-quantity");
    const period = byId("dashboard-sales-period");
    const refresh = byId("dashboard-sales-refresh");
    if (!container || !total || !quantity || !period || !refresh) return;

    refresh.disabled = true;
    container.setAttribute("aria-busy", "true");
    renderState(container, "正在讀取近七日銷售紀錄...", "dashboard-widget-loading");
    const dates = lastSevenDays(getTaiwanToday());
    period.textContent = `${dates[0]} ～ ${dates[6]}（包含起訖日期）`;

    salesRequest = (async () => {
        try {
            const records = await requestJson(API.salesRecords);
            if (!Array.isArray(records)) throw new Error("銷售紀錄 API 回傳格式不正確");
            const result = aggregateSales(records, dates);
            total.textContent = money(result.totalCents / 100);
            quantity.textContent = `${result.totalQuantity.toLocaleString("zh-TW")} 單位`;
            renderSalesChart(container, result.days);
            if (result.invalidCount > 0) {
                period.textContent += `；有 ${result.invalidCount} 筆欄位不完整的紀錄未納入`;
            }
        } catch (error) {
            total.textContent = "--";
            quantity.textContent = "--";
            renderState(container, `銷售資料載入失敗：${error.message}`, "dashboard-widget-error");
            console.error("儀表板銷售趨勢載入失敗：", error);
        } finally {
            container.setAttribute("aria-busy", "false");
            refresh.disabled = false;
            salesRequest = null;
        }
    })();
    return salesRequest;
}

function svgElement(tag, attrs = {}, textValue = null) {
    const el = document.createElementNS(SVG_NS, tag);
    for (const [name, value] of Object.entries(attrs)) el.setAttribute(name, String(value));
    if (textValue !== null) el.textContent = String(textValue);
    return el;
}

function renderSalesChart(container, days) {
    container.replaceChildren();
    if (!days.some(day => day.quantity > 0 || day.cents > 0)) {
        renderState(container, "近七天尚無有效銷售紀錄；沒有可繪製的趨勢");
        return;
    }

    // 雙軸呈現：左軸售出件數、右軸銷售金額；每個日曆日各有一根柱子和一個折線節點。
    const W = 730, H = 300;
    const left = 58, right = 663, top = 25, bottom = 236;
    const plotH = bottom - top;
    const maxQty = Math.max(1, ...days.map(d => d.quantity));
    const maxMoney = Math.max(1, ...days.map(d => d.cents / 100));
    const amountY = cents => bottom - (cents / 100) / maxMoney * plotH;
    const qtyY = qty => bottom - qty / maxQty * plotH;
    const stepX = (right - left) / 7;
    const x = index => left + stepX * (index + 0.5);
    const svg = svgElement("svg", {
        viewBox: `0 0 ${W} ${H}`,
        role: "img",
        "aria-label": "近七日銷售趨勢，柱狀圖為售出數量、橘色折線為銷售金額。"
    });
    svg.appendChild(svgElement("title", {}, "近七日每日銷售數量與銷售紀錄金額"));
    svg.appendChild(svgElement("desc", {}, days.map(d => `${d.date} ${d.quantity} 單位 ${money(d.cents / 100)}`).join("；")));

    for (let tick = 0; tick <= 4; tick++) {
        const fraction = tick / 4;
        const y = bottom - plotH * fraction;
        svg.appendChild(svgElement("line", {
            x1: left, x2: right, y1: y, y2: y,
            stroke: "#EBDDCE", "stroke-width": 1
        }));
        svg.appendChild(svgElement("text", {
            x: left - 11, y: y + 4, "text-anchor": "end", "font-size": 12, fill: "#887469"
        }, Math.round(maxQty * fraction)));
        svg.appendChild(svgElement("text", {
            x: right + 10, y: y + 4, "text-anchor": "start", "font-size": 12, fill: "#887469"
        }, axisAmount(maxMoney * fraction)));
    }
    svg.appendChild(svgElement("text", {
        x: left, y: 15, "font-size": 11, fill: "#8B6A54"
    }, "數量（單位）"));
    svg.appendChild(svgElement("text", {
        x: right + 8, y: 15, "font-size": 11, fill: "#8B6A54"
    }, "金額（NT$）"));

    const linePoints = [];
    days.forEach((day, i) => {
        const center = x(i);
        const barTop = qtyY(day.quantity);
        const rect = svgElement("rect", {
            x: center - 24, y: barTop,
            width: 48, height: Math.max(0, bottom - barTop),
            rx: 7, fill: "#F3BD83"
        });
        rect.appendChild(svgElement("title", {}, `${dateLabel(day.date)} 售出 ${day.quantity} 單位`));
        svg.appendChild(rect);
        linePoints.push(`${center},${amountY(day.cents)}`);
        svg.appendChild(svgElement("text", {
            x: center, y: bottom + 27,
            "text-anchor": "middle", "font-size": 12, fill: "#665347"
        }, day.date.slice(5).replace("-", "/")));
        svg.appendChild(svgElement("text", {
            x: center, y: bottom + 45,
            "text-anchor": "middle", "font-size": 11, fill: "#917D71"
        }, `週${WEEKDAYS[new Date(`${day.date}T12:00:00+08:00`).getUTCDay()]}`));
    });

    svg.appendChild(svgElement("polyline", {
        points: linePoints.join(" "),
        fill: "none", stroke: "#D96828", "stroke-width": 3.2,
        "stroke-linecap": "round", "stroke-linejoin": "round"
    }));
    days.forEach((day, index) => {
        const circle = svgElement("circle", {
            cx: x(index), cy: amountY(day.cents), r: 4.8,
            fill: "#FFF9F0", stroke: "#D96828", "stroke-width": 2.7
        });
        circle.appendChild(svgElement("title", {}, `${dateLabel(day.date)} 銷售紀錄金額 ${money(day.cents / 100)}`));
        svg.appendChild(circle);
    });
    container.appendChild(svg);
}

function loadDashboardWidgets() {
    void loadDashboardWeather();
    void loadDashboardSales();
}

function initializeDashboardOverview() {
    if (!byId("page-dashboard") || !byId("dashboard-weather-cards") || !byId("dashboard-sales-chart")) return;
    byId("dashboard-weather-refresh")?.addEventListener("click", () => void loadDashboardWeather());
    byId("dashboard-sales-refresh")?.addEventListener("click", () => void loadDashboardSales());
    // app.js 負責切頁；這裡僅在回到首頁時更新資訊，避免侵入其導覽程式。
    document.querySelector('[data-page="dashboard"]')?.addEventListener("click", loadDashboardWidgets);
    if (!byId("page-dashboard").hidden) loadDashboardWidgets();
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initializeDashboardOverview, { once: true });
} else {
    initializeDashboardOverview();
}
