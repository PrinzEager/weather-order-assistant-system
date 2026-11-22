package com.example.weather_order_assistant_system.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// =====================================================
// 採購單主檔回傳 DTO
// =====================================================
// 負責將完整採購單資料轉換成 JSON 回傳給前端。
//
// 包含：
// 1. 採購單基本資訊
// 2. 下單當時的供應商歷史資訊快照
// 3. 採購單狀態與金額
// 4. 商品採購明細
// 5. 建立與修改時間
//
// 不直接回傳 PurchaseOrder Entity，
// 避免 JPA LAZY 關聯序列化問題及雙向關聯無限遞迴。
// =====================================================
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderResponse {

    // =====================================================
    // 採購單基本資訊
    // =====================================================

    // 採購單編號
    private Long id;

    // 原始供應商編號
    // 即使供應商終止合作，仍保留原本的 ID
    private Long supplierId;

    // =====================================================
    // 供應商歷史資訊快照
    // =====================================================
    // 以下五個欄位必須讀取 PurchaseOrder 儲存的快照，
    // 不應重新讀取 Supplier 目前的基本資料。
    //
    // 因此即使供應商日後：
    // 1. 修改名稱
    // 2. 修改聯絡方式
    // 3. 停用
    // 4. 軟刪除
    //
    // 舊採購單仍可顯示下單當時的資訊。
    // =====================================================

    // 下單當時的供應商名稱
    private String supplierNameSnapshot;

    // 下單當時的供應商聯絡人
    private String supplierContactPersonSnapshot;

    // 下單當時的供應商電話
    private String supplierPhoneSnapshot;

    // 下單當時的供應商電子郵件
    private String supplierEmailSnapshot;

    // 下單當時的供應商地址
    private String supplierAddressSnapshot;

    // =====================================================
    // 訂貨日期與狀態
    // =====================================================

    // 實際建立訂單的日期時間
    private LocalDateTime orderDate;

    // 預計到貨日期
    private LocalDate expectedDeliveryDate;

    // 訂單狀態
    // DRAFT：草稿
    // SUBMITTED：已送出
    // RECEIVED：已到貨
    // CANCELLED：已取消
    private String orderStatus;

    // =====================================================
    // 訂貨金額與備註
    // =====================================================

    // 訂單總金額
    // 由所有商品明細 subtotal 加總
    private BigDecimal totalAmount;

    // 訂貨備註
    private String remark;

    // =====================================================
    // 採購商品明細
    // =====================================================
    // 使用 PurchaseOrderItemResponse，
    // 不直接輸出 PurchaseOrderItem Entity。
    //
    // 每筆明細包含：
    // 商品 ID、SKU、商品名稱、
    // 訂貨數量、單價及小計。
    // =====================================================
    private List<PurchaseOrderItemResponse> items;

    // =====================================================
    // 系統時間欄位
    // =====================================================

    // 採購單建立時間
    private LocalDateTime createdAt;

    // 採購單最後修改時間
    private LocalDateTime updatedAt;
}
