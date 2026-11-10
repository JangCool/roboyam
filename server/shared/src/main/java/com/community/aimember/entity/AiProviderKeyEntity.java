package com.community.aimember.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 제공사 API 키 — {@code ai_provider_keys}(shared V183, 정책 12). 값은 {@code SecretEncryptor}({@code ROBOYAM_CRYPTO_KEY}) 암호문만 둔다.
 * {@link #toString()} 은 기본(값 없음) 그대로 — 암호문도 로그에 싣지 않는다.
 */
@Entity
@Table(name = "ai_provider_keys")
public class AiProviderKeyEntity {

    @Id
    @Enumerated(EnumType.STRING)
    private AiProvider provider;

    private String keyCipher;
    private Instant updatedAt;
    private Long updatedBy;

    protected AiProviderKeyEntity() {
    }

    public static AiProviderKeyEntity create(AiProvider provider, String cipher, long operatorId, Instant now) {
        AiProviderKeyEntity e = new AiProviderKeyEntity();
        e.provider = provider;
        e.change(cipher, operatorId, now);
        return e;
    }

    public void change(String cipher, long operatorId, Instant now) {
        this.keyCipher = cipher;
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public AiProvider getProvider() {
        return provider;
    }

    /** 암호문 — 응답·로그에 싣지 않는다 */
    public String getKeyCipher() {
        return keyCipher;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
