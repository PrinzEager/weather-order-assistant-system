package com.example.weather_order_assistant_system.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.weather_order_assistant_system.entity.PurchaseOrderItem;

@Repository
public interface PurchaseOrderItemRepository
        extends JpaRepository<PurchaseOrderItem, Long> {

    // =====================================================
    // 1. 查詢指定採購單的所有商品明細
    // =====================================================
    // 根據 purchase_order_id 查詢明細，
    // 並依照明細 ID 由小到大排序。
    //
    // 即使商品日後停用或軟刪除，
    // 仍保留原本的採購明細供歷史查詢使用。
    // =====================================================
    List<PurchaseOrderItem> findByPurchaseOrder_IdOrderByIdAsc(
            Long purchaseOrderId
    );

    // =====================================================
    // 2. 查詢指定採購單中的單筆商品明細
    // =====================================================
    // 同時使用採購單 ID 與商品 ID 查詢。
    //
    // 因為資料庫已設定 UNIQUE：
    // (purchase_order_id, product_id)
    // 所以最多只會找到一筆明細。
    // =====================================================
    Optional<PurchaseOrderItem> findByPurchaseOrder_IdAndProduct_Id(
            Long purchaseOrderId,
            Long productId
    );

    // =====================================================
    // 3. 檢查同一張採購單是否已有指定商品
    // =====================================================
    // 新增明細前先檢查是否重複，
    // 避免違反資料庫的 UNIQUE 限制。
    //
    // 後續 Service 可在商品重複時回傳：
    // HTTP 409：此採購單已包含相同商品
    //
    // 資料庫 UNIQUE 仍是最後一道保護。
    // =====================================================
    boolean existsByPurchaseOrder_IdAndProduct_Id(
            Long purchaseOrderId,
            Long productId
    );

    // =====================================================
    // 4. 統計指定商品尚未到貨的採購數量
    // =====================================================
    // 這個方法將提供給未來的
    // OrderRecommendationService 使用。
    //
    // 統計條件：
    // 1. 商品 ID 必須符合指定商品
    // 2. 採購單狀態必須為 SUBMITTED（已送出）
    // 3. 採購單不可已軟刪除
    //
    // 不限制供應商目前的啟用狀態，
    // 因為供應商終止合作不應自動取消既有採購單。
    //
    // 不納入：
    // DRAFT     尚未送出
    // RECEIVED  已到貨
    // CANCELLED 已取消
    //
    // COALESCE：
    // 沒有符合條件的明細時，回傳 0，
    // 避免 SUM() 回傳 NULL。
    //
    // 使用 Long 避免多筆明細加總時，
    // 過早受到 Integer 數值範圍限制。
    // =====================================================
    @Query("""
        SELECT COALESCE(SUM(i.quantity), 0)
        FROM PurchaseOrderItem i
        JOIN i.purchaseOrder po
        WHERE i.product.id = :productId
          AND po.orderStatus = 'SUBMITTED'
          AND po.deletedAt IS NULL
        """)
    Long sumOutstandingQuantityByProductId(
            @Param("productId") Long productId
    );
}
