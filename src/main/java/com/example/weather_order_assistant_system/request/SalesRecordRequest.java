package com.example.weather_order_assistant_system.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalesRecordRequest {

    // 銷售商品編號
    // 不可為空，且必須大於 0
    // 商品是否存在、啟用及未軟刪除，由 Service 驗證
    @NotNull(message = "商品編號不可為空")
    @Positive(message = "商品編號必須大於 0")
    private Long productId;

    // 銷售數量
    // 不可為空，且必須大於 0
    // 不支援部分退貨，退貨時直接軟刪除整筆銷售紀錄
    @NotNull(message = "銷售數量不可為空")
    @Positive(message = "銷售數量必須大於 0")
    private Integer quantity;

    // 銷售當下的單價
    // 不可為空，也不可小於 0
    // 整數最多 8 位，小數最多 2 位
    // 對應 MySQL DECIMAL(10,2)
    @NotNull(message = "銷售單價不可為空")
    @DecimalMin(
        value = "0.00",
        message = "銷售單價不可小於 0"
    )
    @Digits(
        integer = 8,
        fraction = 2,
        message = "銷售單價最多 8 位整數及 2 位小數"
    )
    private BigDecimal unitPrice;
}
