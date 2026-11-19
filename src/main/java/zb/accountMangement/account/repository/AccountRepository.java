package zb.accountMangement.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zb.accountMangement.account.model.AccountStatus;
import zb.accountMangement.account.model.entity.Account;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByMemberId(Long userId);

    List<Account> findByMemberIdAndStatusNot(Long memberId, AccountStatus status);

    @Query("select a.id from Account a where a.memberId = :memberId order by a.id")
    List<Long> findIdsByMemberId(@Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    // 틀린 횟수가 읽어 둔 값 그대로이고 정상 계좌일 때만 0으로 되돌림. 그사이 틀린 요청이 들어왔으면 아무것도 바꾸지 않음
    @Modifying
    @Query("update Account a set a.passwordFailCount = 0 where a.id = :id and a.passwordFailCount = :seenCount and a.status = :status")
    int resetPasswordFailCount(@Param("id") Long id, @Param("seenCount") int seenCount, @Param("status") AccountStatus status);
}
