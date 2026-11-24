package zb.accountMangement.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMemberDto {

  // DB 칸(VARCHAR(255))보다 길면 저장에서 실패하므로 요청 단계에서 막음
  @NotBlank
  @Size(max = 255)
  private String name;

  @NotBlank
  @Pattern(regexp = "^01([0-9])[ -.]?([0-9]{3,4})[ -.]?([0-9]{4})$", message = "핸드폰 번호 포맷이 일치하지 않습니다")
  private String phoneNumber;

  @NotBlank
  private String password; // 새 로그인 비밀번호. 바꾸지 않으려면 현재 비밀번호를 그대로 넣음

  @NotBlank
  private String currentPassword;

}
