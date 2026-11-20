package com.example.weather_order_assistant_system.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseOrderRequest {

    // =====================================================
    // 供應商編號
    // =====================================================
    // 建立採購單時必須指定供應商。
    //
    // @NotNull：
    // 供應商編號不可為空。
    //
    // @Positive：
    // 供應商編號必須大於 0。
    //
    // 供應商是否存在、是否啟用及是否已軟刪除，
    // 由後續 PurchaseOrderService 驗證。
    //
    // 供應商名稱與聯絡資料不由前端傳入，
    // 而是由 Service 自動建立歷史快照。
    // =====================================================
    @NotNull(message = "供應商編號不可為空")
    @Positive(message = "供應商編號必須大於 0")
    private Long supplierId;

    // =====================================================
    // 預計到貨日期
    // =====================================================
    // 使用 LocalDate，因為資料庫欄位是 DATE。
    //
    // 允許 null：
    // 代表尚未確定預計到貨日期。
    //
    // 日期是否符合業務規則，
    // 由後續 Service 進一步判斷。
    // =====================================================
    private LocalDate expectedDeliveryDate;

    // =====================================================
    // 採購備註
    // =====================================================
    // 非必填欄位，允許 null。
    //
    // 資料庫 remark 為 VARCHAR(500)，
    // 因此最多允許 500 個字元。
    // =====================================================
    @Size(max = 500, message = "訂貨備註不可超過 500 個字元")
    private String remark;

    // =====================================================
    // 採購商品明細
    // =====================================================
    // 一張採購單至少要有一筆商品明細。
    //
    // @NotEmpty：
    // 不允許 null 或空集合。
    //
    // @Valid：
    // 逐筆檢查 PurchaseOrderItemRequest 的驗證規則，
    // 例如 productId 不可為空、quantity 必須大於 0。
    //
    // 同一張採購單不可重複加入相同商品，
    // 這項業務規則會由後續 Service 處理。
    // =====================================================
    @Valid
    @NotEmpty(message = "採購單至少需要一筆商品明細")
    private List<
            @NotNull(message = "採購明細不可為空")
            @Valid PurchaseOrderItemRequest
    > items;
}
