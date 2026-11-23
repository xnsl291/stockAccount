package zb.accountMangement.stock.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.model.TransactionType;
import zb.accountMangement.account.service.AccountService;
import zb.accountMangement.account.service.TransactionService;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.exception.UniqueConstraint;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.stock.model.entity.StockBalance;
import zb.accountMangement.stock.model.entity.Trading;
import zb.accountMangement.stock.dto.AdminStockResponse;
import zb.accountMangement.stock.dto.PriceUpdateResponse;
import zb.accountMangement.stock.dto.RegisterStockDto;
import zb.accountMangement.stock.dto.TradingResponse;
import zb.accountMangement.stock.model.OrderStatus;
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

    public Stock getStockById(Long stockId){
        return stockRepository.findById(stockId)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_EXIST));
    }

    public Long getCurrentStockPrice(Long stockId) {
        return getStockById(stockId).getCurrentPrice();
    }

    /**
     * 최근 등록 순
     */
    public Page<AdminStockResponse> getAdminStocks(int page, int size) {
        return stockRepository.findAllWithPendingOrderCount(PageRequest.of(page, size));
    }

    public AdminStockResponse getAdminStock(Long stockId) {
        Stock stock = getStockById(stockId);
        return new AdminStockResponse(stock.getId(), stock.getCode(), stock.getName(), stock.getCurrentPrice(),
                tradeHistoryRepository.countByStockIdAndStatus(stockId, OrderStatus.PENDING));
    }

    /**
     * 먼저 들어온 순서(체결 순서와 같음)
     */
    public List<TradingResponse> getOrdersOfStock(Long stockId, OrderStatus status) {
        Stock stock = getStockById(stockId);
        return tradeHistoryRepository.findByStockIdAndStatusOrderByIdAsc(stockId, status).stream()
                .map(trading -> TradingResponse.of(trading, stock))
                .collect(Collectors.toList());
    }

    public List<Stock> searchStocks(String keyword) {
        return stockRepository.findByCodeContainingOrNameContaining(keyword, keyword);
    }

    @Transactional
    public Stock registerStock(RegisterStockDto registerStockDto) {
        if (stockRepository.existsByCode(registerStockDto.getCode()))
            throw new CustomException(ErrorCode.DUPLICATED_STOCK_CODE);
        // 위의 검사는 아직 커밋되지 않은 같은 코드의 등록을 보지 못해, 그런 등록이 먼저 커밋되면 저장이 유니크 제약에 걸림.
        // 종목코드 유니크 위반일 때만 같은 오류 코드로 바꾸고, 다른 저장 오류는 그대로 올려 보냄
        try {
            return stockRepository.saveAndFlush(Stock.builder()
                    .code(registerStockDto.getCode())
                    .name(registerStockDto.getName())
                    .currentPrice(registerStockDto.getCurrentPrice())
                    .build());
        } catch (DataIntegrityViolationException e) {
            if (UniqueConstraint.isViolated(e, "uk_stock_code"))
                throw new CustomException(ErrorCode.DUPLICATED_STOCK_CODE);
            throw e;
        }
    }

    /**
     * 새 시세로 체결되는 미체결 주문을 함께 체결함
     */
    // 시세를 먼저 커밋해야 주문마다 따로 여는 체결 트랜잭션이 새 시세를 읽으므로, 전체를 한 트랜잭션으로 묶지 않음
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PriceUpdateResponse updatePrice(Long stockId, long price) {
        Stock stock = getStockById(stockId);
        stock.setCurrentPrice(price);
        stockRepository.save(stock);
        return orderService.matchPendingOrders(stock);
    }

    /**
     * 받는 쪽은 보내는 쪽의 평균단가를 그대로 이어받음
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
        // 받는 계좌는 비밀번호를 확인한 계좌가 아니므로 거래정지여도 기존 코드로 거절함. 남의 계좌가 정지됐는지 알려 주지 않기 위함
        accountService.checkNotSuspended(sender);
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
                transactionService.changeBalance(sender, TransactionType.FEE, transferFee, "주식 이체 수수료", stock.getName());
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
     * 평가손익순으로 정렬
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

    public Map<Long, Stock> getStocksById(Set<Long> stockIds) {
        return stockRepository.findAllById(stockIds).stream()
                .collect(Collectors.toMap(Stock::getId, Function.identity()));
    }
}
