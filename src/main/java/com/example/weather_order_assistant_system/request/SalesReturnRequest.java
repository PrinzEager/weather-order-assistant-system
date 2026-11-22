package com.example.weather_order_assistant_system.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalesReturnRequest {

    // 本次退貨數量必填且大於零。
    @NotNull(message = "退貨數量不可為空")
    @Positive(message = "退貨數量必須大於 0")
    private Integer quantity;

    // 退貨原因非必填；長度鎖在100字內。
    @Size(max = 100, message = "退貨原因最多 100 字")
    private String reason;
}
