package vn.movehome.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.security.DriverWorkflowAccessService;
import vn.movehome.backend.security.JwtAuthenticationFilter;

import java.util.List;

/**
 * Cau hinh Spring Security: JWT stateless, RBAC, CORS.
 * HR-17: phan tach ro Public vs Authenticated endpoints.
 * Constitution AC-11: CORS whitelist tuong minh (khong dung allowedOrigins "*").
 * BCrypt cost 12 (HR-02, FR-006).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // Cho phep @PreAuthorize, @PostAuthorize tren controller methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserRepository userRepository;
    private final DriverWorkflowAccessService driverWorkflowAccessService;

    /**
     * BCryptPasswordEncoder cost 12 — khoang 300ms moi hash tren CPU trung binh (NFR-07).
     * Bean duy nhat cho toan bo app (HR-02).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * UserDetailsService load User theo email.
     * Dung boi DaoAuthenticationProvider (AuthenticationManager).
     * Luu y: AuthService xu ly login thu cong, UserDetailsService chi dung cho AuthenticationManager.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return email -> userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Nguoi dung khong tim thay voi email: " + email));
    }

    /**
     * DaoAuthenticationProvider ket hop UserDetailsService + PasswordEncoder.
     * Dung de cau hinh AuthenticationManager.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager — de lo theo RequestMapping giup AuthService inject neu can.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * SecurityFilterChain — cau hinh chinh sach bao mat toan bo app.
     *
     * Endpoint phan loai theo HR-17:
     *   PUBLIC  (/api/public/**, /api/auth/**) — khong can JWT
     *   AUTHENTICATED — can JWT + kiem tra role theo RBAC (CONTEXT §3)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            // JWT stateless — khong dung session (AC-03)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Tat CSRF (khong can voi JWT stateless REST API)
            .csrf(AbstractHttpConfigurer::disable)

            // CORS config (AC-11)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Phan quyen endpoint (HR-17, CONTEXT §3 RBAC)
            .authorizeHttpRequests(auth -> auth
                // PUBLIC: Guest + toan bo luong auth (khong can JWT)
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/vnpay/**").permitAll()
                // OPTIONS request (preflight CORS) — luon cho phep
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // RBAC theo role — Constitution HR-10
                // Audit log là màn vận hành dùng chung cho Admin và Manager (HR-10).
                .requestMatchers(HttpMethod.GET, "/api/admin/audit-logs").hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/manager/**").hasRole("MANAGER")

                // Onboarding chỉ cần Driver đã xác thực email; workflow orders bắt buộc ACTIVE.
                .requestMatchers("/api/driver/onboarding", "/api/driver/onboarding/**").hasRole("DRIVER")
                .requestMatchers("/api/driver/profile", "/api/driver/profile/**").hasRole("DRIVER")
                .requestMatchers("/api/driver/location")
                    .access((authentication, context) -> new AuthorizationDecision(
                            driverWorkflowAccessService.isActiveDriver(authentication.get())))
                .requestMatchers("/api/driver/orders", "/api/driver/orders/**")
                    .access((authentication, context) -> new AuthorizationDecision(
                            driverWorkflowAccessService.isActiveDriver(authentication.get())))
                .requestMatchers("/api/driver/**").hasRole("DRIVER")
                    .requestMatchers("/api/customer/**").hasRole("CUSTOMER")

                    // Notification: dung chung cho moi user da dang nhap (customer/driver/manager/admin).
                    // Quyen so huu (chi xem noti CUA MINH) do service loc theo userId tu JWT.
                    .requestMatchers("/api/notifications/**").authenticated()

                    // Mac dinh: moi request con lai phai xac thuc (HR-17 default deny)
                    .anyRequest().authenticated()
            )

            // Xu ly loi xac thuc va phan quyen — tra JSON (khong redirect login page)
            .exceptionHandling(ex -> ex
                // 401 cho unauthenticated request (FR-075)
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                        "{\"error_code\":\"AUTHENTICATION_REQUIRED\"," +
                        "\"message\":\"Vui lòng đăng nhập để tiếp tục.\"," +
                        "\"details\":[]}");
                })
                // 403 cho authenticated nhung khong du quyen (HR-10)
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    boolean onboardingPendingReview = DriverWorkflowAccessService.ONBOARDING_PENDING_REVIEW_MESSAGE
                            .equals(accessDeniedException.getMessage());
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    if (onboardingPendingReview) {
                        response.getWriter().write(
                            "{\"error_code\":\"ONBOARDING_PENDING_REVIEW\"," +
                            "\"message\":\"Hồ sơ đang được Manager xem xét. Vui lòng đợi.\"," +
                            "\"details\":[]}");
                    } else {
                        response.getWriter().write(
                            "{\"error_code\":\"FORBIDDEN\"," +
                            "\"message\":\"Bạn không có quyền truy cập chức năng này.\"," +
                            "\"details\":[]}");
                    }
                })
            )

            // JWT filter chay truoc filter xac thuc mac dinh cua Spring Security
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authenticationProvider(authenticationProvider())

            .build();
    }

    /**
     * CORS config tuong minh — khong dung allowedOrigins("*") (AC-11).
     * Dev: localhost:3000, localhost:5500, 127.0.0.1:5500 (VS Code Live Server).
     * Prod: them URL frontend khi chot deploy provider (TODO AC-11).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Whitelist origin dev (AC-11)
        config.setAllowedOrigins(List.of(
            "http://localhost:3000",
            "http://localhost:5500",
            "http://127.0.0.1:5500",
            "http://localhost:8080"  // Swagger UI cung o day trong dev
        ));

        // Cho phep cac method REST chuan (ES-02)
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Cho phep tat ca header (de don gian trong dev; co the thu hep tren prod)
        config.addAllowedHeader("*");

        // Bat buoc de browser gui cookie (refresh token cookie trong Round 3)
        config.setAllowCredentials(true);

        // Cache preflight 1 gio
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
