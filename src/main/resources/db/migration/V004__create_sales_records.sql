-- 銷售歷史紀錄：保留銷售當下的單價與金額
CREATE TABLE sales_records (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '銷售紀錄編號',
    product_id BIGINT NOT NULL COMMENT '商品編號',
    quantity INT NOT NULL COMMENT '銷售數量',
    unit_price DECIMAL(10, 2) NOT NULL COMMENT '銷售當下單價',
    total_amount DECIMAL(12, 2) NOT NULL COMMENT '銷售總金額',
    date_time DATETIME NOT NULL COMMENT '實際銷售日期與時間',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '紀錄建立時間',
    deleted_at DATETIME NULL COMMENT '軟刪除時間',
    PRIMARY KEY (id),
    CONSTRAINT fk_sales_records_product FOREIGN KEY (product_id) REFERENCES products(id),
    INDEX idx_sales_records_product_date (product_id, date_time),
    INDEX idx_sales_records_date (date_time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='商品銷售紀錄表';
