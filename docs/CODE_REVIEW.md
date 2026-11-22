# 程式碼架構與品質審查報告

**日期**：2026-11-22　**方式**：對已整理的原始碼、前端、POM、設定及 11 個 Flyway Migration 進行唯讀靜態審查；沒有修改業務程式。

**基準版本**：`weather_order_assistant_system_formatted_flyway_fixed.zip`（V005/V006 保留已套用資料庫的原始 checksum）。本報告的「風險」是**原始碼推導或部署前待驗證項目**，不是已重現的實際故障。

## 1. 整體評估

| 項目 | 評估 | 理由 |
|---|---|---|
| 分層與模組 | 良好、可維護 | Controller／Service／Repository／DTO 分開，前端模組化，Flyway 管理 DB |
| 基礎安全 | 已有良好起點 | Spring Security 表單驗證、CSRF 啟用、BCrypt、修改密碼登出 |
| 資料一致性 | 關鍵流程待加強 | 批次銷售採交易及庫存列鎖；採購到貨、商品編輯有不同鎖定策略 |
| 操作規則 | 可讀且有測試 | 台灣日期政策抽出純函式，軟刪除、退貨規則明確 |
| 測試 | 核心規則有測試，整合覆蓋不足 | 共 7 個 Java 測試檔；未見 MySQL Testcontainers 併發或端到端測試 |
| 雲端就緒 | 尚未完成 | 無 Docker、健康檢查、CI/CD、`.gitignore`、正式環境 profile |

## 2. 優先修正清單

風險分級：**P0** 立即阻擋正式公開、**P1** 部署前必處理、**P2** 建議補齊、**P3** 可逐步重構。以下不代表已有利用證據。

| 優先 | 問題／程式證據 | 風險與建議 | 驗證方式 |
|---|---|---|---|
| P1 | **到貨入庫的並行更新**：`PurchaseOrderService.receiveOrder()` 約 L510–640 由普通 `findByIdAndDeletedAtIsNull()` 載入採購單、直接更新 `item.getProduct().currentStock`；`PurchaseOrderRepository` 無 PESSIMISTIC_LOCK 查詢 | 在兩個同時到貨請求、或與銷售同時更新時，可能發生重複處理／庫存遺失更新。將訂單狀態檢查與庫存讀寫納入一致的列鎖／版本控制，交易內以固定商品順序加鎖 | MySQL 併發兩次 `/receive` 與 `/sales-records/batch`；核對最終庫存和採購狀態 |
| P1 | **商品編輯可覆寫庫存**：`ProductService.updateProduct()` 約 L196–288 直接 `setCurrentStock(request.getCurrentStock())`，查詢未加鎖 | 管理員編輯舊畫面時，可能覆蓋同時發生的銷售／到貨庫存。規劃獨立庫存調整 API（含原因）或 `@Version`／悲觀鎖 | 兩個並行交易：商品編輯 vs 銷售／退貨／到貨 |
| P1 | **缺少 Git 敏感檔保護**：專案根目錄無 `.gitignore`，`application.properties` 載入根目錄 `.env` | 上 GitHub 前可能誤提交 DB 密碼、CWA Key、帳號初始化密碼。先加入忽略清單並跑 `git status`、秘密掃描 | 模擬 `.env`、`uploads/`、`target/`，確認 `git status --ignored` |
| P1 | **部署健康與自動復原不足**：`pom.xml` 未見 Actuator；無 Dockerfile、Docker Compose、正式環境 profile 或健康路由 | EC2 上難以建立 readiness、監控、失敗回滾與啟動順序。加入 Actuator（只公開安全 health）、檢查 DB 連線及部署 smoke test | 容器啟停、MySQL 未就緒、外部預報超時情境 |
| P1 | **部署前外部連線保護**：`CwaApiClient.java` 以預設 `RestClient.Builder` 建立客戶端，原始碼未明確設定 connect/read timeout | 上游停滯時請求等待時間難掌控。設定明確 timeout、可觀測日誌與有限重試（避免重複請求風暴） | 模擬 DNS、連線等待、延遲及上游 500；確認 502/504 |
| P2 | **SQL 詳細輸出預設開啟**：`application.properties` 有 `spring.jpa.show-sql=true` 及格式化 SQL | 正式環境產生過多輸出、可能揭露資料結構及敏感處理細節；分 profile 設定，正式關閉 SQL 印出 | 使用 `prod` profile 啟動，確認日誌輸出 |
| P2 | **修改密碼前後端限制不一致**：`static/js/account.js` 約 L111 設定 UTF-8 **20 bytes**；`AccountService` 約 L58 和頁面文案允許 **72 bytes** | 使用者在前端被誤拒絕合法密碼；統一為 72 bytes，並增加多位元組中文字元測試 | 21、72、73 bytes 密碼組合 |
| P2 | **查詢清單一次撈全部**：`SalesRecordService.findAllSalesRecords()`、`PurchaseOrderRepository.findByDeletedAtIsNullOrderByOrderDateDesc()`、`OrderRecommendationService` 的商品及採購單查詢 | 資料量增加後記憶體、資料庫 IO 與前端報表時間可能上升；建立後端日期查詢、分頁、統計聚合 | 模擬 10 萬筆銷售、1 萬筆採購，量測 P95、heap |
| P2 | **測試覆蓋缺口**：`src/test` 共 7 份 Java 測試類；未見正式使用 MySQL 的整合測試設定 | 退貨回補／到貨庫存／Flyway schema 欄位校驗、Session 安全需要可重跑的整合測試 | Testcontainers MySQL + MockMvc + CI `mvn verify` |
| P2 | **登入保護需壓測／限制**：`SecurityConfig.java` 表單登入支援單 Session，但未見登入錯誤次數與速率限制的獨立實作 | 公開網站可能遭撞密碼；以反向代理限流、監控與帳號政策防護 | 嘗試頻繁錯誤登入，檢查限流與記錄 |
| P2 | **資料時區需統一**：`PurchaseOrderTimePolicy` 顯式採 `Asia/Taipei`，`SalesRecordService` 使用 `LocalDateTime.now()`；多個 `DATETIME` 欄位未保存 offset | EC2 容器若採 UTC 預設時區，銷售日期、七日退貨期限、報表分日可能與台灣營運時區不一致。統一 Java Clock／JVM TZ 並測跨日 | 使用 UTC 容器重跑台灣 00:00 邊界測試 |
| P2 | **錯誤格式未完全一致**：`GlobalExceptionHandler` 只處理 `ResponseStatusException` 與 `MethodArgumentNotValidException`；Spring Security 另自行回 `{"status", "message"}` | 各種驗證、JSON 格式錯誤、登入過期可能呈現不同 schema；可逐步統一 JSON ErrorResponse，勿直接暴露堆疊 | 缺欄位、格式錯、沒有 CSRF、未登入、未知路徑 |
| P2 | **Session Registry 為記憶體**：`SecurityConfig` 使用 `SessionRegistryImpl` 與單一 Spring Boot instance 設計 | 單一 EC2 一個應用容器可用；未來若多副本，Session 與「只允許一個登入」無法天然跨節點一致。若要擴容須另設共享 Session | 雙實例登入與 session 覆核 |
| P2 | **冷飲需求倍率通用性**：`WeatherFactorCalculator` 清楚標註冷飲 Demo 規則，`OrderRecommendationService` 仍可為每項商品計算天氣量 | 操作上可選擇是否套用，但不宜宣稱所有品類通用；在 README、UI 及文件維持限制說明 | 用非冷飲商品手動確認套用範圍 |
| P3 | **類別過長**：`PurchaseOrderService` 約 1292 行、`WeatherService` 約 1018 行、`OrderRecommendationService` 約 593 行 | 邏輯目前可用，但進一步擴充與測試成本增加；未來以查詢、狀態政策、DTO 組裝、氣象解析等職責漸進拆分 | 先新增測試，逐段移動，不大幅一次重構 |
| P3 | **重複日期政策**：`PurchaseOrderTimePolicy` 及 `PurchaseDeliveryDatePolicy` 均有 12:00 截單計算邏輯 | 日後可能兩處改動不同步；待單元測試涵蓋後整理成單一政策來源 | 比較所有截止邊界測試 |
| P3 | **閒置設定與前端檔案**：`app.upload.product-image-dir`／`demo-crud.js` 有歷史用途，但 Controller 清單未見圖片上傳 API | README 不應宣稱圖片上傳功能；可在部署前釐清是否刪除未用設定或歸檔 Demo 程式 | 檢查實際 imports、頁面有無使用 |

