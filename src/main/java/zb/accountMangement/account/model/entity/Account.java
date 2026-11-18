package zb.accountMangement.account.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import zb.accountMangement.account.model.AccountStatus;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
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

    @Setter
    private String nickname;

    @Setter
    private String password;

    private int passwordFailCount;

    @Column(unique = true)
    private String accountNumber;

    // 잔액은 거래 기록과 함께 바꿔야 하므로 TransactionService만 이 setter를 부름
    @Setter
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

    /**
     * 미체결 매수 주문에 돈을 묶음 - 묶인 금액이 잔액을 넘으면 거절함
     * @param amount - 묶을 금액
     */
    public void hold(long amount) {
        requirePositive(amount);
        // 더하기 전에 쓸 수 있는 잔액과 비교해, 아주 큰 금액도 long 넘침 없이 같은 오류로 거절함
        if (amount > getAvailableBalance())
            throw new CustomException(ErrorCode.EXCEED_BALANCE);
        changeHeldAmount(heldAmount + amount);
    }

    /**
     * 체결되거나 취소된 매수 주문이 묶어 둔 돈을 풂
     * @param amount - 풀 금액
     */
    public void releaseHold(long amount) {
        requirePositive(amount);
        changeHeldAmount(Math.subtractExact(heldAmount, amount));
    }

    // 음수를 받으면 묶기가 풀기로, 풀기가 묶기로 뒤집힘. 호출하는 쪽이 이미 검증한 양수만 넘기므로 들어오면 코드 오류임
    private static void requirePositive(long amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("묶거나 풀 금액은 0보다 커야 함: " + amount);
    }

    // 묶인 금액은 0 이상, 잔액 이하여야 함. 0 아래로 내려가면 묶고 푼 금액의 계산이 어긋난 것이라 요청 오류가 아니라 서버 오류로 봄
    private void changeHeldAmount(long heldAmountAfter) {
        if (heldAmountAfter < 0)
            throw new IllegalStateException("묶인 금액은 0보다 작아질 수 없음: " + heldAmountAfter);
        if (heldAmountAfter > balance)
            throw new CustomException(ErrorCode.EXCEED_BALANCE);
        heldAmount = heldAmountAfter;
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

    public void unlock() {
        passwordFailCount = 0;
        status = AccountStatus.EXISTS;
    }

    public void close() {
        status = AccountStatus.DELETED;
        deletedAt = LocalDateTime.now();
    }
}
