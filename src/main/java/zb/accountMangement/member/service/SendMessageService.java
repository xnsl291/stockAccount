package zb.accountMangement.member.service;

import static zb.accountMangement.common.type.RedisTime.PHONE_VALID;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.service.RedisService;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.member.dto.SmsVerificationDto;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SendMessageService {

    private final RedisService redisService;

    @Value("${coolsms.api-key}")
    private String apiKey;

    @Value("${coolsms.secret-key}")
    private String secretKey;

    @Value("${provider.phone-number}")
    private String senderPhoneNumber;
    private final int VERIFY_CODE_LEN = 5;

    /**
     * 핸드폰 인증 문자 발송
     * @param phoneNumber - 핸드폰번호
     * @return "인증 메세지 발송 완료"
     */
    public String sendVerificationMessage(String phoneNumber) {
        String verificationCode = RandomStringUtils.random(VERIFY_CODE_LEN, false, true);
        Message message = new Message();
        message.setFrom(senderPhoneNumber);
        message.setTo(phoneNumber);
        message.setText("핸드폰 인증 메세지 \n 인증번호는 [" + verificationCode + "] 입니다.");

        try {
            NurigoApp.INSTANCE.initialize(apiKey, secretKey, "https://api.coolsms.co.kr")
                    .sendOne(new SingleMessageSendingRequest(message));
            redisService.setData(phoneNumber,verificationCode, Duration.ofMinutes(PHONE_VALID.getTime()));
        } catch (Exception e) {
            log.info(e.getMessage());
            throw new RuntimeException(e);
        }
        return "인증 메세지 발송 완료";
    }

    /**
     * 토큰정보 + 인증번호가 일치하는지 확인
     * @param smsVerificationDto - 문자인증 dto (인증번호, 핸드폰번호)
     * @return 일치여부
     */
    public boolean verifyCode(SmsVerificationDto smsVerificationDto) {
        //TODO : getMsgVerificationInfo 사용해서 토큰 정보 맞는지 확인. (인증번호 + 토큰 일치해야함)
        SmsVerificationDto info = redisService.getMsgVerificationInfo(senderPhoneNumber);
        if (info==null)
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        return info.getVerificationCode().equals(smsVerificationDto.getVerificationCode());
    }
}
