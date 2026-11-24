package zb.accountMangement.stock.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.stock.dto.AdminStockResponse;
import zb.accountMangement.stock.dto.PriceUpdateResponse;
import zb.accountMangement.stock.dto.RegisterStockDto;
import zb.accountMangement.stock.dto.StockResponse;
import zb.accountMangement.stock.dto.TradingResponse;
import zb.accountMangement.stock.model.OrderStatus;
import zb.accountMangement.stock.dto.UpdateStockPriceDto;
import zb.accountMangement.stock.service.StockService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * 관리자만 쓸 수 있는 종목 등록·조회와 시세 갱신
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/admin/stocks")
public class AdminStockController {
	private final StockService stockService;

	@PostMapping
	public ResponseEntity<StockResponse> registerStock(@Valid @RequestBody RegisterStockDto registerStockDto) {
		return ResponseEntity.ok().body(StockResponse.from(stockService.registerStock(registerStockDto)));
	}

	/**
	 * 최근 등록 순
	 */
	@GetMapping
	public ResponseEntity<Page<AdminStockResponse>> getStocks(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ResponseEntity.ok(stockService.getAdminStocks(page, size));
	}

	/**
	 * 시세 입력 화면을 주소로 바로 열 때 씀
	 */
	@GetMapping("/{stock_id}")
	public ResponseEntity<AdminStockResponse> getStock(@PathVariable("stock_id") Long stockId) {
		return ResponseEntity.ok(stockService.getAdminStock(stockId));
	}

	/**
	 * 시세를 넣기 전에 어떤 주문이 체결될지 봄
	 */
	@GetMapping("/{stock_id}/orders")
	public ResponseEntity<List<TradingResponse>> getOrders(
			@PathVariable("stock_id") Long stockId,
			@RequestParam(defaultValue = "PENDING") OrderStatus status) {
		return ResponseEntity.ok(stockService.getOrdersOfStock(stockId, status));
	}

	/**
	 * 새 시세로 체결되는 미체결 주문을 함께 체결함
	 */
	@PutMapping("/{stock_id}/price")
	public ResponseEntity<PriceUpdateResponse> updatePrice(
			@PathVariable("stock_id") Long stockId,
			@Valid @RequestBody UpdateStockPriceDto updateStockPriceDto) {
		return ResponseEntity.ok().body(stockService.updatePrice(stockId, updateStockPriceDto.getPrice()));
	}
}
