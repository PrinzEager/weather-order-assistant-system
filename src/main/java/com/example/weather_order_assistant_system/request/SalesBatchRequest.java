package com.example.weather_order_assistant_system.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 一次銷售內的多商品明細。
 * 各商品沿用單筆銷售的欄位驗證，並限制交易筆數以防大量請求。
 */
@Getter
@Setter
public class SalesBatchRequest {

    @NotEmpty(message = "至少選擇一項銷售商品")
    @Size(max = 50, message = "單次銷售最多 50 項商品")
    private List<@Valid SalesRecordRequest> items;
}
