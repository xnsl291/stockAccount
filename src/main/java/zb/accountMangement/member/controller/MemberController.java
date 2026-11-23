package zb.accountMangement.member.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.member.dto.*;
import zb.accountMangement.member.service.MemberService;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Min;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/member")
public class MemberController {
  private final MemberService memberService;

  @GetMapping("/{member_id}")
  public ResponseEntity<MemberResponse> getMemberInfo(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("member_id") @Min(1) Long memberId  ){
    validMemberId(loginMemberId,memberId);
    return ResponseEntity.ok().body(MemberResponse.from(memberService.getMemberById(memberId)));
  }

  @PatchMapping("/{member_id}")
  public ResponseEntity<MemberResponse> updateMemberInfo(
      @AuthenticationPrincipal Long loginMemberId,
      @PathVariable("member_id") @Min(1) long memberId,
      @RequestBody @Valid UpdateMemberDto updateMemberDto){
    validMemberId(loginMemberId,memberId);
    return ResponseEntity.ok().body(MemberResponse.from(memberService.updateMemberInfo(memberId, updateMemberDto)));
  }



  @PostMapping("/find-pw/{member_id}")
  public ResponseEntity<String> requestResetPw(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("member_id") @Min(1) Long memberId,
          @Valid @RequestBody FindMemberInfoDto findMemberInfoDto,
          HttpServletRequest request) {
    validMemberId(loginMemberId,memberId);
    return ResponseEntity.ok().body(memberService.requestResetPw(memberId, findMemberInfoDto, request.getRemoteAddr()));
  }

  @PatchMapping("/find-pw/{member_id}/confirm")
  public ResponseEntity<String> verifyResetPw(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("member_id") @Min(1) Long memberId,
          @Valid @RequestBody ResetPwDto resetPwDto) {
    validMemberId(loginMemberId,memberId);
    return ResponseEntity.ok().body(memberService.verifyResetPw(memberId, resetPwDto));
  }

  private void validMemberId(Long loginMemberId, Long memberId){
    if (!loginMemberId.equals(memberId))
      throw new CustomException(ErrorCode.MISMATCHED_USER_ID);
  }
}