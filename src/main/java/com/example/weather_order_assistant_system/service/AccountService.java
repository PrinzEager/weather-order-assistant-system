package com.example.weather_order_assistant_system.service;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.weather_order_assistant_system.entity.AppUser;
import com.example.weather_order_assistant_system.repository.AppUserRepository;
import com.example.weather_order_assistant_system.request.ChangePasswordRequest;
import com.example.weather_order_assistant_system.response.AccountMeResponse;

import lombok.RequiredArgsConstructor;

/**
 * 登入者自己的帳號服務。
 * 第一版先提供帳號資訊查詢、密碼修改，不讓前端指定其他使用者 ID。
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public AccountMeResponse getMyAccount(String username) {
        AppUser user = findActiveAccount(username);

        // 只回傳適合顯示的帳號欄位，絕不回傳密碼雜湊。
        return new AccountMeResponse(
                user.getId(),
                user.getUsername(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    @Transactional
    public void changeMyPassword(String username, ChangePasswordRequest request) {
        AppUser user = findActiveAccount(username);

        // 即使登入中的 Session 仍有效，修改密碼時也必須重新確認原密碼。
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目前密碼不正確");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "兩次輸入的新密碼不一致");
        }

        // 與首次管理員初始化使用相同的 BCrypt 長度限制。
        // BCrypt 最多只處理密碼的前 72 個 bytes，避免後半段被忽略。
        if (request.getNewPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "新密碼 UTF-8 編碼長度不可超過 72 bytes");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "新密碼不可與目前密碼相同");
        }

        // 儲存新的 BCrypt 雜湊；JPA 在交易提交時寫入 MySQL。
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        appUserRepository.save(user);
    }

    private AppUser findActiveAccount(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "登入帳號不存在，請重新登入"));

        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "此帳號已停用");
        }
        return user;
    }
}
