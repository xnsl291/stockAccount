package zb.accountMangement.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.common.auth.JwtToken;
import zb.accountMangement.common.auth.JwtTokenProvider;
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
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/send-code")
    public ResponseEntity<String> sendCode(@Valid @RequestBody SendCodeDto sendCodeDto, HttpServletRequest request) {
        // 발송 횟수는 IP별로도 셈. X-Forwarded-For는 클라이언트가 마음대로 바꿀 수 있어 믿지 않음
        return ResponseEntity.ok().body(sendMessageService.sendVerificationMessage(sendCodeDto.getPhoneNumber(), request.getRemoteAddr()));
    }

    /**
     * 가입, 핸드폰번호 변경, 계좌 잠금 해제 중 어디에 쓸지 정해서 인증함
     */
    @PostMapping("/verify-phone")
    public ResponseEntity<Boolean> verifySMS(
            @Valid @RequestBody SmsVerificationDto smsVerificationDto) {
        sendMessageService.verifyPhone(
                smsVerificationDto.getPhoneNumber(), smsVerificationDto.getVerificationCode(), smsVerificationDto.getPurpose());
        return ResponseEntity.ok().body(true);
    }

    @PostMapping("/sign-up")
    public ResponseEntity<Boolean> signUp(
            @Valid @RequestBody SignUpDto signUpDto){
        authenticationService.signUp(signUpDto);
        return ResponseEntity.ok().body(true);
    }

    @DeleteMapping("/sign-out")
    public ResponseEntity<Boolean> deleteMemberInfo(Authentication authentication){
        authenticationService.deleteMember((Long) authentication.getPrincipal(), (String) authentication.getCredentials());
        return ResponseEntity.ok().body(true);
    }

    @PostMapping("/login")
    public ResponseEntity<JwtToken> signIn(@Valid @RequestBody SignInDto signInDto){
        return ResponseEntity.ok().body(authenticationService.signIn(signInDto));
    }

    @PostMapping("/logout")
    public ResponseEntity<Boolean> signOut(Authentication authentication){
        jwtTokenProvider.invalidateTokens((Long) authentication.getPrincipal(), (String) authentication.getCredentials());
        return ResponseEntity.ok().body(true);
    }

    @PostMapping("/reissue")
    public ResponseEntity<JwtToken> reissue(@Valid @RequestBody ReissueDto reissueDto){
        return ResponseEntity.ok().body(authenticationService.reissue(reissueDto.getRefreshToken()));
    }
}
