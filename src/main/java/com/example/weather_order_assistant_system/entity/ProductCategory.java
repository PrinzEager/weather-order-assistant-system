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
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "product_categories")
public class ProductCategory {

    // 商品分類編號（主鍵，自動遞增）
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 商品分類名稱
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // 上層商品分類
    // 第一層分類：parent 為 null
    // 第二層分類：parent 指向第一層分類
    // 使用 LAZY 避免查詢分類時立即載入上層分類
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ProductCategory parent;

    // 商品分類狀態
    // 1：啟用
    // 0：停用
    @Column(name = "status", nullable = false)
    private Integer status = 1;

    // 建立時間
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 最後修改時間
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 軟刪除時間
    // null：商品分類尚未刪除
    // 非 null：商品分類已軟刪除
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 第一次新增商品分類前，自動設定預設值及時間
    @PrePersist
    public void prePersist() {

        LocalDateTime now = LocalDateTime.now();

        if (status == null) {
            status = 1;
        }

        createdAt = now;
        updatedAt = now;
    }

    // 修改商品分類資料時，自動更新修改時間
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
