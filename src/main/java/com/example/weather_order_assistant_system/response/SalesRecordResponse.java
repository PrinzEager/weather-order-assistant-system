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
}
