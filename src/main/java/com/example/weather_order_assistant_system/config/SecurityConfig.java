package com.example.weather_order_assistant_system.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * 單店後台安全設定。
 *
 * 第一版採用表單登入 + HttpSession，並拒絕第二個有效登入。
 * 所有 API 一律由後端檢查登入，不以側欄選單是否顯示作為權限判斷。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt 使用隨機 salt，資料庫只存雜湊、不存原始密碼。
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        // 第一版單一 Spring Boot 執行個體使用記憶體追蹤已登入 Session。
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        // 追蹤 Session 逾時或登出事件，讓後續登入能正確釋出名額。
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {

        http
            .authorizeHttpRequests(authorize -> authorize
                // 登入畫面與靜態資源必須能在未登入時讀取。
                .requestMatchers("/login", "/css/**", "/js/**", "/favicon.ico", "/error")
                .permitAll()
                // 後台功能、首頁以及所有 REST API 均必須登入。
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                // 被導向登入頁的使用者，登入後可回到原先想前往的頁面。
                .defaultSuccessUrl("/home", false)
                .failureHandler((request, response, exception) -> {
                    // 若是第二台裝置登入，回傳獨立提示，不踢掉已登入的 Session。
                    boolean alreadyLoggedIn = exception instanceof SessionAuthenticationException;
                    String errorCode = alreadyLoggedIn ? "occupied" : "error";
                    response.sendRedirect(request.getContextPath() + "/login?" + errorCode);
                })
                .permitAll()
            )
            .logout(logout -> logout
                // 只能以 POST /logout 登出，Spring Security 會驗證 CSRF。
                .logoutUrl("/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessUrl("/login?logout")
            )
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.changeSessionId())
                .maximumSessions(1)
                .maxSessionsPreventsLogin(true)
                .sessionRegistry(sessionRegistry)
            )
            // 不停用 CSRF；Thymeleaf 表單與現有 JS fetch 都會傳送 Token。
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    if (isApiRequest(request)) {
                        writeJsonError(response, 401, "請先登入後再操作");
                    } else {
                        response.sendRedirect(request.getContextPath() + "/login");
                    }
                })
                .accessDeniedHandler((request, response, deniedException) -> {
                    if (isApiRequest(request)) {
                        writeJsonError(response, 403, "沒有操作權限或安全驗證失敗");
                    } else {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    }
                })
            );

        return http.build();
    }

    private static boolean isApiRequest(HttpServletRequest request) {
        // 顧及未來可能設定的 Servlet Context Path。
        String relativePath = request.getRequestURI().substring(request.getContextPath().length());
        return relativePath.startsWith("/api/");
    }

    private static void writeJsonError(HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        // 用既有 API 容易處理的 JSON 回覆，不把登入 HTML 傳給 AJAX。
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
