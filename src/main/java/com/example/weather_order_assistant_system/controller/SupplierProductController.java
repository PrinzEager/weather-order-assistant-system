package com.example.weather_order_assistant_system.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.weather_order_assistant_system.request.SupplierProductCreateRequest;
import com.example.weather_order_assistant_system.request.SupplierProductPriceRequest;
import com.example.weather_order_assistant_system.request.SupplierProductStatusRequest;
import com.example.weather_order_assistant_system.response.SupplierProductResponse;
import com.example.weather_order_assistant_system.service.SupplierProductService;

import lombok.RequiredArgsConstructor;

/**
 * 指定供應商的供貨商品管理 API。
 * 為避免影響原 SupplierController，新增獨立 Controller 處理供貨關聯。
 */
@RestController
@RequestMapping("/api/suppliers/{supplierId}/products")
@RequiredArgsConstructor
public class SupplierProductController {

    private final SupplierProductService supplierProductService;

    // POST /api/suppliers/1/products：新增供貨商品與採購價格。
    @PostMapping
    public ResponseEntity<SupplierProductResponse> create(
            @PathVariable Long supplierId,
            @Valid @RequestBody SupplierProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierProductService.create(supplierId, request));
    }

    // GET /api/suppliers/1/products：包含已停止供貨的關聯，方便供應商維護。
    @GetMapping
    public ResponseEntity<List<SupplierProductResponse>> list(
            @PathVariable Long supplierId) {
        return ResponseEntity.ok(supplierProductService.findBySupplier(supplierId));
    }

    // PUT /api/suppliers/1/products/2：只修改該商品的採購價格。
    @PutMapping("/{productId}")
    public ResponseEntity<SupplierProductResponse> updatePrice(
            @PathVariable Long supplierId,
            @PathVariable Long productId,
            @Valid @RequestBody SupplierProductPriceRequest request) {
        return ResponseEntity.ok(
                supplierProductService.updatePrice(supplierId, productId, request));
    }

    // PUT /api/suppliers/1/products/2/status：停止供貨或重新啟用。
    @PutMapping("/{productId}/status")
    public ResponseEntity<SupplierProductResponse> updateStatus(
            @PathVariable Long supplierId,
            @PathVariable Long productId,
            @Valid @RequestBody SupplierProductStatusRequest request) {
        return ResponseEntity.ok(
                supplierProductService.updateStatus(supplierId, productId, request.getStatus()));
    }
}
