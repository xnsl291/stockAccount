package zb.accountMangement.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zb.accountMangement.account.model.entity.SystemEntry;

@Repository
public interface SystemEntryRepository extends JpaRepository<SystemEntry, Long> {
}
