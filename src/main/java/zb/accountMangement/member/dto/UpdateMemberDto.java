package zb.accountMangement.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMemberDto {

  @NotBlank
  private String name;

  @NotBlank
  @Pattern(regexp = "^01([0-9])[ -.]?([0-9]{3,4})[ -.]?([0-9]{4})$", message = "핸드폰 번호 포맷이 일치하지 않습니다")
  private String phoneNumber;

  @NotBlank
  private String password; // 새 로그인 비밀번호. 바꾸지 않으려면 현재 비밀번호를 그대로 넣음

  @NotBlank
  private String currentPassword;

}
