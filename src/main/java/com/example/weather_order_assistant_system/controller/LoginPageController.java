package com.example.weather_order_assistant_system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 專用登入頁，與原本的八個 History Router 網址分開。
 * POST /login 由 Spring Security 處理，不需要自己寫密碼驗證 Controller。
 */
@Controller
public class LoginPageController {

    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }
}
