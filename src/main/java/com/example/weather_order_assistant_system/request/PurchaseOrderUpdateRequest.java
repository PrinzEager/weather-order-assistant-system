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

// =====================================================
// 採購單修改 Request DTO
// =====================================================
// 用於接收 PUT 修改採購單的資料。
//
// 本專案採用完整更新（PUT）：
// 1. 必須提供供應商 ID
// 2. 可以修改預計到貨日期
// 3. 可以修改備註
// 4. 必須提供完整商品明細清單
//
// 只有 DRAFT 狀態的採購單允許修改，
// 實際狀態檢查由 PurchaseOrderService 執行。
//
// 不接受前端自行修改：
// - 採購單狀態
// - 採購單總金額
// - 明細單價與小計
// - 供應商歷史資訊快照
// - 建立時間與修改時間
// =====================================================
@Getter
@Setter
public class PurchaseOrderUpdateRequest {

    // =====================================================
    // 供應商編號
    // =====================================================
    // PUT 為完整更新，因此供應商 ID 必填。
    //
    // @NotNull：不可為 null
    // @Positive：必須大於 0
    //
    // 供應商是否存在、啟用及未軟刪除，
    // 由 Service 進行驗證。
    //
    // 若更換供應商，Service 需同步更新五個快照。
    // 若未更換供應商，應保留原本的歷史快照。
    // =====================================================
    @NotNull(message = "供應商編號不可為空")
    @Positive(message = "供應商編號必須大於 0")
    private Long supplierId;

    // =====================================================
    // 預計到貨日期
    // =====================================================
    // 使用 LocalDate 對應 MySQL DATE。
    //
    // 允許 null：
    // 表示清除原本的預計到貨日期。
    // =====================================================
    private LocalDate expectedDeliveryDate;

    // =====================================================
    // 採購單備註
    // =====================================================
    // 允許 null：
    // 表示清除原本的備註。
    //
    // 最大長度需符合資料庫 VARCHAR(500)。
    // =====================================================
    @Size(max = 500, message = "訂貨備註不可超過 500 個字元")
    private String remark;

    // =====================================================
    // 採購商品明細
    // =====================================================
    // PUT 更新時必須提供完整明細清單。
    //
    // @NotEmpty：
    // 至少需要一筆明細，不可為 null 或空集合。
    //
    // @Valid：
    // 驗證每一筆 PurchaseOrderItemRequest。
    //
    // 商品不可重複、商品必須有效等規則，
    // 由 PurchaseOrderService 驗證。
    //
    // 修改時會以此清單取代原有明細，
    // 並重新計算每筆小計及整張採購單總金額。
    // =====================================================
    @NotEmpty(message = "採購單至少需要一筆商品明細")
    private List<
            @NotNull(message = "採購明細不可為空")
            @Valid PurchaseOrderItemRequest
    > items;
}
