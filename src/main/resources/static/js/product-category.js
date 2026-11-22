/* 商品分類管理模組：由原 app.js 分離，保留既有功能。 */
import { API, byId, makeElement, clearElement, showEmpty, showMessage, hideMessage, setButtonLoading, displayValue, apiGet, csrfHeaders, redirectIfSessionExpired } from "./common.js";

let categoriesLoaded = false;
let categoryData = [];

export function initCategories() {
    initializeCategoryForm();
}

export function enterCategoriesPage() {
    if (!categoriesLoaded) loadCategories();
}

async function loadCategories() {

    const container = byId("category-list");

    try {
        hideMessage();

        // 顯示載入中訊息。
        showEmpty(container, "正在載入商品分類...");

        // 呼叫現有 Spring Boot REST API。
        const categories = await apiGet(API.categories, {});

        // 確認回傳資料為陣列。
        if (!Array.isArray(categories)) {
            throw new Error("商品分類回傳格式不正確");
        }

        // 保存最新分類資料，提供新增分類表單使用。
        categoryData = categories;

        // 將分類資料組成兩層階層畫面。
        renderCategories(categories);

        // 成功載入後記錄狀態。
        categoriesLoaded = true;

    } catch (error) {

        // 查詢失敗時允許下次重新載入。
        categoriesLoaded = false;

        showEmpty(container, "商品分類載入失敗");

        showMessage(error.message, "error");

        console.error("ProductCategory API 錯誤：", error);
    }
}

/**
 * 將商品分類組合成兩層清單。
 *
 * 第一層：
 * parentId === null
 *
 * 第二層：
 * parentId 對應第一層分類的 id
 */

function renderCategories(categories) {

    const container = byId("category-list");

    clearElement(container);

    // 沒有任何分類資料。
    if (categories.length === 0) {
        showEmpty(container, "目前尚無商品分類");
        return;
    }

    // 取得第一層分類。
    const parentCategories = categories.filter(
        category => category.parentId == null
    );

    // 取得第二層分類。
    const childCategories = categories.filter(
        category => category.parentId != null
    );

    // 使用 ID 排序，讓畫面順序固定。
    parentCategories.sort((a, b) => a.id - b.id);
    childCategories.sort((a, b) => a.id - b.id);

    // 依序顯示第一層分類。
    parentCategories.forEach(parent => {

        // 第一層分類容器。
        const parentGroup = makeElement(
            "div",
            null,
            "category-group"
        );

        // 第一層分類標題。
        const parentHeader = makeElement(
            "div",
            null,
            "category-header"
        );

        parentHeader.style.display = "flex";
        parentHeader.style.alignItems = "center";
        parentHeader.style.gap = "12px";
        parentHeader.style.padding = "14px";
        parentHeader.style.background = "#f8fafc";
        parentHeader.style.borderRadius = "8px";
        parentHeader.style.marginBottom = "8px";

        // 分類圖示。
        parentHeader.appendChild(
            makeElement("span", "📁")
        );

        // 分類名稱。
        parentHeader.appendChild(
            makeElement(
                "strong",
                displayValue(parent.name)
            )
        );

        // 第一層標籤。
        parentHeader.appendChild(
            makeElement(
                "span",
                "第一層",
                "status-badge neutral"
            )
        );

        // 分類狀態。
        parentHeader.appendChild(
            createCategoryStatusBadge(parent.status)
        );

        // 第一層分類編輯按鈕。
        parentHeader.appendChild(
            createCategoryEditButton(parent)
        );

        // 第一層分類軟刪除按鈕。
        parentHeader.appendChild(
            createCategoryDeleteButton(parent)
        );

        parentGroup.appendChild(parentHeader);

        // 找出此第一層分類的子分類。
        const children = childCategories.filter(
            child => child.parentId === parent.id
        );

        // 第二層分類區域。
        const childContainer = makeElement(
            "div",
            null,
            "category-children"
        );

        childContainer.style.paddingLeft = "32px";
        childContainer.style.marginBottom = "20px";

        if (children.length === 0) {

            childContainer.appendChild(
                makeElement(
                    "p",
                    "此分類目前沒有第二層分類",
                    "empty-state"
                )
            );

        } else {

            children.forEach(child => {

                const childRow = makeElement(
                    "div",
                    null,
                    "category-child"
                );

                childRow.style.display = "flex";
                childRow.style.alignItems = "center";
                childRow.style.gap = "12px";
                childRow.style.padding = "12px";
                childRow.style.borderBottom =
                    "1px solid #e2e8f0";

                childRow.appendChild(
                    makeElement("span", "↳")
                );

                childRow.appendChild(
                    makeElement(
                        "span",
                        displayValue(child.name)
                    )
                );

                childRow.appendChild(
                    makeElement(
                        "span",
                        "第二層",
                        "status-badge neutral"
                    )
                );

                childRow.appendChild(
                    createCategoryStatusBadge(child.status)
                );

                // 分類 ID 僅作為 API 識別，不顯示給一般使用者。

                // 第二層分類編輯按鈕。
                childRow.appendChild(
                    createCategoryEditButton(child)
                );

                childContainer.appendChild(childRow);

                // 第二層分類軟刪除按鈕。
                childRow.appendChild(
                    createCategoryDeleteButton(child)
                );
            });
        }

        parentGroup.appendChild(childContainer);

        container.appendChild(parentGroup);
    });

    // 理論上正常資料不應存在孤立的第二層分類。
    // 為避免資料被忽略，另外顯示找不到上層的項目。
    const parentIds = new Set(
        parentCategories.map(category => category.id)
    );

    const orphanCategories = childCategories.filter(
        category => !parentIds.has(category.parentId)
    );

    if (orphanCategories.length > 0) {

        const warning = makeElement(
            "div",
            "部分第二層分類找不到對應的第一層分類",
            "warning-message"
        );

        container.appendChild(warning);

        orphanCategories.forEach(category => {
            container.appendChild(
                makeElement(
                    "p",
                    `${displayValue(category.name)}（找不到上層分類）`
                )
            );
        });
    }
}

