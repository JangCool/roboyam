package com.community.sanction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Generated;

/**
 * 사이트 제재 한 줄 — shared V136 의 {@code member_sanctions}. 만들기는 {@link #issue}(번호 {@code sanction_no} 는 DB 시퀀스가 매기고 넣은 뒤 읽어 온다).
 * 해제·무효·만료·확인은 저장소의 조건부 UPDATE(회원 행 잠금 아래에서 한 번만 이긴다)가 한다 — 상태 칸을 엔티티로 고치지 않는다.
 * 읽기는 저장소가 회원·운영자·이의제기와 함께 한 줄 모양({@code MemberSanctionStore.SanctionRow})으로 꺼낸다.
 */
@Entity
@Table(name = "member_sanctions")
public class MemberSanctionEntity {

    @Id
    private Long id;

    /** DB 시퀀스 기본값({@code member_sanction_no_seq}) — 넣을 때 보내지 않고 넣은 뒤 읽어 온다 */
    @Generated
    @Column(insertable = false, updatable = false)
    private Long sanctionNo;

    private Long userId;
    @Enumerated(EnumType.STRING)
    private SanctionKind kind;
    @Enumerated(EnumType.STRING)
    private SanctionStatus status;
    private Integer warningNo;
    private boolean skipped;
    private String reason;
    private Instant startsAt;
    private Instant endsAt;
    private Long issuedBy;
    private Long reportCaseId;
    private Instant acknowledgedAt;
    private Long liftedBy;
    private Instant liftedAt;
    private String liftReason;
    private Instant voidedAt;
    private Instant endedAt;
    private Instant createdAt;
    private Instant updatedAt;
    /** 익명 글·댓글 작성자로 건 제재(V184) */
    private boolean sealed;

    protected MemberSanctionEntity() {
    }

    /** 「이 글/댓글 작성자」로 건 제재(V184 — anonymous-board-contract 정책 9) — 목록·결과·감사에서 대상이 봉인으로 보인다. {@code persist} 전에 부른다 */
    public void seal() {
        this.sealed = true;
    }

    public boolean isSealed() {
        return sealed;
    }

    /** 새 제재(진행 중). 단계·사유 검사는 부르는 서비스가 끝냈다 */
    public static MemberSanctionEntity issue(long id, long userId, SanctionKind kind, Integer warningNo, boolean skipped, String reason, Instant startsAt,
                                             Instant endsAt, long issuedBy, Long reportCaseId, Instant now) {
        MemberSanctionEntity s = new MemberSanctionEntity();
        s.id = id;
        s.userId = userId;
        s.kind = kind;
        s.status = SanctionStatus.ACTIVE;
        s.warningNo = warningNo;
        s.skipped = skipped;
        s.reason = reason;
        s.startsAt = startsAt;
        s.endsAt = endsAt;
        s.issuedBy = issuedBy;
        s.reportCaseId = reportCaseId;
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }

    /** 넣고 내보낸 뒤의 번호 */
    public Long getSanctionNo() {
        return sanctionNo;
    }
}
