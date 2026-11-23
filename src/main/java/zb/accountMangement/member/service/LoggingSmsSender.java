package zb.accountMangement.member.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬·테스트용 발송기. 문자를 보내지 않고 내용을 로그로만 남김
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sms.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

	@Override
	public void send(String phoneNumber, String text) {
		log.info("[SMS] to={} text={}", phoneNumber, text);
	}
}
