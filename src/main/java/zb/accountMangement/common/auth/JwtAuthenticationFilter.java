package zb.accountMangement.common.auth;

import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import zb.accountMangement.common.exception.CustomException;

import java.io.IOException;


@Component
@AllArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain chain)
            throws IOException, ServletException {

        String token = jwtTokenProvider.resolveToken(request);
        if (token != null) {
            try {
                SecurityContextHolder.getContext().setAuthentication(jwtTokenProvider.authenticate(token));
            } catch (CustomException e) {
                // 여기서 응답을 쓰지 않고 넘기면, 인증이 필요한 경로일 때 EntryPoint가 이 사유로 401을 만든다
                request.setAttribute(JwtAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE, e.getErrorCode());
            }
        }
        chain.doFilter(request, response);
    }
}
