package com.community.aimember.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI 회원 — {@code ai_member_profiles}(shared V183, ai-members-contract 정책 1). 회원({@code users}) 하나에 한 줄. 이 표에 줄이 있는 회원이 AI 회원이다.
 * 닉네임·아바타·공개 소개는 회원 프로필({@code profiles})에 있다. 관계는 양쪽 줄에 같이 적는다({@code AiMemberAdminService} 가 한 트랜잭션에서).
 */
@Entity
@Table(name = "ai_member_profiles")
public class AiMemberProfileEntity {

    @Id
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private AiProvider provider;

    private String model;

    @Enumerated(EnumType.STRING)
    private AiGender gender;

    @Enumerated(EnumType.STRING)
    private AiAgeBand ageBand;

    private String personality;
    private String tone;
    private String interests;

    @Enumerated(EnumType.STRING)
    private AiRelationKind relationKind;

    private Long partnerId;

    private LocalTime morningStart;
    private LocalTime morningEnd;
    private LocalTime afternoonStart;
    private LocalTime afternoonEnd;

    private short dailyPosts;

    /** {@code bigint[]} — 쓸 수 있는 게시판 ID */
    @JdbcTypeCode(SqlTypes.ARRAY)
    private Long[] boardIds;

    private boolean enabled;
    private Instant createdAt;
    private Long createdBy;
    private Instant updatedAt;
    private Long updatedBy;

    protected AiMemberProfileEntity() {
    }

    /** 페르소나·활동 값 한 묶음 — 검사는 부르는 서비스가 끝냈다 */
    public record Values(AiProvider provider, String model, AiGender gender, AiAgeBand ageBand, String personality, String tone, String interests,
                         LocalTime morningStart, LocalTime morningEnd, LocalTime afternoonStart, LocalTime afternoonEnd, int dailyPosts, List<Long> boardIds) {
    }

    /** 새 AI 회원 — 처음에는 꺼져 있다(관리자가 켠다) */
    public static AiMemberProfileEntity create(long id, long userId, Values v, long operatorId, Instant now) {
        AiMemberProfileEntity e = new AiMemberProfileEntity();
        e.id = id;
        e.userId = userId;
        e.enabled = false;
        e.createdAt = now;
        e.createdBy = operatorId;
        e.apply(v, operatorId, now);
        return e;
    }

    /** 페르소나·모델·활동 값 바꾸기 */
    public void apply(Values v, long operatorId, Instant now) {
        this.provider = v.provider();
        this.model = v.model();
        this.gender = v.gender();
        this.ageBand = v.ageBand();
        this.personality = v.personality();
        this.tone = v.tone();
        this.interests = v.interests();
        this.morningStart = v.morningStart();
        this.morningEnd = v.morningEnd();
        this.afternoonStart = v.afternoonStart();
        this.afternoonEnd = v.afternoonEnd();
        this.dailyPosts = (short) v.dailyPosts();
        this.boardIds = v.boardIds().toArray(Long[]::new);
        touch(operatorId, now);
    }

    /** 관계를 적는다(상대 쪽 줄도 부르는 쪽이 같이 적는다) — 상대가 null 이면 관계를 푼다 */
    public void relate(AiRelationKind kind, Long partnerId, Long operatorId, Instant now) {
        this.relationKind = partnerId == null ? null : kind;
        this.partnerId = partnerId;
        if (operatorId != null) {
            touch(operatorId, now);
        }
    }

    public void enable(boolean on, long operatorId, Instant now) {
        this.enabled = on;
        touch(operatorId, now);
    }

    private void touch(long operatorId, Instant now) {
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public AiGender getGender() {
        return gender;
    }

    public AiAgeBand getAgeBand() {
        return ageBand;
    }

    public String getPersonality() {
        return personality;
    }

    public String getTone() {
        return tone;
    }

    public String getInterests() {
        return interests;
    }

    /** 관계 종류 — 상대가 없으면 null */
    public AiRelationKind getRelationKind() {
        return partnerId == null ? null : relationKind;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public LocalTime getMorningStart() {
        return morningStart;
    }

    public LocalTime getMorningEnd() {
        return morningEnd;
    }

    public LocalTime getAfternoonStart() {
        return afternoonStart;
    }

    public LocalTime getAfternoonEnd() {
        return afternoonEnd;
    }

    public int getDailyPosts() {
        return dailyPosts;
    }

    public List<Long> getBoardIds() {
        return boardIds == null ? List.of() : Arrays.asList(boardIds.clone());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
