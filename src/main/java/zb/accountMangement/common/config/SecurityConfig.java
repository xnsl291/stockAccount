package zb.accountMangement.common.config;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import zb.accountMangement.common.auth.JwtAuthenticationEntryPoint;
import zb.accountMangement.common.auth.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

  @Bean
  public BCryptPasswordEncoder bCryptPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    return http
        .cors(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
          // Boot 3부터 오류 화면 전달(/error)에도 권한 검사가 걸림. 원래 요청에서 이미 검사했으므로 전처럼 통과시킴
          .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
          // 상태 확인과 지표는 토큰 없이 엶. 이 경로는 업무 포트(8080)에는 없고 관리 포트(기본 127.0.0.1:9081)에만 생겨
          // 이 PC 밖에서는 닿지 않음. 관리 포트를 업무 포트와 같게 바꾸면 관리 주소 설정과 부딪혀 앱이 뜨지 않음
          .requestMatchers(EndpointRequest.to(HealthEndpoint.class, PrometheusScrapeEndpoint.class)).permitAll()
          .requestMatchers("/api/auth/login", "/api/auth/sign-up", "/api/auth/reissue",
            "/api/auth/send-code", "/api/auth/verify-phone")
          .permitAll()
          // 로그아웃은 자기 토큰만 무효화하므로 역할과 무관하게 로그인만 요구함. 관리자도 로그아웃할 수 있어야 함
          .requestMatchers("/api/auth/logout").authenticated()
          // 탈퇴 등 나머지 회원 경로는 회원(USER)만 씀. 관리자 계정이 스스로 탈퇴해 관리자가 사라지는 일을 막음
          .requestMatchers("/api/accounts/**", "/api/transactions/**", "/api/stocks/**", "/api/auth/**",
            "/api/member/**").hasRole("USER")
          .requestMatchers("/admin/**").hasRole("ADMIN")
          .anyRequest().authenticated())
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .exceptionHandling(exceptionHandling -> exceptionHandling
          .authenticationEntryPoint(jwtAuthenticationEntryPoint)
          .accessDeniedHandler(jwtAuthenticationEntryPoint))
        .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class).build();
  }
}
