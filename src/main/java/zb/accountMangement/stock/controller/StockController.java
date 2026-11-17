package zb.accountMangement.stock.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.account.model.entity.IdempotencyRecord;
import zb.accountMangement.account.service.IdempotencyService;
import zb.accountMangement.common.service.ValidationService;
import zb.accountMangement.stock.model.OrderStatus;
import zb.accountMangement.stock.model.TradeType;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.stock.model.entity.StockBalance;
import zb.accountMangement.stock.model.entity.Trading;
import zb.accountMangement.stock.dto.*;
import zb.accountMangement.stock.service.OrderService;
import zb.accountMangement.stock.service.StockService;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/stocks")
public class StockController {
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final StockService stockService;
    private final OrderService orderService;
    private final ValidationService validationService;
    private final IdempotencyService idempotencyService;

    /**
    * 주식 매수 주문
    * @param loginMemberId - 로그인한 사용자 ID
    * @param idempotencyKey - 요청마다 새로 만든 키. 같은 키로 다시 보내면 처음 접수한 주문만 돌려줌
    * @param buyStockDto - 매수 dto (계좌 ID, 주식 ID, 매수희망가, 매수수량, 계좌 PW)
    * @return 접수된 주문 (바로 체결되면 FILLED, 아니면 PENDING)
    */
    @PostMapping("/buy")
    public ResponseEntity<Trading> buyStock(
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
    public ResponseEntity<Trading> sellStock(
            @AuthenticationPrincipal Long loginMemberId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = IdempotencyRecord.KEY_MAX_LENGTH) String idempotencyKey,
            @Valid @RequestBody TradeStockDto sellStockDto){
        return ResponseEntity.ok().body(placeOrder(loginMemberId, idempotencyKey, TradeType.SELL, sellStockDto));
    }

    private Trading placeOrder(Long loginMemberId, String idempotencyKey, TradeType type, TradeStockDto tradeStockDto) {
        validationService.validAccountPassword(loginMemberId, tradeStockDto.getAccountId(), tradeStockDto.getAccountPassword());
        String orderId = idempotencyService.execute(loginMemberId, idempotencyKey,
                type + " " + tradeStockDto.getAccountId() + " " + tradeStockDto.getStockId() + " "
                        + tradeStockDto.getPrice() + " " + tradeStockDto.getQuantity(),
                () -> String.valueOf(orderService.placeOrder(type, tradeStockDto)));
        return orderService.getOrder(Long.valueOf(orderId));
    }

    /**
     * 주문 취소 - 체결되지 않은 주문만 취소할 수 있음
     * @param loginMemberId - 로그인한 사용자 ID
     * @param accountId - 주문을 낸 계좌 ID
     * @param orderId - 주문 ID
     * @return 취소된 주문
     */
    @DeleteMapping("/{account_id}/orders/{order_id}")
    public ResponseEntity<Trading> cancelOrder(
            @AuthenticationPrincipal Long loginMemberId,
            @PathVariable("account_id") Long accountId,
            @PathVariable("order_id") Long orderId){
        validationService.validAccountOwner(loginMemberId, accountId);
        return ResponseEntity.ok().body(orderService.cancelOrder(accountId, orderId));
    }

    /**
     * 주식 잔고 이체
     * @param loginMemberId - 로그인한 사용자 ID
     * @param transferStockDto - 주식 잔고 이체 dto (발신자 계좌 ID, 수신자 계좌 ID, 주식 ID, 가격, 수량, 발신자 계좌 PW)
     * @return 이체 결과
     */
    @PostMapping("/transfer")
    public ResponseEntity<Boolean> transferStock(
            @AuthenticationPrincipal Long loginMemberId,
            @Valid @RequestBody TransferStockDto transferStockDto){
        validationService.validAccountPassword(loginMemberId, transferStockDto.getSenderAccountId(), transferStockDto.getAccountPassword());
        return ResponseEntity.ok().body(stockService.transferStock(transferStockDto));
    }

    /**
     * 계좌 잔고 조회
     * @param loginMemberId - 로그인한 사용자 ID
     * @param accountId - 계좌  ID
     * @return 현재 보유 중인 주식 종목 리스트
     */
    @GetMapping("/{account_id}/balance")
    public ResponseEntity<List<StockBalance>> getStockBalance(
            @AuthenticationPrincipal Long loginMemberId,
            @Validated @PathVariable("account_id") Long accountId){
        validationService.validAccountOwner(loginMemberId, accountId);
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
    public ResponseEntity<List<Trading>> getTradeHistory(
            @AuthenticationPrincipal Long loginMemberId,
            @Validated @PathVariable("account_id") Long accountId,
            @RequestParam(required = false) OrderStatus status,
            @ModelAttribute @Valid DateDto dateDto) {
        validationService.validAccountOwner(loginMemberId, accountId);
        return ResponseEntity.ok().body(orderService.getTradeHistory(accountId, status, dateDto));
    }

    /**
     * 종목 검색
     * @param keyword - 종목코드 또는 종목명의 일부
     * @return 종목 리스트 (종목코드, 종목명, 현재가)
     */
    @GetMapping
    public ResponseEntity<List<Stock>> searchStocks(@RequestParam @NotBlank String keyword){
        return ResponseEntity.ok().body(stockService.searchStocks(keyword));
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
