# Weather Order Assistant System 專案交接文件

> 整理日期：2026-11-22。依目前工作目錄的原始碼整理，包含尚未提交的前端修改。用途是讓其他開發者或 AI 在沒有本次對話的情況下理解專案、討論與延續開發。這是現況摘要與介面索引，不是完整原始碼備份；實際行為以程式及 migration 為準。未收錄 `.env` 內容、真實帳密、API Key、資料庫實際資料、Git 歷史及編譯產物。

## 1. 專案目的與目前範圍

這是繁體中文的天氣訂貨輔助管理系統。以商品上月同期銷量為需求基礎，依中央氣象署的氣溫與降雨機率調整需求，再扣除現有庫存及已送出未到貨的採購量，產生補貨建議。使用者可管理兩層商品分類、商品與庫存、供應商、銷售及退貨、採購草稿與到貨。

目前是單一 Spring Boot 應用，同時提供 HTML、靜態資源與 JSON API；前端以頁面區塊切換呈現。訂貨建議採明確規則計算，沒有機器學習模型或生成式 AI 呼叫。建議本身不會自動寫入採購單。

## 2. 技術與執行架構

| 項目 | 原始碼中的設定 |
| --- | --- |
| Maven 座標 | `com.example:weather-order-assistant-system:0.0.1-SNAPSHOT` |
| Java | 21 |
| Spring Boot parent | 4.1.1（`pom.xml` 宣告值，不代表本次已驗證下載與執行） |
| Maven Wrapper | wrapper 3.3.4，distribution Maven 3.10.0 |
| 後端 | Spring MVC、Bean Validation、Spring Data JPA、Lombok |
| 資料庫 | MySQL、MySQL Connector/J、Flyway + flyway-mysql |
| JSON 與外部 HTTP | Spring Boot Jackson starter、RestClient |
| 畫面 | Thymeleaf `index.html`、原生 JavaScript ES modules、CSS |
| 測試 | Spring Boot test、JPA test、Web MVC test starter |

主程式：`src/main/java/com/example/weather_order_assistant_system/WeatherOrderAssistantSystemApplication.java`。Java base package：`com.example.weather_order_assistant_system`。

資料流：瀏覽器 → Controller → Service（交易及業務檢查）→ Repository → MySQL。天氣資料流：WeatherController／OrderRecommendationService → WeatherService → CwaApiClient → CWA JSON → Response DTO。

## 3. 目錄與閱讀順序

```text
pom.xml                         相依套件、Java 版本與 Maven plugin
mvnw / mvnw.cmd                 Maven Wrapper 入口
.mvn/wrapper/                   Wrapper 設定
notes/DB_README.md              早期資料庫設計說明；部分內容已落後 migration
src/main/java/.../
  client/                       CWA HTTP 呼叫、縣市資料集對應
  controller/                   首頁及 REST API
  entity/                       六個 JPA 資料模型
  repository/                   六個 JpaRepository 與查詢
  request/                      輸入 DTO 及欄位驗證
  response/                     輸出 DTO
  service/                      七個功能服務
  exception/                    共用錯誤格式及例外處理
src/main/resources/
  application.properties        環境、資料庫、Flyway、CWA、上傳大小設定
  db/migration/                 V001～V007 SQL
  templates/index.html          所有功能頁面區塊
  static/css/style.css          全站樣式
  static/js/                    入口、共用工具及各功能模組
src/test/java/.../              contextLoads 啟動測試
```

建議先讀本文件第 5～8 節，再讀 `OrderRecommendationService`、`WeatherService`、`PurchaseOrderService`，最後依要改的功能查看相對應 Controller、DTO 與 JS。附錄提供全部應用程式檔案清單及 DTO 欄位。

## 4. 本機設定與執行

