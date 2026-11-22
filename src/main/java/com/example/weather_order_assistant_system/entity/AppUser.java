package com.example.weather_order_assistant_system.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 後台登入帳號 Entity。
 * 第一版僅儲存登入所需資訊；不直接當成 API 回傳物件，避免洩漏密碼雜湊。
 */
@Getter
@Setter
@Entity
@Table(
        name = "app_users",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_app_users_username",
                columnNames = "username"
        )
)
public class AppUser {

    // 帳號主鍵。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 登入帳號；以資料庫 UNIQUE 約束避免重複。
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    // 僅允許儲存 PasswordEncoder 編碼後的結果，絕不可存明文密碼。
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // 1：啟用；0：停用。登入驗證階段會檢查此狀態。
    @Column(name = "status", nullable = false)
    private Integer status = 1;

    // 帳號建立時間。
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 最後更新時間。
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        // 新增時寫入建立與更新時間，與既有 Entity 模式一致。
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        // 每次資料更新都更新時間戳記。
        updatedAt = LocalDateTime.now();
    }
}
