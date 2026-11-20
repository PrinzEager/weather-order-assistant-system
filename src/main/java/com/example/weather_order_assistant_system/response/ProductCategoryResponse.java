package com.example.weather_order_assistant_system.response;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductCategoryResponse {

    // 商品分類編號
    private Long id;

    // 商品分類名稱
    private String name;

    // 上層分類編號
    // 第一層分類：null
    // 第二層分類：對應第一層分類 ID
    private Long parentId;

    // 上層分類名稱
    // 第一層分類：null
    // 第二層分類：對應第一層分類名稱
    private String parentName;

    // 商品分類狀態
    // 1：啟用
    // 0：停用
    // 使用 Integer，與 Entity 保持一致
    private Integer status;

    // 建立時間
    private LocalDateTime createdAt;

    // 最後修改時間
    private LocalDateTime updatedAt;

    // 建構子：用於將 Entity 資料轉換成 API Response
    public ProductCategoryResponse(
            Long id,
            String name,
            Long parentId,
            String parentName,
            Integer status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.parentName = parentName;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
