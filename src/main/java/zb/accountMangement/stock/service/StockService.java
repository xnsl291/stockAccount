package zb.accountMangement.stock.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
import zb.accountMangement.stock.dto.RegisterStockDto;
import zb.accountMangement.stock.dto.StockBalanceResponse;
import zb.accountMangement.stock.dto.TransferStockDto;
import zb.accountMangement.stock.repository.StockBalanceRepository;
import zb.accountMangement.stock.repository.StockRepository;
import zb.accountMangement.stock.repository.TradingRepository;
import zb.accountMangement.stock.model.TradeType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final OrderService orderService;

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
     * 종목 등록
     * @param registerStockDto - 종목 dto (종목코드, 종목명, 현재가)
     * @return 등록된 종목
     */
    @Transactional
    public Stock registerStock(RegisterStockDto registerStockDto) {
        if (stockRepository.existsByCode(registerStockDto.getCode()))
            throw new CustomException(ErrorCode.DUPLICATED_STOCK_CODE);
        return stockRepository.save(Stock.builder()
                .code(registerStockDto.getCode())
                .name(registerStockDto.getName())
                .currentPrice(registerStockDto.getCurrentPrice())
                .build());
    }

    /**
     * 시세 갱신 - 새 시세로 체결되는 미체결 주문을 함께 체결함
     * @param stockId - 주식 ID
     * @param price - 새 시세
     * @return 시세가 바뀐 종목
     */
    // 시세를 먼저 커밋해야 주문마다 따로 여는 체결 트랜잭션이 새 시세를 읽으므로, 전체를 한 트랜잭션으로 묶지 않음
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Stock updatePrice(Long stockId, long price) {
        Stock stock = getStockById(stockId);
        stock.setCurrentPrice(price);
        stockRepository.save(stock);
        orderService.matchPendingOrders(stockId);
        return stock;
    }

    /**
     * 주식 잔고 이체 - 받는 쪽은 보내는 쪽의 평균단가를 그대로 이어받음
     * @param transferStockDto - 주식 잔고 이체 dto (발신자 계좌 ID, 수신자 계좌 ID, 주식 ID, 수량)
     * @return "이체완료"
     */
    @Transactional
    public String transferStock(TransferStockDto transferStockDto){
        Long senderAccountId = transferStockDto.getSenderAccountId();
        Long receiverAccountId = transferStockDto.getReceiverAccountId();
        int quantity = transferStockDto.getQuantity();
        if (senderAccountId.equals(receiverAccountId))
            throw new CustomException(ErrorCode.SAME_ACCOUNT_TRANSFER);

        // 송금과 같은 순서(ID 오름차순)로 잠가야 동시에 실행돼도 교착 상태에 빠지지 않음.
        // 보유 주식은 계좌를 잠근 뒤에 읽어야 앞선 거래가 바꾼 수량이 보임
        Account first = accountService.getAccountForUpdate(Math.min(senderAccountId, receiverAccountId));
        Account second = accountService.getAccountForUpdate(Math.max(senderAccountId, receiverAccountId));
        Account sender = first.getId().equals(senderAccountId) ? first : second;
        Account receiver = first.getId().equals(senderAccountId) ? second : first;
        if (!sender.isExistsAccount() || !receiver.isExistsAccount())
            throw new CustomException(ErrorCode.INVALID_ACCOUNT);

        Stock stock = getStockById(transferStockDto.getStockId());
        StockBalance senderStockBalance = stockBalanceRepository.findByAccountIdAndStockId(senderAccountId, stock.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.NO_STOCK_BALANCE));
        if (senderStockBalance.getAvailableQuantity() < quantity)
            throw new CustomException(ErrorCode.INSUFFICIENT_STOCK);

        // 수수료는 다른 회원에게 보낼 때만 받음. 같은 회원의 계좌끼리 옮기는 것은 무료
        if (!sender.getMemberId().equals(receiver.getMemberId())) {
            long transferFee = TRANSFER_FEE_RATE.of(Math.multiplyExact(stock.getCurrentPrice(), quantity));
            if (transferFee > 0)
                transactionService.changeBalance(senderAccountId, TransactionType.FEE, transferFee, "주식 이체 수수료", stock.getName());
        }

        long avgPrice = senderStockBalance.getAvgPrice();
        senderStockBalance.setQuantity(senderStockBalance.getQuantity() - quantity);
        if (senderStockBalance.getQuantity() == 0)
            stockBalanceRepository.delete(senderStockBalance);

        StockBalance receiverStockBalance = stockBalanceRepository.findByAccountIdAndStockId(receiverAccountId, stock.getId())
                .orElseGet(() -> StockBalance.builder().accountId(receiverAccountId).stockId(stock.getId()).build());
        receiverStockBalance.add(quantity, avgPrice);
        stockBalanceRepository.save(receiverStockBalance);

        tradeHistoryRepository.save(transferRecord(senderAccountId, stock.getId(), avgPrice, -quantity));
        tradeHistoryRepository.save(transferRecord(receiverAccountId, stock.getId(), avgPrice, quantity));
        return "이체완료";
    }

    private Trading transferRecord(Long accountId, Long stockId, long avgPrice, int signedQuantity) {
        Trading record = Trading.builder()
                .accountId(accountId)
                .stockId(stockId)
                .price(avgPrice)
                .quantity(signedQuantity)
                .type(TradeType.TRANSFER)
                .build();
        record.fill(avgPrice);
        return record;
    }

    /**
     * 계좌 잔고 조회 - 평가손익순으로 정렬
     * @param accountId - 계좌  ID
     * @return 현재 보유중인 주식 종목 리스트 (종목 정보와 현재가 기준 평가금액·평가손익 포함)
     */
    public List<StockBalanceResponse> getStockBalance(Long accountId) {
        List<StockBalance> stockBalances = stockBalanceRepository.findByAccountId(accountId);
        // 종목을 보유 건마다 따로 읽지 않고 한 번에 읽어, 보유 종목 수와 상관없이 쿼리 수가 같게 함
        Map<Long, Stock> stocks = getStocksById(stockBalances.stream().map(StockBalance::getStockId).collect(Collectors.toSet()));
        return stockBalances.stream()
                .map(stockBalance -> StockBalanceResponse.of(stockBalance, stocks.get(stockBalance.getStockId())))
                .sorted(Comparator.comparingLong(StockBalanceResponse::getProfitNLoss).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 여러 종목을 쿼리 한 번으로 읽음
     * @param stockIds - 주식 ID 목록
     * @return 주식 ID별 종목
     */
    public Map<Long, Stock> getStocksById(Set<Long> stockIds) {
        return stockRepository.findAllById(stockIds).stream()
                .collect(Collectors.toMap(Stock::getId, Function.identity()));
    }
}
