package com.example.weather_order_assistant_system.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.weather_order_assistant_system.entity.SupplierProduct;

/** 供貨關聯資料存取。 */
@Repository
public interface SupplierProductRepository extends JpaRepository<SupplierProduct, Long> {

    // 同一供應商 + 商品只允許一筆供貨關聯（包含停用紀錄）。
    Optional<SupplierProduct> findBySupplier_IdAndProduct_Id(
            Long supplierId, Long productId);

    // 供應商管理頁要看得到已停用的商品，方便重新啟用。
    List<SupplierProduct> findBySupplier_IdOrderByIdAsc(Long supplierId);

    // 採購草稿的供應商選單：只顯示有效供應商、商品及啟用的供貨關聯。
    // 連同供應商、商品取回，避免離開交易後觸發 LAZY 載入問題。
    @Query("""
            SELECT sp FROM SupplierProduct sp
            JOIN FETCH sp.supplier s
            JOIN FETCH sp.product p
            WHERE p.id = :productId
              AND sp.status = 1
              AND s.status = 1
              AND s.deletedAt IS NULL
              AND p.status = 1
              AND p.deletedAt IS NULL
            ORDER BY s.name ASC, sp.id ASC
            """)
    List<SupplierProduct> findActiveOffersByProductId(
            @Param("productId") Long productId);
}
