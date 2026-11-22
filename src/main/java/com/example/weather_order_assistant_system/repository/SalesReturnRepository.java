package com.example.weather_order_assistant_system.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.weather_order_assistant_system.entity.SalesReturn;

@Repository
public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {

    // 依退貨時間倒序列出同一筆銷售的所有退貨紀錄。
    List<SalesReturn> findBySalesRecord_IdOrderByReturnedAtDescIdDesc(Long salesRecordId);
}
