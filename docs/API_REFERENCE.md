# API 文件｜目前 Controller 實際端點

**基準**：程式 `controller/*Controller.java`，2026-11-22。此文件記載**已存在的 URL／HTTP Method／用途**，不是 Swagger 自動產出的 OpenAPI 規格；未對本次文件版重新跑 Postman。

## 1. 共通規則

- 本機 Base URL：`http://localhost:8080`（實際依 `server.port` 調整）。
- `/api/**` **需登入**；使用 Spring Security **HttpSession Cookie (`JSESSIONID`)**，不是 Bearer JWT。
- `POST`、`PUT`、`PATCH`、`DELETE` 需附有效 CSRF Token。後台的 `templates/index.html` 將 Token 放在 `<meta name="_csrf">` 及 `<meta name="_csrf_header">`，`static/js/common.js` 讀取後送 Header；Postman 需先以 Session 登入並取得有效 Token／Cookie。
- JSON 日期：`YYYY-MM-DD`，回傳日期時間格式由 Java DTO 的時間型別和 Jackson 序列化決定。
- 典型 HTTP 狀態：讀取／更新 `200`、建立 `201`、成功無內容 `204`、驗證 `400`、未登入 `401`、CSRF／存取拒絕 `403`、不存在 `404`、業務衝突 `409`、部分舊入口 `405`、上游氣象 `502/504`；各端點具體狀態以實際條件為準。
- 多數業務錯誤為 `{ "timestamp": "...", "status": 400, "message": "...", "path": "/api/..." }`；Security 的 `401/403` 由不同 Handler 回傳 `{ "status": 401, "message": "..." }`，schema 尚不完全統一。

## 2. 帳號與頁面

| Method | Path | 功能 | 成功狀態 |
|---|---|---|---|
| `GET` | `/login` | 登入頁（公開） | 200 |
| `POST` | `/login` | Spring Security 表單登入（帶 CSRF） | 重新導向 |
| `POST` | `/logout` | 清除 Session（帶 CSRF） | 重新導向 |
| `GET` | `/`、`/home` | 後台首頁（需登入） | 200 |
| `GET` | `/weather`、`/recommendations`、`/categories`、`/products`、`/suppliers`、`/sales`、`/orders` | 同一 Thymeleaf 模板，不是獨立後端功能頁 | 200 |
| `GET` | `/api/account/me` | 本人帳號（不回傳 password hash） | 200 |
| `POST` | `/api/account/change-password` | 變更密碼，成功後登出 | 204 |

**修改密碼 Request：**

```json
{ "currentPassword": "current-password", "newPassword": "new-12-or-more-characters", "confirmPassword": "new-12-or-more-characters" }
```

## 3. 商品分類（`ProductCategoryController`）

| Method | Path | 說明 | 成功 |
|---|---|---|---|
| GET | `/api/product-categories` | 查詢有效分類 | 200 |
| GET | `/api/product-categories/{id}` | 依 ID | 200 |
| POST | `/api/product-categories` | 新增第一層／第二層 | 201 |
| PUT | `/api/product-categories/{id}` | 更新名稱、上層 | 200 |
| DELETE | `/api/product-categories/{id}` | 軟刪除 | 204 |

```json
{ "name": "茶類", "parentId": 1 }
```

`parentId: null` 為第一層，第二層商品分類由 Service 驗證不允許第三層。刪除仍被有效商品使用的分類會遭拒絕。

## 4. 商品（`ProductController`）

| Method | Path | 說明 | 成功 |
|---|---|---|---|
| GET | `/api/products` | 啟用且未軟刪除商品 | 200 |
| GET | `/api/products/{id}` | 單一商品 | 200 |
| POST | `/api/products` | 新增商品 | 201 |
| PUT | `/api/products/{id}` | 完整修改商品（包含 currentStock） | 200 |
| PATCH | `/api/products/{id}/status?status=0` | 停用；`status=1` 啟用 | 200 |
| DELETE | `/api/products/{id}` | 軟刪除 | 204 |

```json
{ "categoryId": 2, "sku": "TEA001", "name": "紅茶", "price": 25.00, "unit": "瓶", "currentStock": 100, "safetyStock": 20 }
```

