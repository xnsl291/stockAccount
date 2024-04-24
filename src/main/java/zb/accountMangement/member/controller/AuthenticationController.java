package zb.accountMangement.member.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.common.service.ValidationService;
import zb.accountMangement.member.dto.*;
import zb.accountMangement.member.service.AuthenticationService;
import zb.accountMangement.member.service.SendMessageService;
import javax.validation.Valid;
import javax.validation.constraints.Min;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AuthenticationService authenticationService;
    private final SendMessageService sendMessageService;
    private final ValidationService validationService;

    /**
    * 핸드폰 인증 성공 여부
    * @param token - 토큰
    * @param smsVerificationDto - 문자인증 dto (인증번호, 핸드폰번호)
    * @return 성공여부 (T/F)
    */
    @PostMapping("/verify-phone")
    public ResponseEntity<Boolean> verifySMS(
            @RequestHeader(value = "Authorization") String token,
            @Valid @RequestBody SmsVerificationDto smsVerificationDto) {
        validationService.validTokenNUserPhoneNumber(token,smsVerificationDto.getPhoneNumber());
        return ResponseEntity.ok().body(sendMessageService.verifyCode(token, smsVerificationDto));
    }

    /**
    * 비밀번호 재설정 요청
    * @param token - 토큰
    * @param userId  사용자 ID
    * @param findUserInfoDto - 회원정보 조회 dto (이름, 핸드폰번호)
    * @return "인증 메세지 발송 완료"
    */
    @PostMapping("/find-pw/{user_id}")
    public ResponseEntity<String> requestResetPw(
            @RequestHeader(value = "Authorization") String token,
            @PathVariable("user_id") @Min(1) Long userId,
            @Valid @RequestBody FindUserInfoDto findUserInfoDto) {
        validationService.validTokenNUserId(token,userId);
        return ResponseEntity.ok().body(authenticationService.requestResetPw(token,userId,findUserInfoDto));
    }

    /**
    * 비밀번호 재설정
    * @param token - 토큰
    * @param userId - 사용자 ID
    * @param resetPwDto - 비밀번호 재설정 dto (인증번호, 새로운 PW)
    * @return "비밀번호 재설정 완료"
    */
    @PatchMapping("/find-pw/{user_id}/confirm")
    public ResponseEntity<String> verifyResetPw(
            @RequestHeader(value = "Authorization") String token,
            @PathVariable("user_id") @Min(1) Long userId,
            @Valid @RequestBody ResetPwDto resetPwDto) {
        validationService.validTokenNUserId(token,userId);
        return ResponseEntity.ok().body(authenticationService.verifyResetPw(token,userId,resetPwDto));
    }
}
