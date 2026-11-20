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

import com.example.weather_order_assistant_system.response.OrderRecommendationResponse;
import com.example.weather_order_assistant_system.service.OrderRecommendationService;

import lombok.RequiredArgsConstructor;

/**
 * 天氣訂貨建議 API Controller。
 *
 * 提供以下功能：
 * 1. 查詢所有有效商品的天氣訂貨建議。
 * 2. 查詢指定商品的天氣訂貨建議。
 *
 * 訂貨建議整合：
 * - 歷史銷售數量
 * - 商品目前庫存
 * - 商品安全庫存
 * - 尚未到貨的採購數量
 * - 中央氣象署天氣需求倍率
 *
 * Controller 只負責接收 HTTP 請求與回傳結果，
 * 實際商業邏輯交由 OrderRecommendationService 處理。
 *
 * 此 API 只產生訂貨建議，不會自動建立採購單。
 */
@RestController
@RequestMapping("/api/order-recommendations")
@RequiredArgsConstructor
public class OrderRecommendationController {

    // 天氣訂貨建議服務
    private final OrderRecommendationService orderRecommendationService;

    /**
     * 查詢所有有效商品的訂貨建議。
     *
     * GET /api/order-recommendations
     *
     * 查詢參數：
     * city：縣市名稱
     * district：鄉鎮市區名稱
     * startDate：預估需求起始日期
     * endDate：預估需求結束日期
     *
     * 所有商品共用同一次天氣 API 查詢結果，
     * 避免重複呼叫中央氣象署。
     *
     * @return 所有有效商品的訂貨建議清單
     */
    @GetMapping
    public ResponseEntity<List<OrderRecommendationResponse>>
            getAllRecommendations(

                    @RequestParam String city,

                    @RequestParam String district,

                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,

                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate) {

        // 呼叫 Service 計算全部商品的訂貨建議
        List<OrderRecommendationResponse> responses =
                orderRecommendationService.getAllRecommendations(
                        city,
                        district,
                        startDate,
                        endDate);

        // 成功時回傳 HTTP 200 與訂貨建議清單
        // 若目前沒有有效商品，會回傳空陣列 []
        return ResponseEntity.ok(responses);
    }

    /**
     * 查詢指定商品的訂貨建議。
     *
     * GET /api/order-recommendations/{productId}
     *
     * @param productId 商品 ID
     * @param city 縣市名稱
     * @param district 鄉鎮市區名稱
     * @param startDate 預估需求起始日期
     * @param endDate 預估需求結束日期
     * @return 指定商品的訂貨建議
     */
    @GetMapping("/{productId}")
    public ResponseEntity<OrderRecommendationResponse>
            getRecommendation(

                    @PathVariable Long productId,

                    @RequestParam String city,

                    @RequestParam String district,

                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,

                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate) {

        // 呼叫 Service 計算指定商品的訂貨建議
        OrderRecommendationResponse response =
                orderRecommendationService.getRecommendation(
                        productId,
                        city,
                        district,
                        startDate,
                        endDate);

        // 成功時回傳 HTTP 200 與單一商品訂貨建議
        return ResponseEntity.ok(response);
    }
}
