package com.example.weather_order_assistant_system.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductCategoryRequest {

    // 分類名稱不可為空，最多 100 個字元
    @NotBlank(message = "分類名稱不可為空")
    @Size(max = 100, message = "分類名稱不可超過 100 個字元")
    private String name;

    // 上層分類 ID：
    // null 代表建立第一層分類，有值代表建立第二層分類
    private Long parentId;
}
