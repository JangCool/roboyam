package com.community.aimember.service;

import com.community.aimember.entity.AiMemberModelHistoryEntity;
import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.repository.master.AiMemberModelHistoryRepository;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.pointshop.repository.master.PointShopMemberStore;
import com.community.pointshop.service.AvatarCatalog;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 회원 계정 한 개를 DB 에 만든다 — 회원(로그인 불가 — {@link UserEntity#createAiAccount}) + 프로필(닉네임 · 공개 소개) + 확장 줄(꺼진 채) + 모델 이력 첫 줄 + 카탈로그 아바타.
 * 관리자 「AI 회원」 만들기(admin {@code AiMemberAdminService#create})와 설치 프리셋의 AI 기본 계정(api {@code AiMemberPresetInstaller})이 같이 쓴다.
 *
 * <p>검사(닉네임 형식·중복, 모델·게시판·아바타)는 부르는 쪽이 끝낸 값만 받는다. 관계·소모임 멤버·감사 기록도 부르는 쪽의 몫이다. 부르는 쪽의 트랜잭션 안에서만.
 */
@Component
public class AiMemberAccountFactory {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final AiMemberProfileRepository accounts;
    private final AiMemberModelHistoryRepository history;
    private final PointShopMemberStore avatarStore;
    private final SnowflakeIdGenerator ids;

    public AiMemberAccountFactory(UserRepository users, ProfileRepository profiles, AiMemberProfileRepository accounts, AiMemberModelHistoryRepository history,
                                  PointShopMemberStore avatarStore, SnowflakeIdGenerator ids) {
        this.users = users;
        this.profiles = profiles;
        this.accounts = accounts;
        this.history = history;
        this.avatarStore = avatarStore;
        this.ids = ids;
    }

    /**
     * 만든다. 처음에는 꺼져 있다(관리자가 켠다).
     *
     * @param avatarId   카탈로그 아바타 ID(검사를 지난 값) — 없으면 null
     * @param animated   그 아바타가 움직이는 그림인가(마지막 정지 아바타 칸을 건드리지 않는다)
     * @param operatorId 만든 운영자(설치 프리셋이면 루트 운영자)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AiMemberProfileEntity create(String nickname, String bio, AiMemberProfileEntity.Values values, String avatarId, boolean animated, long operatorId,
                                        Instant now) {
        long userId = ids.nextId();
        users.persist(UserEntity.createAiAccount(userId, now));
        ProfileEntity profile = ProfileEntity.create(userId, nickname, now);
        profile.changeBio(bio, now);
        profiles.persist(profile);
        AiMemberProfileEntity a = AiMemberProfileEntity.create(ids.nextId(), userId, values, operatorId, now);
        accounts.persist(a);
        history.persist(AiMemberModelHistoryEntity.of(ids.nextId(), a.getId(), a.getProvider(), a.getModel(), now, operatorId)); // 모델 이력 첫 줄
        accounts.flush();
        if (avatarId != null) {
            avatarStore.setAvatar(userId, AvatarCatalog.PROFILE_IMAGE_PREFIX + avatarId, animated ? null : avatarId, now);
        }
        return a;
    }
}
