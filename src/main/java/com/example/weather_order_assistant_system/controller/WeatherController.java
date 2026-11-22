package com.example.weather_order_assistant_system.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.weather_order_assistant_system.response.WeatherForecastResponse;
import com.example.weather_order_assistant_system.service.WeatherService;

import lombok.RequiredArgsConstructor;

/**
 * 天氣預報 API Controller。
 *
 * 提供前端查詢中央氣象署未來 7 天天氣預報。
 *
 * 主要功能：
 * 1. 接收縣市與鄉鎮市區名稱。
 * 2. 接收查詢起始日期與結束日期。
 * 3. 呼叫 WeatherService 取得預報結果。
 * 4. 回傳每日天氣、各時段預報及天氣需求倍率。
 *
 * Controller 只負責 HTTP 請求與回應，
 * 日期驗證、JSON 解析及天氣倍率計算
 * 均交由 WeatherService 處理。
 */
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    // 天氣預報服務
    private final WeatherService weatherService;

    /**
     * 查詢指定縣市、行政區與日期範圍的天氣預報。
     *
     * API：
     * GET /api/weather/forecast
     *
     * 查詢範例：
     * /api/weather/forecast
     * ?city=高雄市
     * &district=路竹區
     * &startDate=2026-11-21
     * &endDate=2026-11-27
     *
     * @param city 縣市完整名稱
     * @param district 鄉鎮市區完整名稱
     * @param startDate 預報起始日期，包含當日
     * @param endDate 預報結束日期，包含當日
     * @return 天氣預報資料與訂貨需求倍率
     */
    @GetMapping("/forecast")
    public ResponseEntity<WeatherForecastResponse> getForecast(

            @RequestParam String city,

            @RequestParam String district,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate) {

        // 呼叫 WeatherService 取得天氣預報
        WeatherForecastResponse response =
                weatherService.getForecast(
                        city,
                        district,
                        startDate,
                        endDate);

        // 成功時回傳 HTTP 200 與完整天氣預報資料
        return ResponseEntity.ok(response);
    }
}
