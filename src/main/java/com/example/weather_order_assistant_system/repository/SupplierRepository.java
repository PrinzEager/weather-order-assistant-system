package com.example.weather_order_assistant_system.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.weather_order_assistant_system.entity.Supplier;

@Repository
public interface SupplierRepository
        extends JpaRepository<Supplier, Long> {

    // 查詢啟用且未軟刪除的供應商
    // status = 1：啟用
    // deletedAt 為 null：尚未刪除
    // 依照供應商 ID 由小到大排序
    List<Supplier> findByStatusAndDeletedAtIsNullOrderByIdAsc(
            Integer status
    );

    // 依照供應商 ID 查詢未軟刪除的資料
    // 允許查詢停用但尚未刪除的供應商
    Optional<Supplier> findByIdAndDeletedAtIsNull(
            Long id
    );

    // 檢查電子郵件是否已存在
    // 包含啟用、停用及已軟刪除的供應商
    // 避免新增供應商時發生 Email 重複
    boolean existsByEmail(
            String email
    );

    // 修改供應商時檢查 Email 是否與其他供應商重複
    // 排除目前正在修改的供應商 ID
    // 同樣包含已軟刪除的資料
    boolean existsByEmailAndIdNot(
            String email,
            Long id
    );

    // 依照供應商名稱進行模糊查詢
    // 只查詢啟用且未軟刪除的供應商
    // 依照供應商 ID 由小到大排序
    List<Supplier> findByNameContainingAndStatusAndDeletedAtIsNullOrderByIdAsc(
            String name,
            Integer status
    );
}
