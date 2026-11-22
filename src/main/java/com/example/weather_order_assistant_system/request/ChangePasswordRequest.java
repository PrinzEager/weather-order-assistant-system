package com.example.weather_order_assistant_system.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 登入者修改自身密碼的請求資料。
 * 不接受 userId / username，避免從前端指定要修改別人的帳號。
 */
@Getter
@Setter
public class ChangePasswordRequest {

    // 必須輸入目前密碼，後端會重新驗證 BCrypt 雜湊。
    @NotBlank(message = "請輸入目前密碼")
    private String currentPassword;

    // 至少 12 個字元；另由 Service 確認 UTF-8 不超過 BCrypt 的 72 bytes 限制。
    @NotBlank(message = "請輸入新密碼")
    @Size(min = 12, message = "新密碼至少需要 12 個字元")
    private String newPassword;

    // 再次輸入新密碼，減少輸入錯誤造成無法登入的風險。
    @NotBlank(message = "請再次輸入新密碼")
    private String confirmPassword;
}
