package zb.accountMangement.member.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
   * @param loginMemberId - 로그인한 사용자 ID
   * @return Member
   */
  @GetMapping("/{member_id}")
  public ResponseEntity<Member> getMemberInfo(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("member_id") @Min(1) Long memberId  ){
    validationService.validMemberId(loginMemberId,memberId);
    return ResponseEntity.ok().body(memberService.getMemberById(memberId));
  }

  /**
   * 회원 정보 수정
   * @param loginMemberId - 로그인한 사용자 ID
   * @param updateMemberDto - 사용자 정보수정 dto (이름, 핸드폰번호, 로그인 PW)
   * @return true
   */
  @PatchMapping("/{member_id}")
  public ResponseEntity<Member> updateMemberInfo(
      @AuthenticationPrincipal Long loginMemberId,
      @PathVariable("member_id") @Min(1) long memberId,
      @RequestBody @Valid UpdateMemberDto updateMemberDto){
    validationService.validMemberId(loginMemberId,memberId);
    return ResponseEntity.ok().body(memberService.updateMemberInfo(loginMemberId, memberId, updateMemberDto));
  }



  /**
   * 비밀번호 재설정 요청
   * @param loginMemberId - 로그인한 사용자 ID
   * @param memberId  사용자 ID
   * @param findMemberInfoDto - 회원정보 조회 dto (이름, 핸드폰번호)
   * @return "인증 메세지 발송 완료"
   */
  @PostMapping("/find-pw/{member_id}")
  public ResponseEntity<String> requestResetPw(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("member_id") @Min(1) Long memberId,
          @Valid @RequestBody FindMemberInfoDto findMemberInfoDto) {
    validationService.validMemberId(loginMemberId,memberId);
    return ResponseEntity.ok().body(memberService.requestResetPw(loginMemberId,memberId,findMemberInfoDto));
  }

  /**
   * 비밀번호 재설정
   * @param loginMemberId - 로그인한 사용자 ID
   * @param memberId - 사용자 ID
   * @param resetPwDto - 비밀번호 재설정 dto (인증번호, 새로운 PW)
   * @return "비밀번호 재설정 완료"
   */
  @PatchMapping("/find-pw/{member_id}/confirm")
  public ResponseEntity<String> verifyResetPw(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("member_id") @Min(1) Long memberId,
          @Valid @RequestBody ResetPwDto resetPwDto) {
    validationService.validMemberId(loginMemberId,memberId);
    return ResponseEntity.ok().body(memberService.verifyResetPw(loginMemberId,memberId,resetPwDto));
  }
}