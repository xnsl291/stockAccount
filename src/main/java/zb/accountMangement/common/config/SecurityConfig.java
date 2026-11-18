package zb.accountMangement.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
        .cors()
        .and()
        .csrf().disable()
        .authorizeRequests()
          // 상태 확인과 지표는 토큰 없이 엶. 이 경로는 업무 포트(8080)에는 없고 관리 포트(기본 127.0.0.1:8081)에만 생겨
          // 이 PC 밖에서는 닿지 않음. 관리 포트를 업무 포트와 같게 바꾸면 관리 주소 설정과 부딪혀 앱이 뜨지 않음
          .requestMatchers(EndpointRequest.to(HealthEndpoint.class, PrometheusScrapeEndpoint.class)).permitAll()
          .antMatchers("/api/auth/login", "/api/auth/sign-up", "/api/auth/reissue",
            "/api/auth/send-code", "/api/auth/verify-phone")
          .permitAll()
          .antMatchers("/api/accounts/**", "/api/transactions/**", "/api/stocks/**", "/api/auth/**",
            "/api/member/**").hasRole("USER")
          .antMatchers("/admin/**").hasRole("ADMIN")
          .anyRequest().authenticated()
        .and()
        .formLogin().disable()
        .httpBasic().disable()
        .exceptionHandling()
          .authenticationEntryPoint(jwtAuthenticationEntryPoint)
          .accessDeniedHandler(jwtAuthenticationEntryPoint)
        .and()
        .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class).build();
  }
}
