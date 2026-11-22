package com.example.weather_order_assistant_system.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 批次採購草稿建立結果。僅回傳 DTO，避免直接序列化 JPA Entity。
 * 使用 Java record 表示建立完成後不需要再修改的回傳資料。
 */
public record PurchaseOrderBatchResponse(
        int createdOrderCount,
        BigDecimal totalAmount,
        List<CreatedOrder> orders) {

    // 同一供應商建立一張採購單；各筆仍是 DRAFT。
    public record CreatedOrder(
            Long orderId,
            Long supplierId,
            String supplierName,
            String orderStatus,
            LocalDate expectedDeliveryDate,
            BigDecimal totalAmount,
            List<CreatedItem> items) {
    }

    // 採購單價來自 supplier_products 的有效報價，並寫入下單當下的明細。
    public record CreatedItem(
            Long productId,
            String productName,
            String sku,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal) {
    }
}
