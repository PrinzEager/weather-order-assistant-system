package com.example.weather_order_assistant_system.exception;

import java.time.LocalDateTime;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

// 全域例外處理器，統一處理 Controller 發生的例外
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 處理 Service 使用 ResponseStatusException 拋出的例外
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request) {

        // 取得原本設定的 HTTP 狀態碼
        HttpStatusCode status = ex.getStatusCode();

        // 取得例外訊息
        String message = ex.getReason();

        // 建立統一格式的錯誤回應
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                message != null ? message : "發生系統錯誤",
                request.getRequestURI()
        );

        // 回傳原本的 HTTP 狀態碼與錯誤資訊
        return ResponseEntity.status(status).body(errorResponse);
    }

    // 處理 Request DTO 欄位驗證失敗的例外
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        // 取得第一個欄位驗證錯誤
        FieldError fieldError = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .orElse(null);

        // 組合欄位名稱與驗證錯誤訊息
        String message = fieldError != null
                ? fieldError.getField() + "：" + fieldError.getDefaultMessage()
                : "請求資料驗證失敗";

        // 建立統一格式的錯誤回應
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                400,
                message,
                request.getRequestURI()
        );

        // 回傳 HTTP 400 與錯誤資訊
        return ResponseEntity.badRequest().body(errorResponse);
    }
}
