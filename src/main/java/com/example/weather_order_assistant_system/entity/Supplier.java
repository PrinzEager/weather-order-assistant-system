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

@Getter
@Setter
@Entity
@Table(
    name = "suppliers",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_suppliers_email",
            columnNames = "email"
        )
    }
)
public class Supplier {

    // 供應商編號（主鍵，自動遞增）
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 供應商名稱（必填，最多 100 字元）
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // 聯絡人姓名（可為空，最多 50 字元）
    @Column(name = "contact_person", length = 50)
    private String contactPerson;

    // 聯絡電話（可為空，最多 30 字元）
    @Column(name = "phone", length = 30)
    private String phone;

    // 電子郵件（可為空，但非空值不可重複）
    @Column(name = "email", length = 100, unique = true)
    private String email;

    // 供應商地址（可為空，最多 255 字元）
    @Column(name = "address", length = 255)
    private String address;

    // 供應商狀態
    // 1：啟用
    // 0：停用
    @Column(name = "status", nullable = false)
    private Integer status = 1;

    // 資料建立時間
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 資料最後修改時間
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 軟刪除時間
    // null：尚未刪除
    // 有時間：已軟刪除
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 新增資料前自動執行
    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        // 設定建立與修改時間
        createdAt = now;
        updatedAt = now;

        // 未指定狀態時，預設為啟用
        if (status == null) {
            status = 1;
        }
    }

    // 更新資料前自動執行
    @PreUpdate
    protected void onUpdate() {

        // 每次更新資料時，重新設定修改時間
        updatedAt = LocalDateTime.now();
    }
}
