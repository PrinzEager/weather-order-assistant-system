package com.example.weather_order_assistant_system.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.weather_order_assistant_system.entity.PurchaseOrder;

@Repository
public interface PurchaseOrderRepository
        extends JpaRepository<PurchaseOrder, Long> {

    // =====================================================
    // 1. 查詢所有未軟刪除的採購單
    // =====================================================
    // 依照訂貨日期由新到舊排序
    //
    // 不限制供應商目前是否啟用或已軟刪除，
    // 確保終止合作供應商的歷史採購單仍可查詢。
    //
    // 供應商歷史名稱及聯絡資料，
    // 後續由 PurchaseOrderResponse 讀取快照欄位。
    // =====================================================
    List<PurchaseOrder> findByDeletedAtIsNullOrderByOrderDateDesc();

    // =====================================================
    // 2. 依採購單 ID 查詢未軟刪除的採購單
    // =====================================================
    // 即使供應商已停用或軟刪除，
    // 只要採購單本身未被軟刪除，就允許查詢。
    // =====================================================
    Optional<PurchaseOrder> findByIdAndDeletedAtIsNull(Long id);

    // =====================================================
    // 3. 查詢指定供應商的歷史採購單
    // =====================================================
    // supplier_id 為原始外鍵，會持續保留。
    //
    // 即使供應商已終止合作，
    // 仍可透過 supplier_id 查詢原有採購單。
    // =====================================================
    List<PurchaseOrder> findBySupplier_IdAndDeletedAtIsNullOrderByOrderDateDesc(
            Long supplierId
    );

    // =====================================================
    // 4. 依採購單狀態查詢
    // =====================================================
    // 例如：
    // DRAFT     草稿
    // SUBMITTED 已送出
    // RECEIVED  已到貨
    // CANCELLED 已取消
    //
    // 僅查詢尚未軟刪除的採購單。
    // =====================================================
    List<PurchaseOrder> findByOrderStatusAndDeletedAtIsNullOrderByOrderDateDesc(
            String orderStatus
    );

    // =====================================================
    // 5. 依訂貨日期區間查詢採購單
    // =====================================================
    // 查詢條件：
    // order_date >= startDateTime
    // order_date < endDateTime
    //
    // 採用「起始包含、結束不包含」的時間區間，
    // 與 SalesRecordRepository 的日期查詢原則一致。
    //
    // 若使用者指定 11/20 至 11/25（包含兩天），
    // Service 應將結束時間轉成 11/26 00:00。
    // =====================================================
    List<PurchaseOrder>
            findByOrderDateGreaterThanEqualAndOrderDateLessThanAndDeletedAtIsNullOrderByOrderDateDesc(
                    LocalDateTime startDateTime,
                    LocalDateTime endDateTime
            );

    // =====================================================
    // 6. 查詢尚未到貨的有效採購單
    // =====================================================
    // 目前先將 SUBMITTED 視為已送出但尚未到貨。
    //
    // DRAFT     尚未正式送出，不列入未到貨量
    // SUBMITTED 已送出，列入未到貨量
    // RECEIVED  已到貨，不列入未到貨量
    // CANCELLED 已取消，不列入未到貨量
    //
    // 後續 OrderRecommendation 將配合
    // PurchaseOrderItemRepository 統計商品未到貨數量。
    // =====================================================
    List<PurchaseOrder> findByOrderStatusAndDeletedAtIsNull(
            String orderStatus
    );
}
