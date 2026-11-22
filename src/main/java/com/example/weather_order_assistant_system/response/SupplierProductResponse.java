package com.example.weather_order_assistant_system.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 供貨關聯回傳資料，包含供應商與商品名稱以供前端直接顯示。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplierProductResponse {
    private Long id;
    private Long supplierId;
    private String supplierName;
    private Long productId;
    private String productName;
    private String sku;
    private BigDecimal purchasePrice;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
