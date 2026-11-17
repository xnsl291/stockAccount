package zb.accountMangement.stock.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.model.TransactionType;
import zb.accountMangement.account.service.AccountService;
import zb.accountMangement.account.service.TransactionService;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.stock.model.entity.StockBalance;
import zb.accountMangement.stock.model.entity.Trading;
import zb.accountMangement.stock.dto.TransferStockDto;
import zb.accountMangement.stock.repository.StockBalanceRepository;
import zb.accountMangement.stock.repository.StockRepository;
import zb.accountMangement.stock.repository.TradingRepository;
import zb.accountMangement.stock.model.OrderStatus;
import zb.accountMangement.stock.model.TradeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static zb.accountMangement.common.type.FeeRate.TRANSFER_FEE_RATE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final StockRepository stockRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final TradingRepository tradeHistoryRepository;

    /**
     * 주식 ID를 이용한 종목 조회
     * @param stockId - 주식 ID
     * @return Stock
     */
    public Stock getStockById(Long stockId){
        return stockRepository.findById(stockId)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_EXIST));
    }

    /**
     * 계좌 ID와 주식 ID를 이용한 잔고조회
     * @param accountId - 계좌 ID
     * @param stockId - 주식 ID
     * @return StockBalance
     */
    public StockBalance getStockBalanceByAccountIdAndStockId(Long accountId, Long stockId){
        return stockBalanceRepository.findByAccountIdAndStockId(accountId, stockId)
                .orElseThrow(() -> new CustomException(ErrorCode.NO_STOCK_BALANCE));
    }

    /**
     * 주식 현재 시세 조회
     * @param stockId - 주식 ID
     * @return 주식 가격
     */
    public Long getCurrentStockPrice(Long stockId) {
        return getStockById(stockId).getCurrentPrice();
    }

    /**
     * 종목 검색
     * @param keyword - 종목코드 또는 종목명의 일부
     * @return 종목 리스트
     */
    public List<Stock> searchStocks(String keyword) {
        return stockRepository.findByCodeContainingOrNameContaining(keyword, keyword);
    }

    /**
     * 주식 수량이 0이면 StockBalance에서 해당 주식 종목 삭제
     * @param stockBalance - 잔고
     */
    private void quantityCheck(StockBalance stockBalance){
        if (stockBalance.getQuantity() == 0) {
            stockBalanceRepository.delete(stockBalance);
        }
    }

    /**
     * 주식 잔고 이체
     * @param transferStockDto - 주식 잔고 이체 dto(발신자 계좌 ID,수신자 계좌 ID 주식 ID, 매수희망가, 매수수량, 거래일시)
     * @return 이체 결과
     */
    @Transactional
    public Boolean transferStock(TransferStockDto transferStockDto){
        Account senderAccount = accountService.getAccountForUpdate(transferStockDto.getSenderAccountId());
        StockBalance senderStockBalance = getStockBalanceByAccountIdAndStockId(transferStockDto.getSenderAccountId(), transferStockDto.getStockId());

        Long senderId = senderAccount.getId();
        Long receiverId = accountService.getAccountById(transferStockDto.getReceiverAccountId()).getMemberId();

        // 본인 계좌에 이체할 경우 수수료 X
        long transferFee = senderId.equals(receiverId) ?
                calculateTransferFee(transferStockDto.getStockId() , transferStockDto.getQuantity()) : 0 ;

        if (senderAccount.getBalance() < transferFee)
            throw new CustomException(ErrorCode.EXCEED_BALANCE);

        if (senderStockBalance.getQuantity() < transferStockDto.getQuantity()) {
            throw new CustomException(ErrorCode.INSUFFICIENT_STOCK);
        }

        senderStockBalance.setQuantity(senderStockBalance.getQuantity() - transferStockDto.getQuantity());

        // 수수료 차감
        if (transferFee > 0)
            transactionService.changeBalance(senderAccount.getId(), TransactionType.FEE, transferFee, "주식 이체 수수료", null);

        quantityCheck(senderStockBalance);

        // 수신계좌
        Optional<StockBalance> optionalReceiverStockBalance =
                stockBalanceRepository.findByAccountIdAndStockId(transferStockDto.getReceiverAccountId(), transferStockDto.getStockId());
        StockBalance receiverStockBalance;

        if (optionalReceiverStockBalance.isPresent()) {
            receiverStockBalance = optionalReceiverStockBalance.get();

            long totalCost = transferStockDto.getPrice() * transferStockDto.getQuantity();

            // 평단가 변경
            receiverStockBalance.setAvgPrice((receiverStockBalance.getAvgPrice() * receiverStockBalance.getQuantity() + totalCost) /
                    (receiverStockBalance.getQuantity() + transferStockDto.getQuantity()));
            receiverStockBalance.setQuantity(receiverStockBalance.getQuantity() + transferStockDto.getQuantity());

        } else {
            receiverStockBalance = StockBalance.builder()
                    .stockId(transferStockDto.getStockId())
                    .avgPrice(transferStockDto.getPrice())
                    .quantity(transferStockDto.getQuantity())
                    .build();
            stockBalanceRepository.save(receiverStockBalance);
        }

        Trading senderTradeHistory = Trading.builder()
                .stockId(transferStockDto.getStockId())
                .accountId(transferStockDto.getSenderAccountId())
                .price(transferStockDto.getPrice())
                .quantity(-transferStockDto.getQuantity())
                .type(TradeType.TRANSFER)
                .tradeAt(LocalDateTime.now())
                .status(OrderStatus.FILLED)
                .build();

        Trading receiverTradeHistory = Trading.builder()
                .stockId(transferStockDto.getStockId())
                .accountId(transferStockDto.getReceiverAccountId())
                .price(transferStockDto.getPrice())
                .quantity(transferStockDto.getQuantity())
                .type(TradeType.TRANSFER)
                .tradeAt(LocalDateTime.now())
                .status(OrderStatus.FILLED)
                .build();

        tradeHistoryRepository.save(senderTradeHistory);
        tradeHistoryRepository.save(receiverTradeHistory);
        return true;
    }

    /**
     * 이체 수수료 계산 - 원 미만은 버림
     * @param stockId - 주식 ID
     * @param quantity - 수량
     * @return 수수료
     */
    private long calculateTransferFee(Long stockId , int quantity){
        Stock stock = getStockById(stockId);

        return BigDecimal.valueOf(stock.getCurrentPrice())
                .multiply(BigDecimal.valueOf(quantity))
                .multiply(TRANSFER_FEE_RATE.getRate())
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }

    /**
     * 계좌 잔고 조회 - 평가손익순으로 정렬
     * @param accountId - 계좌  ID
     * @return 현재 보유중인 주식 종목 리스트
     */
    public List<StockBalance> getStockBalance(Long accountId) {
        List<StockBalance> stockBalances = stockBalanceRepository.findByAccountId(accountId);

        if (!stockBalances.isEmpty())
            // TODO: 스케줄러에 등록해서 STOCK 정보가 업데이트 될 떄 같이 업데이트 되게 변경
            for (StockBalance stockBalance : stockBalances)
                stockBalance.setProfitNLoss(calculateProfitLoss(stockBalance));  // 평가손익 업데이트

        //평가손익순으로 정렬
        stockBalances.sort(Comparator.comparingLong(StockBalance::getProfitNLoss).reversed());
        return stockBalances;
    }

    /**
     * 평가손익 계산
     * @param stockBalance - 주식 잔고
     * @return 평가손익
     */
    private long calculateProfitLoss(StockBalance stockBalance){
        Stock stock = getStockById(stockBalance.getStockId());
        return (stock.getCurrentPrice() - stockBalance.getAvgPrice()) * stockBalance.getQuantity();
    }
}
