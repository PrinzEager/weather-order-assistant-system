package com.example.weather_order_assistant_system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端首頁控制器
 *
 * 負責處理 Thymeleaf 頁面導覽。
 * 與既有 REST API Controller 分開，
 * 避免影響商品、銷售、訂貨單及天氣等 API。
 */
@Controller
public class HomeController {

    /**
     * 顯示系統首頁。
     *
     * 使用者開啟 http://localhost:8080/
     * Spring MVC 會交由 Thymeleaf 載入：
     * src/main/resources/templates/index.html
     *
     * @return Thymeleaf 模板名稱 index
     */
    @GetMapping("/")
    public String index() {

        // 回傳的是模板名稱，而不是 JSON 資料。
        // Thymeleaf 會自動解析 templates/index.html。
        return "index";
    }
}
