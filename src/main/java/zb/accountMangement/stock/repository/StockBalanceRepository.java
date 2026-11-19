package zb.accountMangement.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zb.accountMangement.stock.model.entity.StockBalance;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {
    Optional<StockBalance> findByAccountIdAndStockId(Long accountId, Long stockId);
    List<StockBalance> findByAccountId(Long accountId);
    // 잠그며 읽어야 트랜잭션 시작 시점의 스냅샷이 아니라 방금 커밋된 보유 내역까지 보임
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sb from StockBalance sb where sb.accountId = :accountId")
    List<StockBalance> findByAccountIdForUpdate(@Param("accountId") Long accountId);
}
