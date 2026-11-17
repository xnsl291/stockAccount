package zb.accountMangement.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zb.accountMangement.stock.model.OrderStatus;
import zb.accountMangement.stock.model.entity.Trading;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradingRepository extends JpaRepository<Trading, Long> {
    List<Trading> findByStockIdAndStatusOrderByIdAsc(Long stockId, OrderStatus status);

    @Query("select t from Trading t where t.accountId = :accountId"
            + " and (:status is null or t.status = :status)"
            + " and (:from is null or t.createdAt >= :from)"
            + " and (:to is null or t.createdAt < :to)"
            + " order by t.id desc")
    List<Trading> search(@Param("accountId") Long accountId, @Param("status") OrderStatus status,
                         @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
