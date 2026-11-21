package com.example.weather_order_assistant_system.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.weather_order_assistant_system.entity.SalesRecord;

@Repository
public interface SalesRecordRepository
        extends JpaRepository<SalesRecord, Long> {

    // 查詢所有未軟刪除的銷售紀錄
    // 依實際銷售時間由新到舊排序
    List<SalesRecord> findByDeletedAtIsNullOrderByDateTimeDesc();

    // 依銷售紀錄 ID 查詢未軟刪除的紀錄
    // 已退貨（軟刪除）的紀錄不視為有效資料
    Optional<SalesRecord> findByIdAndDeletedAtIsNull(Long id);

    // 查詢指定商品的有效銷售紀錄
    // 依實際銷售時間由新到舊排序
    List<SalesRecord> findByProduct_IdAndDeletedAtIsNullOrderByDateTimeDesc(
            Long productId
    );

    // 查詢指定日期時間區間內的有效銷售紀錄
    // 起始時間與結束時間皆包含在查詢範圍內
    List<SalesRecord> findByDateTimeBetweenAndDeletedAtIsNullOrderByDateTimeDesc(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    );

    // 退貨更新時鎖定原銷售紀錄，避免同時退貨超額。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SalesRecord s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<SalesRecord> findActiveByIdForUpdate(@Param("id") Long id);

    // 統計指定商品在指定期間內的淨銷售總數量
    // 此方法將用於未來 OrderRecommendation 訂貨建議
    //
    // SUM：加總符合條件的原始銷售量扣除累計退貨數量
    // COALESCE：若沒有任何銷售紀錄，回傳 0
    // 排除舊版已軟刪除（整筆退貨）的銷售紀錄
    //
    // 起始時間包含、結束時間不包含
    // 例如查詢 11/01～11/07：
    // startDateTime = 11/01 00:00:00
    // endDateTime   = 11/08 00:00:00
    @Query("""
        SELECT COALESCE(SUM(s.quantity - s.returnedQuantity), 0)
        FROM SalesRecord s
        WHERE s.product.id = :productId
          AND s.dateTime >= :startDateTime
          AND s.dateTime < :endDateTime
          AND s.deletedAt IS NULL
        """)
    Long sumQuantityByProductAndDateRange(
            @Param("productId") Long productId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );
}
