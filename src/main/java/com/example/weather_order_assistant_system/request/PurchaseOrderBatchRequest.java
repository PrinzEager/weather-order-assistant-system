package com.example.weather_order_assistant_system.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 一次確認多個商品的訂貨草稿。
 * 全部商品暫時共用一個預計到貨日期及備註；後續如有需要，再擴充個別供應商日期。
 */
@Getter
@Setter
public class PurchaseOrderBatchRequest {

    // 本次批次建立的共用預計到貨日；依台灣時間中午 12 點規則驗證。
    @NotNull(message = "預計到貨日期不可為空")
    private LocalDate expectedDeliveryDate;

    @Size(max = 500, message = "採購備註最多 500 字")
    private String remark;

    @NotEmpty(message = "請至少選擇一項訂貨商品")
    private List<@NotNull(message = "訂貨明細不可為空") @Valid PurchaseOrderBatchItemRequest> items;
}
