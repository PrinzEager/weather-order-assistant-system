-- V010：新增後台登入帳號資料表。
-- 第一版以單店後台帳號為主，不建立多店或複雜角色結構。
-- 登入密碼只儲存安全雜湊，絕不可儲存明文密碼。
-- 不修改已執行的 V001～V009 migration。
CREATE TABLE app_users (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '登入帳號編號',
    username VARCHAR(50) NOT NULL COMMENT '後台登入帳號（不得重複）',
    password_hash VARCHAR(255) NOT NULL COMMENT '經 PasswordEncoder 處理的密碼雜湊',
    status INT NOT NULL DEFAULT 1 COMMENT '1=啟用，0=停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後更新時間',
    PRIMARY KEY (id),
    CONSTRAINT uq_app_users_username UNIQUE (username),
    INDEX idx_app_users_status (status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='後台使用者登入帳號';