1. 安裝 JDK 21，準備 MySQL 與應用程式可使用的資料庫。MySQL server 版本目前沒有在專案中鎖定。
2. 從專案根目錄啟動。設定檔使用 `spring.config.import=optional:file:.env[.properties]`，`.env` 是 Java properties 格式，不是 shell 腳本。
3. 在本機提供資料庫連線與 CWA API Key。以下只有佔位範例，請換成本機值：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/your_database
spring.datasource.username=YOUR_DB_USER
spring.datasource.password=YOUR_DB_PASSWORD
CWA_API_KEY=YOUR_CWA_API_KEY
```

也可用 Spring Boot 對應的環境變數覆寫資料庫設定。既有設定檔內的實際資料庫值刻意不複製到本文件。

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target/weather-order-assistant-system-0.0.1-SNAPSHOT.jar
```

未另外指定 port 時可開啟 `http://localhost:8080/`。非 Windows 使用 `./mvnw`。首次使用 Wrapper／建置需要能下載 Maven 與相依套件。

Flyway 啟用，位置 `classpath:db/migration`。Hibernate 設為 `ddl-auto=validate`，只驗證 schema，不替代 migration 自動修改資料表。新增欄位應新增下一版 migration，避免改寫已套用的舊檔。啟動測試也會載入真實應用設定，應另備測試資料庫，避免將日常資料庫當作隔離測試環境。

其他現況設定：SQL 印出及格式化已開啟；CWA base URL 是 `https://opendata.cwa.gov.tw/api/v1/rest/datastore/`；授權值由 `${CWA_API_KEY}` 取得。設定中有 `uploads/products` 圖片目錄及單檔／請求 5MB 限制，但目前沒有對應圖片上傳 Controller 或商品圖片欄位，不能視為已實作上傳。

## 5. 資料模型與 migration

所有主鍵為 BIGINT 自動遞增，Java 使用 Long。金額使用 BigDecimal／DECIMAL；一般庫存及數量使用 Integer，建議計算與加總使用 long／Long。表使用 InnoDB、utf8mb4_unicode_ci。

| 表／Entity | 主要欄位及關聯 |
| --- | --- |
| `product_categories`／ProductCategory | name、parent_id 自關聯、status、created_at、updated_at、deleted_at |
| `products`／Product | category_id、唯一 sku、name、price DECIMAL(10,2)、unit、current_stock、safety_stock、status、時間戳與 deleted_at |
| `suppliers`／Supplier | name、contact_person、phone、唯一且可空 email、address、status、時間戳與 deleted_at |
| `sales_records`／SalesRecord | product_id、quantity、unit_price DECIMAL(10,2)、total_amount DECIMAL(12,2)、date_time、created_at、deleted_at；沒有 status／updated_at |
| `purchase_orders`／PurchaseOrder | supplier_id、order_date、expected_delivery_date、order_status、total_amount DECIMAL(12,2)、remark、供應商五個快照欄位、時間戳與 deleted_at |
| `purchase_order_items`／PurchaseOrderItem | purchase_order_id、product_id、quantity、unit_price DECIMAL(10,2)、subtotal DECIMAL(12,2)、created_at；同張單商品組合唯一 |

關聯：分類有多個商品；供應商有多張採購單；採購單有多筆明細；商品有多筆採購明細及銷售紀錄。採購單保存供應商名稱、聯絡人、電話、email、地址快照，並保留原 supplier_id 外鍵。商品名稱與 SKU 在採購／銷售回應中取目前商品資料，沒有相同的商品名稱快照機制。

Migration：V001 分類、V002 商品、V003 供應商、V004 銷售紀錄、V005 採購主檔、V006 採購明細、V007 新增供應商快照（nullable 欄位，該 SQL 沒有回填舊資料）。Flyway 自行管理 schema history。

注意 `notes/DB_README.md` 只列到 V006，且對採購明細 created_at 的描述與目前 V006 不一致；應以 SQL 與 Entity 為準。

## 6. 核心業務規則

### 分類、商品、供應商

