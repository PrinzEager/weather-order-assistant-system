package com.example.weather_order_assistant_system.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SalesRecordResponse {

    // 銷售紀錄編號
    private Long id;

    // 同一批多商品銷售的識別碼；舊紀錄為 null。
    private String batchId;

    // 銷售商品編號
    private Long productId;

    // 銷售商品名稱
    // 由關聯的 Product Entity 取得
    private String productName;

    // 商品內部代碼（SKU）
    // 方便前端辨識實際銷售的商品
    private String sku;

    // 銷售數量
    private Integer quantity;

    // 累計退貨量，以及尚可退貨的數量。
    private Integer returnedQuantity;
    private Integer remainingReturnableQuantity;

    // 銷售當下的單價
    // 保留歷史成交價格
    private BigDecimal unitPrice;

    // 銷售總金額
    // 由 Service 計算 quantity × unitPrice
    private BigDecimal totalAmount;

    // 實際銷售日期與時間
    private LocalDateTime dateTime;

    // 銷售紀錄建立時間
    private LocalDateTime createdAt;

    // 保留原本九個參數的建構子，避免既有呼叫端因 DTO 擴充而無法編譯。
    public SalesRecordResponse(Long id, Long productId, String productName,
            String sku, Integer quantity, BigDecimal unitPrice,
            BigDecimal totalAmount, LocalDateTime dateTime, LocalDateTime createdAt) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
        this.dateTime = dateTime;
        this.createdAt = createdAt;
        this.returnedQuantity = 0;
        this.remainingReturnableQuantity = quantity;
    }
}
