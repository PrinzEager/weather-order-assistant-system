/* 天氣訂貨建議模組：由原 app.js 分離，保留既有功能。 */
import { API, byId, makeElement, clearElement, showEmpty, showMessage, hideMessage, setButtonLoading, displayValue, formatNumber, getSearchParameters, apiGet, createStatCard } from "./common.js";

let recommendationLoaded = false;

export function initRecommendations() {
    byId("recommendation-search-form").addEventListener("submit", event => {
        event.preventDefault();
        loadRecommendations();
    });
}

async function loadRecommendations() {
    const form = byId("recommendation-search-form");
    const button = form.querySelector('button[type="submit"]');

    try {
        const parameters = getSearchParameters("recommendation");

        setButtonLoading(button, true, "計算訂貨建議中...");
        hideMessage();

        showEmpty(
            byId("recommendation-summary"),
            "正在計算訂貨建議..."
        );

        const table = byId("recommendation-table-body");
        clearElement(table);

        const data = await apiGet(
            API.recommendations,
            parameters
        );

        if (!Array.isArray(data)) {
            throw new Error("訂貨建議 API 回傳格式不正確");
        }

        renderRecommendationSummary(data);
        renderRecommendationTable(data);

        recommendationLoaded = true;

        showMessage(
            `訂貨建議計算完成，共 ${data.length} 筆商品`,
            "success"
        );

    } catch (error) {
        recommendationLoaded = false;

        clearElement(byId("recommendation-summary"));

        const tbody = byId("recommendation-table-body");
        clearElement(tbody);

        const row = makeElement("tr");
        const cell = makeElement(
            "td",
            "無法取得訂貨建議",
            "empty-state"
        );

        cell.colSpan = 7;
        row.appendChild(cell);
        tbody.appendChild(row);

        byId("stat-reorder").textContent = "--";

        showMessage(error.message, "error");

        console.error("OrderRecommendation API 錯誤：", error);

    } finally {
        setButtonLoading(button, false);
    }
}

function renderRecommendationSummary(data) {
    const container = byId("recommendation-summary");
    clearElement(container);

    const needsReorder = data.filter(
        item => item.needsReorder === true
    ).length;

    const totalQuantity = data.reduce(
        (sum, item) =>
            sum + (Number(item.recommendedQuantity) || 0),
        0
    );

    container.appendChild(
        createStatCard("有效商品數", data.length, "本次查詢")
    );

    container.appendChild(
        createStatCard("需要補貨商品", needsReorder, "建議數量大於零")
    );

    container.appendChild(
        createStatCard(
            "建議訂貨總數",
            formatNumber(totalQuantity),
            "所有商品建議量合計"
        )
    );

    // 更新首頁已取得的統計數字。
    byId("stat-reorder").textContent = needsReorder;
}

function renderRecommendationTable(data) {
    const tbody = byId("recommendation-table-body");
    clearElement(tbody);

    if (data.length === 0) {
        const row = makeElement("tr");
        const cell = makeElement(
            "td",
            "目前沒有符合條件的有效商品",
            "empty-state"
        );

        cell.colSpan = 7;
        row.appendChild(cell);
        tbody.appendChild(row);
        return;
    }

    data.forEach(item => {
        const row = makeElement("tr");

        // 第一欄：商品選擇。
        const selectCell = makeElement("td");
        const checkbox = makeElement("input");

        checkbox.type = "checkbox";
        checkbox.className = "recommendation-select";
        checkbox.dataset.productId = item.productId;
        checkbox.setAttribute(
            "aria-label",
            `選擇商品 ${displayValue(item.productName)}`
        );

        selectCell.appendChild(checkbox);
        row.appendChild(selectCell);

        // 第二欄：商品名稱與 SKU。
        const productCell = makeElement("td");
        productCell.appendChild(
            makeElement("strong", item.productName)
        );

        productCell.appendChild(
            makeElement(
                "div",
                displayValue(item.sku),
                "weather-description"
            )
        );

        row.appendChild(productCell);

        // 庫存及歷史銷售。
        row.appendChild(
            makeElement("td", formatNumber(item.currentStock))
        );

        row.appendChild(
            makeElement(
                "td",
                formatNumber(item.historicalSalesQuantity)
            )
        );

        // 天氣倍率。
        const factor = item.weatherFactor;

        row.appendChild(
            makeElement(
                "td",
                factor == null
                    ? "--"
                    : `${Number(factor).toFixed(2)} ×`
            )
        );

        // 建議訂貨數量。
        const quantityCell = makeElement("td");

        const badge = makeElement(
            "span",
            formatNumber(item.recommendedQuantity),
            item.needsReorder
                ? "status-badge warning"
                : "status-badge success"
        );

        quantityCell.appendChild(badge);
        row.appendChild(quantityCell);

        // 實際訂貨數量，可由使用者自行調整。
        const actualCell = makeElement("td");

        const quantityInput = makeElement("input");

        quantityInput.type = "number";
        quantityInput.min = "0";
        quantityInput.step = "1";
        quantityInput.value = String(
            item.recommendedQuantity ?? 0
        );

        quantityInput.className = "actual-order-quantity";
        quantityInput.dataset.productId = item.productId;
        quantityInput.style.width = "90px";
        quantityInput.setAttribute(
            "aria-label",
            `${displayValue(item.productName)}實際訂貨量`
        );

        actualCell.appendChild(quantityInput);
        row.appendChild(actualCell);

        // 滑鼠停留時可查看詳細計算依據。
        row.title =
            `歷史銷售：${displayValue(item.historicalSalesQuantity)}\n` +
            `天氣倍率：${displayValue(item.weatherFactor)}\n` +
            `調整後需求：${displayValue(item.weatherAdjustedSalesQuantity)}\n` +
            `安全庫存：${displayValue(item.safetyStock)}\n` +
            `目前庫存：${displayValue(item.currentStock)}\n` +
            `未到貨數量：${displayValue(item.outstandingQuantity)}\n` +
            `可用庫存：${displayValue(item.availableStock)}`;

        tbody.appendChild(row);
    });

    // 採購單新增 API 尚未核對，維持停用。
    byId("create-order-from-recommendation").disabled = true;

    // 不會在查詢訂貨建議時建立採購單。
}
