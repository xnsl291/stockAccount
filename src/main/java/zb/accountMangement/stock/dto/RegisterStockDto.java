package zb.accountMangement.stock.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

@Getter
@Setter
public class RegisterStockDto {

	@NotNull
	@Pattern(regexp = "[0-9A-Z]{6}", message = "종목코드는 숫자와 영문 대문자 6자리입니다")
	private String code;

	@NotBlank
	private String name;

	@NotNull
	@Positive
	private Long currentPrice;
}