- 分類最多兩層；第一層 parentId=null，第二層只能掛在有效第一層之下。同層同名會衝突。不可指定自己為父分類，有子分類者不可移到第二層。
- 分類有未軟刪除子分類或未軟刪除商品時不可刪除；停用但未刪除的商品仍會阻止刪除分類。
- 商品只能使用啟用且未刪除的第二層分類。建立時預設啟用、單位「個」、庫存及安全庫存 0。售價最多八位整數及兩位小數，庫存不可為負。
- 商品 SKU 唯一檢查包含停用與軟刪除商品，不能藉刪除重新使用同一代碼。
- 供應商名稱會 trim；email 空白轉 null，有值時 trim，唯一檢查包含已停用及軟刪除資料。
- 商品、供應商狀態為整數 1 啟用、0 停用。列表只列啟用且未刪除資料，依 ID 升序；單筆查詢允許停用但未刪除資料。分類列表也是啟用且未刪除。
- 刪除主要以 deleted_at 軟刪除保留歷史。停用與刪除是不同操作。

### 銷售與退貨

建立銷售須選有效啟用商品、正數數量及非負單價，庫存不足回 409。總額由單價乘數量計算，銷售時間由後端 LocalDateTime.now() 決定；Request 沒有歷史日期欄位。儲存紀錄與扣庫存在同一交易。

DELETE 銷售紀錄的實際業務意義是整筆退貨：回補該筆數量，再將紀錄軟刪除。重複退貨無法再次取得有效紀錄；回補有 Integer 上限檢查。沒有部分退貨或修改銷售紀錄 API。列表依銷售時間降序。

### 採購單

| 操作 | 可操作狀態 | 結果及庫存影響 |
| --- | --- | --- |
| 建立 | 新單 | DRAFT；不增庫存 |
| 修改 | DRAFT | 替換明細、重算金額；不增庫存 |
| submit | DRAFT | SUBMITTED；列入未到貨量，不增現貨 |
| receive | SUBMITTED | RECEIVED；所有明細一次入庫 |
| cancel | DRAFT／SUBMITTED | CANCELLED；不增減現貨，移出未到貨量 |
| DELETE | DRAFT／CANCELLED | 軟刪除 |

建立／修改要求有效啟用供應商、至少一項有效啟用商品、數量大於零，同張單不可重複商品。單價取當下 Product.price，目前沒有獨立進貨成本；小計與總額上限 9999999999.99。前端不能以傳入總額覆蓋後端計算。

新建時複製供應商快照；修改草稿只有在更換 supplierId 時更新快照。相同供應商後來修改聯絡資料，不會自動覆蓋原快照。歷史採購查詢不因供應商停用／軟刪除而被封鎖。

修改草稿先移除舊明細並 flush，再儲存新明細，以避開同單同商品唯一鍵衝突。主檔新增透過 CascadeType.PERSIST 儲存明細。到貨有數量及庫存溢位保護，整張單與庫存更新在同一交易；沒有部分到貨、已到貨回退或已到貨單取消功能。

### 天氣查詢

- 支援臺灣 22 縣市，資料集對照寫在 CwaDatasetMapping；名稱須符合對照表，例如「臺北市」。行政區由 CWA 回應中核對。
- 查詢參數 city、district、startDate、endDate。以 Asia/Taipei 判斷日期；只允許今天至後天，起日不得晚於迄日。
- CwaApiClient 透過 RestClient GET 資料集，帶 Authorization、locationName、format=JSON；datasetId 格式限制為 `F-D0047-\d{3}`。
- WeatherService 解析 `records.Locations[].Location[].WeatherElement[]` 的「溫度」「3小時降雨機率」「天氣現象」，不依賴元素陣列固定順序。時間以 OffsetDateTime 處理並轉 UTC+8。
- 缺失或非數值保持 null，降雨值須為 0～100；不把缺資料當 0。每日高低溫由有效溫度點計算，代表天氣取涵蓋當日 12:00 的區間。
- 天氣倍率從 1.00 起，查詢期間最高溫 >=32°C 加 0.10，最高降雨機率 >=60% 再加 0.10，最高 1.20。所有商品共用此規則。
- 狀態 COMPLETE：溫度點間距及邊界容許最多三小時，降雨與天氣現象時段完整覆蓋有效查詢範圍。UNAVAILABLE：沒有有效氣溫與降雨；其他為 PARTIAL。部分／不可用回傳 warning。
- 上游授權／連線／回應問題轉 502，偵測到 timeout 轉 504。直接天氣 API 回錯誤；訂貨建議對 502／504 採倍率 1.00 繼續計算，並回 UNAVAILABLE 與 warning。參數錯誤不在此降級範圍。

