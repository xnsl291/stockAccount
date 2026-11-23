package zb.accountMangement.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import zb.accountMangement.common.exception.ErrorResponse;
import zb.accountMangement.common.type.ErrorCode;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증 실패(401)와 권한 부족(403)을 다른 API와 같은 ErrorResponse 형식으로 응답
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

	static final String ERROR_CODE_ATTRIBUTE = "jwtErrorCode";

	private final ObjectMapper objectMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		Object errorCode = request.getAttribute(ERROR_CODE_ATTRIBUTE);
		write(response, errorCode instanceof ErrorCode ? (ErrorCode) errorCode : ErrorCode.FAILED_VALIDATION);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(response, ErrorCode.ACCESS_DENIED);
	}

	private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(), ErrorResponse.toResponseEntity(errorCode).getBody());
	}
}
