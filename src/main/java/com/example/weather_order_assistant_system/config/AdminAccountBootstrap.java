package com.example.weather_order_assistant_system.config;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.weather_order_assistant_system.entity.AppUser;
import com.example.weather_order_assistant_system.repository.AppUserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 開發環境第一次啟動時，使用外部環境變數建立首個管理員帳號。
 *
 * 預設完全不執行，必須主動設定 APP_ADMIN_BOOTSTRAP=true。
 * 絕不可把實際密碼寫在 Java、SQL migration 或 Git 中。
 */
@Component
@RequiredArgsConstructor
public class AdminAccountBootstrap implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean enabled = Boolean.parseBoolean(
                environment.getProperty("APP_ADMIN_BOOTSTRAP", "false"));
        if (!enabled) {
            return;
        }

        // 僅限空白的帳號資料表建立首個帳號，避免重啟時反覆覆寫密碼。
        if (appUserRepository.count() > 0) {
            return;
        }

        String username = environment.getProperty("APP_ADMIN_USERNAME", "").trim();
        String password = environment.getProperty("APP_ADMIN_PASSWORD", "");

        if (username.length() < 3 || username.length() > 50) {
            throw new IllegalStateException("管理員登入帳號長度必須為 3 至 50 字元");
        }
        if (password.length() < 12 ||
                password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("管理員密碼至少 12 字元，且 UTF-8 編碼長度不得超過 72 bytes");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(1);
        appUserRepository.save(user);

        // 不印出密碼或雜湊。建好後應刪除 .env 內的初始化密碼。
        System.out.println("已建立首個後台管理員帳號；請關閉 APP_ADMIN_BOOTSTRAP 並清除初始化密碼");
    }
}
