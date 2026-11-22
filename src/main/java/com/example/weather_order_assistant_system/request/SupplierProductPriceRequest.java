package com.example.weather_order_assistant_system.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 修改既有供貨關聯的採購單價，不改動供貨狀態。 */
@Getter
@Setter
public class SupplierProductPriceRequest {

    @NotNull(message = "採購單價不可為空")
    @DecimalMin(value = "0.00", message = "採購單價不可小於 0")
    @Digits(integer = 8, fraction = 2, message = "採購單價最多 8 位整數及 2 位小數")
    private BigDecimal purchasePrice;
}
