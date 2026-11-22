# P1 風險修正｜修改內容與本機驗收

版本基準：上一階段已驗收的 `weather_order_project_reviewed_with_docs.zip`。本次只修改 P1 相關程式、加入單元測試與這份說明。**沒有覆蓋你的 `.gitignore`、沒有更動任何既有 Flyway SQL（V001～V011）。**

> 狀態：程式修改與靜態語法檢查完成；Maven、Spring Boot、Postman、MySQL 真實併發驗收請在本機執行，未驗收前不可標為全部完成。

## 1. 本次實作的 P1 修正

| 項目 | 程式變更 | 預期效果 |
|---|---|---|
| 採購到貨重複入庫 | `PurchaseOrderRepository.findActiveByIdForUpdate` + `PurchaseOrderService` | 在交易中先鎖主單；第二次到貨等前一筆提交，再讀到 `RECEIVED`，拒絕重複入庫（409） |
| 多商品入庫與銷售競爭 | `receiveOrder()` | 主單鎖後，按商品 ID 遞增取得 `ProductRepository.findByIdForStockUpdate()` 列鎖；與銷售／退貨共用相同商品鎖定策略 |
| 同一張採購單其他狀態競爭 | `submitOrder`、`cancelOrder`、`updateOrder`、`deleteOrder` | 同張採購單的狀態變更、修改和軟刪除也先鎖主單，防止同時讀舊狀態 |
| 商品編輯覆寫庫存 | `ProductRequest`、`ProductService`、`product.js` | 商品編輯時帶入 `expectedCurrentStock`（開啟畫面時的舊庫存）；後端鎖商品後比對最新庫存，不一致時拒絕 409 |
| 商品停用／刪除競爭 | `ProductService` | 停用、軟刪除也使用商品列鎖；不再與銷售／入庫同時以未鎖定的舊商品狀態寫入 |
| 部署健康檢查 | `pom.xml`、`SecurityConfig.java`、`application.properties` | 安裝 Actuator；只開放 health，提供 `/actuator/health/liveness`、`/actuator/health/readiness`，readiness 包含 MySQL 狀態 |
| 上游天氣服務連線掛起 | `CwaApiClient.java`、`application.properties` | CWA 專用 HTTP Client 連線逾時 5 秒、單次請求逾時 10 秒；不更動原本的錯誤處理和天氣倍率演算法 |
| 預備正式環境設定 | `application-prod.properties` | 正式環境以 `DB_URL` 注入連線、停用 SQL 列印、強制安全 Cookie（需要 HTTPS） |

**`.gitignore` 不在本次補丁內。** 依你說明，現有 `.gitignore` 已排除 `.env`。上 GitHub 前仍建議用 `git ls-files .env` 檢查 `.env` 是否曾被追蹤：被 Git 追蹤的檔案不會因新增忽略規則而自動解除追蹤。

### 商品編輯相容性注意

**新增商品 POST 不變**，依舊用 `currentStock` 填入新商品初始庫存。修改商品 PUT 多一個選填的 `expectedCurrentStock`（原畫面庫存快照）：

```json
{
  "categoryId": 2,
  "sku": "TEA001",
  "name": "紅茶",
  "price": 25.00,
  "unit": "瓶",
  "currentStock": 15,
  "expectedCurrentStock": 12,
  "safetyStock": 20
}
```

- 若最新庫存仍是 12，允許依原先表單把庫存改成 15。
- 若該商品已銷售、入庫或退貨，導致最新庫存變成 10，回傳 **409**；不接受舊表單直接覆寫，使用者需重新載入。
- 舊版 PUT 若**未提供** `expectedCurrentStock`，仍可修改商品名稱、分類、售價等**基本資料**，前提是要求的 `currentStock` 為空或等於資料庫最新值；若嘗試改寫庫存則回傳 **409**。這是為確保安全的刻意介面限制。直接用 Postman 修改庫存請先查詢最新商品庫存後填入 `expectedCurrentStock`。
- 保留「編輯商品時可調整庫存」的操作模式；**尚未建立獨立庫存異動歷史表或盤點原因紀錄**，這屬於後續功能，不在本次 P1 範圍。
- 即使使用者編輯時未更動庫存，只要表單開啟後發生銷售／退貨／入庫，最新版前端也會因快照不一致得到 409，避免基於過期資料儲存。

## 2. 建置及基本驗收（Windows）

1. 先備份已驗收的專案與 MySQL，將補丁解壓到**相同相對路徑**，不要覆蓋既有 `.gitignore` 和 `.env`。
2. 專案根目錄執行：

   ```powershell
   .\mvnw.cmd clean test
   .\mvnw.cmd spring-boot:run
   ```

3. 確認 Flyway V001～V011 checksum 均通過；此次**沒有新增 Migration**。
4. 測試原有商品新增、編輯、停用／啟用、銷售、退貨與採購到貨功能。