### 訂貨建議公式

```text
期間天數 = endDate - startDate + 1
歷史起日 = startDate.minusMonths(1)
歷史迄日 = 歷史起日 + 期間天數 - 1
historicalSalesQuantity = 歷史區間內未退貨銷售數量加總
outstandingQuantity = 所有未刪除 SUBMITTED 採購單的該商品數量
availableStock = currentStock + outstandingQuantity
weatherAdjustedSalesQuantity = ceil(historicalSalesQuantity × weatherFactor)
recommendedQuantity = max(0, weatherAdjustedSalesQuantity + safetyStock - availableStock)
needsReorder = recommendedQuantity > 0
```

歷史 SQL 使用 `[起日00:00, 迄日次日00:00)`，沒有銷售資料以 0 計。月底依 Java minusMonths 調整日期，歷史迄日再依天數推算，並非各自把起迄日減一個月。未到貨量沒有依預計到貨日期篩選。批次建議只計算啟用未刪除商品，一次共用天氣查詢及已送出採購單，再逐商品查歷史量；單品停用／已刪除回 404。

例如歷史銷量 100、倍率 1.20、安全庫存 10、現貨 30、未到貨 20，建議量為 max(0,120+10-30-20)=80。

## 7. API 契約

一般成功回應直接是 DTO 或陣列，沒有額外統一成功包裝或分頁。新增回 201，刪除回 204，其餘一般成功回 200。JSON 欄位使用 camelCase。

| 路徑 | 方法與功能 |
| --- | --- |
| `/` | GET 首頁 index |
| `/api/product-categories` | GET 列表、POST 新增 |
| `/api/product-categories/{id}` | GET 單筆、PUT 修改、DELETE 軟刪除 |
| `/api/products` | GET 列表、POST 新增 |
| `/api/products/{id}` | GET 單筆、PUT 修改、DELETE 軟刪除 |
| `/api/products/{id}/status?status=0或1` | PATCH 停用／啟用 |
| `/api/suppliers` | GET 列表、POST 新增 |
| `/api/suppliers/{id}` | GET 單筆、PUT 修改、DELETE 軟刪除 |
| `/api/suppliers/{id}/status?status=0或1` | PATCH 停用／啟用 |
| `/api/sales-records` | GET 列表、POST 銷售扣庫存 |
| `/api/sales-records/{id}` | GET 單筆、DELETE 整筆退貨 |
| `/api/purchase-orders` | GET 列表、POST 建立草稿 |
| `/api/purchase-orders/{id}` | GET 單筆、PUT 修改草稿、DELETE 軟刪除 |
| `/api/purchase-orders/{id}/submit` | POST 送出 |
| `/api/purchase-orders/{id}/receive` | POST 整單到貨 |
| `/api/purchase-orders/{id}/cancel` | POST 取消 |
| `/api/weather/forecast` | GET 天氣；四個必填 query：city、district、startDate、endDate |
| `/api/order-recommendations` | GET 全部有效商品建議；相同四個 query |
| `/api/order-recommendations/{productId}` | GET 單品建議；相同四個 query |

日期 query 格式 YYYY-MM-DD。範例日期須換成實際今天至後天：

```text
GET /api/weather/forecast?city=臺北市&district=中正區&startDate=2026-11-22&endDate=2026-11-24
```

主要請求 body 範例（ID 需已存在）：

```json
{"name":"飲料","parentId":null}
```

```json
{"categoryId":2,"sku":"DRINK-001","name":"瓶裝水","price":20.00,"unit":"瓶","currentStock":100,"safetyStock":20}
```

