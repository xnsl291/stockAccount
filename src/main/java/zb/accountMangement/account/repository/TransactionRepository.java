package zb.accountMangement.account.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zb.accountMangement.account.model.TransactionType;
import zb.accountMangement.account.model.entity.Transaction;

import java.time.LocalDateTime;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    @Query("select t from Transaction t where t.accountId = :accountId"
            + " and (:type is null or t.type = :type)"
            + " and (:from is null or t.transactedAt >= :from)"
            + " and (:to is null or t.transactedAt < :to)")
    Page<Transaction> search(@Param("accountId") Long accountId, @Param("type") TransactionType type,
                             @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);
}
