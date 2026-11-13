package zb.accountMangement.member.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Getter
@Setter
public class FindMemberInfoDto {

  @NotBlank
  private String name;

  @NotBlank
  @Pattern(regexp = "^01([0-9])[ -.]?([0-9]{3,4})[ -.]?([0-9]{4})$", message = "핸드폰 번호 포맷이 일치하지 않습니다")
  private String phoneNumber;
}
