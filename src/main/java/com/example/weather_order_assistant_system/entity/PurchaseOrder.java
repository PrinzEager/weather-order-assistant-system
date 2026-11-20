package com.example.weather_order_assistant_system.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    // 訂貨單編號（主鍵）
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 訂貨單所屬供應商
    // 使用 LAZY 避免查詢訂貨單時自動載入所有供應商資料
    // 即使供應商日後停用或軟刪除，也保留原本的關聯
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "supplier_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_purchase_orders_supplier")
    )
    private Supplier supplier;

    // =====================================================
    // 供應商歷史資訊快照
    // =====================================================
    // 建立訂貨單時，由 Service 複製當下的供應商基本資料
    // 之後供應商修改名稱、聯絡方式、停用或軟刪除，
    // 都不應修改既有訂貨單內保存的快照。
    //
    // 這些欄位允許 NULL，以兼容 Migration 前的歷史資料。
    // 新訂貨單的 supplierNameSnapshot 必須由 Service 填入。
    // =====================================================

    // 下單當時的供應商名稱
    @Column(name = "supplier_name_snapshot", length = 100)
    private String supplierNameSnapshot;

    // 下單當時的供應商聯絡人
    @Column(name = "supplier_contact_person_snapshot", length = 50)
    private String supplierContactPersonSnapshot;

    // 下單當時的供應商電話
    @Column(name = "supplier_phone_snapshot", length = 30)
    private String supplierPhoneSnapshot;

    // 下單當時的供應商電子郵件
    @Column(name = "supplier_email_snapshot", length = 100)
    private String supplierEmailSnapshot;

    // 下單當時的供應商地址
    @Column(name = "supplier_address_snapshot", length = 255)
    private String supplierAddressSnapshot;

    // 訂貨日期與時間
    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    // 預計到貨日期
    // 非必填，允許尚未確定到貨日期
    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    // 訂貨單狀態
    // DRAFT：草稿
    // SUBMITTED：已送出
    // RECEIVED：已到貨
    // CANCELLED：已取消
    // 不另外增加 status 欄位，避免與訂貨狀態重複
    @Column(name = "order_status", nullable = false, length = 20)
    private String orderStatus = "DRAFT";

    // 訂貨單總金額
    // 由 Service 將所有明細小計加總
    @Column(
        name = "total_amount",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // 訂貨備註
    @Column(name = "remark", length = 500)
    private String remark;

    // 建立時間
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 最後修改時間
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 軟刪除時間
    // NULL 代表訂貨單尚未刪除
    // 有值代表訂貨單已軟刪除
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // =====================================================
    // 訂貨單與訂貨明細的一對多關聯
    // =====================================================
    // 一張訂貨單可以包含多筆商品明細
    //
    // mappedBy = "purchaseOrder"
    // 表示外鍵由 PurchaseOrderItem 的 purchaseOrder 欄位管理
    //
    // CascadeType.PERSIST：
    // 新增訂貨單時，可一起新增所屬明細
    //
    // CascadeType.MERGE：
    // 更新訂貨單時，可一併更新所屬明細
    //
    // 不使用 REMOVE，避免誤刪除歷史採購明細
    // =====================================================
    @OneToMany(
        mappedBy = "purchaseOrder",
        cascade = {
            CascadeType.PERSIST,
            CascadeType.MERGE
        }
    )
    private List<PurchaseOrderItem> items = new ArrayList<>();

    // 新增訂貨單前執行
    @PrePersist
    public void prePersist() {

        LocalDateTime now = LocalDateTime.now();

        // 訂貨日期未指定時，使用目前時間
        if (orderDate == null) {
            orderDate = now;
        }

        // 設定建立時間
        if (createdAt == null) {
            createdAt = now;
        }

        // 設定最後修改時間
        if (updatedAt == null) {
            updatedAt = now;
        }

        // 預設為草稿狀態
        if (orderStatus == null) {
            orderStatus = "DRAFT";
        }

        // 預設總金額為 0
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }
    }

    // 修改訂貨單前執行
    @PreUpdate
    public void preUpdate() {

        // 每次更新訂貨單時，自動更新最後修改時間
        updatedAt = LocalDateTime.now();
    }
}
