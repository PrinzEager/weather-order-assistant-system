package com.example.weather_order_assistant_system.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 新增供應商供貨商品的輸入資料。 */
@Getter
@Setter
public class SupplierProductCreateRequest {

    // 必須指定既有且啟用的商品。
    @NotNull(message = "商品 ID 不可為空")
    @Positive(message = "商品 ID 必須大於 0")
    private Long productId;

    // 採購價格與既有採購單單價規格保持一致：DECIMAL(10,2)。
    @NotNull(message = "採購單價不可為空")
    @DecimalMin(value = "0.00", message = "採購單價不可小於 0")
    @Digits(integer = 8, fraction = 2, message = "採購單價最多 8 位整數及 2 位小數")
    private BigDecimal purchasePrice;
}
