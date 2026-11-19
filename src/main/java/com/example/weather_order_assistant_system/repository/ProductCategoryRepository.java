package com.example.weather_order_assistant_system.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.weather_order_assistant_system.entity.ProductCategory;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    // 查詢所有尚未刪除且符合指定狀態的分類
    List<ProductCategory> findByStatusAndDeletedAtIsNullOrderByIdAsc(Byte status);

    // 根據 ID 查詢尚未刪除的分類
    Optional<ProductCategory> findByIdAndDeletedAtIsNull(Long id);

    // 查詢指定上層分類底下，所有尚未刪除的子分類
    List<ProductCategory> findByParent_IdAndDeletedAtIsNull(Long parentId);

    // 查詢所有尚未刪除的第一層分類
    List<ProductCategory> findByParentIsNullAndDeletedAtIsNull();

    // 檢查指定上層分類底下是否已有相同名稱的分類
    boolean existsByNameAndParent_IdAndDeletedAtIsNull(String name, Long parentId);

    // 檢查第一層分類是否已有相同名稱
    boolean existsByNameAndParentIsNullAndDeletedAtIsNull(String name);

    // 檢查指定名稱是否已被其他分類使用（修改分類時排除自己）
    boolean existsByNameAndParent_IdAndDeletedAtIsNullAndIdNot(
            String name, Long parentId, Long id);

    // 檢查第一層分類名稱是否已被其他分類使用（修改分類時排除自己）
    boolean existsByNameAndParentIsNullAndDeletedAtIsNullAndIdNot(
            String name, Long id);

    // 查詢指定分類是否有尚未刪除的子分類
    boolean existsByParent_IdAndDeletedAtIsNull(Long parentId);

    // 檢查分類是否仍有尚未軟刪除的商品使用
    @Query(value = """
            SELECT COUNT(*)
            FROM products
            WHERE category_id = :categoryId
            AND deleted_at IS NULL
            """, nativeQuery = true)
    long countActiveProductsByCategoryId(@Param("categoryId") Long categoryId);
}