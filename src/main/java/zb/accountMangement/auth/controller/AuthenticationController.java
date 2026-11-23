package zb.accountMangement.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.common.auth.JwtToken;
import zb.accountMangement.auth.dto.*;
import zb.accountMangement.auth.service.AuthenticationService;
import zb.accountMangement.member.service.SendMessageService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AuthenticationService authenticationService;
    private final SendMessageService sendMessageService;

    /**
     * 핸드폰 인증번호 발송
     * @param sendCodeDto - 인증번호 발송 dto (핸드폰번호)
     * @param request - 발송 횟수를 IP별로 세는 데 씀. X-Forwarded-For는 클라이언트가 마음대로 바꿀 수 있어 믿지 않음
     * @return "인증 메세지 발송 완료"
     */
    @PostMapping("/send-code")
    public ResponseEntity<String> sendCode(@Valid @RequestBody SendCodeDto sendCodeDto, HttpServletRequest request) {
        return ResponseEntity.ok().body(sendMessageService.sendVerificationMessage(sendCodeDto.getPhoneNumber(), request.getRemoteAddr()));
    }

    /**
    * 핸드폰 인증 - 가입, 핸드폰번호 변경, 계좌 잠금 해제 중 어디에 쓸지 정해서 인증함
    * @param smsVerificationDto - 문자인증 dto (인증번호, 핸드폰번호, 용도)
    * @return true
    */
    @PostMapping("/verify-phone")
    public ResponseEntity<Boolean> verifySMS(
            @Valid @RequestBody SmsVerificationDto smsVerificationDto) {
        return ResponseEntity.ok().body(sendMessageService.verifyPhone(
                smsVerificationDto.getPhoneNumber(), smsVerificationDto.getVerificationCode(), smsVerificationDto.getPurpose()));
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
     * @param authentication - 인증 정보 (사용자 ID, 액세스 토큰)
     * @return true
     */
    @DeleteMapping("/sign-out")
    public ResponseEntity<Boolean> deleteMemberInfo(Authentication authentication){
        return ResponseEntity.ok().body(authenticationService.deleteMember(
                (Long) authentication.getPrincipal(), (String) authentication.getCredentials()));
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
     * @param authentication - 인증 정보 (사용자 ID, 액세스 토큰)
     * @return true
     */
    @PostMapping("/logout")
    public ResponseEntity<Boolean> signOut(Authentication authentication){
        return ResponseEntity.ok().body(authenticationService.signOut(
                (Long) authentication.getPrincipal(), (String) authentication.getCredentials()));
    }

    /**
     * 토큰 재발급
     * @param reissueDto - 재발급 dto (리프레시 토큰)
     * @return token
     */
    @PostMapping("/reissue")
    public ResponseEntity<JwtToken> reissue(@Valid @RequestBody ReissueDto reissueDto){
        return ResponseEntity.ok().body(authenticationService.reissue(reissueDto.getRefreshToken()));
    }
}
