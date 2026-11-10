package com.community.aimember.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * AI 회원 모델 변경 이력 한 줄 — {@code ai_member_model_history}(shared V183). 계정을 만들 때 첫 줄, 제공사·모델이 바뀔 때만 새 줄. 고치지 않는다.
 */
@Entity
@Table(name = "ai_member_model_history")
public class AiMemberModelHistoryEntity {

    @Id
    private Long id;

    private Long aiMemberId;

    @Enumerated(EnumType.STRING)
    private AiProvider provider;

    private String model;
    private Instant since;
    private Long changedBy;

    protected AiMemberModelHistoryEntity() {
    }

    public static AiMemberModelHistoryEntity of(long id, long aiMemberId, AiProvider provider, String model, Instant since, Long changedBy) {
        AiMemberModelHistoryEntity e = new AiMemberModelHistoryEntity();
        e.id = id;
        e.aiMemberId = aiMemberId;
        e.provider = provider;
        e.model = model;
        e.since = since;
        e.changedBy = changedBy;
        return e;
    }

    public Long getId() {
        return id;
    }

    public Long getAiMemberId() {
        return aiMemberId;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Instant getSince() {
        return since;
    }

    public Long getChangedBy() {
        return changedBy;
    }
}