## 3. Postman：商品舊庫存防護

1. 選一個測試商品，用 `GET /api/products/{id}` 記住庫存，例如 12。
2. 模擬在此期間透過正常銷售流程扣庫存 2 件，確認最新庫存為 10。
3. 用 `PUT /api/products/{id}` 送出舊表單：`currentStock=12`、`expectedCurrentStock=12`，其他欄位填有效值。
4. 預期 **HTTP 409**：`商品庫存已變更，請重新載入商品資料後再儲存`；商品庫存仍為 10。
5. 再次 GET 取得最新庫存 10，使用 `currentStock=15`、`expectedCurrentStock=10` 更新；預期 200，最新庫存 15。
6. 測試缺少 `expectedCurrentStock` 卻把庫存 15 改成 16；預期 409。僅修改商品名稱、維持庫存 15 則應成功。
7. 瀏覽器實測：開啟商品編輯表單後，從另一個 Postman 執行銷售；返回舊表單按儲存，應提示更新庫存而非靜默覆寫。

## 4. Postman／MySQL：採購入庫與併發

1. 建立測試商品（庫存例如 10），建立含該商品 5 件的採購單並送出，使狀態成為 `SUBMITTED`。
2. 單獨按「確認到貨」：預期 200，訂單變 `RECEIVED`，庫存 10 → 15。
3. 再次呼叫同張採購單到貨：預期 **409**，庫存仍為 15。
4. **真正併發驗證**（需使用兩個獨立 HTTP 請求／Session 或並行測試工具）：重新建立一張 `SUBMITTED` 採購單，使兩次 `/receive` 在相近時間同時發送。預期只有一筆成功，另一筆收到 409，庫存只增加一次。
5. **銷售同時入庫**：開始前庫存 10、採購 +5、銷售 −3，兩種請求並行完成後，預期庫存 = **12**，且只有一次到貨計入。
6. **跨商品順序**：同一張採購單有不同商品，與多商品銷售同時執行；檢查是否能完成且最終數量正確。MySQL 死結有可能因其他事務的鎖定範圍而發生，仍需監控錯誤記錄及必要的重試策略。
7. 檢查已停用商品的歷史 `SUBMITTED` 採購單仍能確認到貨；歷史快照不應改變。

> 注意：Mockito 單元測試能驗證 Repository 鎖定方法有被呼叫、狀態驗證及計算，**不能證明**兩個真實 MySQL 交易會互斥；第 4、5、6 項應在真實 MySQL 環境執行。

## 5. Actuator 健康檢查與安全

本機啟動後，未登入直接呼叫：

```http
GET http://localhost:8080/actuator/health
GET http://localhost:8080/actuator/health/liveness
GET http://localhost:8080/actuator/health/readiness
```

- 正常應回傳 `200` 與 `{"status":"UP"}`。
- 若 MySQL 掛掉，readiness 應回 `503`（`DOWN`），但應用程式 JVM 仍存活時 liveness 可以保持 UP；不要在未備份的正式資料庫上做中斷測試。
- `/actuator/env`、`/actuator/configprops`、`/actuator/metrics` 不公開；不得對匿名使用者揭露環境變數、帳密或堆疊。
- Docker 的存活探針未來可使用 liveness，而部署後接流量前應檢查 readiness。

## 6. 天氣連線逾時及 prod 設定

`application.properties` 中新增：

```properties
cwa.api.connect-timeout-ms=5000
cwa.api.read-timeout-ms=10000
```

氣象請求逾時時，既有 `WeatherService` 會將網路逾時轉為 HTTP 504；上游 HTTP 錯誤仍回 502。**不要以暫停正式中央氣象署網路作為必要驗收**，可以在獨立測試環境模擬慢速或無法連接的上游。

正式部署時啟用 `SPRING_PROFILES_ACTIVE=prod`：

- 設定 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`CWA_API_KEY` 等外部機密。
- `application-prod.properties` 會強制 `server.servlet.session.cookie.secure=true`，**必須先完成 HTTPS 反向代理與 Cookie 設定**，否則本機 HTTP 登入可能失敗。
- Dockerfile、Compose、EC2、ECR 與 GitHub Actions 尚未建立；本次只鋪好健康檢查及 prod 基礎設定。

## 7. 尚需追蹤的項目

- 原報告 P1 `.gitignore` 缺失是因交付 ZIP 未包含原檔；你已確認本機有該檔並排除 `.env`，本次不修改。
- 由於 MySQL 實際鎖順序、隔離等級及併發負載需測試，目前還不能宣稱已完全排除所有死結及競爭。
- 大規模壓力測試、庫存異動稽核、限流、CI/CD、自動回滾將在後續階段處理。
