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
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

/**
 * 供應商與商品的供貨關聯。
 *
 * 一個供應商可以供應多個商品，同一商品也可以由多個供應商供應。
 * 採購單價是「目前供貨報價」；正式建單時應複製到 PurchaseOrderItem.unitPrice，
 * 讓日後供應商調價不會改變舊採購單。
 */
@Getter
@Setter
@Entity
@Table(
        name = "supplier_products",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_supplier_products_supplier_product",
                columnNames = {"supplier_id", "product_id"}))
public class SupplierProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 多筆供貨關聯可指向同一個供應商。
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "supplier_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_supplier_products_supplier"))
    private Supplier supplier;

    // 多筆供貨關聯可指向同一個商品。
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_supplier_products_product"))
    private Product product;

    // 供應商提供此商品的目前採購單價，允許 0 元但不可為負數。
    @Column(name = "purchase_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal purchasePrice;

    // 1：仍有供貨；0：停止供貨。停用後資料仍保留。
    @Column(name = "status", nullable = false)
    private Integer status = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = 1;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
