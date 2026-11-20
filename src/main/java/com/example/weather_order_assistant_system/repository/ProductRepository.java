package com.example.weather_order_assistant_system.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.weather_order_assistant_system.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // 查詢所有啟用且尚未軟刪除的商品
    // status = 1：商品啟用
    // deletedAt IS NULL：商品尚未刪除
    // 依照商品 ID 由小到大排序
    List<Product> findByStatusAndDeletedAtIsNullOrderByIdAsc(
            Integer status
    );

    // 根據商品 ID 查詢尚未軟刪除的商品
    // Optional 用來處理商品不存在的情況
    Optional<Product> findByIdAndDeletedAtIsNull(
            Long id
    );

    // 根據 SKU 查詢尚未軟刪除的商品
    // 後續可用於確認商品代碼是否重複
    Optional<Product> findBySkuAndDeletedAtIsNull(
            String sku
    );

    // 檢查 SKU 是否已存在
    // 新增商品時使用
    // 包含已停用及已軟刪除的商品
    // 避免違反資料庫的 SKU UNIQUE 約束
    boolean existsBySku(
            String sku
    );

    // 修改商品時，檢查 SKU 是否被其他商品使用
    // 排除目前正在修改的商品 ID
    boolean existsBySkuAndIdNot(
            String sku,
            Long id
    );

    // 根據商品名稱進行模糊搜尋
    // 只查詢啟用且尚未軟刪除的商品
    List<Product> findByNameContainingAndStatusAndDeletedAtIsNullOrderByIdAsc(
            String name,
            Integer status
    );

    // 根據商品分類 ID 查詢商品
    // 只查詢尚未軟刪除的商品
    List<Product> findByCategory_IdAndDeletedAtIsNull(
            Long categoryId
    );

    // 檢查指定分類是否仍有尚未軟刪除的商品
    // 不論商品啟用或停用，都視為仍在使用分類
    boolean existsByCategory_IdAndDeletedAtIsNull(
            Long categoryId
    );
}
