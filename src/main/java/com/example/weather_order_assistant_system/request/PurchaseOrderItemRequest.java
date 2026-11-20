package com.example.weather_order_assistant_system.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseOrderItemRequest {

    // =====================================================
    // 商品編號
    // =====================================================
    // 每筆採購明細必須指定一個商品。
    //
    // @NotNull：
    // 商品編號不可為 null。
    //
    // @Positive：
    // 商品編號必須大於 0。
    //
    // 商品是否存在、是否已停用或軟刪除，
    // 由後續 PurchaseOrderService 驗證。
    // =====================================================
    @NotNull(message = "商品編號不可為空")
    @Positive(message = "商品編號必須大於 0")
    private Long productId;

    // =====================================================
    // 採購數量
    // =====================================================
    // 必須大於 0，不允許 0 或負數。
    //
    // @NotNull：
    // 採購數量不可為 null。
    //
    // @Positive：
    // 採購數量必須大於 0。
    // =====================================================
    @NotNull(message = "訂貨數量不可為空")
    @Positive(message = "訂貨數量必須大於 0")
    private Integer quantity;
}
