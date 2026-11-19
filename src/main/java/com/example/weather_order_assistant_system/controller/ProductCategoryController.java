package com.example.weather_order_assistant_system.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.weather_order_assistant_system.request.ProductCategoryRequest;
import com.example.weather_order_assistant_system.response.ProductCategoryResponse;
import com.example.weather_order_assistant_system.service.ProductCategoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/product-categories")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryService categoryService;

    /**
     * 查詢所有商品分類
     * GET /api/product-categories
     */
    @GetMapping
    public ResponseEntity<List<ProductCategoryResponse>> findAllCategories() {

        List<ProductCategoryResponse> categories =
                categoryService.findAllCategories();

        return ResponseEntity.ok(categories);
    }

    /**
     * 根據 ID 查詢商品分類
     * GET /api/product-categories/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductCategoryResponse> findCategoryById(
            @PathVariable Long id) {

        ProductCategoryResponse response =
                categoryService.findCategoryById(id);

        return ResponseEntity.ok(response);
    }

    /**
     * 新增商品分類
     * POST /api/product-categories
     */
    @PostMapping
    public ResponseEntity<ProductCategoryResponse> createCategory(
            @Valid @RequestBody ProductCategoryRequest request) {

        ProductCategoryResponse response =
                categoryService.createCategory(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 修改商品分類
     * PUT /api/product-categories/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductCategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody ProductCategoryRequest request) {

        ProductCategoryResponse response =
                categoryService.updateCategory(id, request);

        return ResponseEntity.ok(response);
    }

    /**
     * 軟刪除商品分類
     * DELETE /api/product-categories/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long id) {

        categoryService.deleteCategory(id);

        return ResponseEntity.noContent().build();
    }
}