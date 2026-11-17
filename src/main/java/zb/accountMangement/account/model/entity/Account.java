package zb.accountMangement.account.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import zb.accountMangement.account.model.AccountStatus;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    // 연속으로 이 횟수만큼 비밀번호를 틀리면 거래정지
    private static final int MAX_PASSWORD_FAILURES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private String nickname;

    @JsonIgnore
    private String password;

    private int passwordFailCount;

    @Column(unique = true)
    private String accountNumber;

    private long balance; // 원 단위

    private long heldAmount; // 미체결 매수 주문에 묶여 쓸 수 없는 금액

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AccountStatus status = AccountStatus.EXISTS;

    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    public long getAvailableBalance() {
        return balance - heldAmount;
    }

    public boolean isExistsAccount() {
        return status.equals(AccountStatus.EXISTS);
    }
    public boolean isDeletedAccount() {
        return status.equals(AccountStatus.DELETED);
    }

    public void recordPasswordFailure() {
        passwordFailCount++;
        if (passwordFailCount >= MAX_PASSWORD_FAILURES)
            status = AccountStatus.PENDING;
    }

    public void resetPasswordFailure() {
        passwordFailCount = 0;
    }

    public void close() {
        status = AccountStatus.DELETED;
        deletedAt = LocalDateTime.now();
    }
}
