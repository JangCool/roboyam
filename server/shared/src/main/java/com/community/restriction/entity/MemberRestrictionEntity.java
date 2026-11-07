package com.community.restriction.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 기능 제한 한 줄 — shared V84 의 {@code member_restrictions}. 만들기는 {@link #grant}, 해제는 저장소의 조건부 UPDATE(진행 중일 때만 — 한 번만 이긴다)가 한다.
 * 읽기는 저장소가 운영자 이름과 함께 한 줄 모양({@code MemberRestrictionStore.Row})으로 꺼낸다.
 */
@Entity
@Table(name = "member_restrictions")
public class MemberRestrictionEntity {

    @Id
    private Long id;

    private Long userId;
    @Enumerated(EnumType.STRING)
    private RestrictionType type;
    private String reason;
    private String adminMemo;
    private Instant startsAt;
    private Instant until;
    @Enumerated(EnumType.STRING)
    private RestrictionSource source;
    private String autoRule;
    private Long createdBy;
    private Instant createdAt;
    private Long liftedBy;
    private Instant liftedAt;
    private String liftReason;

    protected MemberRestrictionEntity() {
    }

    /** 새 제한(운영자 — {@code createdBy} 있음 · 자동 — {@code autoRule} 있음). 검사는 부르는 서비스가 끝냈다 */
    public static MemberRestrictionEntity grant(long id, long userId, RestrictionType type, String reason, String adminMemo, Instant startsAt, Instant until,
                                                RestrictionSource source, String autoRule, Long createdBy, Instant createdAt) {
        MemberRestrictionEntity r = new MemberRestrictionEntity();
        r.id = id;
        r.userId = userId;
        r.type = type;
        r.reason = reason;
        r.adminMemo = adminMemo;
        r.startsAt = startsAt;
        r.until = until;
        r.source = source;
        r.autoRule = autoRule;
        r.createdBy = createdBy;
        r.createdAt = createdAt;
        return r;
    }

    public Long getId() {
        return id;
    }
}
