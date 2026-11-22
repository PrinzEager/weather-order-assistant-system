package com.example.weather_order_assistant_system.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductRequest {

    // 商品分類 ID
    // 不可為空，且必須大於 0
    // 是否為有效的第二層分類，交由 Service 驗證
    @NotNull(message = "商品分類不可為空")
    @Positive(message = "商品分類 ID 必須大於 0")
    private Long categoryId;

    // 商品代碼（SKU）
    // 不可為空白，長度最多 50 個字元
    // SKU 是否重複，交由 Service 驗證
    @NotBlank(message = "商品代碼不可為空")
    @Size(max = 50, message = "商品代碼不可超過 50 個字元")
    private String sku;

    // 商品名稱
    // 不可為空白，長度最多 100 個字元
    @NotBlank(message = "商品名稱不可為空")
    @Size(max = 100, message = "商品名稱不可超過 100 個字元")
    private String name;

    // 商品售價
    // 不可為空，且不可小於 0
    // 金額精度限制會在 Service 中進一步確認
    @NotNull(message = "商品售價不可為空")
    @DecimalMin(
        value = "0.00",
        message = "商品售價不可小於 0"
    )
    private BigDecimal price;

    // 商品單位，例如：個、瓶、包
    // 可不提供，預設為「個」
    // 若有提供，不可為空白且不可超過 20 個字元
    @Size(max = 20, message = "商品單位不可超過 20 個字元")
    private String unit;

    // 目前庫存數量
    // 可不提供，預設為 0
    // 若有提供，不可小於 0
    @PositiveOrZero(message = "目前庫存不可小於 0")
    private Integer currentStock;

    // 編輯表單開啟時讀到的庫存快照（只用於修改商品）。
    // 修改庫存前，Service 會與資料庫最新庫存比對；
    // 若銷售或入庫已改變庫存，拒絕覆蓋，請使用者重新載入。
    // 新增商品不需要傳入；舊版僅修改基本資料的請求可省略。
    @PositiveOrZero(message = "原始庫存不可小於 0")
    private Integer expectedCurrentStock;

    // 安全庫存數量
    // 可不提供，預設為 0
    // 若有提供，不可小於 0
    @PositiveOrZero(message = "安全庫存不可小於 0")
    private Integer safetyStock;
}