```json
{"name":"範例供應商","contactPerson":"窗口","phone":"","email":null,"address":""}
```

```json
{"productId":1,"quantity":2,"unitPrice":20.00}
```

```json
{"supplierId":1,"expectedDeliveryDate":"2026-11-24","remark":"補貨","items":[{"productId":1,"quantity":10}]}
```

最後一例供新增／修改採購使用；不需傳 unitPrice、subtotal、totalAmount、orderStatus。

錯誤格式 `ErrorResponse` 為 timestamp、status、message、path。GlobalExceptionHandler 明確處理 ResponseStatusException 與 MethodArgumentNotValidException（取第一個欄位錯誤）。常見狀態 400 驗證錯誤、404 不存在、409 重複／庫存不足／狀態衝突、502／504 上游故障。IllegalArgumentException、參數轉型、DB constraint 等尚未全部納入同一處理；不能保證所有錯誤都有同一格式或一定是預期 400。

## 8. 前端結構與功能

`HomeController` 回傳 Thymeleaf index。`app.js` 綁定導覽、日期初始化與各模組，切換 dashboard、weather、recommendations、categories、products、suppliers、sales、orders 區塊；多數管理頁進入時才載入資料。

| 檔案 | 職責 |
| --- | --- |
| common.js | API 路徑常數、fetch／JSON、DOM 工具、訊息、數字／日期格式、載入狀態及統計卡 |
| weather.js | 查詢、每日天氣卡、摘要及原生 SVG 圖表 |
| order-recommendation.js | 查詢與呈現訂貨建議、補貨相關畫面互動 |
| product-category.js | 兩層分類表格與新增／修改／刪除 |
| product.js | 商品表單、庫存、安全庫存與商品狀態管理 |
| supplier.js | 供應商表單、狀態與刪除 |
| sales-record.js | 銷售表單、商品價格帶入、退貨及刷新庫存列表 |
| purchase-order.js | 採購單表單、明細、草稿修改與狀態操作 |
| demo-crud.js | 可配置的舊 Demo JSON CRUD 工具；目前 app.js 沒有匯入，不能以其註解推斷正式模組未實作 |
| style.css | 版面、導覽、卡片、表格、表單與響應式樣式 |

以 common.js 的 API 常數維持同源請求。DOM 工具用 textContent 呈現文字。現有 app.js 對建議及採購模組加上 `?v=20261121-2`；調整模組載入時須留意瀏覽器快取與匯入 URL 一致性。沒有 package.json、Node 建置或前端框架流程。

## 9. 測試、限制及後續討論重點

目前只有一個 @SpringBootTest contextLoads，沒有業務單元測試、API 契約測試或前端自動化測試。本次僅新增文件並靜態核對原始碼，沒有執行建置、測試、啟動應用或呼叫真實 CWA；不應將此文件視為已驗證整體執行成功。

以下是依程式觀察的待討論項目，不代表已授權或已完成改造：

- 交易存在，但 Entity／Repository 未見 @Version 或顯式鎖定；多人同時銷售／到貨時應評估庫存競態及狀態重複處理。
- 沒有 Spring Security 相依、登入與角色權限設計，也沒有看到正式部署、Docker、CI 測試工作流程。
- 未提供獨立進貨成本、商品與供應商配對、部分到貨、部分退貨、庫存異動帳、圖片上傳、分頁與一般匯入／匯出功能。
- 啟用列表不含停用資料；停用後如需從畫面重新啟用，應檢查其資料取得與操作流程。
- 所有品類使用同一天氣倍率；未到貨量未按到貨日篩選；無歷史銷售時主要靠安全庫存形成建議。這些都是目前公式的實際假設。
- CWA client 未見明確的連線／讀取 timeout、重試或快取設定；雖有 timeout 錯誤轉換，實際期限仍取決於底層 client。
- 銷售時間與多數時間戳使用系統 LocalDateTime，天氣查詢另指定 Asia/Taipei；部署時需統一 JVM、DB 與營運時間定義。
- 舊採購單的供應商快照可能為 null；顯示策略或資料回填應另行決定。
- 建議查詢目前逐商品加總歷史並遍歷已送出明細，資料量增加時可考慮批次聚合及避免 lazy loading 的額外查詢。

