package com.example.weather_order_assistant_system.controller;

import java.util.List;

import jakarta.validation.Valid;

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

import com.example.weather_order_assistant_system.request.PurchaseOrderRequest;
import com.example.weather_order_assistant_system.request.PurchaseOrderUpdateRequest;
import com.example.weather_order_assistant_system.response.PurchaseOrderResponse;
import com.example.weather_order_assistant_system.service.PurchaseOrderService;

import lombok.RequiredArgsConstructor;

// =====================================================
// 採購單 API Controller
// =====================================================
// 負責接收前端或 Postman 發送的 HTTP 請求。
//
// Controller 主要工作：
// 1. 接收 API 請求及參數
// 2. 使用 @Valid 驗證 Request DTO
// 3. 呼叫 PurchaseOrderService 執行業務邏輯
// 4. 回傳 Response DTO 與適當的 HTTP 狀態碼
//
// 不在 Controller 直接操作 Repository，
// 也不在 Controller 計算金額或修改商品庫存。
//
// 所有業務規則由 PurchaseOrderService 處理。
// =====================================================
@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    // 採購單 Service
    // 透過 Lombok @RequiredArgsConstructor 進行建構子注入
    private final PurchaseOrderService purchaseOrderService;

    // =====================================================
    // 1. 查詢所有未軟刪除的採購單
    // =====================================================
    // GET /api/purchase-orders
    //
    // 成功回傳 HTTP 200。
    // 依訂貨日期由新到舊排列。
    //
    // 即使供應商已停用或軟刪除，
    // 既有未軟刪除的採購單仍可查詢。
    // =====================================================
    @GetMapping
    public ResponseEntity<List<PurchaseOrderResponse>> findAllOrders() {

        List<PurchaseOrderResponse> orders =
                purchaseOrderService.findAllOrders();

        return ResponseEntity.ok(orders);
    }

    // =====================================================
    // 2. 依 ID 查詢採購單
    // =====================================================
    // GET /api/purchase-orders/{id}
    //
    // 成功：HTTP 200
    // 不存在或已軟刪除：HTTP 404
    //
    // 回傳採購單完整資訊，
    // 包含供應商歷史快照及所有商品明細。
    // =====================================================
    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrderResponse> findOrderById(
            @PathVariable Long id
    ) {

        PurchaseOrderResponse response =
                purchaseOrderService.findOrderById(id);

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // 3. 新增草稿採購單
    // =====================================================
    // POST /api/purchase-orders
    //
    // 使用 @Valid 驗證輸入資料。
    //
    // 新增時：
    // 1. 供應商必須啟用且未軟刪除
    // 2. 商品必須啟用且未軟刪除
    // 3. 至少需要一筆商品明細
    // 4. 同一張採購單不可有重複商品
    // 5. Service 自動計算明細小計與訂單總金額
    // 6. Service 保存供應商歷史資訊快照
    // 7. 訂單狀態預設為 DRAFT
    //
    // 成功回傳 HTTP 201。
    // =====================================================
    @PostMapping
    public ResponseEntity<PurchaseOrderResponse> createOrder(
            @Valid @RequestBody PurchaseOrderRequest request
    ) {

        PurchaseOrderResponse response =
                purchaseOrderService.createOrder(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // =====================================================
    // 4. 完整修改草稿採購單
    // =====================================================
    // PUT /api/purchase-orders/{id}
    //
    // 只允許 DRAFT 狀態修改。
    //
    // 使用 PUT 完整更新：
    // 1. 更新供應商（如有更換）
    // 2. 更新預計到貨日期與備註
    // 3. 使用新明細完整取代原明細
    // 4. 重新計算採購總金額
    //
    // 若更換供應商，Service 會更新五個快照欄位。
    // 若供應商不變，保留原本快照。
    //
    // 成功回傳 HTTP 200。
    // =====================================================
    @PutMapping("/{id}")
    public ResponseEntity<PurchaseOrderResponse> updateOrder(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderUpdateRequest request
    ) {

        PurchaseOrderResponse response =
                purchaseOrderService.updateOrder(id, request);

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // 5. 送出採購單
    // =====================================================
    // POST /api/purchase-orders/{id}/submit
    //
    // 只有 DRAFT 可以送出。
    //
    // DRAFT → SUBMITTED
    //
    // 送出後不立即增加庫存。
    // 商品數量將計入未來訂貨建議的未到貨量 O。
    //
    // 成功回傳 HTTP 200。
    // =====================================================
    @PostMapping("/{id}/submit")
    public ResponseEntity<PurchaseOrderResponse> submitOrder(
            @PathVariable Long id
    ) {

        PurchaseOrderResponse response =
                purchaseOrderService.submitOrder(id);

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // 6. 確認採購單到貨
    // =====================================================
    // POST /api/purchase-orders/{id}/receive
    //
    // 只有 SUBMITTED 可以確認到貨。
    //
    // SUBMITTED → RECEIVED
    //
    // Service 會：
    // 1. 逐筆增加商品庫存
    // 2. 更新採購單狀態
    // 3. 在同一交易內完成
    //
    // 成功回傳 HTTP 200。
    // =====================================================
    @PostMapping("/{id}/receive")
    public ResponseEntity<PurchaseOrderResponse> receiveOrder(
            @PathVariable Long id
    ) {

        PurchaseOrderResponse response =
                purchaseOrderService.receiveOrder(id);

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // 7. 取消採購單
    // =====================================================
    // POST /api/purchase-orders/{id}/cancel
    //
    // 允許：
    // DRAFT → CANCELLED
    // SUBMITTED → CANCELLED
    //
    // 不允許：
    // RECEIVED → CANCELLED
    // CANCELLED → CANCELLED
    //
    // 取消採購單不會修改商品庫存，
    // 也不會刪除歷史採購資料。
    //
    // 成功回傳 HTTP 200。
    // =====================================================
    @PostMapping("/{id}/cancel")
    public ResponseEntity<PurchaseOrderResponse> cancelOrder(
            @PathVariable Long id
    ) {

        PurchaseOrderResponse response =
                purchaseOrderService.cancelOrder(id);

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // 8. 軟刪除採購單
    // =====================================================
    // DELETE /api/purchase-orders/{id}
    //
    // 只有 DRAFT 或 CANCELLED 可以軟刪除。
    //
    // 不允許直接軟刪除：
    // SUBMITTED
    // RECEIVED
    //
    // 只設定 deletedAt，不實體刪除資料。
    // 採購明細與供應商快照都會保留。
    //
    // 成功回傳 HTTP 204，沒有 Response Body。
    // =====================================================
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(
            @PathVariable Long id
    ) {

        purchaseOrderService.deleteOrder(id);

        return ResponseEntity.noContent().build();
    }
}
