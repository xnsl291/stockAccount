package zb.accountMangement.auth.dto;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
public class ReissueDto {

  @NotBlank
  private String refreshToken;
}
