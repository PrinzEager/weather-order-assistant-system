package com.example.weather_order_assistant_system.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// =====================================================
// 採購單商品明細回傳 DTO
// =====================================================
// 負責將採購單中的商品明細轉成 JSON 回傳給前端。
//
// 不直接回傳 PurchaseOrderItem Entity，
// 避免 JPA LAZY 關聯造成序列化問題，
// 也避免雙向關聯導致無限遞迴。
//
// 商品單價與小計使用採購當下儲存的金額，
// 不因商品後續修改售價而改變。
// =====================================================
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItemResponse {

    // 採購明細編號
    private Long id;

    // 商品編號
    private Long productId;

    // 商品內部代碼
    private String sku;

    // 商品名稱
    private String productName;

    // 訂貨數量
    private Integer quantity;

    // 訂貨當下的商品單價
    private BigDecimal unitPrice;

    // 商品小計
    // subtotal = quantity × unitPrice
    private BigDecimal subtotal;
}
