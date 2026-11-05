package com.community.auth.service;

import com.community.auth.dto.response.MeResponse;
import com.community.auth.dto.response.OperatorAccountView;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.auth.dto.response.RestrictionView;
import com.community.auth.dto.response.UserView;
import com.community.business.service.BusinessEligibility;
import com.community.grade.entity.GradeEntity;
import com.community.grade.repository.master.GradeRepository;
import com.community.sanction.service.MemberSanctionService;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.entity.UserIdentityEntity;
import com.community.user.entity.UserStatus;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserIdentityRepository;
import com.community.user.repository.master.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 회원의 화면용 정보({@code me}). 로그인 응답도 같은 모양이다.
 *
 * <ul>
 *   <li>{@code restriction}(member-sanction-contract): 읽기 전에 지난 정지를 먼저 끝낸다({@link MemberSanctionService#settle}) — 그래서 이 메서드는 쓰기 트랜잭션이다.
 *       정지·영구 정지 중이면 {@code { status, until(진행 중 기간 정지의 끝 — 영구 정지는 null), pendingNotices(확인 안 한 유효 경고 수) }}, 정상이면 확인할 경고가 있을 때만
 *       {@code { ACTIVE, null, n }}, 그 밖(정상이고 경고도 없음 · 탈퇴 진행 중)은 null.</li>
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
    private final MemberSanctionService sanctions;
    private final OperatorMemberAccountService operatorAccounts;

    public MeService(UserRepository users, ProfileRepository profiles, UserIdentityRepository identities, GradeRepository grades, BusinessEligibility business,
                     MemberSanctionService sanctions, OperatorMemberAccountService operatorAccounts) {
        this.operatorAccounts = operatorAccounts;
        this.sanctions = sanctions;
        this.business = business;
        this.users = users;
        this.grades = grades;
        this.profiles = profiles;
        this.identities = identities;
    }

    /**
     * 그 회원의 로그인 정보. 회원이나 프로필이 없거나 탈퇴한 계정이면 비어 있다 — 부르는 쪽은 그 세션을 낡은 것으로 본다.
     */
    @Transactional
    public Optional<MeResponse> of(long userId) {
        sanctions.settle(userId); // 지난 정지를 먼저 끝낸다(크론이 늦어도 회원에게는 정확 — 정책 7). 할 일이 없으면 색인 읽기 한 번
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

        RestrictionView restriction = restrictionOf(u);

        // 운영자 회원 계정(힘이 살아 있을 때만 — 요청마다 판정)
        OperatorAccountView operatorAccount = operatorAccounts.linkOfUser(userId).filter(link -> link.active()).map(link -> new OperatorAccountView(link.operatorName()))
                .orElse(null);
        return Optional.of(new MeResponse(true, view, restriction, null, u.getStatus() == UserStatus.WITHDRAWING, operatorAccount));
    }

    /** 제한 요약 — 정지·영구 정지면 늘, 정상이면 확인 안 한 유효 경고가 있을 때만, 그 밖 null */
    private RestrictionView restrictionOf(UserEntity u) {
        boolean restricted = u.getStatus() == UserStatus.SUSPENDED || u.getStatus() == UserStatus.BANNED;
        if (!restricted && u.getStatus() != UserStatus.ACTIVE) {
            return null;
        }
        int pending = sanctions.pendingWarnings(u.getId());
        if (!restricted && pending == 0) {
            return null;
        }
        Instant until = u.getStatus() == UserStatus.SUSPENDED ? sanctions.runningUntil(u.getId()).orElse(null) : null;
        return new RestrictionView(u.getStatus().name(), until, pending);
    }
}
