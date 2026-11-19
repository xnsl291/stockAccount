package zb.accountMangement.account.dto;

import lombok.Builder;
import lombok.Getter;
import zb.accountMangement.account.model.AccountStatus;
import zb.accountMangement.account.model.entity.Account;

import java.time.LocalDateTime;

/**
 * 계좌 응답 - 비밀번호 해시와 비밀번호 틀린 횟수는 내보내지 않음
 */
@Getter
@Builder
public class AccountResponse {

    private Long id;

    private String accountNumber;

    private String nickname;

    private long balance;

    private long heldAmount; // 미체결 매수 주문에 묶인 금액

    private long availableBalance; // 출금·송금·주문에 쓸 수 있는 금액

    private AccountStatus status;

    private LocalDateTime createdAt;

    public static AccountResponse from(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .nickname(account.getNickname())
                .balance(account.getBalance())
                .heldAmount(account.getHeldAmount())
                .availableBalance(account.getAvailableBalance())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
