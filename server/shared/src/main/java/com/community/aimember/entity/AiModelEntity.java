package com.community.aimember.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 고를 수 있는 모델 한 줄 — {@code ai_models}(shared V189). 옛 배포 설정 {@code irobit.ai-member.models.*} 를 대신하는 정본이다.
 * {@link #isEnabled()} 인 모델만 계정에 고를 수 있고 실행기가 부른다. {@link #isDefault()} 는 제공사당 하나(켜진 모델만 — DB 검사·부분 유일 색인).
 * 가격은 100만 토큰당 USD 참고값(없으면 null).
 */
@Entity
@Table(name = "ai_models")
public class AiModelEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private AiProvider provider;

    private String modelId;
    private String label;
    private BigDecimal priceInput;
    private BigDecimal priceOutput;
    private boolean freeTier;
    private boolean enabled;

    @Column(name = "is_default")
    private boolean defaultModel;

    private int sortOrder;
    private Instant createdAt;
    private Long createdBy;
    private Instant updatedAt;
    private Long updatedBy;

    protected AiModelEntity() {
    }

    /** 관리자가 직접 더한 모델 — 켜진 채로, 기본이 아니다 */
    public static AiModelEntity added(long id, AiProvider provider, String modelId, String label, BigDecimal priceInput, BigDecimal priceOutput, boolean freeTier,
                                      int sortOrder, long operatorId, Instant now) {
        AiModelEntity e = new AiModelEntity();
        e.id = id;
        e.provider = provider;
        e.modelId = modelId;
        e.label = label;
        e.priceInput = priceInput;
        e.priceOutput = priceOutput;
        e.freeTier = freeTier;
        e.enabled = true;
        e.defaultModel = false;
        e.sortOrder = sortOrder;
        e.createdAt = now;
        e.createdBy = operatorId;
        e.updatedAt = now;
        e.updatedBy = operatorId;
        return e;
    }

    /** 사용 켜기·끄기 — 기본 모델은 끌 수 없다(부르는 쪽이 먼저 막는다) */
    public void enable(boolean enabled, long operatorId, Instant now) {
        if (!enabled && defaultModel) {
            throw new IllegalStateException("기본 모델은 끌 수 없다");
        }
        this.enabled = enabled;
        touch(operatorId, now);
    }

    /** 기본 표시 — 켜진 모델만 기본이 된다 */
    public void markDefault(boolean value, long operatorId, Instant now) {
        if (value && !enabled) {
            throw new IllegalStateException("꺼진 모델은 기본이 될 수 없다");
        }
        this.defaultModel = value;
        touch(operatorId, now);
    }

    private void touch(long operatorId, Instant now) {
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public Long getId() {
        return id;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public String getModelId() {
        return modelId;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getPriceInput() {
        return priceInput;
    }

    public BigDecimal getPriceOutput() {
        return priceOutput;
    }

    public boolean isFreeTier() {
        return freeTier;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDefault() {
        return defaultModel;
    }

    public int getSortOrder() {
        return sortOrder;
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
