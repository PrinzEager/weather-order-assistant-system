package com.example.weather_order_assistant_system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 單頁式後台（Thymeleaf）功能路由。
 *
 * 當使用者直接輸入 /orders、重新整理 /products 等網址時，
 * Spring Boot 必須回傳同一個 templates/index.html。
 * 真正要顯示哪一個功能區塊，由前端 router.js 依 pathname 決定。
 *
 * 這裡只註冊八個固定頁面路徑，不使用萬用字元，
 * 避免攔截既有 /api/**、靜態資源與未定義路徑的正常 404。
 * 原本 GET / 的 Controller 保留不變。
 */
@Controller
public class FrontendRouteController {

    @GetMapping({
        "/home",
        "/weather",
        "/recommendations",
        "/categories",
        "/products",
        "/suppliers",
        "/sales",
        "/orders"
    })
    public String showFrontendPage() {
        // 不要回傳 index.html：Spring MVC 的 Thymeleaf view name 是 index。
        return "index";
    }
}
