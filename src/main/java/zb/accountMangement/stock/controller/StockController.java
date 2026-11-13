package zb.accountMangement.stock.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.stock.model.entity.StockBalance;
import zb.accountMangement.stock.model.entity.Trading;
import zb.accountMangement.stock.dto.*;
import zb.accountMangement.stock.service.StockService;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/stocks")
public class StockController {
    private final StockService stockService;

    /**
    * 주식 매수
    * @param buyStockDto - 매수 dto (계좌 ID, 주식 ID, 매수희망가, 매수수량)
    * @return 주문 체결 여부
    */
    @PostMapping("/buy")
    public ResponseEntity<Boolean> buyStock(@Valid @RequestBody TradeStockDto buyStockDto){
        return ResponseEntity.ok().body(stockService.buyStock(buyStockDto));
    }

    /**
     * 주식 매도
     * @param sellStockDto - 매도 dto (계좌 ID, 주식 ID, 매수희망가, 매수수량)
     * @return 주문 체결 여부
     */
    @PostMapping("/sell")
    public ResponseEntity<Boolean> sellStock(@Valid @RequestBody TradeStockDto sellStockDto){
        return ResponseEntity.ok().body(stockService.sellStock(sellStockDto));
    }

    @PostMapping("/transfer")
    public ResponseEntity<Boolean> transferStock(@Valid @RequestBody TransferStockDto transferStockDto){
        return ResponseEntity.ok().body(stockService.transferStock(transferStockDto));
    }

    /**
     * 계좌 잔고 조회
     * @param accountId - 계좌  ID
     * @return 현재 보유중인 주식 종목 리스트
     */
    @GetMapping("/{account_id}/balance")
    public ResponseEntity<List<StockBalance>> getStockBalance(@Validated @PathVariable("account_id") Long accountId){
        return ResponseEntity.ok().body(stockService.getStockBalance(accountId));
    }

    /**
     * 거래내역 조회 - 날짜값을 지정하여 검색가능
     * @param accountId - 계좌 ID
     * @param dateDto - 조회할 날짜, 월
     * @return 거래내역
     */
    @GetMapping("/{account_id}/history/")
    public ResponseEntity<List<Trading>> getTradeHistory(
            @Validated @PathVariable("account_id") Long accountId,
            @ModelAttribute @Valid DateDto dateDto) {
        if (dateDto == null)
            return ResponseEntity.ok().body(stockService.getTradeHistory(accountId));
        return ResponseEntity.ok().body(stockService.getTradeHistory(dateDto, accountId));
    }

    /**
     * 주식 현재 시세 조회
     * @param stockId - 주식 ID
     * @return 주식 가격
     */
    @GetMapping("/{stock_id}")
    public ResponseEntity<Double> getStockPrice(@Validated @PathVariable("stock_id") Long stockId){
        return ResponseEntity.ok().body(stockService.getCurrentStockPrice(stockId));
    }
}
