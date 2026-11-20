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

import com.example.weather_order_assistant_system.request.SupplierRequest;
import com.example.weather_order_assistant_system.response.SupplierResponse;
import com.example.weather_order_assistant_system.service.SupplierService;

import lombok.RequiredArgsConstructor;

// 供應商管理 API Controller
@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    // 供應商 Service，負責處理商業邏輯
    private final SupplierService supplierService;

    // 查詢所有啟用且未軟刪除的供應商
    // GET /api/suppliers
    // 成功回傳 HTTP 200
    @GetMapping
    public ResponseEntity<List<SupplierResponse>> findAllSuppliers() {

        List<SupplierResponse> suppliers =
                supplierService.findAllSuppliers();

        return ResponseEntity.ok(suppliers);
    }

    // 根據 ID 查詢單筆供應商
    // GET /api/suppliers/{id}
    // 成功回傳 HTTP 200
    @GetMapping("/{id}")
    public ResponseEntity<SupplierResponse> findSupplierById(
            @PathVariable Long id
    ) {

        SupplierResponse response =
                supplierService.findSupplierById(id);

        return ResponseEntity.ok(response);
    }

    // 新增供應商
    // POST /api/suppliers
    // 使用 @Valid 驗證 Request DTO
    // 成功回傳 HTTP 201
    @PostMapping
    public ResponseEntity<SupplierResponse> createSupplier(
            @Valid @RequestBody SupplierRequest request
    ) {

        SupplierResponse response =
                supplierService.createSupplier(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // 修改供應商基本資料
    // PUT /api/suppliers/{id}
    // 不修改供應商啟用或停用狀態
    // 成功回傳 HTTP 200
    @PutMapping("/{id}")
    public ResponseEntity<SupplierResponse> updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request
    ) {

        SupplierResponse response =
                supplierService.updateSupplier(id, request);

        return ResponseEntity.ok(response);
    }

    // 修改供應商狀態
    // PATCH /api/suppliers/{id}/status?status=0
    // PATCH /api/suppliers/{id}/status?status=1
    // 0：停用，1：啟用
    // 成功回傳 HTTP 200
    @PatchMapping("/{id}/status")
    public ResponseEntity<SupplierResponse> updateSupplierStatus(
            @PathVariable Long id,
            @RequestParam Integer status
    ) {

        SupplierResponse response =
                supplierService.updateSupplierStatus(id, status);

        return ResponseEntity.ok(response);
    }

    // 軟刪除供應商
    // DELETE /api/suppliers/{id}
    // 不實際刪除資料庫紀錄
    // 成功回傳 HTTP 204，沒有 Response Body
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSupplier(
            @PathVariable Long id
    ) {

        supplierService.deleteSupplier(id);

        return ResponseEntity.noContent().build();
    }
}
