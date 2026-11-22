package com.example.weather_order_assistant_system.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.weather_order_assistant_system.entity.AppUser;
import com.example.weather_order_assistant_system.repository.AppUserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 由 app_users 讀取登入帳號，交由 Spring Security 比對 BCrypt 密碼雜湊。
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("帳號或密碼不正確"));

        // 暫時只使用單一管理員權限，不擴充多店或角色資料表。
        // 使用 Spring Security User 物件，可正確依 username 判定同一帳號的 Session。
        return User.withUsername(appUser.getUsername())
                .password(appUser.getPasswordHash())
                .roles("ADMIN")
                .disabled(!Integer.valueOf(1).equals(appUser.getStatus()))
                .build();
    }
}
