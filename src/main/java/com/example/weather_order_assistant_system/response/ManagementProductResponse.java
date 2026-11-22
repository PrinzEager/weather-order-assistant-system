package com.example.weather_order_assistant_system.response;

import java.math.BigDecimal;

/**
 * 後台商品狀態管理清單 DTO。
 *
 * 僅用在 /api/management/products，不改變原有 Product API 回傳格式。
 * 只回傳未軟刪除商品；停用商品會保留在此清單供重新啟用。
 */
public record ManagementProductResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String sku,
        String name,
        BigDecimal price,
        String unit,
        Integer currentStock,
        Integer safetyStock,
        Integer status
) {
}
