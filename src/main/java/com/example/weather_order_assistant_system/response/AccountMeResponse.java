package com.example.weather_order_assistant_system.response;

import java.time.LocalDateTime;

/**
 * 已登入帳號的公開資訊。
 * 故意不包含 passwordHash、密碼或任何 Session/CSRF Token。
 */
public record AccountMeResponse(
        Long id,
        String username,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
