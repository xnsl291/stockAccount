package zb.accountMangement.common.auth;

import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import zb.accountMangement.member.model.RoleType;

import java.io.IOException;


@Component
@AllArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain chain)
            throws IOException, ServletException {

        String token = jwtTokenProvider.resolveToken(request);
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            Authentication authentication = getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }

    private Authentication getAuthentication(String token) {
        Long userId = jwtTokenProvider.getId(token);
        String phoneNumber = jwtTokenProvider.getPhoneNumber(token);
        RoleType roleType = RoleType.valueOf(jwtTokenProvider.getRoles(token));
        return new UsernamePasswordAuthenticationToken(userId, phoneNumber, roleType.getAuthorities());
    }
}
