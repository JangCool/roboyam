package com.community.grade.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원 등급 변경 이력 한 줄 — V44__member_grade_changes.sql 의 {@code member_grade_changes}. 등급 키는 외래키가 아니라 값이다(등급이 지워져도 이력이 남는다).
 *
 * <p>ID 는 DB 가 매긴다(IDENTITY) — DB 배치(SQL)가 같은 표에 한꺼번에 넣기 때문이다. 한 회원의 승급을 하나씩 기록하는 곳은 {@link #automatic}·{@link #byOperator} 로 만들어 {@code persist()} 하고,
 * 여러 회원을 한 문장으로 바꾸는 곳은 바꾸는 문장과 같은 문장에서 직접 INSERT 한다({@code GradeRepositoryQDSL#promote}·{@code moveMembers}, {@code R__batch_15_grade_promotion.sql}).
 */
@Entity
@Table(name = "member_grade_changes")
public class MemberGradeChangeEntity {

    /** 등급 삭제로 회원이 바로 아래 등급으로 옮겨졌다 — 이력 조회가 요청 언어로 풀어 보인다({@code member.gradeChange.reason.GRADE_REMOVED}) */
    public static final String REASON_GRADE_REMOVED = "GRADE_REMOVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String fromGradeKey;
    private String toGradeKey;

    @Enumerated(EnumType.STRING)
    private GradeChangeKind kind;

    /** 운영자가 입력한 사유 */
    private String reason;

    /** 시스템이 정한 사유 코드 */
    private String reasonCode;

    /** 처리한 운영자. 자동이면 null */
    private Long actorOperatorId;

    private Instant createdAt;

    protected MemberGradeChangeEntity() {
    }

    /** 자동 승급 — 조건을 채워 시스템이 올렸다 */
    public static MemberGradeChangeEntity automatic(long userId, String fromGradeKey, String toGradeKey, Instant now) {
        MemberGradeChangeEntity change = new MemberGradeChangeEntity();
        change.userId = userId;
        change.fromGradeKey = fromGradeKey;
        change.toGradeKey = toGradeKey;
        change.kind = GradeChangeKind.AUTO;
        change.createdAt = now;
        return change;
    }

    /** 운영자가 바꿨다(수동 변경·자동 승급 재개). 사유는 운영자가 입력한 글(없으면 null) */
    public static MemberGradeChangeEntity byOperator(long userId, String fromGradeKey, String toGradeKey, GradeChangeKind kind, String reason, long operatorId, Instant now) {
        if (kind == GradeChangeKind.AUTO) {
            throw new IllegalArgumentException("운영자가 한 변경은 AUTO 가 아니다");
        }
        MemberGradeChangeEntity change = new MemberGradeChangeEntity();
        change.userId = userId;
        change.fromGradeKey = fromGradeKey;
        change.toGradeKey = toGradeKey;
        change.kind = kind;
        change.reason = reason;
        change.actorOperatorId = operatorId;
        change.createdAt = now;
        return change;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getFromGradeKey() {
        return fromGradeKey;
    }

    public String getToGradeKey() {
        return toGradeKey;
    }

    public GradeChangeKind getKind() {
        return kind;
    }

    public String getReason() {
        return reason;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public Long getActorOperatorId() {
        return actorOperatorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
