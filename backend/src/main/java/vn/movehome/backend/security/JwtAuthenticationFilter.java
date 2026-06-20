package vn.movehome.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.movehome.backend.repository.UserRepository;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter JWT chay mot lan moi request (OncePerRequestFilter).
 * Doc Bearer token tu header Authorization → xac thuc → set SecurityContext.
 * Neu token khong hop le: bo qua (khong set context), request se bi tu choi o tang endpoint.
 * Request toi /api/auth/** va /api/public/** khong can token — Security config xu ly.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Neu khong co header hoac khong phai Bearer → bo qua, tiep tuc filter chain
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // Xac thuc token → lay userId
        jwtTokenProvider.validateAccessToken(token).ifPresent(userId -> {
            // Neu SecurityContext da co auth (tru huong do filter nao do set truoc) → bo qua
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                return;
            }
            setSecurityContext(userId, request);
        });

        filterChain.doFilter(request, response);
    }

    /**
     * Tim user trong DB va set SecurityContext neu user hop le.
     * Kiểm tra: email đã xác thực qua isEnabled(), tài khoản không khóa và chưa hết hiệu lực.
     * Trạng thái nghiệp vụ ACTIVE/PENDING_* được kiểm tra tại service của từng luồng.
     */
    private void setSecurityContext(UUID userId, HttpServletRequest request) {
        userRepository.findById(userId).ifPresent(user -> {
            // Chỉ xác thực danh tính/tình trạng tài khoản tại đây; không gate trạng thái onboarding.
            if (!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired()) {
                log.debug("JWT hợp lệ nhưng user {} không thể xác thực: emailVerified={}, nonLocked={}, nonExpired={}",
                        userId, user.isEmailVerified(), user.isAccountNonLocked(), user.isAccountNonExpired());
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

            // Luu request info vao WebAuthenticationDetails (cho audit log sau nay)
            authentication.setDetails(request.getRemoteAddr());

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Xac thuc JWT thanh cong: userId={}, role={}", userId, user.getRole());
        });
    }
}
