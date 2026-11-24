package zb.accountMangement.auth.dto;

import lombok.Getter;
import lombok.Setter;

import zb.accountMangement.member.model.VerificationPurpose;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Getter
@Setter
public class SmsVerificationDto {

  @NotBlank
  @Pattern(regexp = "^01([0-9])[ -.]?([0-9]{3,4})[ -.]?([0-9]{4})$", message = "핸드폰 번호 포맷이 일치하지 않습니다")
  private String phoneNumber;

  @NotBlank
  private String verificationCode;

  @NotNull
  private VerificationPurpose purpose;

}
