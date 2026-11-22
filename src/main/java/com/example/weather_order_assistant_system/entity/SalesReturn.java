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
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sales_returns")
public class SalesReturn {

    // 每次退貨獨立保存一筆紀錄，不刪除原始銷售紀錄。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // 多筆退貨可以對應同一筆原始銷售紀錄。
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_record_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sales_returns_sales_record"))
    private SalesRecord salesRecord;

    // 本次退貨數量：必須大於零，且不可超出剩餘可退量。
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // 退款金額依銷售當下單價乘以退貨數量計算。
    @Column(name = "return_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal returnAmount;

    // 退貨原因可不填，最長 100 字。
    @Column(name = "reason", length = 100)
    private String reason;

    // 實際退貨時間由後端產生，不接受前端覆寫。
    @Column(name = "returned_at", nullable = false)
    private LocalDateTime returnedAt;
}
