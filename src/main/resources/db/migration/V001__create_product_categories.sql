-- 商品分類：第一層 parent_id 為 NULL，第二層指向第一層
-- 最多兩層的限制由 Service 驗證
CREATE TABLE product_categories (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品分類編號',
    name VARCHAR(100) NOT NULL COMMENT '分類名稱',
    parent_id BIGINT NULL COMMENT '上層分類編號，NULL 表示第一層',
    status INT NOT NULL DEFAULT 1 COMMENT '1=啟用，0=停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後更新時間',
    deleted_at DATETIME NULL COMMENT '軟刪除時間',
    PRIMARY KEY (id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES product_categories(id),
    INDEX idx_categories_parent (parent_id),
    INDEX idx_categories_status (status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='商品分類資料表';
