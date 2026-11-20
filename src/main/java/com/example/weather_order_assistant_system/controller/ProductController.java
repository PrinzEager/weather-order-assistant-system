package com.example.weather_order_assistant_system.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.weather_order_assistant_system.request.ProductRequest;
import com.example.weather_order_assistant_system.response.ProductResponse;
import com.example.weather_order_assistant_system.service.ProductService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    // 商品 Service：負責執行商品相關商業邏輯
    private final ProductService productService;

    /*
     * 查詢所有啟用且尚未軟刪除的商品
     *
     * GET /api/products
     *
     * 成功回傳 HTTP 200
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> findAllProducts() {

        List<ProductResponse> products =
                productService.findAllProducts();

        return ResponseEntity.ok(products);
    }

    /*
     * 根據商品 ID 查詢單筆商品
     *
     * GET /api/products/{id}
     *
     * 成功回傳 HTTP 200
     * 商品不存在或已刪除，回傳 HTTP 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findProductById(
            @PathVariable Long id
    ) {

        ProductResponse response =
                productService.findProductById(id);

        return ResponseEntity.ok(response);
    }

    /*
     * 新增商品
     *
     * POST /api/products
     *
     * @Valid：驗證 ProductRequest 中的欄位
     * 成功回傳 HTTP 201
     */
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest request) {

        ProductResponse response =
                productService.createProduct(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /*
     * 修改商品基本資料
     *
     * PUT /api/products/{id}
     *
     * 使用 ProductRequest 接收完整商品資料
     * 成功回傳 HTTP 200
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {

        ProductResponse response =
                productService.updateProduct(id, request);

        return ResponseEntity.ok(response);
    }

    /*
     * 修改商品啟用／停用狀態
     *
     * PATCH /api/products/{id}/status?status=0
     *
     * status = 1：啟用
     * status = 0：停用
     *
     * 成功回傳 HTTP 200
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ProductResponse> updateProductStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {

        ProductResponse response =
                productService.updateProductStatus(id, status);

        return ResponseEntity.ok(response);
    }

    /*
     * 軟刪除商品
     *
     * DELETE /api/products/{id}
     *
     * 成功回傳 HTTP 204
     * 不回傳 Response Body
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long id) {

        productService.deleteProduct(id);

        return ResponseEntity.noContent().build();
    }
}
