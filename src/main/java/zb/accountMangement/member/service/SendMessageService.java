package zb.accountMangement.member.service;

import static zb.accountMangement.common.type.RedisTime.PHONE_VALID;
import static zb.accountMangement.common.type.RedisTime.PHONE_VERIFIED;
import static zb.accountMangement.common.type.RedisTime.SMS_SEND_WINDOW;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.service.RedisService;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.member.model.PhoneNumber;
import zb.accountMangement.member.model.VerificationPurpose;

@Service
@RequiredArgsConstructor
public class SendMessageService {

    private static final int VERIFY_CODE_LEN = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_SENDS = 3;
    private static final int MAX_SENDS_PER_CLIENT = 10;

    private final RedisService redisService;
    private final SmsSender smsSender;

    /**
     * 핸드폰 인증 문자 발송. 정해진 시간 안에 같은 번호로, 그리고 같은 요청 IP에서 정해진 횟수까지만 보냄
     * @param phoneNumber - 핸드폰번호
     * @param clientIp - 요청한 IP
     * @return "인증 메세지 발송 완료"
     */
    public String sendVerificationMessage(String phoneNumber, String clientIp) {
        String normalized = PhoneNumber.normalize(phoneNumber);
        Duration sendWindow = Duration.ofMinutes(SMS_SEND_WINDOW.getTime());
        // IP 한도를 먼저 봐야 IP 한도에 걸린 요청이 남의 번호 발송 횟수를 깎지 않음
        if (redisService.increment(clientSendCountKey(clientIp), sendWindow) > MAX_SENDS_PER_CLIENT
                || redisService.increment(sendCountKey(normalized), sendWindow) > MAX_SENDS)
            throw new CustomException(ErrorCode.TOO_MANY_SMS_REQUESTS);

        String verificationCode = RandomStringUtils.random(VERIFY_CODE_LEN, false, true);
        redisService.deleteData(attemptCountKey(normalized));
        redisService.setData(codeKey(normalized), verificationCode, Duration.ofMinutes(PHONE_VALID.getTime()));
        smsSender.send(normalized, "핸드폰 인증 메세지 \n 인증번호는 [" + verificationCode + "] 입니다.");
        return "인증 메세지 발송 완료";
    }

    /**
     * 인증번호 확인. 일치하면 인증번호를 지워 다시 쓸 수 없게 함.
     * 시도 횟수는 발송한 인증번호마다 따로 세고, 한도까지 틀리면 그 인증번호를 폐기함.
     * 번호별로 틀린 횟수를 오래 묶어 두면 남이 일부러 틀려서 본인 인증을 막을 수 있어서 이렇게 함.
     * 대입할 수 있는 횟수는 발송 횟수 제한과 합쳐 10분에 15번(인증번호 3개 x 5번)까지임
     * @param phoneNumber - 핸드폰번호
     * @param verificationCode - 입력한 인증번호
     */
    public void confirmCode(String phoneNumber, String verificationCode) {
        String normalized = PhoneNumber.normalize(phoneNumber);
        String key = codeKey(normalized);
        String savedCode = redisService.getData(key);
        if (savedCode == null)
            throw new CustomException(ErrorCode.VERIFICATION_CODE_NOT_EXIST);

        // 비교하기 전에 먼저 세야 동시에 여러 번 대입해도 한도를 넘지 못함
        long attempts = redisService.increment(attemptCountKey(normalized), Duration.ofMinutes(PHONE_VALID.getTime()));
        if (attempts > MAX_ATTEMPTS) {
            redisService.deleteData(key);
            throw new CustomException(ErrorCode.TOO_MANY_SMS_REQUESTS);
        }
        if (!savedCode.equals(verificationCode)) {
            if (attempts == MAX_ATTEMPTS)
                redisService.deleteData(key);
            throw new CustomException(ErrorCode.UNMATCHED_VERIFICATION_CODE);
        }
        redisService.deleteData(key);
        redisService.deleteData(attemptCountKey(normalized));
    }

    /**
     * 핸드폰 인증. 성공하면 일정 시간 동안 해당 번호로 정한 용도의 작업을 한 번 할 수 있음
     * @param phoneNumber - 핸드폰번호
     * @param verificationCode - 입력한 인증번호
     * @param purpose - 인증을 쓸 곳
     * @return true
     */
    public boolean verifyPhone(String phoneNumber, String verificationCode, VerificationPurpose purpose) {
        confirmCode(phoneNumber, verificationCode);
        redisService.setData(verifiedKey(PhoneNumber.normalize(phoneNumber), purpose), "true",
                Duration.ofMinutes(PHONE_VERIFIED.getTime()));
        return true;
    }

    /**
     * 인증 완료 표시를 확인하고 지움. 인증 한 번으로 정한 용도의 작업을 한 번만 할 수 있음
     * @param phoneNumber - 핸드폰번호
     * @param purpose - 인증을 쓸 곳
     */
    public void consumeVerifiedPhone(String phoneNumber, VerificationPurpose purpose) {
        String key = verifiedKey(PhoneNumber.normalize(phoneNumber), purpose);
        if (redisService.getData(key) == null)
            throw new CustomException(ErrorCode.UNVERIFIED_PHONE_NUMBER);
        redisService.deleteData(key);
    }

    private String codeKey(String phoneNumber) {
        return "SMS:" + phoneNumber;
    }

    private String sendCountKey(String phoneNumber) {
        return "SMS_SEND:" + phoneNumber;
    }

    private String clientSendCountKey(String clientIp) {
        return "SMS_SEND_IP:" + clientIp;
    }

    private String attemptCountKey(String phoneNumber) {
        return "SMS_ATTEMPT:" + phoneNumber;
    }

    private String verifiedKey(String phoneNumber, VerificationPurpose purpose) {
        return "SMS_VERIFIED:" + purpose + ":" + phoneNumber;
    }
}
