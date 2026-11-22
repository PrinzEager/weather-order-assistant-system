package com.example.weather_order_assistant_system.response;

/**
 * 後台供應商狀態管理清單 DTO。
 *
 * 不修改 Supplier API 的既有有效供應商查詢邏輯。
 * 停用供應商在管理查詢中仍可顯示，但軟刪除的供應商不會回傳。
 */
public record ManagementSupplierResponse(
        Long id,
        String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        Integer status
) {
}
