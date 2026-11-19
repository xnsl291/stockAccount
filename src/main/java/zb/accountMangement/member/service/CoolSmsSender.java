package zb.accountMangement.member.service;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * CoolSMS로 실제 문자를 발송 (sms.provider=coolsms 일 때만 사용)
 */
@Component
@ConditionalOnProperty(name = "sms.provider", havingValue = "coolsms")
public class CoolSmsSender implements SmsSender {

	private final DefaultMessageService messageService;
	private final String senderPhoneNumber;

	public CoolSmsSender(
			@Value("${coolsms.api-key}") String apiKey,
			@Value("${coolsms.secret-key}") String secretKey,
			@Value("${provider.phone-number}") String senderPhoneNumber) {
		this.messageService = NurigoApp.INSTANCE.initialize(apiKey, secretKey, "https://api.coolsms.co.kr");
		this.senderPhoneNumber = senderPhoneNumber;
	}

	@Override
	public void send(String phoneNumber, String text) {
		Message message = new Message();
		message.setFrom(senderPhoneNumber);
		message.setTo(phoneNumber);
		message.setText(text);
		messageService.sendOne(new SingleMessageSendingRequest(message));
	}
}
