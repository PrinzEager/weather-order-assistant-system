-- V008：為銷售模組加入「多次部分退貨」資料結構。
-- 本版只變更 schema；不會回補庫存，也不會修改既有銷售／退貨紀錄。
-- 已執行的 V004 不得修改，請透過 Flyway 套用此新版本。

-- 原始銷售數量 quantity 永遠保留；returned_quantity 代表累計退貨量。
-- 既有紀錄（含之前已軟刪除的整筆退貨）統一初始化為 0。
-- 舊的整筆退貨已透過 deleted_at 排除，不可重複回補庫存。
ALTER TABLE sales_records
    ADD COLUMN returned_quantity INT NOT NULL DEFAULT 0
        COMMENT '累計退貨數量，不得大於原始銷售數量'
        AFTER quantity;

-- 防止資料庫接受負數、或累計退貨超過原始銷售數量。
-- 需 MySQL 8.0.16 以上才能真正執行 CHECK 條件。
ALTER TABLE sales_records
    ADD CONSTRAINT chk_sales_records_returned_quantity
        CHECK (returned_quantity >= 0 AND returned_quantity <= quantity);

-- 每次退貨都保留一筆明細，不覆寫／軟刪除原始銷售。
CREATE TABLE sales_returns (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '退貨紀錄編號',
    sales_record_id BIGINT NOT NULL COMMENT '原始銷售紀錄編號',
    quantity INT NOT NULL COMMENT '本次退貨數量',
    return_amount DECIMAL(12, 2) NOT NULL COMMENT '本次退款金額，後端依銷售單價計算',
    reason VARCHAR(255) NULL COMMENT '退貨原因',
    returned_at DATETIME NOT NULL COMMENT '實際退貨時間',
    PRIMARY KEY (id),
    CONSTRAINT fk_sales_returns_sales_record
        FOREIGN KEY (sales_record_id) REFERENCES sales_records(id),
    CONSTRAINT chk_sales_returns_quantity CHECK (quantity > 0),
    CONSTRAINT chk_sales_returns_amount CHECK (return_amount >= 0),
    INDEX idx_sales_returns_record_time (sales_record_id, returned_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='銷售部分退貨歷史紀錄';
