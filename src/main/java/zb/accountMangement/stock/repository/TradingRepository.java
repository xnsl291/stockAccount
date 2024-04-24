package zb.accountMangement.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zb.accountMangement.stock.model.entity.Trading;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradingRepository extends JpaRepository<Trading, Long> {
    List<Trading> findByAccountIdOrderByTradeAtDesc(Long accountId);
    List<Trading> findByAccountIdAndTradeAtBetweenOrderByTradeAtDesc(Long accountId, LocalDateTime startDate, LocalDateTime endDate);
}
