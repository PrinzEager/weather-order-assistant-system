package com.example.weather_order_assistant_system.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.StreamSupport;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.weather_order_assistant_system.entity.Product;
import com.example.weather_order_assistant_system.entity.Supplier;
import com.example.weather_order_assistant_system.repository.ProductRepository;
import com.example.weather_order_assistant_system.repository.SupplierRepository;
import com.example.weather_order_assistant_system.response.ManagementProductResponse;
import com.example.weather_order_assistant_system.response.ManagementSupplierResponse;

import lombok.RequiredArgsConstructor;

/**
 * 後台狀態清單：讓管理者能查看啟用與停用資料。
 *
 * 特別注意：一般 /api/products、/api/suppliers 仍維持只查有效資料；
 * 訂貨建議及採購下拉選單不應改用本服務。
 *
 * 現階段是 Demo，採用既有 JpaRepository.findAll() 再篩選；
 * 未來資料量大時可改成資料庫分頁查詢。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagementStatusService {

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;

    // 只接受 all / active / inactive，避免讓呼叫者任意查詢其他狀態。
    private Integer parseStatus(String status) {
        String value = status == null ? "all" : status.trim().toLowerCase(Locale.ROOT);

        return switch (value) {
            case "all" -> null;
            case "active" -> 1;
            case "inactive" -> 0;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "status 只能是 all、active 或 inactive"
            );
        };
    }

    public List<ManagementProductResponse> findProducts(String status) {
        Integer selectedStatus = parseStatus(status);

        // 刻意排除軟刪除資料；但保留 status=0 的停用商品。
        // @Transactional 確保組裝分類名稱時，LAZY 分類關聯可正常取得。
        return StreamSupport.stream(productRepository.findAll().spliterator(), false)
                .filter(product -> product.getDeletedAt() == null)
                .filter(product -> selectedStatus == null
                        || selectedStatus.equals(product.getStatus()))
                .sorted(Comparator.comparing(Product::getId))
                .map(product -> new ManagementProductResponse(
                        product.getId(),
                        product.getCategory() == null ? null : product.getCategory().getId(),
                        product.getCategory() == null ? null : product.getCategory().getName(),
                        product.getSku(),
                        product.getName(),
                        product.getPrice(),
                        product.getUnit(),
                        product.getCurrentStock(),
                        product.getSafetyStock(),
                        product.getStatus()
                ))
                .toList();
    }

    public List<ManagementSupplierResponse> findSuppliers(String status) {
        Integer selectedStatus = parseStatus(status);

        // 軟刪除的供應商不回傳；歷史採購單與快照均不會受影響。
        return StreamSupport.stream(supplierRepository.findAll().spliterator(), false)
                .filter(supplier -> supplier.getDeletedAt() == null)
                .filter(supplier -> selectedStatus == null
                        || selectedStatus.equals(supplier.getStatus()))
                .sorted(Comparator.comparing(Supplier::getId))
                .map(supplier -> new ManagementSupplierResponse(
                        supplier.getId(),
                        supplier.getName(),
                        supplier.getContactPerson(),
                        supplier.getPhone(),
                        supplier.getEmail(),
                        supplier.getAddress(),
                        supplier.getStatus()
                ))
                .toList();
    }
}
