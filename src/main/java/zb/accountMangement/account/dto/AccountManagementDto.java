package zb.accountMangement.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountManagementDto {

  // DB 칸(VARCHAR(255))보다 길면 저장에서 실패하므로 요청 단계에서 막음
  @Size(max = 255)
  private String nickname;

  @NotBlank
  private String password; //계좌비밀번호

}
