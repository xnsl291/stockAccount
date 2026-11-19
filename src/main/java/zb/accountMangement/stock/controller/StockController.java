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
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
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

    /**
    * 주식 매수 주문
    * @param loginMemberId - 로그인한 사용자 ID
    * @param idempotencyKey - 요청마다 새로 만든 키. 같은 키로 다시 보내면 처음 접수한 주문만 돌려줌
    * @param buyStockDto - 매수 dto (계좌 ID, 주식 ID, 매수희망가, 매수수량, 계좌 PW)
    * @return 접수된 주문 (바로 체결되면 FILLED, 아니면 PENDING)
    */
    @PostMapping("/buy")
    public ResponseEntity<TradingResponse> buyStock(
            @AuthenticationPrincipal Long loginMemberId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = IdempotencyRecord.KEY_MAX_LENGTH) String idempotencyKey,
            @Valid @RequestBody TradeStockDto buyStockDto){
        return ResponseEntity.ok().body(placeOrder(loginMemberId, idempotencyKey, TradeType.BUY, buyStockDto));
    }

    /**
     * 주식 매도 주문
     * @param loginMemberId - 로그인한 사용자 ID
     * @param idempotencyKey - 요청마다 새로 만든 키. 같은 키로 다시 보내면 처음 접수한 주문만 돌려줌
     * @param sellStockDto - 매도 dto (계좌 ID, 주식 ID, 매도희망가, 매도수량, 계좌 PW)
     * @return 접수된 주문 (바로 체결되면 FILLED, 아니면 PENDING)
     */
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
        // 이미 접수한 주문을 다시 보낸 것이면 비밀번호를 보지 않고 처음 주문을 돌려줌. 틀린 횟수도 오르지 않음
        String orderId = idempotencyService.findResponse(loginMemberId, idempotencyKey, request).orElseGet(() -> {
            accountValidationService.validAccountPassword(loginMemberId, tradeStockDto.getAccountId(), tradeStockDto.getAccountPassword());
            return idempotencyService.execute(loginMemberId, idempotencyKey, request,
                    () -> String.valueOf(orderService.placeOrder(type, tradeStockDto)));
        });
        // 위의 접수 트랜잭션이 커밋된 뒤에 다시 확인해야, 접수 중에 지나간 시세 갱신이 놓친 체결을 잡음
        orderService.recheckFill(tradeStockDto.getAccountId(), Long.valueOf(orderId));
        Trading order = orderService.getOrder(Long.valueOf(orderId));
        return TradingResponse.of(order, stockService.getStockById(order.getStockId()));
    }

    /**
     * 주문 취소 - 체결되지 않은 주문만 취소할 수 있음
     * @param loginMemberId - 로그인한 사용자 ID
     * @param accountId - 주문을 낸 계좌 ID
     * @param orderId - 주문 ID
     * @return 취소된 주문
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

    /**
     * 주식 잔고 이체
     * @param loginMemberId - 로그인한 사용자 ID
     * @param idempotencyKey - 요청마다 새로 만든 키. 같은 키로 다시 보내면 처음 결과만 돌려줌
     * @param transferStockDto - 주식 잔고 이체 dto (발신자 계좌 ID, 수신자 계좌 ID, 주식 ID, 수량, 발신자 계좌 PW)
     * @return "이체완료"
     */
    @PostMapping("/transfer")
    public ResponseEntity<String> transferStock(
            @AuthenticationPrincipal Long loginMemberId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = IdempotencyRecord.KEY_MAX_LENGTH) String idempotencyKey,
            @Valid @RequestBody TransferStockDto transferStockDto){
        String request = "STOCK_TRANSFER " + transferStockDto.getSenderAccountId() + " " + transferStockDto.getReceiverAccountId() + " "
                + transferStockDto.getStockId() + " " + transferStockDto.getQuantity();
        return ResponseEntity.ok().body(idempotencyService.findResponse(loginMemberId, idempotencyKey, request).orElseGet(() -> {
            accountValidationService.validAccountPassword(loginMemberId, transferStockDto.getSenderAccountId(), transferStockDto.getAccountPassword());
            return idempotencyService.execute(loginMemberId, idempotencyKey, request, () -> stockService.transferStock(transferStockDto));
        }));
    }

    /**
     * 계좌 잔고 조회
     * @param loginMemberId - 로그인한 사용자 ID
     * @param accountId - 계좌  ID
     * @return 현재 보유 중인 주식 종목 리스트
     */
    @GetMapping("/{account_id}/balance")
    public ResponseEntity<List<StockBalanceResponse>> getStockBalance(
            @AuthenticationPrincipal Long loginMemberId,
            @Validated @PathVariable("account_id") Long accountId){
        accountValidationService.validAccountOwner(loginMemberId, accountId);
        return ResponseEntity.ok().body(stockService.getStockBalance(accountId));
    }

    /**
     * 주문·거래내역 조회 - 조건을 주지 않으면 전체를 최근 순으로 돌려줌
     * @param loginMemberId - 로그인한 사용자 ID
     * @param accountId - 계좌 ID
     * @param status - 주문 상태 (없으면 전체)
     * @param dateDto - 조회할 연도와 월 (연도만 주면 그 해 전체, 둘 다 없으면 전체 기간)
     * @return 거래내역
     */
    @GetMapping("/{account_id}/history")
    public ResponseEntity<List<TradingResponse>> getTradeHistory(
            @AuthenticationPrincipal Long loginMemberId,
            @Validated @PathVariable("account_id") Long accountId,
            @RequestParam(required = false) OrderStatus status,
            @ModelAttribute @Valid DateDto dateDto) {
        accountValidationService.validAccountOwner(loginMemberId, accountId);
        List<Trading> history = orderService.getTradeHistory(accountId, status, dateDto);
        Map<Long, Stock> stocks = stockService.getStocksById(history.stream().map(Trading::getStockId).collect(Collectors.toSet()));
        return ResponseEntity.ok().body(history.stream()
                .map(trading -> TradingResponse.of(trading, stocks.get(trading.getStockId())))
                .collect(Collectors.toList()));
    }

    /**
     * 종목 검색
     * @param keyword - 종목코드 또는 종목명의 일부
     * @return 종목 리스트 (종목코드, 종목명, 현재가)
     */
    @GetMapping
    public ResponseEntity<List<StockResponse>> searchStocks(@RequestParam @NotBlank String keyword){
        return ResponseEntity.ok().body(stockService.searchStocks(keyword).stream()
                .map(StockResponse::from)
                .collect(Collectors.toList()));
    }

    /**
     * 종목 한 건 조회 - 종목 ID만 아는 화면이 코드·이름·현재가를 얻을 때 씀
     * @param stockId - 주식 ID
     * @return 종목 (종목코드, 종목명, 현재가)
     */
    @GetMapping("/{stock_id}/info")
    public ResponseEntity<StockResponse> getStock(@PathVariable("stock_id") Long stockId){
        return ResponseEntity.ok().body(StockResponse.from(stockService.getStockById(stockId)));
    }

    /**
     * 주식 현재 시세 조회
     * @param stockId - 주식 ID
     * @return 주식 가격
     */
    @GetMapping("/{stock_id}")
    public ResponseEntity<Long> getStockPrice(@Validated @PathVariable("stock_id") Long stockId){
        return ResponseEntity.ok().body(stockService.getCurrentStockPrice(stockId));
    }
}
