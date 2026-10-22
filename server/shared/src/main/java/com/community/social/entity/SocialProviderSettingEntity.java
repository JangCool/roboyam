package com.community.social.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 운영자가 등록한 소셜 로그인 키(공급자마다 한 행) — V3__social_provider_settings.sql.
 *
 * <p>시크릿은 <b>암호문만</b> 들고 있다({@code v1:} 로 시작). 평문 시크릿은 어디에도 저장하지 않는다. 설치 마법사가 처음 넣고, 공개 사이트 서버의 소셜 로그인이
 * 읽는다(설정 화면의 수정은 그 기능을 만들 때 더한다).
 * {@link #toString()} 은 값을 싣지 않는다(기본 {@code Object.toString} 을 그대로 쓴다).
 */
@Entity
@Table(name = "social_provider_settings")
public class SocialProviderSettingEntity {

    @Id
    @Enumerated(EnumType.STRING)
    private SocialProvider provider;

    private String clientId;

    /** 암호문. 평문 시크릿은 어디에도 저장하지 않는다 */
    private String clientSecretEnc;

    private boolean enabled;

    /** 마지막으로 저장한 운영자(회원 ID). 설치 때는 루트 계정 */
    private Long updatedBy;

    private Instant updatedAt;

    protected SocialProviderSettingEntity() {
    }

    /**
     * 설치 때 처음 만든다.
     *
     * @param clientSecretEnc 이미 암호화한 시크릿({@code v1:…}). 평문을 넘기지 않는다
     */
    public static SocialProviderSettingEntity create(SocialProvider provider, String clientId, String clientSecretEnc, boolean enabled,
                                                     long updatedBy, Instant now) {
        SocialProviderSettingEntity setting = new SocialProviderSettingEntity();
        setting.provider = provider;
        setting.clientId = clientId;
        setting.clientSecretEnc = clientSecretEnc;
        setting.enabled = enabled;
        setting.updatedBy = updatedBy;
        setting.updatedAt = now;
        return setting;
    }

    public SocialProvider getProvider() {
        return provider;
    }

    public String getClientId() {
        return clientId;
    }

    /** 시크릿의 암호문({@code v1:…}) — 소셜 로그인이 공급자와 코드를 바꿀 때만 복호화한다. 응답·로그에 싣지 않는다 */
    public String getClientSecretEnc() {
        return clientSecretEnc;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 로그인에 쓸 수 있는 키가 있는가(클라이언트 ID 와 암호화된 시크릿이 모두 있다) — 값 자체는 내보내지 않는다 */
    public boolean hasKeys() {
        return clientId != null && !clientId.isBlank() && clientSecretEnc != null && !clientSecretEnc.isBlank();
    }
}
