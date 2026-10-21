package com.community.auth.service;

import com.community.auth.dto.response.ProviderView;
import com.community.social.entity.SocialProvider;
import com.community.social.entity.SocialProviderSettingEntity;
import com.community.social.repository.master.SocialProviderSettingRepository;
import com.community.zconfiguration.message.Messages;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 화면에 보일 소셜 로그인 공급자. 운영자가 키를 등록한({@code social_provider_settings}) <b>켜 둔 공급자만</b> 나온다 — 꺼 뒀거나 키가 없으면 빠진다.
 * 로그인 흐름(OAuth) 자체는 이 서비스의 일이 아니다.
 */
@Service
@Transactional(readOnly = true)
public class SocialProviderService {

    /** 버튼 순서 */
    private static final List<SocialProvider> ORDER = List.of(SocialProvider.KAKAO, SocialProvider.NAVER, SocialProvider.GOOGLE);

    private final SocialProviderSettingRepository settings;
    private final Messages messages;

    public SocialProviderService(SocialProviderSettingRepository settings, Messages messages) {
        this.settings = settings;
        this.messages = messages;
    }

    public List<ProviderView> enabledProviders() {
        List<SocialProvider> enabled = settings.findByEnabledTrue().stream()
                .filter(SocialProviderSettingEntity::hasKeys)
                .map(SocialProviderSettingEntity::getProvider)
                .toList();
        return ORDER.stream()
                .filter(enabled::contains)
                .map(provider -> new ProviderView(provider.id(), messages.get(provider.displayNameKey())))
                .toList();
    }
}
