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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "purchase_order_items",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_poi_order_product",
            columnNames = {
                "purchase_order_id",
                "product_id"
            }
        )
    }
)
public class PurchaseOrderItem {

    // 訂貨明細編號（主鍵）
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // =====================================================
    // 訂貨單關聯
    // =====================================================
    // 多筆訂貨明細可以屬於同一張訂貨單
    //
    // purchase_order_id 是資料庫的外鍵欄位
    // 對應 PurchaseOrder Entity 的 id
    //
    // 使用 LAZY 載入，避免查詢明細時
    // 自動載入整張訂貨單的資料
    // =====================================================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "purchase_order_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_poi_order")
    )
    private PurchaseOrder purchaseOrder;

    // =====================================================
    // 商品關聯
    // =====================================================
    // 每筆訂貨明細對應一個商品
    //
    // 即使商品日後停用或軟刪除，
    // 仍保留原本的商品外鍵關聯
    //
    // 新增採購明細時是否允許使用該商品，
    // 交由後續 Service 驗證
    // =====================================================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "product_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_poi_product")
    )
    private Product product;

    // 訂貨數量
    // 必須大於 0
    // 實際驗證由 Request DTO 與 Service 負責
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // 訂貨當下的商品單價
    // 使用 BigDecimal 避免浮點數計算誤差
    //
    // 單價不可小於 0
    // 且必須符合 DECIMAL(10,2) 的範圍
    @Column(
        name = "unit_price",
        nullable = false,
        precision = 10,
        scale = 2
    )
    private BigDecimal unitPrice;

    // 訂貨商品小計
    // subtotal = quantity × unitPrice
    //
    // 由 Service 計算，不接受前端自行指定
    @Column(
        name = "subtotal",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal subtotal;

    // 明細建立時間
    // 對應資料庫 created_at
    // 資料表沒有 updated_at 或 deleted_at
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // =====================================================
    // 新增明細前自動執行
    // =====================================================
    // 如果建立時間尚未設定，
    // 自動使用目前系統時間
    // =====================================================
    @PrePersist
    public void prePersist() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
