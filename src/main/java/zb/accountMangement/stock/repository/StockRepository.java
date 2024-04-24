package zb.accountMangement.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zb.accountMangement.stock.model.entity.Stock;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {
//  Optional<Member> findByPhoneNumber(String phoneNumber);

}
