package zb.accountMangement.member.service;

import static zb.accountMangement.common.type.RedisTime.PHONE_VALID;
import static zb.accountMangement.common.type.RedisTime.PHONE_VERIFIED;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.service.RedisService;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.member.model.PhoneNumber;

@Service
@RequiredArgsConstructor
public class SendMessageService {

    private static final int VERIFY_CODE_LEN = 5;

    private final RedisService redisService;
    private final SmsSender smsSender;

    /**
     * 핸드폰 인증 문자 발송
     * @param phoneNumber - 핸드폰번호
     * @return "인증 메세지 발송 완료"
     */
    public String sendVerificationMessage(String phoneNumber) {
        String normalized = PhoneNumber.normalize(phoneNumber);
        String verificationCode = RandomStringUtils.random(VERIFY_CODE_LEN, false, true);
        redisService.setData(codeKey(normalized), verificationCode, Duration.ofMinutes(PHONE_VALID.getTime()));
        smsSender.send(normalized, "핸드폰 인증 메세지 \n 인증번호는 [" + verificationCode + "] 입니다.");
        return "인증 메세지 발송 완료";
    }

    /**
     * 인증번호 확인. 일치하면 인증번호를 지워 다시 쓸 수 없게 함
     * @param phoneNumber - 핸드폰번호
     * @param verificationCode - 입력한 인증번호
     */
    public void confirmCode(String phoneNumber, String verificationCode) {
        String key = codeKey(PhoneNumber.normalize(phoneNumber));
        String savedCode = redisService.getData(key);
        if (savedCode == null)
            throw new CustomException(ErrorCode.VERIFICATION_CODE_NOT_EXIST);
        if (!savedCode.equals(verificationCode))
            throw new CustomException(ErrorCode.UNMATCHED_VERIFICATION_CODE);
        redisService.deleteData(key);
    }

    /**
     * 가입 전 핸드폰 인증. 성공하면 일정 시간 동안 해당 번호로 가입할 수 있음
     * @param phoneNumber - 핸드폰번호
     * @param verificationCode - 입력한 인증번호
     * @return true
     */
    public boolean verifyPhone(String phoneNumber, String verificationCode) {
        confirmCode(phoneNumber, verificationCode);
        redisService.setData(verifiedKey(PhoneNumber.normalize(phoneNumber)), "true",
                Duration.ofMinutes(PHONE_VERIFIED.getTime()));
        return true;
    }

    /**
     * 인증 완료 표시를 확인하고 지움. 인증 한 번으로 한 번만 가입할 수 있음
     * @param phoneNumber - 핸드폰번호
     */
    public void consumeVerifiedPhone(String phoneNumber) {
        String key = verifiedKey(PhoneNumber.normalize(phoneNumber));
        if (redisService.getData(key) == null)
            throw new CustomException(ErrorCode.UNVERIFIED_PHONE_NUMBER);
        redisService.deleteData(key);
    }

    private String codeKey(String phoneNumber) {
        return "SMS:" + phoneNumber;
    }

    private String verifiedKey(String phoneNumber) {
        return "SMS_VERIFIED:" + phoneNumber;
    }
}
