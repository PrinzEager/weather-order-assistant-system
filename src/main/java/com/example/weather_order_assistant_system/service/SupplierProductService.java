package com.example.weather_order_assistant_system.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.weather_order_assistant_system.entity.Product;
import com.example.weather_order_assistant_system.entity.Supplier;
import com.example.weather_order_assistant_system.entity.SupplierProduct;
import com.example.weather_order_assistant_system.repository.ProductRepository;
import com.example.weather_order_assistant_system.repository.SupplierProductRepository;
import com.example.weather_order_assistant_system.repository.SupplierRepository;
import com.example.weather_order_assistant_system.request.SupplierProductCreateRequest;
import com.example.weather_order_assistant_system.request.SupplierProductPriceRequest;
import com.example.weather_order_assistant_system.response.SupplierProductResponse;

import lombok.RequiredArgsConstructor;

/**
 * 供應商供貨商品管理。
 *
 * 將供貨關聯和供應商基本資料 CRUD 分開，避免原 SupplierService 變得複雜。
 * 所有更新都放在交易內；一律停用關聯而不刪除，保留歷史資料。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SupplierProductService {

    private static final Integer ACTIVE = 1;
    private static final Integer INACTIVE = 0;

    private final SupplierProductRepository supplierProductRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    /** 新增單筆供貨商品；已存在的組合不可重複新增，停用者應改用狀態 API 重新啟用。 */
    public SupplierProductResponse create(
            Long supplierId, SupplierProductCreateRequest request) {

        Supplier supplier = findActiveSupplier(supplierId);
        Product product = findActiveProduct(request.getProductId());
        validatePurchasePrice(request.getPurchasePrice());

        // UNIQUE 限制包含已停用的關聯，避免重複供貨資料。
        if (supplierProductRepository.findBySupplier_IdAndProduct_Id(
                supplierId, product.getId()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "此供應商已建立該商品的供貨關聯，請修改或重新啟用");
        }

        SupplierProduct relation = new SupplierProduct();
        relation.setSupplier(supplier);
        relation.setProduct(product);
        relation.setPurchasePrice(request.getPurchasePrice());
        relation.setStatus(ACTIVE);

        // 資料庫 UNIQUE 約束仍會在併發請求下提供最後一道保護。
        return toResponse(supplierProductRepository.save(relation));
    }

    /** 供應商管理：顯示該供應商所有商品及其狀態（包含停止供貨）。 */
    @Transactional(readOnly = true)
    public List<SupplierProductResponse> findBySupplier(Long supplierId) {
        findExistingSupplier(supplierId);
        return supplierProductRepository.findBySupplier_IdOrderByIdAsc(supplierId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** 採購草稿選單：只提供仍有效、仍啟用的供應商報價。 */
    @Transactional(readOnly = true)
    public List<SupplierProductResponse> findAvailableByProduct(Long productId) {
        findActiveProduct(productId);
        return supplierProductRepository.findActiveOffersByProductId(productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** 修改單一供貨關聯的採購單價；不會修改已建立的採購單明細。 */
    public SupplierProductResponse updatePrice(
            Long supplierId, Long productId, SupplierProductPriceRequest request) {

        findActiveSupplier(supplierId);
        findActiveProduct(productId);
        validatePurchasePrice(request.getPurchasePrice());

        SupplierProduct relation = findRelation(supplierId, productId);
        relation.setPurchasePrice(request.getPurchasePrice());
        return toResponse(supplierProductRepository.save(relation));
    }

    /**
     * 修改供貨狀態。
     * 停用允許保留紀錄；重新啟用時必須確認供應商與商品都有效。
     */
    public SupplierProductResponse updateStatus(
            Long supplierId, Long productId, Integer status) {

        if (!ACTIVE.equals(status) && !INACTIVE.equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "供貨狀態只能是 1（啟用）或 0（停用）");
        }

        findExistingSupplier(supplierId);
        SupplierProduct relation = findRelation(supplierId, productId);

        if (ACTIVE.equals(status)) {
            findActiveSupplier(supplierId);
            findActiveProduct(productId);
        }

        if (!status.equals(relation.getStatus())) {
            relation.setStatus(status);
            relation = supplierProductRepository.save(relation);
        }
        return toResponse(relation);
    }

    private Supplier findExistingSupplier(Long id) {
        requirePositiveId(id, "供應商 ID");
        return supplierRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "供應商不存在或已刪除"));
    }

    private Supplier findActiveSupplier(Long id) {
        Supplier supplier = findExistingSupplier(id);
        if (!ACTIVE.equals(supplier.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "供應商已停用，無法新增或修改供貨商品");
        }
        return supplier;
    }

    private Product findActiveProduct(Long id) {
        requirePositiveId(id, "商品 ID");
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "商品不存在或已刪除"));
        if (!ACTIVE.equals(product.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "商品已停用，無法作為有效供貨商品");
        }
        return product;
    }

    private SupplierProduct findRelation(Long supplierId, Long productId) {
        requirePositiveId(productId, "商品 ID");
        return supplierProductRepository.findBySupplier_IdAndProduct_Id(supplierId, productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "找不到此供應商與商品的供貨關聯"));
    }

    // 由 Controller 的 Bean Validation 先驗證；Service 再保護非 HTTP 呼叫的情況。
    private void validatePurchasePrice(BigDecimal price) {
        if (price == null || price.signum() < 0
                || price.scale() > 2 || price.precision() - price.scale() > 8) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "採購單價不可為負數，且最多 8 位整數及 2 位小數");
        }
    }

    private void requirePositiveId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " 必須大於 0");
        }
    }

    /** 只輸出 DTO，不直接序列化 JPA 關聯物件。 */
    private SupplierProductResponse toResponse(SupplierProduct relation) {
        return new SupplierProductResponse(
                relation.getId(),
                relation.getSupplier().getId(),
                relation.getSupplier().getName(),
                relation.getProduct().getId(),
                relation.getProduct().getName(),
                relation.getProduct().getSku(),
                relation.getPurchasePrice(),
                relation.getStatus(),
                relation.getCreatedAt(),
                relation.getUpdatedAt());
    }
}
