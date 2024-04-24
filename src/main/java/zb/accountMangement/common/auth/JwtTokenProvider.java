package zb.accountMangement.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.service.RedisService;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.member.model.RoleType;

import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

	private static final String AUTHORIZATION_PREFIX = "Bearer ";
	private final SecretKey secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
	private final RedisService redisService;
	@Value("${jwt.expiration.access-token-seconds}")
	private Long accessTokenExpirationTimeInSeconds;
	@Value("${jwt.expiration.refresh-token-seconds}")
	private Long refreshTokenExpirationTimeInSeconds;

	public JwtToken generateToken(Long id, String phoneNumber, RoleType authority) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("authority", authority);

		String accessToken = Jwts.builder()
				.setSubject(phoneNumber)
				.setId(id.toString())
				.setClaims(claims)
				.setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + accessTokenExpirationTimeInSeconds))
				.signWith(secretKey, SignatureAlgorithm.HS256)
				.compact();

		String refreshToken = Jwts.builder()
				.setSubject(phoneNumber)
				.setClaims(claims)
				.setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpirationTimeInSeconds))
				.signWith(secretKey, SignatureAlgorithm.HS256)
				.compact();

		//save refresh token
		redisService.setData("RT:" + phoneNumber, refreshToken, refreshTokenExpirationTimeInSeconds);

		return new JwtToken(accessToken, refreshToken);
	}

	// todo : 로그아웃시, 블랙리스트 저장
	public void deleteToken(String phoneNumber) {
		String rtKey = "RT:" + phoneNumber;
		if (redisService.getData(rtKey) != null)
			redisService.deleteData(rtKey);
	}

	public boolean validateToken(String token) {
		try {
			Claims claims = getClaimsFromToken(token);
			if (!claims.getExpiration().before(new Date())) // 토큰 만료 여부
				throw new CustomException(ErrorCode.EXPIRED_TOKEN);
			return true;
		} catch (Exception e) {
			throw new CustomException(ErrorCode.INVALID_TOKEN);
		}
	}

	public Claims getClaimsFromToken(String token) {
		return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();
	}

	public String resolveToken(HttpServletRequest request) {
		String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(AUTHORIZATION_PREFIX)) {
			return bearerToken.substring(7);
		}
		return null;
	}

	public String getPhoneNumber(String token) {
		return getClaimsFromToken(token).get("phoneNumber", String.class);
	}

	public Long getId(String token) {
		return Long.valueOf(getClaimsFromToken(token).getId());
	}

	public String getRoles(String token) {
		return getClaimsFromToken(token).get("roles", String.class);
	}

	public Long getExpirationInSeconds(String token) {
		return getClaimsFromToken(token).getExpiration().getTime();
	}
}
