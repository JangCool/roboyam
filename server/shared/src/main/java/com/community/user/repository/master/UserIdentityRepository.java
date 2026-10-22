package com.community.user.repository.master;

import com.community.social.entity.SocialProvider;
import com.community.user.entity.UserIdentityEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;
import java.util.Optional;

public interface UserIdentityRepository extends RoboyamRepository<UserIdentityEntity, Long> {

    /** 그 회원에게 연결된 소셜 계정(연결한 순서) */
    List<UserIdentityEntity> findByUserIdOrderByCreatedAtAsc(Long userId);

    /** 그 소셜 계정(공급자 + 공급자 회원 번호)이 연결된 회원 — 유일 제약 {@code ux_user_identities_provider} 를 탄다 */
    Optional<UserIdentityEntity> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    /** 그 회원에게 연결된 그 공급자의 계정(한 공급자에 한 계정까지 — 소셜 로그인 서비스가 지킨다) */
    Optional<UserIdentityEntity> findFirstByUserIdAndProvider(Long userId, SocialProvider provider);
}