/**
 * 根據商品分類狀態建立標籤。
 *
 * 1：啟用
 * 0：停用
 */

function createCategoryStatusBadge(status) {

    if (status === 1) {
        return makeElement(
            "span",
            "啟用",
            "status-badge success"
        );
    }

    if (status === 0) {
        return makeElement(
            "span",
            "停用",
            "status-badge danger"
        );
    }

    return makeElement(
        "span",
        "未知狀態",
        "status-badge neutral"
    );
}

function initializeCategoryForm() {

    const addButton = byId("add-category-button");
    const closeButton = byId("category-form-close");
    const cancelButton = byId("category-form-cancel");

    const levelSelect = byId("category-level");
    const form = byId("category-form");

    // 啟用新增分類按鈕。
    addButton.disabled = false;

    // 開啟新增分類表單。
    addButton.addEventListener("click", () => {
        openCreateCategoryForm();
    });

    // 關閉表單。
    closeButton.addEventListener("click", closeCategoryForm);
    cancelButton.addEventListener("click", closeCategoryForm);

    // 分類層級變更時，更新上層分類欄位。
    levelSelect.addEventListener("change", () => {
        updateCategoryLevelFields();
    });

    // 新增與修改共用表單提交事件。
    form.addEventListener("submit", async event => {
        event.preventDefault();
        await submitCategoryForm();
    });
}

/**
 * 開啟新增分類表單。
 */

function openCreateCategoryForm() {

    const panel = byId("category-form-panel");
    const form = byId("category-form");

    // 清空前一次輸入內容。
    form.reset();

    // 新增模式不指定分類 ID。
    byId("category-id").value = "";

    // 設定表單標題。
    byId("category-form-title").textContent = "新增商品分類";

    // 新增模式的提交按鈕文字。
    byId("category-form-submit").textContent = "儲存分類";

    // 預設建立第一層分類。
    byId("category-level").value = "1";

    // 準備可選擇的第一層分類。
    populateCategoryParents();

    // 同步層級欄位顯示狀態。
    updateCategoryLevelFields();

    // 顯示表單。
    panel.hidden = false;

    // 將畫面移動到表單。
    panel.scrollIntoView({
        behavior: "smooth",
        block: "start"
    });

    byId("category-name").focus();
}

/**
 * 關閉商品分類表單。
 */

function closeCategoryForm() {

    byId("category-form-panel").hidden = true;

    // 清空欄位，避免殘留上次資料。
    byId("category-form").reset();
    byId("category-id").value = "";
}

/**
 * 更新分類層級選擇。
 *
 * 第一層：
 * 不需要上層分類，parentId 為 null。
 *
 * 第二層：
 * 必須選擇一筆有效的第一層分類。
 */

function updateCategoryLevelFields() {

    const level = byId("category-level").value;

    const parentGroup = byId("category-parent-group");
    const parentSelect = byId("category-parent-id");

    if (level === "2") {

        parentGroup.hidden = false;
        parentSelect.required = true;

        // 每次切換成第二層，重新載入可選擇項目。
        populateCategoryParents();

    } else {

        parentGroup.hidden = true;
        parentSelect.required = false;
        parentSelect.value = "";
    }
}

