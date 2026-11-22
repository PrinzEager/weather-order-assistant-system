package com.example.weather_order_assistant_system.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.weather_order_assistant_system.response.OrderRecommendationOptionResponse;
import com.example.weather_order_assistant_system.service.OrderRecommendationService;

import lombok.RequiredArgsConstructor;

/**
 * 給新版訂貨清單使用的 API。
 *
 * 舊的 /api/order-recommendations 保留不動，避免影響目前已完成的前端。
 * 前端日後可以依每筆商品的勾選狀態，選擇 normal 或 weather 建議量。
 */
@RestController
@RequestMapping("/api/order-recommendation-options")
@RequiredArgsConstructor
public class OrderRecommendationOptionsController {

    private final OrderRecommendationService orderRecommendationService;

    /**
     * 全部有效商品的兩種訂貨建議。
     * basis=WEEK：上週同期；basis=MONTH：上月同期（預設）。
     */
    @GetMapping
    public ResponseEntity<List<OrderRecommendationOptionResponse>> getAll(
            @RequestParam String city,
            @RequestParam String district,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,
            @RequestParam(defaultValue = "MONTH") String basis) {

        return ResponseEntity.ok(
                orderRecommendationService.getAllRecommendationOptions(
                        city, district, startDate, endDate, basis));
    }

    /**
     * 單一商品查詢，方便驗證同一商品的 WEEK／MONTH 切換。
     */
    @GetMapping("/{productId}")
    public ResponseEntity<OrderRecommendationOptionResponse> getOne(
            @PathVariable Long productId,
            @RequestParam String city,
            @RequestParam String district,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,
            @RequestParam(defaultValue = "MONTH") String basis) {

        return ResponseEntity.ok(
                orderRecommendationService.getRecommendationOptions(
                        productId, city, district, startDate, endDate, basis));
    }
}
