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

    @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:}")
    private String activeProfile;

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
     *
     * - dev/profile 未激活: 使用默认密码 "devflow2024"，打印警告
     * - prod 环境: 强制要求通过环境变量设置密码，否则启动失败
     * - 生产环境建议替换为数据库/JWT/OAuth2
     */
    @Bean
    public UserDetailsService userDetailsService() {
        // 注意：application.yml 中 ${ENV_VAR:} 默认值为空字符串 ""，不是 null
        boolean adminPwdNotSet = (adminPassword == null || adminPassword.isEmpty());
        boolean operatorPwdNotSet = (operatorPassword == null || operatorPassword.isEmpty());
        boolean isProd = "prod".equals(activeProfile);

        if (isProd && (adminPwdNotSet || operatorPwdNotSet)) {
            String msg = "PRODUCTION MODE: DEVFLOW_ADMIN_PASSWORD and DEVFLOW_OPERATOR_PASSWORD must be set as environment variables. "
                    + "Starting without these is forbidden in prod profile.";
            throw new IllegalStateException(msg);
        }

        if (adminPwdNotSet || operatorPwdNotSet) {
            System.err.println("WARNING: DEVFLOW_ADMIN_PASSWORD or DEVFLOW_OPERATOR_PASSWORD not set, using default 'devflow2024'. "
                    + "This is NOT safe for production!");
        }

        String adminPwd = adminPwdNotSet ? DEFAULT_PASSWORD : adminPassword;
        String operatorPwd = operatorPwdNotSet ? DEFAULT_PASSWORD : operatorPassword;

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