package zb.accountMangement.account.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import zb.accountMangement.account.model.SystemAccount;
import zb.accountMangement.account.model.TransactionType;

import java.time.LocalDateTime;

/**
 * 회원 거래 한 줄의 반대편 기록. 한 번 적으면 고치지 않음.
 * 반대 금액(amount)은 DB가 거래 금액에서 계산하므로 여기서 적지 않음
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long transactionId;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    private long transactionAmount;

    @Enumerated(EnumType.STRING)
    private SystemAccount systemAccount;

    @CreatedDate
    private LocalDateTime createdAt;

    public SystemEntry(Transaction transaction, SystemAccount systemAccount) {
        this.transactionId = transaction.getId();
        this.transactionType = transaction.getType();
        this.transactionAmount = transaction.getAmount();
        this.systemAccount = systemAccount;
    }
}