建議手動驗證順序：建立第一／第二層分類 → 商品 → 供應商 → 草稿 → 送出（未到貨增加）→ 到貨（現貨增加、未到貨減少）→ 銷售扣庫存 → 退貨回補 → 天氣與建議查詢；再驗證非法狀態、重複 SKU／email／明細與上游故障降級。

## 10. 交接與修改慣例

維持 Controller／Service／Repository 分工，對外用 Request／Response DTO，不直接回傳 Entity。金額保持 BigDecimal，庫存更新與紀錄應在同一交易；延續軟刪除、歷史快照與狀態檢查。改欄位時同步檢查 migration、Entity、DTO、Service 轉換、JS 表單與顯示。

`.env`、target、IDE 設定等由 .gitignore 排除。`.github/modernize/java-upgrade` 目前為現代化工具 hook 腳本，不是應用程式執行功能或 CI。

整理開始時已有四個未提交修改：`static/js/app.js`、`static/js/order-recommendation.js`、`static/js/purchase-order.js`、`templates/index.html`（皆位於 src/main/resources）。本文件依這些工作目錄內容整理；未改動它們。

帶到新討論可附上以下說明：

> 請先依這份 Agent.md 理解現有系統，區分已實作行為與待討論事項。專案是 Java 21／Spring Boot／MySQL／Flyway，前端為 Thymeleaf + 原生 ES modules。討論新需求時請列出受影響的資料表、API、業務規則與前端模組；若需要精確修改程式，請再取得對應原始碼，不要假設文件包含全部實作細節。

## 附錄 A：Request／Response 欄位索引

以下由目前 DTO 宣告擷取，保留 Java 型別；具體必填與長度條件請參照原 Request 的 Jakarta Validation。

### ProductCategoryRequest

```java
private String name;
private Long parentId;
```

### ProductRequest

```java
private Long categoryId;
private String sku;
private String name;
private BigDecimal price;
private String unit;
private Integer currentStock;
private Integer safetyStock;
```

### PurchaseOrderItemRequest

```java
private Long productId;
private Integer quantity;
```

### PurchaseOrderRequest

```java
private Long supplierId;
private LocalDate expectedDeliveryDate;
private String remark;
private List< @NotNull(message = "採購明細不可為空") @Valid PurchaseOrderItemRequest > items;
```

### PurchaseOrderUpdateRequest

```java
private Long supplierId;
private LocalDate expectedDeliveryDate;
private String remark;
private List< @NotNull(message = "採購明細不可為空") @Valid PurchaseOrderItemRequest > items;
```

### SalesRecordRequest

```java
private Long productId;
private Integer quantity;
private BigDecimal unitPrice;
```

### SupplierRequest

```java
private String name;
private String contactPerson;
private String phone;
private String email;
private String address;
```

### OrderRecommendationResponse

```java
private Long productId;
private String productName;
private String sku;
private Integer currentStock;
private Integer safetyStock;
private LocalDate forecastStartDate;
private LocalDate forecastEndDate;
private LocalDate historicalStartDate;
private LocalDate historicalEndDate;
private Long historicalSalesQuantity;
private Long outstandingQuantity;
private Long availableStock;
private BigDecimal weatherFactor;
private Long weatherAdjustedSalesQuantity;
private Long recommendedQuantity;
private Boolean needsReorder;
private String forecastStatus;
private String warning;
```

### ProductCategoryResponse

```java
private Long id;
private String name;
private Long parentId;
private String parentName;
private Integer status;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

### ProductResponse

```java
private Long id;
private Long categoryId;
private String categoryName;
private String sku;
private String name;
private BigDecimal price;
private String unit;
private Integer currentStock;
private Integer safetyStock;
private Integer status;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

