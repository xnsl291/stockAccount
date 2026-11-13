package zb.accountMangement.account.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import zb.accountMangement.account.model.TransactionType;

import javax.persistence.*;
import java.time.LocalDateTime;

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

    private Double amount;

    private String memo;

    private Double balance;  // 거래 후 잔액

    @CreatedDate
    private LocalDateTime transactedAt;
}
