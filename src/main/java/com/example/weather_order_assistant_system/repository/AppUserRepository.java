package com.example.weather_order_assistant_system.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.weather_order_assistant_system.entity.AppUser;

/**
 * 後台登入帳號資料存取。
 * 後續 UserDetailsService 將透過登入帳號查詢使用者。
 */
@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // 依登入帳號查詢，未來交由 Spring Security 驗證密碼與啟用狀態。
    Optional<AppUser> findByUsername(String username);

    // 初始化管理帳號時，避免重複建立相同名稱的帳號。
    boolean existsByUsername(String username);
}
