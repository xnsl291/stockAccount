package zb.accountMangement.stock.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Getter
@Setter
@Validated
public class DateDto {
  @Min(value = 2000, message = "2000년대 이후로만 조회가 가능합니다")
  @Max(value = 3000, message = "3000보다 큰 값을 조회할 수 없습니다")
  private Integer year;

  @Min(value = 1, message = "1보다 작은값을 조회할 수 없습니다")
  @Max(value = 12, message = "12보다 큰 값을 조회할 수 없습니다")
  private Integer month;
}
