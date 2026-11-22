-- V011：把同一次多商品銷售的逐商品紀錄關聯起來。
-- 既有銷售維持 batch_id = NULL，不改動原有金額、庫存、退貨或訂貨統計。
-- 已執行 V001～V010 不得修改；使用新的 Flyway Migration。
ALTER TABLE sales_records
    ADD COLUMN batch_id VARCHAR(36) NULL
        COMMENT '同一次多商品銷售批次 UUID；舊資料 NULL' AFTER id;

CREATE INDEX idx_sales_records_batch_id ON sales_records (batch_id);
