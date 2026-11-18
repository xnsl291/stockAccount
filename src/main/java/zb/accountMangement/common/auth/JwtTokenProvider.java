package zb.accountMangement.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.service.RedisService;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.common.type.RoleType;

import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

	private static final String AUTHORIZATION_PREFIX = "Bearer ";
	private static final String ROLE_CLAIM = "role";
	private static final String TYPE_CLAIM = "type";
	private static final String ACCESS_TYPE = "access";
	private static final String REFRESH_TYPE = "refresh";

	private final SecretKey secretKey;
	private final RedisService redisService;
	private final Duration accessTokenValidity;
	private final Duration refreshTokenValidity;

	public JwtTokenProvider(
			RedisService redisService,
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiration.access-token-seconds}") long accessTokenSeconds,
			@Value("${jwt.expiration.refresh-token-seconds}") long refreshTokenSeconds) {
		if (!StringUtils.hasText(secret))
			throw new IllegalStateException("jwt.secret이 비어 있습니다. JWT_SECRET 환경변수나 application-secret.yml의 jwt.secret에 "
					+ "Base64로 인코딩한 32바이트 이상의 값을 넣어야 합니다 (예: openssl rand -base64 48)");
		this.redisService = redisService;
		this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
		this.accessTokenValidity = Duration.ofSeconds(accessTokenSeconds);
		this.refreshTokenValidity = Duration.ofSeconds(refreshTokenSeconds);
	}

	public JwtToken generateToken(Long memberId, RoleType role) {
		Date now = new Date();

		// 같은 초에 발급해도 토큰이 서로 달라야 로그아웃 차단과 리프레시 토큰 교체가 다른 토큰에 영향을 주지 않음
		String accessToken = Jwts.builder()
				.setId(UUID.randomUUID().toString())
				.setSubject(memberId.toString())
				.claim(ROLE_CLAIM, role.name())
				.claim(TYPE_CLAIM, ACCESS_TYPE)
				.setIssuedAt(now)
				.setExpiration(new Date(now.getTime() + accessTokenValidity.toMillis()))
				.signWith(secretKey, SignatureAlgorithm.HS256)
				.compact();

		String refreshToken = Jwts.builder()
				.setId(UUID.randomUUID().toString())
				.setSubject(memberId.toString())
				.claim(TYPE_CLAIM, REFRESH_TYPE)
				.setIssuedAt(now)
				.setExpiration(new Date(now.getTime() + refreshTokenValidity.toMillis()))
				.signWith(secretKey, SignatureAlgorithm.HS256)
				.compact();

		redisService.setData(refreshTokenKey(memberId), refreshToken, refreshTokenValidity);

		return new JwtToken(accessToken, refreshToken);
	}

	/**
	 * 액세스 토큰 검증 - 서명, 만료, 토큰 종류, 로그아웃 여부를 확인
	 * @param token - 액세스 토큰
	 * @return principal은 사용자 ID, credentials는 액세스 토큰인 인증 정보
	 */
	public Authentication authenticate(String token) {
		Claims claims = parse(token, ACCESS_TYPE);
		if (redisService.getData(blacklistKey(token)) != null)
			throw new CustomException(ErrorCode.INVALID_TOKEN);
		RoleType role = RoleType.valueOf(claims.get(ROLE_CLAIM, String.class));
		return new UsernamePasswordAuthenticationToken(Long.valueOf(claims.getSubject()), token, role.getAuthorities());
	}

	/**
	 * 리프레시 토큰을 검증하고 지움 - 마지막으로 발급한 리프레시 토큰과 같아야 하고, 같은 토큰으로 동시에 요청해도 한 번만 통과함
	 * @param token - 리프레시 토큰
	 * @return 사용자 ID
	 */
	public Long consumeRefreshToken(String token) {
		Long memberId = Long.valueOf(parse(token, REFRESH_TYPE).getSubject());
		if (!redisService.deleteIfEquals(refreshTokenKey(memberId), token))
			throw new CustomException(ErrorCode.INVALID_TOKEN);
		return memberId;
	}

	public void deleteRefreshToken(Long memberId) {
		redisService.deleteData(refreshTokenKey(memberId));
	}

	/**
	 * 액세스 토큰을 남은 유효시간 동안 차단하고 리프레시 토큰을 삭제
	 * @param memberId - 사용자 ID
	 * @param accessToken - 액세스 토큰
	 */
	public void invalidateTokens(Long memberId, String accessToken) {
		long remainingMillis = parse(accessToken, ACCESS_TYPE).getExpiration().getTime() - System.currentTimeMillis();
		if (remainingMillis > 0)
			redisService.setData(blacklistKey(accessToken), "logout", Duration.ofMillis(remainingMillis));
		deleteRefreshToken(memberId);
	}

	public String resolveToken(HttpServletRequest request) {
		String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(AUTHORIZATION_PREFIX)) {
			return bearerToken.substring(AUTHORIZATION_PREFIX.length());
		}
		return null;
	}

	private Claims parse(String token, String expectedType) {
		Claims claims;
		try {
			claims = Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).getBody();
		} catch (ExpiredJwtException e) {
			throw new CustomException(ErrorCode.EXPIRED_TOKEN);
		} catch (JwtException | IllegalArgumentException e) {
			throw new CustomException(ErrorCode.INVALID_TOKEN);
		}
		if (!expectedType.equals(claims.get(TYPE_CLAIM, String.class)))
			throw new CustomException(ErrorCode.INVALID_TOKEN);
		return claims;
	}

	private String refreshTokenKey(Long memberId) {
		return "RT:" + memberId;
	}

	private String blacklistKey(String accessToken) {
		return "BL:" + accessToken;
	}
}
