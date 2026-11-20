# Flyway 六張資料表（新版 Java 訂貨輔助系統）

將 `db/migration/` 下六個 SQL 檔案放在 Spring Boot 專案的 `src/main/resources/db/migration/`。

- V001 product_categories
- V002 products
- V003 suppliers
- V004 sales_records（原 sales）
- V005 purchase_orders
- V006 purchase_order_items

## 重要

1. 這是**全新空資料庫**的初始化 migrations，並非舊資料庫的升級檔。
2. 若需要清空資料，先停止 Spring Boot、備份，再在 MySQL 操作 DROP DATABASE 和 CREATE DATABASE；資料庫名稱務必與 `spring.datasource.url` 相同。
3. 移除/移走專案中任何舊版或重複的 Flyway migrations，以免版號或 CREATE TABLE 衝突；只適用於這次開發環境重建。已部署過的版本不可任意修改既有 migration。
4. 各表的 `status` 用 INT，Java Entity/Repository/DTO/Service 使用 Integer。
5. `sales_records` 表名更新後，Java 中的 `@Table(name = "sales")`、原生 SQL、查詢與索引命名必須一起檢查。
6. 保留原設計：sales_records 沒有 status/updated_at；purchase_order_items 只有 created_at；purchase_orders 使用 order_status 字串。若要新增欄位應另立 migration。
7. 六張表建立的時候，Flyway 會額外建立 `flyway_schema_history` 表作版本管理。
