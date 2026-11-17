package zb.accountMangement.stock.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.stock.dto.RegisterStockDto;
import zb.accountMangement.stock.dto.UpdateStockPriceDto;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.stock.service.StockService;

import javax.validation.Valid;

/**
 * 관리자만 쓸 수 있는 종목 등록과 시세 갱신
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/stocks")
public class AdminStockController {
	private final StockService stockService;

	/**
	 * 종목 등록
	 * @param registerStockDto - 종목 dto (종목코드, 종목명, 현재가)
	 * @return 등록된 종목
	 */
	@PostMapping
	public ResponseEntity<Stock> registerStock(@Valid @RequestBody RegisterStockDto registerStockDto) {
		return ResponseEntity.ok().body(stockService.registerStock(registerStockDto));
	}

	/**
	 * 시세 갱신 - 새 시세로 체결되는 미체결 주문을 함께 체결함
	 * @param stockId - 주식 ID
	 * @param updateStockPriceDto - 새 시세
	 * @return 시세가 바뀐 종목
	 */
	@PutMapping("/{stock_id}/price")
	public ResponseEntity<Stock> updatePrice(
			@PathVariable("stock_id") Long stockId,
			@Valid @RequestBody UpdateStockPriceDto updateStockPriceDto) {
		return ResponseEntity.ok().body(stockService.updatePrice(stockId, updateStockPriceDto.getPrice()));
	}
}
