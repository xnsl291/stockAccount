package zb.accountMangement.stock.dto;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Getter
@Setter
public class UpdateStockPriceDto {

	@NotNull
	@Positive
	private Long price;
}