`categoryId` 必須為啟用且有效的第二層分類。**商品照片上傳 API 不在目前 Controller 清單中。**

## 5. 後台啟用狀態查詢（`ManagementStatusController`）

| Method | Path | query | 功能 |
|---|---|---|---|
| GET | `/api/management/products` | `status=all`、`active`、`inactive` | 管理清單，可看到停用資料並重新啟用 |
| GET | `/api/management/suppliers` | 同上 | 管理供應商狀態 |

## 6. 供應商與供貨商品

| Method | Path | 說明 | 成功 |
|---|---|---|---|
| GET | `/api/suppliers` | 有效供應商清單 | 200 |
| GET | `/api/suppliers/{id}` | 依 ID | 200 |
| POST | `/api/suppliers` | 新增 | 201 |
| PUT | `/api/suppliers/{id}` | 更新 | 200 |
| PATCH | `/api/suppliers/{id}/status?status=0` | 停用／重新啟用 | 200 |
| DELETE | `/api/suppliers/{id}` | 軟刪除 | 204 |
| POST | `/api/suppliers/{supplierId}/products` | 建立商品供貨關聯／採購價 | 201 |
| GET | `/api/suppliers/{supplierId}/products` | 供應商所有供貨關聯（含停用） | 200 |
| PUT | `/api/suppliers/{supplierId}/products/{productId}` | 更新該供應商之商品採購價 | 200 |
| PUT | `/api/suppliers/{supplierId}/products/{productId}/status` | 更新供貨狀態 | 200 |
| GET | `/api/supplier-products/by-product/{productId}` | 查詢指定商品可用供應商報價 | 200 |

```json
{ "name": "展示供應商", "contactPerson": "王小姐", "phone": "0700000000", "email": "example@example.com", "address": "測試地址" }
```

```json
{ "productId": 1, "purchasePrice": 15.50 }
```

更新採購價 Request 為 `{ "purchasePrice": 16.00 }`，更新供貨狀態為 `{ "status": 0 }`。

## 7. 銷售與退貨（`SalesRecordController`）

| Method | Path | 說明 | 成功 |
|---|---|---|---|
| GET | `/api/sales-records` | 全部未軟刪除銷售紀錄（目前無後端分頁） | 200 |
| GET | `/api/sales-records/{id}` | 銷售明細 | 200 |
| POST | `/api/sales-records` | 單商品銷售 | 201 |
| POST | `/api/sales-records/batch` | 一次銷售 1～50 種商品，每筆寫入同 batch UUID | 201 |
| POST | `/api/sales-records/{id}/returns` | 部分退貨；回補商品庫存 | 201 |
| GET | `/api/sales-records/{id}/returns` | 退貨歷史 | 200 |
| DELETE | `/api/sales-records/{id}` | **舊入口已停用** | **405** |

```json
{ "productId": 1, "quantity": 2, "unitPrice": 25.00 }
```

```json
{ "items": [ { "productId": 1, "quantity": 2, "unitPrice": 25.00 }, { "productId": 2, "quantity": 3, "unitPrice": 30.00 } ] }
```

```json
{ "quantity": 1, "reason": "商品退貨" }
```

單商品與多商品的每一筆仍為 `SalesRecordResponse`，多商品共同 `batchId`；累計退貨量在 `returnedQuantity`。目前業務為售出起 7 × 24 小時內可退、可回補庫存。**CSV 與 PDF 報表由前端產生／使用瀏覽器列印，沒有報表下載 API。**

## 8. 採購單（`PurchaseOrderController`）

| Method | Path | 說明 | 成功 |
|---|---|---|---|
| GET | `/api/purchase-orders` | 有效訂貨單列表 | 200 |
| GET | `/api/purchase-orders/{id}` | 明細與供應商歷史快照 | 200 |
| POST | `/api/purchase-orders` | 建立 DRAFT | 201 |
| PUT | `/api/purchase-orders/{id}` | 只更新 DRAFT，完整取代明細 | 200 |
| POST | `/api/purchase-orders/{id}/submit` | DRAFT → SUBMITTED；跨日不可直接送出 | 200 |
| POST | `/api/purchase-orders/{id}/receive` | SUBMITTED → RECEIVED；回補庫存 | 200 |
| POST | `/api/purchase-orders/{id}/cancel` | DRAFT／SUBMITTED → CANCELLED | 200 |
| DELETE | `/api/purchase-orders/{id}` | 依 Service 規則軟刪除 DRAFT／CANCELLED | 204 |

