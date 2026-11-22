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

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sales_records")
public class SalesRecord {

    // 銷售紀錄編號（主鍵，自動遞增）
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 對應銷售商品
    // 多筆銷售紀錄可以對應同一個商品
    // 使用 LAZY，避免查詢銷售紀錄時立即載入商品資料
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "product_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_sales_records_product")
    )
    private Product product;

    // 同一筆多商品銷售的交易識別碼；舊銷售資料保留 null。
    @Column(name = "batch_id", length = 36)
    private String batchId;

    // 銷售數量
    // 必須大於 0，後續由 Request 與 Service 驗證
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // 累計已退貨數量（預設為 0）
    // 不修改原始銷售數量 quantity，以保留當時的真實銷售紀錄。
    // 後續退貨服務會檢查：0 <= returnedQuantity <= quantity。
    @Column(name = "returned_quantity", nullable = false)
    private Integer returnedQuantity = 0;

    // 銷售當下的商品單價
    // 保留歷史售價，不受商品日後調整售價影響
    // 對應 DECIMAL(10,2)
    @Column(
        name = "unit_price",
        nullable = false,
        precision = 10,
        scale = 2
    )
    private BigDecimal unitPrice;

    // 銷售總金額
    // 計算方式：quantity × unitPrice
    // 後續由 Service 計算，不直接相信前端傳入的金額
    // 對應 DECIMAL(12,2)
    @Column(
        name = "total_amount",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal totalAmount;

    // 實際銷售日期與時間
    // 用於查詢指定日期區間的歷史銷售數量
    // 也是未來訂貨建議的重要統計依據
    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    // 銷售紀錄建立時間
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 軟刪除時間
    // null：正常紀錄
    // 非 null：已軟刪除，不參與歷史銷售統計
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 新增資料前，自動設定建立時間
    // dateTime 代表實際銷售時間，不在這裡自動覆寫
    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
