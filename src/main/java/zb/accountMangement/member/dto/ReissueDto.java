package zb.accountMangement.member.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class ReissueDto {

  @NotBlank
  private String refreshToken;
}
