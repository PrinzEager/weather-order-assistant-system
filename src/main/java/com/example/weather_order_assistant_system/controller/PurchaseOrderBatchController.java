package com.example.weather_order_assistant_system.controller;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.weather_order_assistant_system.request.PurchaseOrderBatchRequest;
import com.example.weather_order_assistant_system.response.PurchaseOrderBatchResponse;
import com.example.weather_order_assistant_system.service.PurchaseDeliveryDatePolicy;
import com.example.weather_order_assistant_system.service.PurchaseOrderBatchService;

/**
 * 多供應商採購草稿 API，與原本 /api/purchase-orders 分開，保留舊單張建單流程。
 */
@RestController
@RequestMapping("/api/purchase-order-batches")
@RequiredArgsConstructor
public class PurchaseOrderBatchController {

    private final PurchaseOrderBatchService batchService;

    /**
     * 前端日期選擇器可先取得後端算出的最早預計到貨日期。
     * 但 POST 時仍會重新驗證，防止 12:00 截單後使用過期日期。
     */
    @GetMapping("/delivery-policy")
    public ResponseEntity<DeliveryPolicyResponse> getDeliveryPolicy() {
        ZonedDateTime now = ZonedDateTime.now(PurchaseDeliveryDatePolicy.TAIWAN_ZONE);
        LocalDate earliest = PurchaseDeliveryDatePolicy.earliestDeliveryDate(
                java.time.Clock.fixed(now.toInstant(), PurchaseDeliveryDatePolicy.TAIWAN_ZONE));
        return ResponseEntity.ok(new DeliveryPolicyResponse(
                now, "12:00", earliest));
    }

    /** 建立一批草稿，按供應商自動分為多張採購單。 */
    @PostMapping("/drafts")
    public ResponseEntity<PurchaseOrderBatchResponse> createDrafts(
            @Valid @RequestBody PurchaseOrderBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(batchService.createDrafts(request));
    }

    /** 使用者只需看當前台灣時間、截單時刻與最早可選日期。 */
    public record DeliveryPolicyResponse(
            ZonedDateTime serverTime, String cutoffTime, LocalDate earliestDeliveryDate) {
    }
}
