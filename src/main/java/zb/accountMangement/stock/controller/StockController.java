package zb.accountMangement.stock.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.idempotency.model.entity.IdempotencyRecord;
import zb.accountMangement.idempotency.service.IdempotencyService;
import zb.accountMangement.account.service.AccountValidationService;
import zb.accountMangement.stock.model.OrderStatus;
import zb.accountMangement.stock.model.TradeType;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.stock.model.entity.Trading;
import zb.accountMangement.stock.dto.*;
import zb.accountMangement.stock.service.OrderService;
import zb.accountMangement.stock.service.StockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/stocks")
public class StockController {
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final StockService stockService;
    private final OrderService orderService;
    private final AccountValidationService accountValidationService;
    private final IdempotencyService idempotencyService;

    @PostMapping("/buy")
    public ResponseEntity<TradingResponse> buyStock(
            @AuthenticationPrincipal Long loginMemberId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = IdempotencyRecord.KEY_MAX_LENGTH) String idempotencyKey,
            @Valid @RequestBody TradeStockDto buyStockDto){
        return ResponseEntity.ok().body(placeOrder(loginMemberId, idempotencyKey, TradeType.BUY, buyStockDto));
    }

    @PostMapping("/sell")
    public ResponseEntity<TradingResponse> sellStock(
            @AuthenticationPrincipal Long loginMemberId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = IdempotencyRecord.KEY_MAX_LENGTH) String idempotencyKey,
            @Valid @RequestBody TradeStockDto sellStockDto){
        return ResponseEntity.ok().body(placeOrder(loginMemberId, idempotencyKey, TradeType.SELL, sellStockDto));
    }

    private TradingResponse placeOrder(Long loginMemberId, String idempotencyKey, TradeType type, TradeStockDto tradeStockDto) {
        String request = type + " " + tradeStockDto.getAccountId() + " " + tradeStockDto.getStockId() + " "
                + tradeStockDto.getPrice() + " " + tradeStockDto.getQuantity();
        String orderId = idempotencyService.execute(loginMemberId, idempotencyKey, request,
                () -> accountValidationService.validAccountPassword(loginMemberId, tradeStockDto.getAccountId(), tradeStockDto.getAccountPassword()),
                () -> String.valueOf(orderService.placeOrder(type, tradeStockDto)));
        // 위의 접수 트랜잭션이 커밋된 뒤에 다시 확인해야, 접수 중에 지나간 시세 갱신이 놓친 체결을 잡음
        orderService.recheckFill(tradeStockDto.getAccountId(), Long.valueOf(orderId));
        Trading order = orderService.getOrder(Long.valueOf(orderId));
        return TradingResponse.of(order, stockService.getStockById(order.getStockId()));
    }

    /**
     * 체결되지 않은 주문만 취소할 수 있음
     */
    @DeleteMapping("/{account_id}/orders/{order_id}")
    public ResponseEntity<TradingResponse> cancelOrder(
            @AuthenticationPrincipal Long loginMemberId,
            @PathVariable("account_id") Long accountId,
            @PathVariable("order_id") Long orderId){
        accountValidationService.validAccountOwner(loginMemberId, accountId);
        Trading order = orderService.cancelOrder(accountId, orderId);
        return ResponseEntity.ok().body(TradingResponse.of(order, stockService.getStockById(order.getStockId())));
    }

    @PostMapping("/transfer")
    public ResponseEntity<String> transferStock(
            @AuthenticationPrincipal Long loginMemberId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = IdempotencyRecord.KEY_MAX_LENGTH) String idempotencyKey,
            @Valid @RequestBody TransferStockDto transferStockDto){
        String request = "STOCK_TRANSFER " + transferStockDto.getSenderAccountId() + " " + transferStockDto.getReceiverAccountId() + " "
                + transferStockDto.getStockId() + " " + transferStockDto.getQuantity();
        return ResponseEntity.ok().body(idempotencyService.execute(loginMemberId, idempotencyKey, request,
                () -> accountValidationService.validAccountPassword(loginMemberId, transferStockDto.getSenderAccountId(), transferStockDto.getAccountPassword()),
                () -> stockService.transferStock(transferStockDto)));
    }

    @GetMapping("/{account_id}/balance")
    public ResponseEntity<List<StockBalanceResponse>> getStockBalance(
            @AuthenticationPrincipal Long loginMemberId,
            @PathVariable("account_id") Long accountId){
        accountValidationService.validAccountOwner(loginMemberId, accountId);
        return ResponseEntity.ok().body(stockService.getStockBalance(accountId));
    }

    /**
     * 조건을 주지 않으면 전체를 최근 순으로 돌려줌. 연도만 주면 그 해 전체를 봄
     */
    @GetMapping("/{account_id}/history")
    public ResponseEntity<List<TradingResponse>> getTradeHistory(
            @AuthenticationPrincipal Long loginMemberId,
            @PathVariable("account_id") Long accountId,
            @RequestParam(required = false) OrderStatus status,
            @ModelAttribute @Valid DateDto dateDto) {
        accountValidationService.validAccountOwner(loginMemberId, accountId);
        List<Trading> history = orderService.getTradeHistory(accountId, status, dateDto);
        Map<Long, Stock> stocks = stockService.getStocksById(history.stream().map(Trading::getStockId).collect(Collectors.toSet()));
        return ResponseEntity.ok().body(history.stream()
                .map(trading -> TradingResponse.of(trading, stocks.get(trading.getStockId())))
                .collect(Collectors.toList()));
    }

    @GetMapping
    public ResponseEntity<List<StockResponse>> searchStocks(@RequestParam @NotBlank String keyword){
        return ResponseEntity.ok().body(stockService.searchStocks(keyword).stream()
                .map(StockResponse::from)
                .collect(Collectors.toList()));
    }

    /**
     * 종목 ID만 아는 화면이 코드·이름·현재가를 얻을 때 씀
     */
    @GetMapping("/{stock_id}/info")
    public ResponseEntity<StockResponse> getStock(@PathVariable("stock_id") Long stockId){
        return ResponseEntity.ok().body(StockResponse.from(stockService.getStockById(stockId)));
    }

    @GetMapping("/{stock_id}")
    public ResponseEntity<Long> getStockPrice(@PathVariable("stock_id") Long stockId){
        return ResponseEntity.ok().body(stockService.getCurrentStockPrice(stockId));
    }
}
