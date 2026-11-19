package com.example.weather_order_assistant_system.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_categories")
public class ProductCategory {

    // 分類 ID，使用資料庫自動遞增
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 分類名稱
    @Column(nullable = false, length = 100)
    private String name;

    // 上層分類，若為第一層分類則 parent 為 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ProductCategory parent;

    // 分類狀態：1 = 啟用、0 = 停用
    @Column(nullable = false)
    private Byte status = 1;

    // 建立時間
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 最後修改時間
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 軟刪除時間，null 代表尚未刪除
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 新增資料前，自動設定建立時間與修改時間
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (status == null) {
            status = 1;
        }
    }

    // 修改資料前，自動更新最後修改時間
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}