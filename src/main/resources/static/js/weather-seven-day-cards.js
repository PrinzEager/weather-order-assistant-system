/* =========================================================
   七天天氣卡片渲染模組
   - 僅負責把「既有天氣 API 的同一份回應」呈現成每日卡片。
   - 不另行呼叫 API，不改日期查詢、不改訂貨建議計算。
   - 由目前專案的 weather.js 在取得 data 後呼叫。
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

    if (text.includes("雷")) return "⛈️";
    if (text.includes("雪")) return "❄️";
    if (text.includes("雨") || (numericCode !== null && numericCode >= 8)) {
        return "🌧️";
    }
    if (text.includes("陰") || text.includes("多雲時陰") ||
        numericCode === 6 || numericCode === 7) {
        return "☁️";
    }
    if (text.includes("晴") && text.includes("雲")) return "🌤️";
    if (text.includes("晴") || numericCode === 1) return "☀️";
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
    if (!period || !isoDay) return false;

    const start = period.startTime ?? period.dataTime;
    const end = period.endTime;
    if (!start) return false;

    const dayStart = Date.parse(`${isoDay}T00:00:00+08:00`);
    const dayEnd = dayStart + 24 * 60 * 60 * 1000;
    const startMillis = Date.parse(start);
    if (Number.isNaN(startMillis)) return false;

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
    const direct = readPercentage(
        day.maxPrecipitationProbability ?? day.precipitationProbability
    );
    if (direct !== null) return direct;

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
    const direct = readPercentage(
        day.averageRelativeHumidity ?? day.relativeHumidity ?? day.avgRelativeHumidity
    );

    if (direct !== null) return direct;

    const dailyFactors = Array.isArray(data?.dailyWeatherFactors)
        ? data.dailyWeatherFactors
        : [];

    const matched = dailyFactors.find(item =>
        String(item?.date ?? "").slice(0, 10) === date
    );

    return readPercentage(matched?.averageRelativeHumidity);
}

function makeElement(tagName, className, value) {
    const element = document.createElement(tagName);
    if (className) element.className = className;
    if (value !== undefined && value !== null) element.textContent = String(value);
    return element;
}

function addMetric(container, label, value) {
    const row = makeElement("div", "weather-card-metric");
    row.appendChild(makeElement("span", "weather-card-metric-label", label));

    const result = makeElement(
        "strong",
        `weather-card-metric-value${value === null ? " is-unavailable" : ""}`,
        value === null ? "未提供" : `${Number(value).toLocaleString("zh-TW")}%`
    );

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
    if (!container) return;
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
        header.appendChild(makeElement(
            "span", "weather-card-day-index", isToday ? "今天" : `第 ${index + 1} 天`
        ));
        card.appendChild(header);

        const condition = makeElement("div", "weather-card-condition");
        const icon = makeElement("span", "weather-icon", getWeatherIcon(day.weatherCode, weather));
        icon.setAttribute("aria-hidden", "true");
        condition.appendChild(icon);
        condition.appendChild(makeElement("div", "weather-description", weather));
        card.appendChild(condition);

        card.appendChild(makeElement("span", "weather-temperature-label", "最高／最低氣溫"));
        card.appendChild(makeElement(
            "div",
            "weather-temperature",
            `${max === null ? "--" : max}° / ${min === null ? "--" : min}°C`
        ));

        const metrics = makeElement("div", "weather-card-metrics");
        addMetric(metrics, "最高降雨機率", rain);
        addMetric(metrics, "平均相對濕度", humidity);
        card.appendChild(metrics);
        fragment.appendChild(card);
    });

    container.appendChild(fragment);
}
