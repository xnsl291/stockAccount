package zb.accountMangement.member.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.common.service.ValidationService;
import zb.accountMangement.member.dto.*;
import zb.accountMangement.member.model.entity.Member;
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
  @GetMapping("/{member_id}")
  public ResponseEntity<Member> getMemberInfo(
          @RequestHeader(value = "Authorization") String token,
          @PathVariable("member_id") @Min(1) Long memberId  ){
    validationService.validTokenNMemberId(token,memberId);
    return ResponseEntity.ok().body(memberService.getMemberById(memberId));
  }

  /**
   * 회원 정보 수정
   * @param token - 토큰
   * @param updateMemberDto - 사용자 정보수정 dto (이름, 핸드폰번호, 로그인 PW)
   * @return true
   */
  @PatchMapping("/{member_id}")
  public ResponseEntity<Member> updateMemberInfo(
      @RequestHeader(value = "Authorization") String token,
      @PathVariable("member_id") @Min(1) long memberId,
      @RequestBody @Valid UpdateMemberDto updateMemberDto){
    validationService.validTokenNMemberId(token,memberId);
    return ResponseEntity.ok().body(memberService.updateMemberInfo(token, memberId, updateMemberDto));
  }



  /**
   * 비밀번호 재설정 요청
   * @param token - 토큰
   * @param memberId  사용자 ID
   * @param findMemberInfoDto - 회원정보 조회 dto (이름, 핸드폰번호)
   * @return "인증 메세지 발송 완료"
   */
  @PostMapping("/find-pw/{member_id}")
  public ResponseEntity<String> requestResetPw(
          @RequestHeader(value = "Authorization") String token,
          @PathVariable("memberId") @Min(1) Long memberId,
          @Valid @RequestBody FindMemberInfoDto findMemberInfoDto) {
    validationService.validTokenNMemberId(token,memberId);
    return ResponseEntity.ok().body(memberService.requestResetPw(token,memberId,findMemberInfoDto));
  }

  /**
   * 비밀번호 재설정
   * @param token - 토큰
   * @param memberId - 사용자 ID
   * @param resetPwDto - 비밀번호 재설정 dto (인증번호, 새로운 PW)
   * @return "비밀번호 재설정 완료"
   */
  @PatchMapping("/find-pw/{member_id}/confirm")
  public ResponseEntity<String> verifyResetPw(
          @RequestHeader(value = "Authorization") String token,
          @PathVariable("member_id") @Min(1) Long memberId,
          @Valid @RequestBody ResetPwDto resetPwDto) {
    validationService.validTokenNMemberId(token,memberId);
    return ResponseEntity.ok().body(memberService.verifyResetPw(token,memberId,resetPwDto));
  }
}