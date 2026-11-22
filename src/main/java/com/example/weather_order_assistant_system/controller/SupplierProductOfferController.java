package com.example.weather_order_assistant_system.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.weather_order_assistant_system.response.SupplierProductResponse;
import com.example.weather_order_assistant_system.service.SupplierProductService;

import lombok.RequiredArgsConstructor;

/** 採購草稿用的有效供貨廠商及價格查詢 API。 */
@RestController
@RequestMapping("/api/supplier-products")
@RequiredArgsConstructor
public class SupplierProductOfferController {

    private final SupplierProductService supplierProductService;

    // 只回傳啟用供應商、啟用商品、啟用供貨關聯。
    // GET /api/supplier-products/by-product/2
    @GetMapping("/by-product/{productId}")
    public ResponseEntity<List<SupplierProductResponse>> findAvailableOffers(
            @PathVariable Long productId) {
        return ResponseEntity.ok(
                supplierProductService.findAvailableByProduct(productId));
    }
}
