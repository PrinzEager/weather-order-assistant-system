package com.example.weather_order_assistant_system.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.weather_order_assistant_system.request.SalesRecordRequest;
import com.example.weather_order_assistant_system.response.SalesRecordResponse;
import com.example.weather_order_assistant_system.service.SalesRecordService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sales-records")
@RequiredArgsConstructor
public class SalesRecordController {

    // 注入銷售紀錄 Service
    // Controller 負責接收 HTTP 請求
    // 實際業務邏輯由 Service 處理
    private final SalesRecordService salesRecordService;

    // 查詢所有未退貨的銷售紀錄
    // GET /api/sales-records
    // 成功回傳 HTTP 200
    @GetMapping
    public ResponseEntity<List<SalesRecordResponse>> findAllSalesRecords() {

        List<SalesRecordResponse> responses =
                salesRecordService.findAllSalesRecords();

        return ResponseEntity.ok(responses);
    }

    // 依 ID 查詢單筆有效銷售紀錄
    // GET /api/sales-records/{id}
    // 成功回傳 HTTP 200
    // 不存在或已退貨時，由 Service 回傳 404
    @GetMapping("/{id}")
    public ResponseEntity<SalesRecordResponse> findSalesRecordById(
            @PathVariable Long id
    ) {

        SalesRecordResponse response =
                salesRecordService.findSalesRecordById(id);

        return ResponseEntity.ok(response);
    }

    // 新增銷售紀錄
    // POST /api/sales-records
    // 成功回傳 HTTP 201
    //
    // @Valid 會觸發 SalesRecordRequest 欄位驗證
    // Service 負責檢查商品狀態、庫存及計算金額
    @PostMapping
    public ResponseEntity<SalesRecordResponse> createSalesRecord(
            @Valid @RequestBody SalesRecordRequest request
    ) {

        SalesRecordResponse response =
                salesRecordService.createSalesRecord(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // 整筆退貨（軟刪除銷售紀錄）
    // DELETE /api/sales-records/{id}
    // 成功回傳 HTTP 204，沒有 Response Body
    //
    // Service 會執行：
    // 1. 確認銷售紀錄存在且尚未退貨
    // 2. 恢復商品庫存
    // 3. 設定銷售紀錄 deletedAt
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSalesRecord(
            @PathVariable Long id
    ) {

        salesRecordService.deleteSalesRecord(id);

        return ResponseEntity.noContent().build();
    }
}