## 3. 已值得保留的實作

1. 銷售批次：`SalesRecordService` 類別有 `@Transactional`；`createSalesBatch` 以相同 UUID 分組、按商品 ID 排序，從 `ProductRepository.findByIdForStockUpdate` 取得悲觀鎖。
2. 退貨：退貨先鎖商品，再鎖銷售紀錄，更新累計退貨量，維持單一交易；Flyway V008 含累計量 `CHECK`。
3. 狀態保存：`products`／`suppliers`／`sales_records`／`purchase_orders` 支援歷史關聯及軟刪除，V007 保存供應商資料快照。
4. 安全基礎：Spring Security 預設保護全站、CSRF 未關閉、登入成功換 Session ID、BCrypt 成本 12、修改密碼使 Session 失效。
5. 天氣分層：`CwaApiClient` 呼叫、`WeatherService` 解析、`WeatherFactorCalculator` 計算；同一批商品的說明視窗共用天氣資料。
6. 過去已套用的 Flyway V005/V006 保持原始位元內容；不應重新格式化或執行 repair 遮蔽問題。

## 4. 建議的實作順序（本階段不變更程式）

**Gate 1｜資料一致性**：先補併發重現測試，再處理到貨入庫與商品編輯的鎖定方式；確認無重複入庫與庫存遺失更新。

**Gate 2｜安全設定**：`.gitignore`、秘密掃描、prod profile、時區、資料庫專用帳號、Session Cookie HTTPS、SQL log 關閉。

**Gate 3｜可部署性**：建立 Dockerfile、Compose、healthcheck、持久化資料、備份與部署回滾策略。

**Gate 4｜CI**：GitHub Actions 執行 `test`／`verify`／容器建置，接入 MySQL Testcontainers、資料庫 migration 驗證。

**Gate 5｜CD**：OIDC → ECR → EC2 部署、健康檢查後切換、失敗回復，設定部署手動核准。

## 5. 本次審查邊界

這是一份**靜態 Code Review**，並非完整滲透測試、效能測試、雲端架構驗證或逐一執行 API 的整合驗收。可從原始碼推導的競爭條件和缺少保護措施已列出，但未宣稱有重現故障；實際修改前建議先重跑既有驗收，並保留乾淨的基準 Git commit。
