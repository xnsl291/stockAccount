package zb.accountMangement.stock.dto;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Getter
@Setter
public class RegisterStockDto {

	@NotNull
	@Pattern(regexp = "[0-9A-Z]{6}", message = "종목코드는 숫자와 영문 대문자 6자리입니다")
	private String code;

	// DB 칸(VARCHAR(255))보다 길면 저장에서 실패하므로 요청 단계에서 막음
	@NotBlank
	@Size(max = 255)
	private String name;

	@NotNull
	@Positive
	private Long currentPrice;
}
