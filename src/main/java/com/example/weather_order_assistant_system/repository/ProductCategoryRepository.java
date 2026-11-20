package com.example.weather_order_assistant_system.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.weather_order_assistant_system.entity.ProductCategory;

@Repository
public interface ProductCategoryRepository
        extends JpaRepository<ProductCategory, Long> {

    // 查詢所有啟用且尚未軟刪除的商品分類
    // status 使用 Integer，與 ProductCategory Entity 一致
    // 查詢結果依分類 ID 由小到大排序
    List<ProductCategory> findByStatusAndDeletedAtIsNullOrderByIdAsc(
            Integer status
    );

    // 根據分類 ID 查詢尚未軟刪除的分類
    Optional<ProductCategory> findByIdAndDeletedAtIsNull(Long id);

    // 查詢指定上層分類底下所有尚未軟刪除的子分類
    List<ProductCategory> findByParent_IdAndDeletedAtIsNull(
            Long parentId
    );

    // 查詢所有尚未軟刪除的第一層分類
    List<ProductCategory> findByParentIsNullAndDeletedAtIsNull();

    // 檢查指定上層分類底下是否已有相同名稱的分類
    boolean existsByNameAndParent_IdAndDeletedAtIsNull(
            String name, Long parentId
    );

    // 檢查第一層分類是否已有相同名稱
    boolean existsByNameAndParentIsNullAndDeletedAtIsNull(
            String name
    );

    // 修改第二層分類時，檢查同一上層分類是否有其他同名分類
    // IdNot 用來排除目前正在修改的分類
    boolean existsByNameAndParent_IdAndDeletedAtIsNullAndIdNot(
            String name, Long parentId, Long id
    );

    // 修改第一層分類時，檢查是否有其他同名分類
    boolean existsByNameAndParentIsNullAndDeletedAtIsNullAndIdNot(
            String name, Long id
    );

    // 檢查指定分類底下是否仍有尚未軟刪除的子分類
    boolean existsByParent_IdAndDeletedAtIsNull(Long parentId);

    // 統計指定分類底下尚未軟刪除的商品數量
    // 使用原生 SQL 查詢 products 資料表
    // 商品尚未軟刪除時，不允許刪除其所屬分類
    @Query(value = """
            SELECT COUNT(*)
            FROM products
            WHERE category_id = :categoryId
            AND deleted_at IS NULL
            """, nativeQuery = true)
    long countActiveProductsByCategoryId(
            @Param("categoryId") Long categoryId
    );
}
