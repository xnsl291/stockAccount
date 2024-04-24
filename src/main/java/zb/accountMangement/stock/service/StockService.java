package zb.accountMangement.stock.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.dto.TransactionDto;
import zb.accountMangement.account.service.AccountService;
import zb.accountMangement.account.service.TransactionService;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.stock.model.entity.StockBalance;
import zb.accountMangement.stock.model.entity.Trading;
import zb.accountMangement.stock.dto.DateDto;
import zb.accountMangement.stock.dto.TradeStockDto;
import zb.accountMangement.stock.dto.TransferStockDto;
import zb.accountMangement.stock.repository.StockBalanceRepository;
import zb.accountMangement.stock.repository.StockRepository;
import zb.accountMangement.stock.repository.TradingRepository;
import zb.accountMangement.stock.model.TradeType;

import java.time.LocalDate;
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
     * 주식 ID를 이용한 잔고조회
     * @param stockId - 주식 ID
     * @return StockBalance
     */
    public Stock getStockById(Long stockId){
        return stockRepository.findById(stockId)
                .orElseThrow(() -> new CustomException(ErrorCode.INSUFFICIENT_STOCK));
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
    public Double getCurrentStockPrice(Long stockId) {
        return getStockById(stockId).getCurrentPrice();
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
     * 총금액 계산
     * @param dto - TradeStockDto (계좌 ID, 주식 ID, 매수희망가, 매수수량)
     * @return 총금액
     */
    private double calculateTotalPrice(TradeStockDto dto){
        return dto.getQuantity() * dto.getPrice();
    }

    /**
    * 주식 매수
    * @param buyStockDto - 매수 dto (계좌 ID, 주식 ID, 매수희망가, 매수수량)
    * @return 주문 체결 여부
    */
    @Transactional
    public Boolean buyStock(TradeStockDto buyStockDto) {
        Account account = accountService.getAccountById(buyStockDto.getAccountId());
        Stock stock = getStockById(buyStockDto.getStockId());

        double totalCost = calculateTotalPrice(buyStockDto);

        if (account.getBalance() < totalCost)
            throw new CustomException(ErrorCode.EXCEED_BALANCE);

        Trading tradeHistory = Trading.builder()
                .stockId(buyStockDto.getStockId())
                .accountId(buyStockDto.getAccountId())
                .price(buyStockDto.getPrice())
                .quantity(buyStockDto.getQuantity())
                .type(TradeType.BUY)
                .tradeAt(LocalDateTime.now())
                .isConcluded(false)
                .build();
        tradeHistoryRepository.save(tradeHistory);

        // 신청한 매수가가 현재가보다 같거나 높으면 체결
        if (stock.getCurrentPrice() <= buyStockDto.getPrice()) {
            account.setBalance(account.getBalance() - totalCost);
            Optional<StockBalance> optionalStockBalance =
                stockBalanceRepository.findByAccountIdAndStockId(buyStockDto.getAccountId(), buyStockDto.getStockId());
            StockBalance stockBalance;

            if (optionalStockBalance.isPresent()) {
                stockBalance = optionalStockBalance.get();

                // 평단가 변경
                stockBalance.setAvgPrice((stockBalance.getAvgPrice() * stockBalance.getQuantity() + totalCost) /
                                            (stockBalance.getQuantity() + buyStockDto.getQuantity()));
                stockBalance.setQuantity(stockBalance.getQuantity() + buyStockDto.getQuantity());

            } else {
                stockBalance = StockBalance.builder()
                    .accountId(buyStockDto.getAccountId())
                    .stockId(buyStockDto.getStockId())
                    .avgPrice(buyStockDto.getPrice())
                    .quantity(buyStockDto.getQuantity())
                    .build();
                stockBalanceRepository.save(stockBalance);
            }
            tradeHistory.setConcluded(true);
        }
        return tradeHistory.isConcluded();
    }

    /**
     * 주식 매도
     * @param sellStockDto - 매도 dto (계좌 ID, 주식 ID, 매수희망가, 매수수량)
     * @return 주문 체결 여부
     */
    @Transactional
    public Boolean sellStock(TradeStockDto sellStockDto) {
        Account account = accountService.getAccountById(sellStockDto.getAccountId());
        Stock stock = getStockById(sellStockDto.getStockId());
        StockBalance stockBalance = getStockBalanceByAccountIdAndStockId(sellStockDto.getAccountId(),sellStockDto.getStockId());

        if (stockBalance.getQuantity() < sellStockDto.getQuantity())
            throw new CustomException(ErrorCode.INSUFFICIENT_STOCK);

        Trading tradeHistory = Trading.builder()
                .stockId(sellStockDto.getStockId())
                .accountId(sellStockDto.getAccountId())
                .price(sellStockDto.getPrice())
                .quantity(sellStockDto.getQuantity())
                .type(TradeType.SELL)
                .tradeAt(LocalDateTime.now())
                .isConcluded(false)
                .build();
        tradeHistoryRepository.save(tradeHistory);

        // 판매하고자 하는 금액이 현재가보다 같거나 낮으면 판매완료
        if (stock.getCurrentPrice() >= sellStockDto.getPrice()) {
            account.setBalance(account.getBalance() + calculateTotalPrice(sellStockDto) );  // 잔액 증액
            stockBalance.setQuantity(stockBalance.getQuantity() - sellStockDto.getQuantity());  // 주식 수량 증가
            tradeHistory.setConcluded(true); // 주문 체결
        }

        quantityCheck(stockBalance);
        return tradeHistory.isConcluded();
    }

    /**
     * 주식 잔고 이체
     * @param transferStockDto - 주식 잔고 이체 dto(발신자 계좌 ID,수신자 계좌 ID 주식 ID, 매수희망가, 매수수량, 거래일시)
     * @return 이체 결과
     */
    @Transactional
    public Boolean transferStock(TransferStockDto transferStockDto){
        StockBalance senderStockBalance = getStockBalanceByAccountIdAndStockId(transferStockDto.getSenderAccountId(), transferStockDto.getStockId());
        Account senderAccount = accountService.getAccountById(transferStockDto.getSenderAccountId());

        Long senderId = senderAccount.getId();
        Long receiverId = accountService.getAccountById(transferStockDto.getReceiverAccountId()).getUserId();

        // 본인 계좌에 이체할 경우 수수료 X
        double transferFee = senderId.equals(receiverId) ?
                calculateTransferFee(transferStockDto.getStockId() , transferStockDto.getQuantity()) : 0 ;

        if (senderAccount.getBalance() < transferFee)
            throw new CustomException(ErrorCode.EXCEED_BALANCE);

        if (senderStockBalance.getQuantity() < transferStockDto.getQuantity()) {
            throw new CustomException(ErrorCode.INSUFFICIENT_STOCK);
        }

        senderStockBalance.setQuantity(senderStockBalance.getQuantity() - transferStockDto.getQuantity());

        // 수수료 차감
        TransactionDto withdrawalDto = TransactionDto.builder()
                .accountId(transferStockDto.getSenderAccountId())
                .amount(transferFee)
                .name("System")
                .memo("주식 이체 수수료 출금")
                .transactedAt(LocalDateTime.now())
                .build();
        transactionService.withdrawal(withdrawalDto);

        quantityCheck(senderStockBalance);

        // 수신계좌
        Optional<StockBalance> optionalReceiverStockBalance =
                stockBalanceRepository.findByAccountIdAndStockId(transferStockDto.getReceiverAccountId(), transferStockDto.getStockId());
        StockBalance receiverStockBalance;

        if (optionalReceiverStockBalance.isPresent()) {
            receiverStockBalance = optionalReceiverStockBalance.get();

            double totalCost = transferStockDto.getPrice() * transferStockDto.getQuantity();

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
                .isConcluded(true)
                .build();

        Trading receiverTradeHistory = Trading.builder()
                .stockId(transferStockDto.getStockId())
                .accountId(transferStockDto.getReceiverAccountId())
                .price(transferStockDto.getPrice())
                .quantity(transferStockDto.getQuantity())
                .type(TradeType.TRANSFER)
                .tradeAt(LocalDateTime.now())
                .isConcluded(true)
                .build();

        tradeHistoryRepository.save(senderTradeHistory);
        tradeHistoryRepository.save(receiverTradeHistory);
        return true;
    }

    /**
     * 이체 수수료 계산
     * @param stockId - 주식 ID
     * @param quantity - 수량
     * @return 수수료
     */
    private double calculateTransferFee(Long stockId , int quantity){
        Stock stock = getStockById(stockId);

        return stock.getCurrentPrice() * quantity * TRANSFER_FEE_RATE.getRate();
    }

    /**
     * 거래내역 조회
     * @param accountId - 계좌 ID
     * @return 거래내역
     */
    public List<Trading> getTradeHistory(Long accountId) {
        return tradeHistoryRepository.findByAccountIdOrderByTradeAtDesc(accountId);
    }

    /**
     * 거래내역 조회 + 날짜 필터링
     * @param accountId - 계좌 ID
     * @param dateDto - 조회할 날짜, 월
     * @return 거래내역
     */
    public List<Trading> getTradeHistory(DateDto dateDto, Long accountId) {
        LocalDate requestedDate = LocalDate.of(dateDto.getYear(), dateDto.getMonth(), 1);

        // 요청된 날짜가 오늘 이후인지
        if (requestedDate.isAfter(LocalDate.now()))
            throw new CustomException(ErrorCode.INVALID_REQUEST_DATE);

        LocalDateTime startDate = LocalDateTime.of(dateDto.getYear(), dateDto.getMonth(), 1, 0, 0);
        LocalDateTime endDate = startDate.plusMonths(1).minusNanos(1); // 월의 마지막 일시

        return tradeHistoryRepository.findByAccountIdAndTradeAtBetweenOrderByTradeAtDesc(accountId, startDate, endDate);
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
        stockBalances.sort(Comparator.comparingDouble(StockBalance::getProfitNLoss).reversed());
        return stockBalances;
    }

    /**
     * 평가손익 계산
     * @param stockBalance - 주식 잔고
     * @return 평가손익
     */
    private Double calculateProfitLoss(StockBalance stockBalance){
        Stock stock = getStockById(stockBalance.getStockId());
        return (stock.getCurrentPrice() - stockBalance.getAvgPrice()) * stockBalance.getQuantity();
    }
}
