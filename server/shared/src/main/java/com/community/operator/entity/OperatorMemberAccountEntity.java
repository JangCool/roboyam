package com.community.operator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 운영자 회원 계정 연결 — {@code operator_member_accounts}(shared V141). 운영자 하나에 회원 하나(운영자 PK · 회원 유일). 만들기는 {@link #link}(저장소
 * {@code OperatorMemberAccountStore} 가 넣고 바로 내보낸다), 끊기는 저장소의 {@code DELETE … RETURNING}(native). 고치지 않는다. 힘(정책 6)은 요청마다 운영자 상태 + 회원 상태로
 * 판정한다 — 표에 「켜짐」 칸이 없다.
 */
@Entity
@Table(name = "operator_member_accounts")
public class OperatorMemberAccountEntity {

    @Id
    private Long operatorId;

    private Long userId;
    private Instant linkedAt;
    /** 이은 운영자(그 운영자가 지워지면 비운다) */
    private Long linkedBy;

    protected OperatorMemberAccountEntity() {
    }

    /** 새 연결 — 자격 검사·회원 행 잠금은 부르는 서비스가 끝냈다 */
    public static OperatorMemberAccountEntity link(long operatorId, long userId, Instant linkedAt, Long linkedBy) {
        OperatorMemberAccountEntity m = new OperatorMemberAccountEntity();
        m.operatorId = operatorId;
        m.userId = userId;
        m.linkedAt = linkedAt;
        m.linkedBy = linkedBy;
        return m;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public Long getLinkedBy() {
        return linkedBy;
    }
}
