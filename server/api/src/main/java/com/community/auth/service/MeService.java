package com.community.auth.service;

import com.community.auth.dto.response.MeResponse;
import com.community.auth.dto.response.RestrictionView;
import com.community.auth.dto.response.UserView;
import com.community.business.service.BusinessEligibility;
import com.community.grade.entity.GradeEntity;
import com.community.grade.repository.master.GradeRepository;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.entity.UserIdentityEntity;
import com.community.user.entity.UserStatus;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserIdentityRepository;
import com.community.user.repository.master.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 회원의 화면용 정보({@code me}). 로그인 응답도 같은 모양이다.
 *
 * <ul>
 *   <li>{@code restriction}: 제재 표가 아직 없다 — {@code SUSPENDED}·{@code BANNED} 일 때만 {@code { status, until: null, pendingNotices: 0 }}, 그 밖에는 null.</li>
 *   <li>{@code adminUrl}: 항상 null — 운영자는 공개 사이트에 로그인하지 않는다(회원과 별도 계정, 관리자 앱에서만 일한다). 화면의 칸은 남겨 두었다.</li>
 *   <li>{@code businessStatus}: 사업자 신원 상태(business-contract) — {@code NONE}·{@code PENDING}·{@code APPROVED}·{@code REJECTED}·{@code SUSPENDED}, 재심사 중은 {@code APPROVED}.</li>
 *   <li>{@code grade}: 등급 설정의 등급 키(기본 SEEDLING·REGULAR·LOYAL·VETERAN), {@code gradeLabel}: 그 등급의 이름 — 운영자가 등급 설정에서 입력한 값이라 요청 언어와 상관없이 같다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class MeService {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final UserIdentityRepository identities;
    private final GradeRepository grades;
    private final BusinessEligibility business;

    public MeService(UserRepository users, ProfileRepository profiles, UserIdentityRepository identities, GradeRepository grades, BusinessEligibility business) {
        this.business = business;
        this.users = users;
        this.grades = grades;
        this.profiles = profiles;
        this.identities = identities;
    }

    /**
     * 그 회원의 로그인 정보. 회원이나 프로필이 없거나 탈퇴한 계정이면 비어 있다 — 부르는 쪽은 그 세션을 낡은 것으로 본다.
     */
    public Optional<MeResponse> of(long userId) {
        Optional<UserEntity> user = users.findById(userId).filter(u -> !u.isGone());
        Optional<ProfileEntity> profile = profiles.findById(userId);
        if (user.isEmpty() || profile.isEmpty()) {
            return Optional.empty();
        }
        UserEntity u = user.get();
        ProfileEntity p = profile.get();

        List<String> methods = new ArrayList<>();
        if (u.hasPassword()) {
            methods.add("EMAIL");
        }
        for (UserIdentityEntity identity : identities.findByUserIdOrderByCreatedAtAsc(userId)) {
            methods.add(identity.getProvider().name());
        }

        String gradeLabel = grades.findById(p.getGradeKey()).map(GradeEntity::getLabel).orElse(p.getGradeKey()); // 등급 행이 없을 수는 없지만(외래키) 키라도 보인다
        UserView view = new UserView(Long.toString(u.getId()), p.getNickname(), p.getGradeKey(), gradeLabel, u.getRole().name(), u.getEmail(),
                u.isEmailVerified(), List.copyOf(methods), business.memberStatusOf(userId));

        boolean restricted = u.getStatus() == UserStatus.SUSPENDED || u.getStatus() == UserStatus.BANNED;
        RestrictionView restriction = restricted ? new RestrictionView(u.getStatus().name(), null, 0) : null;

        return Optional.of(new MeResponse(true, view, restriction, null));
    }
}