/**
 * 載入第一層分類下拉選單。
 *
 * 排除：
 * 1. 第二層分類
 * 2. 已停用分類（status !== 1）
 *
 * 避免使用者選擇不合法的上層分類。
 */

function populateCategoryParents() {

    const select = byId("category-parent-id");

    clearElement(select);

    // 預設提示選項。
    const placeholder = makeElement(
        "option",
        "請選擇第一層分類"
    );

    placeholder.value = "";
    select.appendChild(placeholder);

    // 僅取得啟用中的第一層分類。
    const parents = categoryData
        .filter(category =>
            category.parentId == null &&
            category.status === 1
        )
        .sort((a, b) => a.id - b.id);

    parents.forEach(category => {

        const option = makeElement(
            "option",
            category.name
        );

        option.value = String(category.id);

        select.appendChild(option);
    });
}

async function submitCategoryForm() {

    const idValue = byId("category-id").value;

    // 沒有 ID 代表新增，有 ID 代表修改。
    const isEdit = idValue !== "";

    const categoryId = isEdit
        ? Number(idValue)
        : null;

    const name = byId("category-name").value.trim();
    const level = byId("category-level").value;
    const parentValue = byId("category-parent-id").value;

    const submitButton = byId("category-form-submit");

    try {

        // 驗證修改中的分類 ID。
        if (isEdit &&
            (!Number.isSafeInteger(categoryId) ||
             categoryId <= 0)) {

            throw new Error("商品分類 ID 不正確");
        }

        // 分類名稱驗證。
        if (!name) {
            throw new Error("請輸入商品分類名稱");
        }

        if ([...name].length > 100) {
            throw new Error("分類名稱不可超過 100 個字元");
        }

        // 分類層級驗證。
        if (level !== "1" && level !== "2") {
            throw new Error("商品分類層級不正確");
        }

        // 第一層分類的上層 ID 為 null。
        let parentId = null;

        if (level === "2") {

            if (!parentValue) {
                throw new Error("第二層分類必須選擇上層分類");
            }

            parentId = Number(parentValue);

            if (!Number.isSafeInteger(parentId) ||
                parentId <= 0) {

                throw new Error("上層分類 ID 不正確");
            }

            // 不允許將自己設定成上層分類。
            if (isEdit && parentId === categoryId) {
                throw new Error(
                    "不可將自己設定為上層分類"
                );
            }

            // 確認所選上層分類存在、啟用且屬於第一層。
            const validParent = categoryData.some(
                category =>
                    category.id === parentId &&
                    category.parentId == null &&
                    category.status === 1
            );

            if (!validParent) {
                throw new Error(
                    "請選擇有效且啟用中的第一層分類"
                );
            }
        }

        // 建立符合 ProductCategoryRequest 的資料。
        const requestBody = {
            name: name,
            parentId: parentId
        };

        // 新增使用 POST，修改使用 PUT。
        const method = isEdit ? "PUT" : "POST";

        const url = isEdit
            ? `${API.categories}/${categoryId}`
            : API.categories;

        setButtonLoading(
            submitButton,
            true,
            isEdit ? "修改分類中..." : "新增分類中..."
        );

        hideMessage();

        // 呼叫 Spring Boot REST API。
        const response = await fetch(url, {
            method: method,
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json",
                ...csrfHeaders(method)
            },
            body: JSON.stringify(requestBody)
        });

        redirectIfSessionExpired(response);

        const contentType =
            response.headers.get("content-type") || "";

        let data = null;

        if (contentType.includes("json")) {
            data = await response.json();
        }

        // 由後端回傳實際錯誤訊息。
        if (!response.ok) {
            throw new Error(
                data?.message ||
                data?.detail ||
                data?.error ||
                `${isEdit ? "修改" : "新增"}分類失敗（HTTP ${response.status}）`
            );
        }

        // 驗證回傳狀態。
        const expectedStatus = isEdit ? 200 : 201;

        if (response.status !== expectedStatus) {
            console.warn(
                "分類操作成功，但 HTTP 狀態與預期不同：",
                response.status
            );
        }

        // 關閉表單。
        closeCategoryForm();

        // 重新載入分類清單。
        categoriesLoaded = false;

        await loadCategories();

        // 依實際重新載入結果提示使用者。
        if (categoriesLoaded) {
            showMessage(
                `商品分類「${name}」${isEdit ? "修改" : "新增"}成功`,
                "success"
            );
        } else {
            showMessage(
                `分類已${isEdit ? "修改" : "新增"}，但清單重新載入失敗，請重新整理頁面`,
                "error"
            );
        }

    } catch (error) {

        showMessage(error.message, "error");

        console.error(
            isEdit ? "修改商品分類失敗：" : "新增商品分類失敗：",
            error
        );

    } finally {

        setButtonLoading(submitButton, false);
    }
}

