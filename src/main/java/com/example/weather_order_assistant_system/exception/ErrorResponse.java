package com.example.weather_order_assistant_system.exception;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 統一 API 錯誤回應格式
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    // 發生錯誤的時間
    private LocalDateTime timestamp;

    // HTTP 狀態碼，例如 400、404、409
    private int status;

    // 錯誤訊息
    private String message;

    // 發生錯誤的 API 路徑
    private String path;
}
