package com.example.weather_order_assistant_system.response;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductCategoryResponse {

    // 分類 ID
    private Long id;

    // 分類名稱
    private String name;

    // 上層分類 ID，第一層分類為 null
    private Long parentId;

    // 上層分類名稱，第一層分類為 null
    private String parentName;

    // 分類狀態：1 = 啟用、0 = 停用
    private Integer status;

    // 建立時間
    private LocalDateTime createdAt;

    // 最後修改時間
    private LocalDateTime updatedAt;

    // 將 Entity 轉換成 Response DTO
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