package zb.accountMangement.account.model.entity;

import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import zb.accountMangement.account.model.TransactionType;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;

    private String name;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    private long amount; // 원 단위. 잔액이 늘면 양수, 줄면 음수

    private String memo;

    private long balanceAfter;  // 거래 후 잔액

    @CreatedDate
    private LocalDateTime transactedAt;

    private Long orderId; // 매매 대금·수수료를 만든 주문

    private Long stockTransferId; // 주식 이체 수수료를 만든 이체. 보낸 쪽 이체 기록의 ID

    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID cashTransferId; // 보낸 쪽과 받은 쪽 두 줄이 같은 값을 가짐

    private Long counterpartyAccountId; // 송금 상대 계좌
}
