-- V009：供應商與商品供貨關聯。
-- 一項商品可由多個供應商供貨；不同供應商可設定不同採購單價。
-- 供貨關聯只停用、不實體刪除，保留採購歷史參考。
-- 不修改既有 suppliers、products、purchase_orders、purchase_order_items。
CREATE TABLE supplier_products (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '供貨關聯編號',
    supplier_id BIGINT NOT NULL COMMENT '供應商編號',
    product_id BIGINT NOT NULL COMMENT '商品編號',
    purchase_price DECIMAL(10, 2) NOT NULL COMMENT '目前供貨採購單價',
    status INT NOT NULL DEFAULT 1 COMMENT '1=供貨中，0=停止供貨',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後更新時間',
    PRIMARY KEY (id),
    CONSTRAINT uq_supplier_products_supplier_product UNIQUE (supplier_id, product_id),
    CONSTRAINT fk_supplier_products_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_supplier_products_product
        FOREIGN KEY (product_id) REFERENCES products(id),
    INDEX idx_supplier_products_product_status (product_id, status),
    INDEX idx_supplier_products_supplier_status (supplier_id, status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='供應商供貨商品與採購單價';
