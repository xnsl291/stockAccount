package zb.accountMangement.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zb.accountMangement.account.model.entity.IdempotencyRecord;

import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);
}
