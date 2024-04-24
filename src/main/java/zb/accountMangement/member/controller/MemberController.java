package zb.accountMangement.member.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.common.auth.JwtToken;
import zb.accountMangement.common.service.ValidationService;
import zb.accountMangement.member.dto.SignInDto;
import zb.accountMangement.member.dto.SignUpDto;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.member.dto.UpdateUserDto;
import zb.accountMangement.member.service.MemberService;

import javax.validation.Valid;
import javax.validation.constraints.Min;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/member")
public class MemberController {
  private final MemberService memberService;
  private final ValidationService validationService;

  /**
   * 회원 정보 열람 기능
   * @param token - 토큰
   * @return Member
   */
  @GetMapping("/{user_id}")
  public ResponseEntity<Member> getUserInfo(
          @RequestHeader(value = "Authorization") String token,
          @PathVariable("user_id") @Min(1) Long userId  ){
    validationService.validTokenNUserId(token,userId);
    return ResponseEntity.ok().body(memberService.getUserById(userId));
  }

  /**
   * 회원 정보 수정
   * @param token - 토큰
   * @param updateUserDto - 사용자 정보수정 dto (이름, 핸드폰번호, 로그인 PW)
   * @return true
   */
  @PatchMapping("/{user_id}")
  public ResponseEntity<Member> updateUserInfo(
      @RequestHeader(value = "Authorization") String token,
      @PathVariable("user_id") @Min(1) long userId,
      @RequestBody @Valid UpdateUserDto updateUserDto){
    validationService.validTokenNUserId(token,userId);
    return ResponseEntity.ok().body(memberService.updateUserInfo(userId, updateUserDto));
  }

  /**
   * 회원가입
   * @param token - 토큰
   * @param signUpDto - 회원가입 dto (이름, 핸드폰번호, 로그인 PW, 초기계좌 PW)
   * @return true
   */
  @PostMapping("/sign-up")
  public ResponseEntity<Boolean> signUp(
          @RequestHeader(value = "Authorization") String token,
          @Valid @RequestBody SignUpDto signUpDto){
    return ResponseEntity.ok().body(memberService.signUp(token, signUpDto));
  }

  /**
   * 회원탈퇴
   * @return true
   */
  @DeleteMapping("/{user_id}")
  public ResponseEntity<Boolean> deleteUserInfo(
          @RequestHeader(value = "Authorization") String token,
          @PathVariable("user_id") @Min(1) Long userId){
    validationService.validTokenNUserId(token,userId);
    return ResponseEntity.ok().body(memberService.deleteUser(userId));
  }

  /**
   * 로그인
   * @param signInDto - 로그인 dto (핸드폰번호, 로그인 PW)
   * @return token
   */
  @PostMapping("/login")
  public ResponseEntity<JwtToken> signIn(@Valid @RequestBody SignInDto signInDto){
    return ResponseEntity.ok().body(memberService.signIn(signInDto));
  }

  /**
   * 로그아웃
   * @param token - 토큰
   * @return true
   */
  @PostMapping("/logout")
  public ResponseEntity<Boolean> signOut(@RequestHeader(value = "Authorization") String token){
    return ResponseEntity.ok().body(memberService.signOut(token));
  }
}