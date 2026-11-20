package com.example.weather_order_assistant_system.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 商品 API 回傳資料 DTO
// 將商品 Entity 轉換為前端需要的資料格式
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    // 商品編號
    private Long id;

    // 商品分類編號
    private Long categoryId;

    // 商品分類名稱
    private String categoryName;

    // 商品代碼（SKU）
    private String sku;

    // 商品名稱
    private String name;

    // 商品售價
    private BigDecimal price;

    // 商品單位，例如：個、瓶、包
    private String unit;

    // 目前庫存數量
    private Integer currentStock;

    // 安全庫存數量
    private Integer safetyStock;

    // 商品狀態
    // 1：啟用
    // 0：停用
    private Integer status;

    // 商品建立時間
    private LocalDateTime createdAt;

    // 商品最後修改時間
    private LocalDateTime updatedAt;
}
