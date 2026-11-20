-- 商品與庫存：category_id 由 Service 限定只能使用第二層分類
CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品編號',
    category_id BIGINT NOT NULL COMMENT '商品分類編號',
    sku VARCHAR(50) NOT NULL COMMENT '商品內部代碼',
    name VARCHAR(100) NOT NULL COMMENT '商品名稱',
    price DECIMAL(10, 2) NOT NULL COMMENT '商品售價',
    unit VARCHAR(20) NOT NULL DEFAULT '個' COMMENT '商品單位',
    current_stock INT NOT NULL DEFAULT 0 COMMENT '目前庫存',
    safety_stock INT NOT NULL DEFAULT 0 COMMENT '安全庫存',
    status INT NOT NULL DEFAULT 1 COMMENT '1=啟用，0=停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後更新時間',
    deleted_at DATETIME NULL COMMENT '軟刪除時間',
    PRIMARY KEY (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES product_categories(id),
    CONSTRAINT uq_products_sku UNIQUE (sku),
    INDEX idx_products_name (name),
    INDEX idx_products_category_status (category_id, status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='商品及庫存資料表';
