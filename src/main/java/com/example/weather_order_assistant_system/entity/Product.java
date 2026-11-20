package com.example.weather_order_assistant_system.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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
@Table(name = "products")
public class Product {

    // 商品編號（主鍵，自動遞增）
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 商品所屬分類
    // 多個商品可以屬於同一個商品分類
    // 使用 LAZY 避免查詢商品時立即載入分類資料
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "category_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_products_category")
    )
    private ProductCategory category;

    // 商品代碼（SKU）
    // 每個商品的 SKU 必須唯一
    @Column(
        name = "sku",
        nullable = false,
        unique = true,
        length = 50
    )
    private String sku;

    // 商品名稱
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // 商品售價
    // precision = 10：總共最多 10 位數
    // scale = 2：小數點後保留 2 位
    @Column(
        name = "price",
        nullable = false,
        precision = 10,
        scale = 2
    )
    private BigDecimal price;

    // 商品單位，例如：個、瓶、包
    @Column(name = "unit", nullable = false, length = 20)
    private String unit = "個";

    // 目前庫存數量
    @Column(name = "current_stock", nullable = false)
    private Integer currentStock = 0;

    // 安全庫存數量
    // 後續計算建議訂貨量時使用
    @Column(name = "safety_stock", nullable = false)
    private Integer safetyStock = 0;

    // 商品狀態
    // 1：啟用
    // 0：停用
    @Column(name = "status")
    private Integer status = 1;

    // 建立時間
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 最後修改時間
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 軟刪除時間
    // null：商品尚未刪除
    // 非 null：商品已軟刪除
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 第一次新增商品前，自動設定預設值及時間
    @PrePersist
    public void prePersist() {

        LocalDateTime now = LocalDateTime.now();

        if (status == null) {
            status = 1;
        }

        if (unit == null) {
            unit = "個";
        }

        if (currentStock == null) {
            currentStock = 0;
        }

        if (safetyStock == null) {
            safetyStock = 0;
        }

        createdAt = now;
        updatedAt = now;
    }

    // 修改商品資料時，自動更新修改時間
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
