package zb.accountMangement.member.service;

import static zb.accountMangement.common.type.RedisTime.PHONE_VALID;

import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.java_sdk.api.Message;
import net.nurigo.java_sdk.exceptions.CoolsmsException;
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
     * @param token - 토큰
     * @param phoneNumber - 핸드폰번호
     * @return "인증 메세지 발송 완료"
     */
    public String sendVerificationMessage(String token, String phoneNumber) {
        String verificationCode = RandomStringUtils.random(VERIFY_CODE_LEN, false, true);
        Message message = new Message(apiKey, secretKey);

        HashMap<String, String> params = new HashMap<>();
        params.put("to", phoneNumber);
        params.put("from", senderPhoneNumber);
        params.put("type", "SMS");
        params.put("text", "핸드폰 인증 메세지 \n 인증번호는 [" + verificationCode + "] 입니다.");
        params.put("app_version", "test app 1.2");

        try {
            message.send(params);
            //TODO : setMsgVerificationInfo 사용해서 토큰 정보도 함꼐 저장
//            redisUtil.setMsgVerificationInfo(token, phoneNumber, verificationCode, PHONE_VALID.getTime());
            redisService.setData(phoneNumber,verificationCode, PHONE_VALID.getTime());
        } catch (CoolsmsException e) {
            log.info(e.getMessage());
            throw new RuntimeException(e);
        }
        return "인증 메세지 발송 완료";
    }

    /**
     * 토큰정보 + 인증번호가 일치하는지 확인
     * @param token - 토큰
     * @param smsVerificationDto - 문자인증 dto (인증번호, 핸드폰번호)
     * @return 일치여부
     */
    public boolean verifyCode(String token, SmsVerificationDto smsVerificationDto) {
        //TODO : getMsgVerificationInfo 사용해서 토큰 정보 맞는지 확인. (인증번호 + 토큰 일치해야함)
        SmsVerificationDto info = redisService.getMsgVerificationInfo(senderPhoneNumber);
        if (info==null)
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        return info.getVerificationCode().equals(smsVerificationDto.getVerificationCode());
    }
}