```json
{ "supplierId": 1, "expectedDeliveryDate": "2026-11-25", "remark": "測試採購", "items": [ { "productId": 1, "quantity": 10 }, { "productId": 2, "quantity": 5 } ] }
```

建立／修改時單價與小計由 Service 依目前規則處理，**Request 不接受自填總金額／商品單價欄位**。

## 9. 依供應商批次建立草稿（`PurchaseOrderBatchController`）

| Method | Path | 說明 | 成功 |
|---|---|---|---|
| GET | `/api/purchase-order-batches/delivery-policy` | 台灣目前時間、12:00 截單時間與最早到貨日期 | 200 |
| POST | `/api/purchase-order-batches/drafts` | 選擇不同供應商商品並依供應商分為多張 DRAFT | 201 |

```json
{ "expectedDeliveryDate": "2026-11-25", "remark": "天氣訂貨建議", "items": [ { "productId": 1, "supplierId": 1, "quantity": 10 }, { "productId": 2, "supplierId": 2, "quantity": 5 } ] }
```

預計到貨日**必填**且須符合台灣當地 12:00 截單規則：中午前最早隔天；中午起最早後天；單價從 `supplier_products` 查得。

## 10. 天氣與訂貨建議

| Method | Path | 用途 | 成功 |
|---|---|---|---|
| GET | `/api/weather/forecast` | 七日預報、時段資料與每日係數 | 200 |
| GET | `/api/order-recommendations` | 舊版全部有效商品的天氣建議 | 200 |
| GET | `/api/order-recommendations/{productId}` | 舊版單商品天氣建議 | 200 |
| GET | `/api/order-recommendation-options` | 全商品一般／天氣兩種建議量 | 200 |
| GET | `/api/order-recommendation-options/{productId}` | 單商品一般／天氣建議 | 200 |
| GET | `/api/order-recommendation-options/with-weather-details` | **整合建議＋每日天氣係數說明**，一次共用天氣查詢 | 200 |

上述 GET 端點皆接收必要 query：`city`、`district`、`startDate`、`endDate`（日期 `YYYY-MM-DD`）；`order-recommendation-options` 類額外可用 `basis=MONTH`（預設）或 `basis=WEEK`。

```http
GET /api/order-recommendation-options/with-weather-details?city=高雄市&district=路竹區&startDate=2026-11-22&endDate=2026-11-28&basis=MONTH
```

```json
{
  "city": "高雄市", "district": "路竹區",
  "startDate": "2026-11-22", "endDate": "2026-11-28",
  "weatherFactor": 1.04, "forecastStatus": "COMPLETE", "warning": null,
  "dailyWeatherFactors": [
    {"date":"2026-11-22","maxTemperature":23,"temperatureAdjustment":0.00,"humidityAdjustment":0.00,"rainAdjustment":0.00,"weatherFactor":1.00}
  ],
  "recommendations": []
}
```

**以上 JSON 是欄位展示的「精簡示意」，不是完整 DTO 實際回傳，也不保證目前同一日期的預報仍為 1.04。** 實際回應會有更多每日氣象欄位及逐商品建議清單；日期範圍僅支援台灣今天起七日內，超界回 400；中央氣象署不可用時有 fallback／警告或上游錯誤處理。

## 11. 補充：API 演進限制

- `/api/products`、`/api/sales-records`、`/api/purchase-orders` 等查詢仍以陣列為主，**目前不能宣稱已全面支援分頁或後端複合篩選**。
- 實際 UI 停用商品管理使用 `/api/management/*`；下拉選擇仍使用有效清單。
- 沒有 OpenAPI `springdoc` 依賴，這份文件由目前 Controller 手動整理，後續新 API 請同步更新。
