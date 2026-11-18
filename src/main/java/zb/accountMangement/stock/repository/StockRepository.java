package zb.accountMangement.stock.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zb.accountMangement.stock.dto.AdminStockResponse;
import zb.accountMangement.stock.model.entity.Stock;

import java.util.List;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {
    boolean existsByCode(String code);
    List<Stock> findByCodeContainingOrNameContaining(String code, String name);

    // 미체결 주문 수를 종목마다 따로 세지 않고 한 쿼리에서 묶어 셈. 최근 등록한 종목이 앞에 옴
    @Query(value = "select new zb.accountMangement.stock.dto.AdminStockResponse(s.id, s.code, s.name, s.currentPrice, count(t.id))"
            + " from Stock s left join Trading t"
            + " on t.stockId = s.id and t.status = zb.accountMangement.stock.model.OrderStatus.PENDING"
            + " group by s.id, s.code, s.name, s.currentPrice"
            + " order by s.id desc",
            countQuery = "select count(s) from Stock s")
    Page<AdminStockResponse> findAllWithPendingOrderCount(Pageable pageable);
}
