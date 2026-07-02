package com.devflow.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 安全配置
 * 凭证通过环境变量注入，避免硬编码
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${devflow.auth.admin-password:#{null}}")
    private String adminPassword;

    @Value("${devflow.auth.operator-password:#{null}}")
    private String operatorPassword;

    private static final String DEFAULT_PASSWORD = "devflow2024";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Swagger / Knife4j 文档放行
                .requestMatchers(
                    "/doc.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/webjars/**"
                ).permitAll()
                // Webhook 端点放行（由 HMAC 签名验证保护）
                .requestMatchers("/webhook/**").permitAll()
                // Actuator health 放行
                .requestMatchers("/actuator/health").permitAll()
                // 所有 API 需要认证
                .requestMatchers("/api/**").authenticated()
                // 其他请求放行
                .anyRequest().permitAll()
            )
            .httpBasic(httpBasic -> {});

        return http.build();
    }

    /**
     * 内存用户，密码从环境变量注入
     * 生产环境建议替换为数据库/JWT/OAuth2
     */
    @Bean
    public UserDetailsService userDetailsService() {
        // 密码默认值仅供开发环境使用
        // 注意：application.yml 中 ${ENV_VAR:} 默认值为空字符串 ""，不是 null
        // 因此需要同时检查 null 和空字符串
        boolean adminPwdNotSet = (adminPassword == null || adminPassword.isEmpty());
        boolean operatorPwdNotSet = (operatorPassword == null || operatorPassword.isEmpty());
        String adminPwd = adminPwdNotSet ? DEFAULT_PASSWORD : adminPassword;
        String operatorPwd = operatorPwdNotSet ? DEFAULT_PASSWORD : operatorPassword;

        if (adminPwdNotSet || operatorPwdNotSet) {
            System.err.println("WARNING: DEVFLOW_ADMIN_PASSWORD or DEVFLOW_OPERATOR_PASSWORD not set, using default. Set env variables for production!");
        }

        var admin = User.builder()
                .username("admin")
                .password(passwordEncoder().encode(adminPwd))
                .roles("ADMIN")
                .build();
        var operator = User.builder()
                .username("operator")
                .password(passwordEncoder().encode(operatorPwd))
                .roles("OPERATOR")
                .build();
        return new InMemoryUserDetailsManager(admin, operator);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}