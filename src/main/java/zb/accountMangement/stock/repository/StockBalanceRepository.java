package zb.accountMangement.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zb.accountMangement.stock.model.entity.StockBalance;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {
    Optional<StockBalance> findByAccountIdAndStockId(Long accountId, Long stockId);
    List<StockBalance> findByAccountId(Long accountId);
}
