package com.community.sociallogin.service;

import com.community.social.entity.SocialProvider;
import com.community.sociallogin.dto.response.MeSocialProviderView;
import com.community.sociallogin.dto.response.MeSocialResponse;
import com.community.sociallogin.exception.SocialAlreadyLinkedException;
import com.community.user.entity.UserEntity;
import com.community.user.entity.UserIdentityEntity;
import com.community.user.repository.master.UserIdentityRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.message.Messages;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 회원의 소셜 연결 관리 — 목록·연결 시작 전 확인·해제. 연결 자체는 콜백({@link SocialLoginService#callback})이 한다.
 * 대상은 늘 로그인한 본인이다(경로에 회원 ID 가 없다).
 */
@Service
@Transactional(readOnly = true)
public class MeSocialService {

    /** 화면 순서 */
    private static final List<SocialProvider> ORDER = List.of(SocialProvider.KAKAO, SocialProvider.NAVER, SocialProvider.GOOGLE);

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final SocialClientRegistry clients;
    private final SocialAccountWriter writer;
    private final Messages messages;

    public MeSocialService(UserRepository users, UserIdentityRepository identities, SocialClientRegistry clients, SocialAccountWriter writer,
                           Messages messages) {
        this.users = users;
        this.identities = identities;
        this.clients = clients;
        this.writer = writer;
        this.messages = messages;
    }

    /** 공급자 3종의 연결 상태 */
    public MeSocialResponse view(long userId) {
        UserEntity user = member(userId);
        Set<SocialProvider> linked = identities.findByUserIdOrderByCreatedAtAsc(userId).stream().map(UserIdentityEntity::getProvider)
                .collect(Collectors.toSet());
        boolean hasPassword = user.hasPassword();
        List<MeSocialProviderView> views = ORDER.stream().map(provider -> {
            boolean isLinked = linked.contains(provider);
            boolean otherMethodLeft = hasPassword || linked.size() > 1;
            return new MeSocialProviderView(provider.id(), messages.get(provider.displayNameKey()), isLinked,
                    !isLinked && clients.clientId(provider).isPresent(), isLinked && otherMethodLeft);
        }).toList();
        return new MeSocialResponse(hasPassword, views);
    }

    /**
     * 연결을 시작해도 되는가 — 이미 그 공급자가 연결돼 있으면 409. (쓸 수 있는 공급자인지는 시작이 본다)
     */
    public void assertLinkable(long userId, SocialProvider provider) {
        member(userId);
        if (identities.findFirstByUserIdAndProvider(userId, provider).isPresent()) {
            throw new SocialAlreadyLinkedException();
        }
    }

    /** 연결 해제 — 규칙은 {@link SocialAccountWriter#unlink} */
    @Transactional
    public void unlink(long userId, SocialProvider provider) {
        writer.unlink(userId, provider);
    }

    private UserEntity member(long userId) {
        return users.findById(userId).filter(u -> !u.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
    }
}
