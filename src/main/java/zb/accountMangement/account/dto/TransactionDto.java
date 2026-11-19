package zb.accountMangement.account.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import zb.accountMangement.account.model.TransactionType;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class TransactionDto {

    private Long accountId;

    private TransactionType type;

    private String name;

    private long amount; // 잔액이 늘면 양수, 줄면 음수

    private long balanceAfter; // 거래 후 잔액

    private String memo;

    private LocalDateTime transactedAt;
}
