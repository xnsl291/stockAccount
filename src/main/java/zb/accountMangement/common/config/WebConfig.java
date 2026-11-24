package zb.accountMangement.common.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.UrlHandlerFilter;

@Configuration
public class WebConfig {

  /**
   * 주소 끝의 '/'를 떼고 처리함. Spring 6부터 '/api/stocks/1/history/'가 '/api/stocks/1/history'와 같은 경로로
   * 맞춰지지 않아 404가 나므로 전과 같게 받음. 보안 필터보다 먼저 돌아야 권한 검사도 같은 경로로 함
   */
  @Bean
  public FilterRegistrationBean<UrlHandlerFilter> trailingSlashFilter() {
    FilterRegistrationBean<UrlHandlerFilter> registration =
        new FilterRegistrationBean<>(UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
