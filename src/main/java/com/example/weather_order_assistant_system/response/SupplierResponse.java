package com.example.weather_order_assistant_system.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 供應商 API 回傳資料 DTO
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplierResponse {

    // 供應商編號
    private Long id;

    // 供應商名稱
    private String name;

    // 聯絡人姓名
    private String contactPerson;

    // 聯絡電話
    private String phone;

    // 電子郵件
    private String email;

    // 供應商地址
    private String address;

    // 供應商狀態
    // 1：啟用
    // 0：停用
    private Integer status;

    // 資料建立時間
    private LocalDateTime createdAt;

    // 資料最後修改時間
    private LocalDateTime updatedAt;
}
