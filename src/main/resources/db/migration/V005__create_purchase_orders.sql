-- 訂貨單主檔
CREATE TABLE purchase_orders (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '訂貨單編號',
    supplier_id BIGINT NOT NULL COMMENT '供應商編號',
    order_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '訂貨日期',
    expected_delivery_date DATE NULL COMMENT '預計到貨日期',
    order_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿、SUBMITTED已送出、RECEIVED已到貨、CANCELLED已取消',
    total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT '訂貨單總金額',
    remark VARCHAR(500) NULL COMMENT '訂貨備註',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後更新時間',
    deleted_at DATETIME NULL COMMENT '軟刪除時間',
    PRIMARY KEY (id),
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    INDEX idx_po_supplier (supplier_id),
    INDEX idx_po_order_date (order_date),
    INDEX idx_po_status (order_status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='訂貨單主檔';