### PurchaseOrderItemResponse

```java
private Long id;
private Long productId;
private String sku;
private String productName;
private Integer quantity;
private BigDecimal unitPrice;
private BigDecimal subtotal;
```

### PurchaseOrderResponse

```java
private Long id;
private Long supplierId;
private String supplierNameSnapshot;
private String supplierContactPersonSnapshot;
private String supplierPhoneSnapshot;
private String supplierEmailSnapshot;
private String supplierAddressSnapshot;
private LocalDateTime orderDate;
private LocalDate expectedDeliveryDate;
private String orderStatus;
private BigDecimal totalAmount;
private String remark;
private List<PurchaseOrderItemResponse> items;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

### SalesRecordResponse

```java
private Long id;
private Long productId;
private String productName;
private String sku;
private Integer quantity;
private BigDecimal unitPrice;
private BigDecimal totalAmount;
private LocalDateTime dateTime;
private LocalDateTime createdAt;
```

### SupplierResponse

```java
private Long id;
private String name;
private String contactPerson;
private String phone;
private String email;
private String address;
private Integer status;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

### WeatherConditionResponse

```java
private OffsetDateTime startTime;
private OffsetDateTime endTime;
private String weather;
private String weatherCode;
```

### WeatherDailyResponse

```java
private LocalDate date;
private Integer maxTemperature;
private Integer minTemperature;
private String weather;
private String weatherCode;
```

### WeatherForecastResponse

```java
private String city;
private String district;
private LocalDate startDate;
private LocalDate endDate;
private Integer maxTemperature;
private Integer maxPrecipitationProbability;
private BigDecimal weatherFactor;
private String forecastStatus;
private String warning;
private List<WeatherDailyResponse> dailyForecasts;
private List<WeatherTemperatureResponse> temperatureForecasts;
private List<WeatherPrecipitationResponse> precipitationForecasts;
private List<WeatherConditionResponse> weatherConditions;
```

### WeatherPrecipitationResponse

```java
private OffsetDateTime startTime;
private OffsetDateTime endTime;
private Integer precipitationProbability;
```

### WeatherTemperatureResponse

```java
private OffsetDateTime dataTime;
private Integer temperature;
```

## 附錄 B：應用程式與建置檔案清單

