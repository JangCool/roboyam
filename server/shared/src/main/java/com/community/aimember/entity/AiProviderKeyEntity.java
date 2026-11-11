package com.community.aimember.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 제공사 API 키 — {@code ai_provider_keys}(shared V183, 정책 12 · 상태 칸 V189). 값은 {@code SecretEncryptor}({@code ROBOYAM_CRYPTO_KEY}) 암호문만 둔다.
 * {@link #toString()} 은 기본(값 없음) 그대로 — 암호문도 로그에 싣지 않는다.
 * 상태({@link AiProviderKeyStatus})는 실행기가 제공사 거절을 보고 바꾸고({@link #pause}), 키를 다시 저장하면({@link #change}) {@code OK} 로 돌아온다.
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

    @Enumerated(EnumType.STRING)
    private AiProviderKeyStatus status = AiProviderKeyStatus.OK;

    /** 쉬게 한 제공사 메시지(가린 것 · 300자 이하) */
    private String statusDetail;
    private Instant statusAt;

    protected AiProviderKeyEntity() {
    }

    public static AiProviderKeyEntity create(AiProvider provider, String cipher, long operatorId, Instant now) {
        AiProviderKeyEntity e = new AiProviderKeyEntity();
        e.provider = provider;
        e.change(cipher, operatorId, now);
        return e;
    }

    /** 키 저장(바꾸기) — 상태는 {@code OK} 로 돌아온다 */
    public void change(String cipher, long operatorId, Instant now) {
        this.keyCipher = cipher;
        this.updatedAt = now;
        this.updatedBy = operatorId;
        if (this.status != AiProviderKeyStatus.OK) {
            this.statusAt = now;
        }
        this.status = AiProviderKeyStatus.OK;
        this.statusDetail = null;
    }

    /** 제공사가 거절해 쉬게 한다 — 상태가 바뀌었으면 true(같은 상태면 그대로 두고 false — 알림은 바뀔 때만) */
    public boolean pause(AiProviderKeyStatus next, String detail, Instant now) {
        if (next == null || next == AiProviderKeyStatus.OK || next == getStatus()) {
            return false;
        }
        this.status = next;
        this.statusDetail = detail;
        this.statusAt = now;
        return true;
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

    public AiProviderKeyStatus getStatus() {
        return status == null ? AiProviderKeyStatus.OK : status;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public Instant getStatusAt() {
        return statusAt;
    }
}
