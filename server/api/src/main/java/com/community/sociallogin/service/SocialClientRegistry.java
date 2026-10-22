package com.community.sociallogin.service;

import com.community.social.entity.SocialProvider;
import com.community.social.entity.SocialProviderSettingEntity;
import com.community.social.repository.master.SocialProviderSettingRepository;
import com.community.sociallogin.service.SocialProviderAdapter.ClientCredentials;
import com.community.zconfiguration.crypto.SecretEncryptor;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자가 등록한 소셜 로그인 키({@code social_provider_settings}) 중 <b>지금 쓸 수 있는 것</b> — 켜 두었고, 클라이언트 ID·시크릿 암호문이 있고, 암호화 키가 설정돼 있다.
 * 시크릿은 공급자와 코드를 바꿀 때만 복호화하고({@link #credentials}) 로그·응답·예외에 싣지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class SocialClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(SocialClientRegistry.class);

    private final SocialProviderSettingRepository settings;
    private final SecretEncryptor encryptor;

    public SocialClientRegistry(SocialProviderSettingRepository settings, SecretEncryptor encryptor) {
        this.settings = settings;
        this.encryptor = encryptor;
    }

    /** 쓸 수 있으면 그 공급자의 클라이언트 ID(공개값) */
    public Optional<String> clientId(SocialProvider provider) {
        return usable(provider).map(SocialProviderSettingEntity::getClientId);
    }

    /**
     * 쓸 수 있으면 클라이언트 ID 와 복호화한 시크릿. 복호화하지 못하면(키가 바뀜·암호문이 깨짐) 비어 있다(원인은 클래스 이름만 로그에).
     */
    public Optional<ClientCredentials> credentials(SocialProvider provider) {
        return usable(provider).flatMap(setting -> {
            try {
                return Optional.of(new ClientCredentials(setting.getClientId(), encryptor.decrypt(setting.getClientSecretEnc())));
            } catch (RuntimeException e) {
                log.error("소셜 로그인 시크릿을 복호화하지 못했다 provider={} 원인={} — 설정 화면에서 시크릿을 다시 입력해 주세요", provider, e.getClass().getSimpleName());
                return Optional.empty();
            }
        });
    }

    private Optional<SocialProviderSettingEntity> usable(SocialProvider provider) {
        if (!encryptor.isConfigured()) {
            return Optional.empty();
        }
        return settings.findById(provider).filter(SocialProviderSettingEntity::isEnabled).filter(SocialProviderSettingEntity::hasKeys);
    }
}
