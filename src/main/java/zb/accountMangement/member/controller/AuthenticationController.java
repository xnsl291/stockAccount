package zb.accountMangement.member.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.common.auth.JwtToken;
import zb.accountMangement.common.service.ValidationService;
import zb.accountMangement.member.dto.*;
import zb.accountMangement.member.service.AuthenticationService;
import zb.accountMangement.member.service.SendMessageService;
import javax.validation.Valid;

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
        validationService.validTokenNMemberPhoneNumber(token,smsVerificationDto.getPhoneNumber());
        return ResponseEntity.ok().body(sendMessageService.verifyCode(token, smsVerificationDto));
    }

    /**
     * 회원가입
     * @param signUpDto - 회원가입 dto (이름, 핸드폰번호, 로그인 PW, 초기계좌 PW)
     * @return true
     */
    @PostMapping("/sign-up")
    public ResponseEntity<Boolean> signUp(
            @Valid @RequestBody SignUpDto signUpDto){
        return ResponseEntity.ok().body(authenticationService.signUp(signUpDto));
    }

    /**
     * 회원탈퇴
     * @return true
     */
    @DeleteMapping("/sign-out")
    public ResponseEntity<Boolean> deleteMemberInfo(
            @RequestHeader(value = "Authorization") String token){
        return ResponseEntity.ok().body(authenticationService.deleteMember(token));
    }

    /**
     * 로그인
     * @param signInDto - 로그인 dto (핸드폰번호, 로그인 PW)
     * @return token
     */
    @PostMapping("/login")
    public ResponseEntity<JwtToken> signIn(@Valid @RequestBody SignInDto signInDto){
        return ResponseEntity.ok().body(authenticationService.signIn(signInDto));
    }

    /**
     * 로그아웃
     * @param token - 토큰
     * @return true
     */
    @PostMapping("/logout")
    public ResponseEntity<Boolean> signOut(@RequestHeader(value = "Authorization") String token){
        return ResponseEntity.ok().body(authenticationService.signOut(token));
    }
}
