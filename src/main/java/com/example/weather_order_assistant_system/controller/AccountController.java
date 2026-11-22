package com.example.weather_order_assistant_system.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.weather_order_assistant_system.request.ChangePasswordRequest;
import com.example.weather_order_assistant_system.response.AccountMeResponse;
import com.example.weather_order_assistant_system.service.AccountService;

import lombok.RequiredArgsConstructor;

/**
 * 已登入使用者管理自己的帳號。
 * SecurityConfig 的 anyRequest().authenticated() 已保護 /api/account/**。
 */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountMeResponse> getMyAccount(Authentication authentication) {
        // 從 Spring Security 取得已驗證的登入帳號，不採信 URL 或前端傳入的 userId。
        AccountMeResponse account = accountService.getMyAccount(authentication.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(account);
    }

    @PostMapping(value = "/change-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        // Service 成功提交資料庫交易後，才讓目前登入的 Session 失效。
        accountService.changeMyPassword(authentication.getName(), request);

        // 使用 Spring Security 既有的登出處理器，清除安全內容與 HTTP Session。
        // 由 HttpSessionEventPublisher 協助釋放「單帳號一個 Session」的登入名額。
        new SecurityContextLogoutHandler().logout(
                servletRequest, servletResponse, authentication);

        // 204 表示密碼已修改成功；前端應導向 /login 重新登入。
        return ResponseEntity.noContent().build();
    }
}