```text
mvnw
mvnw.cmd
notes/DB_README.md
pom.xml
src/main/java/com/example/weather_order_assistant_system/client/CwaApiClient.java
src/main/java/com/example/weather_order_assistant_system/client/CwaDatasetMapping.java
src/main/java/com/example/weather_order_assistant_system/controller/HomeController.java
src/main/java/com/example/weather_order_assistant_system/controller/OrderRecommendationController.java
src/main/java/com/example/weather_order_assistant_system/controller/ProductCategoryController.java
src/main/java/com/example/weather_order_assistant_system/controller/ProductController.java
src/main/java/com/example/weather_order_assistant_system/controller/PurchaseOrderController.java
src/main/java/com/example/weather_order_assistant_system/controller/SalesRecordController.java
src/main/java/com/example/weather_order_assistant_system/controller/SupplierController.java
src/main/java/com/example/weather_order_assistant_system/controller/WeatherController.java
src/main/java/com/example/weather_order_assistant_system/entity/Product.java
src/main/java/com/example/weather_order_assistant_system/entity/ProductCategory.java
src/main/java/com/example/weather_order_assistant_system/entity/PurchaseOrder.java
src/main/java/com/example/weather_order_assistant_system/entity/PurchaseOrderItem.java
src/main/java/com/example/weather_order_assistant_system/entity/SalesRecord.java
src/main/java/com/example/weather_order_assistant_system/entity/Supplier.java
src/main/java/com/example/weather_order_assistant_system/exception/ErrorResponse.java
src/main/java/com/example/weather_order_assistant_system/exception/GlobalExceptionHandler.java
src/main/java/com/example/weather_order_assistant_system/repository/ProductCategoryRepository.java
src/main/java/com/example/weather_order_assistant_system/repository/ProductRepository.java
src/main/java/com/example/weather_order_assistant_system/repository/PurchaseOrderItemRepository.java
src/main/java/com/example/weather_order_assistant_system/repository/PurchaseOrderRepository.java
src/main/java/com/example/weather_order_assistant_system/repository/SalesRecordRepository.java
src/main/java/com/example/weather_order_assistant_system/repository/SupplierRepository.java
src/main/java/com/example/weather_order_assistant_system/request/ProductCategoryRequest.java
src/main/java/com/example/weather_order_assistant_system/request/ProductRequest.java
src/main/java/com/example/weather_order_assistant_system/request/PurchaseOrderItemRequest.java
src/main/java/com/example/weather_order_assistant_system/request/PurchaseOrderRequest.java
src/main/java/com/example/weather_order_assistant_system/request/PurchaseOrderUpdateRequest.java
src/main/java/com/example/weather_order_assistant_system/request/SalesRecordRequest.java
src/main/java/com/example/weather_order_assistant_system/request/SupplierRequest.java
src/main/java/com/example/weather_order_assistant_system/response/OrderRecommendationResponse.java
src/main/java/com/example/weather_order_assistant_system/response/ProductCategoryResponse.java
src/main/java/com/example/weather_order_assistant_system/response/ProductResponse.java
src/main/java/com/example/weather_order_assistant_system/response/PurchaseOrderItemResponse.java
src/main/java/com/example/weather_order_assistant_system/response/PurchaseOrderResponse.java
src/main/java/com/example/weather_order_assistant_system/response/SalesRecordResponse.java
src/main/java/com/example/weather_order_assistant_system/response/SupplierResponse.java
src/main/java/com/example/weather_order_assistant_system/response/WeatherConditionResponse.java
src/main/java/com/example/weather_order_assistant_system/response/WeatherDailyResponse.java
src/main/java/com/example/weather_order_assistant_system/response/WeatherForecastResponse.java
src/main/java/com/example/weather_order_assistant_system/response/WeatherPrecipitationResponse.java
src/main/java/com/example/weather_order_assistant_system/response/WeatherTemperatureResponse.java
src/main/java/com/example/weather_order_assistant_system/service/OrderRecommendationService.java
src/main/java/com/example/weather_order_assistant_system/service/ProductCategoryService.java
src/main/java/com/example/weather_order_assistant_system/service/ProductService.java
src/main/java/com/example/weather_order_assistant_system/service/PurchaseOrderService.java
src/main/java/com/example/weather_order_assistant_system/service/SalesRecordService.java
src/main/java/com/example/weather_order_assistant_system/service/SupplierService.java
src/main/java/com/example/weather_order_assistant_system/service/WeatherService.java
src/main/java/com/example/weather_order_assistant_system/WeatherOrderAssistantSystemApplication.java
src/main/resources/application.properties
src/main/resources/db/migration/V001__create_product_categories.sql
src/main/resources/db/migration/V002__create_products.sql
src/main/resources/db/migration/V003__create_suppliers.sql
src/main/resources/db/migration/V004__create_sales_records.sql
src/main/resources/db/migration/V005__create_purchase_orders.sql
src/main/resources/db/migration/V006__create_purchase_order_items.sql
src/main/resources/db/migration/V007__add_purchase_order_supplier_snapshot.sql
src/main/resources/static/css/style.css
src/main/resources/static/js/app.js
src/main/resources/static/js/common.js
src/main/resources/static/js/demo-crud.js
src/main/resources/static/js/order-recommendation.js
src/main/resources/static/js/product.js
src/main/resources/static/js/product-category.js
src/main/resources/static/js/purchase-order.js
src/main/resources/static/js/sales-record.js
src/main/resources/static/js/supplier.js
src/main/resources/static/js/weather.js
src/main/resources/templates/index.html
src/test/java/com/example/weather_order_assistant_system/WeatherOrderAssistantSystemApplicationTests.java
```
