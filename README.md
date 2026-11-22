# 天氣訂貨輔助系統｜Weather Order Assistant System

以 **Java 21 / Spring Boot 4.1.1 / MySQL / Spring Data JPA / Flyway** 建立的單店門市管理作品。結合銷售、庫存、供應商供貨與中央氣象署七天天氣預報，提供一般訂貨量與天氣調整後訂貨量，並能由使用者選擇項目建立採購草稿。

> 文件基準：2026-11-22 已整理版本（使用 Flyway V005/V006 原始 checksum 修復版）。本文描述**已存在的程式**，不表示 Docker、AWS 或 CI/CD 已部署完成。

## 主要功能

- **登入與帳號**：Spring Security 表單登入、Session、CSRF、BCrypt 密碼雜湊、本人帳號資訊與修改密碼；修改後需重新登入。
- **首頁儀表板**：商品、供應商、待處理採購單、補貨摘要、近七日銷售與天氣趨勢。
- **天氣預報**：選擇縣市／行政區，查詢當日起七天內的氣象資料；含每日、時段氣溫、降雨、濕度、天氣圖示。
- **天氣訂貨**：上週同期／上月同期淨銷量、庫存與未到貨量；提供一般量與天氣調整量，並可查看每日倍率計算明細。
- **商品與分類**：二層分類、商品與庫存、安全庫存、啟用／停用及軟刪除。
- **供應商與供貨**：供應商維護、商品對應供應商、採購價、供貨狀態；採購單保存供應商歷史快照。
- **銷售與退貨**：單品與多商品批次銷售、庫存扣減、七天內部分退貨及庫存回補；銷售統計、CSV 匯出、瀏覽器列印／另存 PDF。
- **採購單**：單張建立、依供應商批次建草稿、DRAFT → SUBMITTED → RECEIVED／CANCELLED、到貨入庫、跨日與中午截單日期防呆。
- **後台操作體驗**：繁體中文 Thymeleaf 單頁後台、模組化 ES Modules、暖色系設計、操作說明視窗。

## 技術架構

| 分層 | 技術 |
|---|---|
| 前端 | Thymeleaf、HTML、CSS、JavaScript ES Modules、Fetch、SVG 圖表 |
| 後端 | Java 21、Spring Boot 4.1.1、Spring MVC、Spring Security、Validation |
| 資料層 | Spring Data JPA / Hibernate、MySQL、Flyway V001～V011 |
| 外部資料 | 中央氣象署公開資料 API，`RestClient` 呼叫 |
| 測試 | JUnit 5、Mockito（含核心純函式與部分 Service 測試） |
| 建置 | Maven Wrapper |

**目前部署型態**：程式是單一 Spring Boot 執行個體，前端頁面和 REST API 同源提供；登入 Session 由本機記憶體追蹤。尚無 Docker Compose、EC2、GitHub Actions 工作流程。

## 在本機執行

前置條件：JDK 21、MySQL 8.0.16+、可下載 Maven 依賴的網路環境、中央氣象署 API 金鑰。由於既有 Flyway 使用 `CHECK`，不建議用舊版 MySQL。

1. 建立資料庫（名稱可自訂，例如）：

   ```sql
   CREATE DATABASE weather_order_assistant_db
     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. 於專案根目錄建立**不加入 Git** 的 `.env`（以下只使用假資料）：

   ```dotenv
   DB_URL=jdbc:mysql://localhost:3306/weather_order_assistant_db?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Taipei
   DB_USERNAME=your_db_user
   DB_PASSWORD=replace_with_local_password
   CWA_API_KEY=replace_with_cwa_api_key
   SESSION_COOKIE_SECURE=false
   APP_ADMIN_BOOTSTRAP=true
   APP_ADMIN_USERNAME=admin-demo
   APP_ADMIN_PASSWORD=replace_with_a_12_character_or_longer_password
   ```

   **提醒：現有程式根目錄尚未提供 `.gitignore`；首次 `git add` 前請依 [`docs/DEPLOYMENT_ROADMAP.md`](docs/DEPLOYMENT_ROADMAP.md) 處理 `.env`、本機設定、資料庫與上傳檔案忽略規則。** 初始化首個管理員後應停用 `APP_ADMIN_BOOTSTRAP` 並清除初始化密碼。

3. Windows：` .\mvnw.cmd clean test `、` .\mvnw.cmd spring-boot:run `；Linux/macOS：` ./mvnw clean test `、` ./mvnw spring-boot:run `。
4. 瀏覽器開啟 `http://localhost:8080/login`，登入後由 `/home` 進入後台。
5. Flyway 自動套用 V001～V011；JPA 採 `validate`，**不要改動已在資料庫執行過的 Migration**，尤其 V005／V006 有已知 checksum 歷史。

`/home`、`/weather`、`/recommendations`、`/categories`、`/products`、`/suppliers`、`/sales`、`/orders` 由同一 `templates/index.html` 和 `router.js` 控制。

## 訂貨建議核心概念

- 歷史基準：上週同期 `WEEK` 或上月起始日向前一個月、同天數 `MONTH`（預設）；使用銷售數量扣除累計退貨數量。
- 一般建議：`max(0, 歷史淨銷量 + 安全庫存 - 現有庫存 - SUBMITTED未到貨數量)`。
- 天氣建議：`max(0, ceil(歷史淨銷量 × 天氣倍率) + 安全庫存 - 現有庫存 - SUBMITTED未到貨數量)`。
- 天氣倍率：每天 `1.00 + 溫度調整 + 高溫濕度調整 + 降雨調整`，再對日倍率做**算術平均、四捨五入兩位小數**；這是冷飲情境的 Demo 商業假設，不能宣稱適用所有商品。
- 同批商品建議共用一次預報與同一份倍率明細；空缺的降雨或濕度資料不等於 `0%`。

## 文件導覽

- [程式碼架構與品質審查](docs/CODE_REVIEW.md)（風險、證據、優先順序）
- [ER Diagram / 資料庫結構](docs/ER_DIAGRAM.md)（含 ER 圖）
- [系統架構圖](docs/SYSTEM_ARCHITECTURE.md)
- [API 文件](docs/API_REFERENCE.md)
- [功能展示與 Demo 操作指引](docs/DEMO_GUIDE.md)
- [Docker → AWS EC2 → GitHub Actions CI/CD 規劃](docs/DEPLOYMENT_ROADMAP.md)

## 已知限制與下一步

此版本是已完成使用者操作驗收的作品，但**本次審查僅執行靜態檢閱與文件產出**，沒有重跑實際 MySQL 併發、效能、弱點掃描或雲端部署測試。部署前請先處理採購到貨／商品編輯的庫存競爭、Secrets 排除、外部 HTTP Timeout、Production 設定與健康檢查。詳見 [CODE_REVIEW](docs/CODE_REVIEW.md)。

> 本 README 不包含未實作的商品圖片上傳 API、報表後端產檔或虛構的公開網站網址。擴充時以實際程式為準。