function createCategoryEditButton(category) {

    const button = makeElement(
        "button",
        "編輯",
        "secondary-button"
    );

    button.type = "button";

    // 點擊後開啟既有分類編輯表單。
    button.addEventListener("click", () => {
        openEditCategoryForm(category.id);
    });

    return button;
}

/**
 * 開啟商品分類編輯表單。
 *
 * 使用已載入的分類資料，
 * 不需要再次呼叫單筆 GET API。
 */

function openEditCategoryForm(categoryId) {

    const category = categoryData.find(
        item => item.id === categoryId
    );

    if (!category) {
        showMessage("找不到要修改的商品分類", "error");
        return;
    }

    const panel = byId("category-form-panel");
    const form = byId("category-form");

    // 清除上次表單內容。
    form.reset();

    // 設定編輯中的分類 ID。
    byId("category-id").value = String(category.id);

    // 帶入目前分類名稱。
    byId("category-name").value = category.name;

    // 判斷目前是第一層或第二層分類。
    const level = category.parentId == null ? "1" : "2";

    byId("category-level").value = level;

    // 更新上層分類欄位。
    updateCategoryLevelFields();

    // 第二層分類需帶入目前上層分類。
    if (level === "2") {
        byId("category-parent-id").value =
            String(category.parentId);
    }

    // 調整表單標題與按鈕文字。
    byId("category-form-title").textContent =
        "編輯商品分類";

    byId("category-form-submit").textContent =
        "儲存修改";

    // 顯示編輯表單。
    panel.hidden = false;

    panel.scrollIntoView({
        behavior: "smooth",
        block: "start"
    });

    byId("category-name").focus();
}

function createCategoryDeleteButton(category) {

    const button = makeElement(
        "button",
        "刪除",
        "danger-button"
    );

    button.type = "button";

    button.setAttribute(
        "aria-label",
        `刪除商品分類 ${category.name}`
    );

    button.addEventListener("click", () => {
        deleteCategory(category);
    });

    return button;
}

/**
 * 軟刪除商品分類。
 *
 * DELETE /api/product-categories/{id}
 *
 * 成功：
 * HTTP 204 No Content
 *
 * 失敗：
 * HTTP 404 / 409 等狀態碼
 */

async function deleteCategory(category) {

    // 確認使用者是否真的要刪除。
    const confirmed = window.confirm(
        `確定要刪除商品分類「${category.name}」嗎？\n\n` +
        "此操作會將分類標記為已刪除，" +
        "且無法透過目前前端復原。"
    );

    if (!confirmed) {
        return;
    }

    try {

        hideMessage();

        const response = await fetch(
            `${API.categories}/${category.id}`,
            {
                method: "DELETE",
                headers: {
                    "Accept": "application/json",
                    ...csrfHeaders("DELETE")
                }
            }
        );

        redirectIfSessionExpired(response);

        // DELETE 成功時沒有 Response Body。
        // 不可直接呼叫 response.json()。
        if (!response.ok) {

            const contentType =
                response.headers.get("content-type") || "";

            let errorMessage =
                `刪除商品分類失敗（HTTP ${response.status}）`;

            if (contentType.includes("json")) {

                const data = await response.json();

                errorMessage =
                    data?.message ||
                    data?.detail ||
                    data?.error ||
                    errorMessage;
            }

            throw new Error(errorMessage);
        }

        if (response.status !== 204) {
            console.warn(
                "刪除成功，但狀態碼不是預期的 204：",
                response.status
            );
        }

        // 如果正在編輯被刪除的分類，關閉編輯表單。
        const editingId = byId("category-id").value;

        if (editingId === String(category.id)) {
            closeCategoryForm();
        }

        // 重新取得最新分類資料。
        categoriesLoaded = false;

        await loadCategories();

        if (categoriesLoaded) {

            showMessage(
                `商品分類「${category.name}」已成功刪除`,
                "success"
            );

        } else {

            showMessage(
                "分類已刪除，但重新載入清單失敗，請重新整理頁面",
                "error"
            );
        }

    } catch (error) {

        showMessage(error.message, "error");

        console.error(
            "刪除商品分類失敗：",
            error
        );
    }
}
