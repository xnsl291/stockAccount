package zb.accountMangement.idempotency.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 한 번 처리한 요청과 그 결과. 같은 회원이 같은 키로 다시 보낸 요청을 두 번 실행하지 않기 위해 남김
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "idempotency_key"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    public static final int KEY_MAX_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "idempotency_key", nullable = false, length = KEY_MAX_LENGTH)
    private String idempotencyKey;

    private String request; // 같은 키를 다른 요청에 다시 쓰는지 가려내기 위한 요청 내용

    private String response;

    @CreatedDate
    private LocalDateTime createdAt;
}
