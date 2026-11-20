-- 供應商
CREATE TABLE suppliers (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '供應商編號',
    name VARCHAR(100) NOT NULL COMMENT '供應商名稱',
    contact_person VARCHAR(50) NULL COMMENT '聯絡人',
    phone VARCHAR(30) NULL COMMENT '聯絡電話',
    email VARCHAR(100) NULL COMMENT '電子郵件',
    address VARCHAR(255) NULL COMMENT '供應商地址',
    status INT NOT NULL DEFAULT 1 COMMENT '1=啟用，0=停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後更新時間',
    deleted_at DATETIME NULL COMMENT '軟刪除時間',
    PRIMARY KEY (id),
    CONSTRAINT uq_suppliers_email UNIQUE (email),
    INDEX idx_suppliers_name (name),
    INDEX idx_suppliers_status (status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='供應商資料表';
