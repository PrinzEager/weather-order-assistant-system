package com.example.weather_order_assistant_system.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/**
 * 批次採購草稿的單筆商品。
 * 由使用者選擇供應商與數量；採購價格必須由後端讀取 supplier_products。
 */
@Getter
@Setter
public class PurchaseOrderBatchItemRequest {

    @NotNull(message = "商品 ID 不可為空")
    @Positive(message = "商品 ID 必須大於 0")
    private Long productId;

    @NotNull(message = "供應商 ID 不可為空")
    @Positive(message = "供應商 ID 必須大於 0")
    private Long supplierId;

    @NotNull(message = "訂貨數量不可為空")
    @Positive(message = "訂貨數量必須大於 0")
    private Integer quantity;
}
