package com.example.weather_order_assistant_system.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 修改供貨狀態：1=啟用，0=停止供貨。 */
@Getter
@Setter
public class SupplierProductStatusRequest {

    @NotNull(message = "供貨狀態不可為空")
    @Min(value = 0, message = "供貨狀態只能是 0 或 1")
    @Max(value = 1, message = "供貨狀態只能是 0 或 1")
    private Integer status;
}
