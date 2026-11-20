package com.example.weather_order_assistant_system.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

// 供應商新增與修改 API 共用的 Request DTO
@Getter
@Setter
public class SupplierRequest {

    // 供應商名稱：不可為空白，最多 100 字元
    @NotBlank(message = "供應商名稱不可為空")
    @Size(max = 100, message = "供應商名稱不可超過 100 字元")
    private String name;

    // 聯絡人姓名：非必填，最多 50 字元
    @Size(max = 50, message = "聯絡人姓名不可超過 50 字元")
    private String contactPerson;

    // 聯絡電話：非必填，最多 30 字元
    @Size(max = 30, message = "聯絡電話不可超過 30 字元")
    private String phone;

    // 電子郵件：非必填，但有填寫時必須符合 Email 格式
    // Email 是否重複，後續由 SupplierService 檢查
    @Email(message = "電子郵件格式不正確")
    @Size(max = 100, message = "電子郵件不可超過 100 字元")
    private String email;

    // 供應商地址：非必填，最多 255 字元
    @Size(max = 255, message = "供應商地址不可超過 255 字元")
    private String address;
}
