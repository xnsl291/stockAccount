package zb.accountMangement.member.dto;

import lombok.Builder;
import lombok.Getter;
import zb.accountMangement.common.type.RoleType;
import zb.accountMangement.member.model.entity.Member;

import java.time.LocalDateTime;

/**
 * 회원 응답 - 로그인 비밀번호 해시는 내보내지 않음
 */
@Getter
@Builder
public class MemberResponse {

  private Long id;

  private String name;

  private String phoneNumber;

  private RoleType role;

  private LocalDateTime createdAt;

  public static MemberResponse from(Member member) {
    return MemberResponse.builder()
            .id(member.getId())
            .name(member.getName())
            .phoneNumber(member.getPhoneNumber())
            .role(member.getRole())
            .createdAt(member.getCreatedAt())
            .build();
  }
}
