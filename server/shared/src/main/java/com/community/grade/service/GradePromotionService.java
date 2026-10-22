package com.community.grade.service;

import com.community.grade.dto.GradeThreshold;
import com.community.grade.dto.PromotionScope;
import com.community.grade.entity.GradeEntity;
import com.community.grade.entity.GradeManualPolicy;
import com.community.grade.entity.GradeSettingEntity;
import com.community.grade.entity.MemberGradeChangeEntity;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.grade.repository.master.MemberGradeChangeRepository;
import com.community.grade.repository.master.GradeSettingRepository;
import com.community.member.repository.master.MemberActivityRepository;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.entity.UserStatus;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.ProfileRepositoryQDSL;
import com.community.user.repository.master.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등급 자동 승급 판정 (docs/features/roles-contract.md 2단계, docs/domain/operator-permissions.md "회원 역할").
 *
 * <ul>
 *   <li><b>올리기만 한다</b> — 자동으로 내리지 않는다. 조건 두 개(가입 후 일수 · 글+댓글 수)를 <b>모두</b> 채운 가장 높은 등급으로 올린다(가정).</li>
 *   <li><b>시점</b>: 활동 수 조건은 사건(글·댓글)이 생길 때 즉시({@link #promoteOnActivity} → {@link #promoteOnEvent}) — api 의 글쓰기({@code PostWriteService#create})·
 *       댓글쓰기({@code CommentService#create})가 회원 글일 때 저장이 커밋된 뒤 별도 트랜잭션으로 부른다(승급 실패가 글쓰기를 깨지 않는다). 가입 일수 조건은 시간이 지나야
 *       채워지므로 배치. 배치는 <b>DB 배치</b>다 — 저장 프로시저 {@code batch_grade_promotion} 을 pg_cron 이 하루 한 번 부른다(기본 04:30 KST — {@code R__batch_15_grade_promotion.sql}).</li>
 *   <li><b>이중 구현 주의</b>: 같은 승급 판정이 <b>Java({@link #promoteAll}·{@link #promoteOnEvent}, {@code GradeRepositoryQDSL})와 SQL({@code R__batch_15_grade_promotion.sql})</b> 두 곳에 있다.
 *       어느 쪽을 고치면 다른 쪽도 같이 고친다(조건·대상 회원·수동 상향 옵션·활동 수 가정). 두 쪽은 같은 데이터에서 같은 결과를 내야 한다.</li>
 *   <li><b>소급</b>: 기준을 낮추거나 등급을 추가하면 이미 조건을 채운 회원도 올린다({@link #promoteAll}) — 등급 저장 서비스가 저장 직후 부른다.</li>
 *   <li><b>대상</b>: 정상(ACTIVE) 회원만. 정지 회원은 자동 승급이 멈추고(제재로 중지) 탈퇴 회원은 대상이 아니다. 수동으로 올린 회원은 등급 옵션이
 *       {@code CONTINUE_AUTO} 일 때만 포함한다.</li>
 *   <li><b>이력</b>: 올린 회원마다 등급 변경 이력({@code member_grade_changes}, kind=AUTO)을 <b>승급과 같은 문장·같은 트랜잭션</b>에서 남긴다 — 일괄({@link #promoteAll})은
 *       {@code GradeRepositoryQDSL#promote} 의 한 문장, 한 명({@link #promoteOnEvent})은 엔티티 한 줄. DB 배치(SQL)도 같은 문장 안에서 남긴다.</li>
 *   <li><b>활동 수</b>: 즉시 경로({@link #promoteOnActivity})는 실제 활동 수를 본다 — 공개 게시판에 지금 보이는 회원 글·댓글({@link MemberActivityRepository}, 회원 상세 활동 요약과
 *       같은 정의)을 다음 등급에 필요한 수까지만 센다. <b>일괄 경로({@link #promoteAll} 소급·{@link #countPromotedTo} 미리보기·DB 배치)는 여전히 활동 수를 보지 않는다</b>
 *       ({@link #KNOWN_ACTIVITY} = 0 — 활동 조건이 0 인 등급만 올린다). 매일 전체 회원을 훑는 배치·등급 저장 PUT 이 회원마다 글·댓글 조각을 세면 무겁기 때문이고, 작성 쪽에서
 *       증감하는 카운터 표가 생길 때 잇는다(후속 — 그때 {@link #activityReachable}·{@code GradeRepositoryQDSL} 의 대상 조건·SQL 배치를 함께 고친다). 그래서 활동 조건을 이미 채운
 *       회원(기준을 낮춘 경우 포함)은 다음 글·댓글을 쓸 때 올라간다.</li>
 * </ul>
 */
@Service
public class GradePromotionService {

    /** 일괄 경로(소급·미리보기·DB 배치)가 모든 회원에게 가정하는 활동 수 — 활동 수를 세지 않는다(클래스 설명). 즉시 경로는 쓰지 않는다 */
    private static final long KNOWN_ACTIVITY = 0;

    private final GradeRepositoryQDSL gradeQueries;
    private final GradeSettingRepository settings;
    private final ProfileRepository profiles;
    private final ProfileRepositoryQDSL profileQueries;
    private final UserRepository users;
    private final MemberGradeChangeRepository changes;
    private final MemberActivityRepository activities;

    public GradePromotionService(GradeRepositoryQDSL gradeQueries, GradeSettingRepository settings, ProfileRepository profiles, ProfileRepositoryQDSL profileQueries,
                                 UserRepository users, MemberGradeChangeRepository changes, MemberActivityRepository activities) {
        this.gradeQueries = gradeQueries;
        this.settings = settings;
        this.profiles = profiles;
        this.profileQueries = profileQueries;
        this.users = users;
        this.changes = changes;
        this.activities = activities;
    }

    /** 일괄 경로에서 활동 수 조건을 채울 수 있는 회원이 있는가 — 일괄 경로는 활동 수를 0 으로 보므로 조건이 0 인 등급만 채울 수 있다 */
    public static boolean activityReachable(int minActivity) {
        return minActivity <= KNOWN_ACTIVITY;
    }

    /**
     * 주어진 서열 순서 조건으로 모든 정상 회원을 판정해 올린다. 높은 등급부터 처리하므로 회원은 채운 가장 높은 등급으로 한 번에 올라간다(조건은 위 등급일수록 같거나 크다).
     * 소급 승급에서 일수 조건을 "다음 배치"로 미루려면 부르는 쪽이 일수를 저장 전 값 이상으로 올려 넘긴다. 하루 한 번 도는 일수 승급 배치는 SQL 프로시저
     * ({@code batch_grade_promotion}) 이 같은 규칙으로 한다 — <b>이 메서드와 {@code GradeRepositoryQDSL#promote} 를 고치면 {@code R__batch_15_grade_promotion.sql} 도 고친다.</b>
     *
     * @param ordered 서열 순서(첫 등급은 기본 등급이라 판정 대상이 아니다). 키는 모두 존재하는 등급이어야 한다
     * @return 올린 회원 수
     */
    @Transactional
    public long promoteAll(List<GradeThreshold> ordered, boolean includeManual, Instant now) {
        long promoted = 0;
        for (int i = ordered.size() - 1; i >= 1; i--) {
            GradeThreshold target = ordered.get(i);
            if (!activityReachable(target.minActivity())) {
                continue;
            }
            List<String> below = ordered.subList(0, i).stream().map(GradeThreshold::gradeKey).toList();
            promoted += gradeQueries.promote(new PromotionScope(below, cutoff(now, target.minDays()), null, includeManual), target.gradeKey(), now);
        }
        return promoted;
    }

    /**
     * 저장 전 미리보기 — 이 기준으로 판정하면 {@code ordered.get(index)} 등급이 <b>될</b> 회원 수(그보다 높은 등급이 될 회원은 세지 않는다).
     * 저장하지 않고 센다. 새 등급(키가 없는)도 센다.
     *
     * @param fromKeys 지금 이 등급(index)보다 낮은 서열에 있는 <b>기존</b> 등급 키들(새 목록의 서열 기준 — 지워질 등급은 넣지 않는다)
     */
    @Transactional(readOnly = true)
    public long countPromotedTo(List<GradeThreshold> ordered, int index, Collection<String> fromKeys, boolean includeManual, Instant now) {
        GradeThreshold target = ordered.get(index);
        if (index == 0 || !activityReachable(target.minActivity())) {
            return 0;
        }
        Instant notBefore = null; // 위 등급의 일수 조건까지 채운 회원은 그 위 등급이 된다
        if (index + 1 < ordered.size() && activityReachable(ordered.get(index + 1).minActivity())) {
            notBefore = cutoff(now, ordered.get(index + 1).minDays());
        }
        return gradeQueries.countPromotable(new PromotionScope(fromKeys, cutoff(now, target.minDays()), notBefore, includeManual));
    }

    /**
     * <b>즉시(활동 수 경로)</b> — 회원이 글·댓글을 쓴 직후 부른다(글·댓글 저장이 커밋된 뒤, 이 메서드의 트랜잭션은 따로다).
     * <ol>
     *   <li>사전 판정(잠금 없음): 정상 회원인가 · 수동 상향 옵션 · 지금 등급보다 높고 <b>일수 조건을 채운</b> 등급이 있는가. 없으면 활동 수를 세지 않고 끝낸다.</li>
     *   <li>활동 수를 그 등급들이 요구하는 가장 큰 수까지만 센다({@link MemberActivityRepository#countUpTo}) — 매 글쓰기마다 전체 수를 세지 않는다. 일수뿐인 등급만 남았으면 세지 않는다(0).</li>
     *   <li>올릴 등급이 있으면 회원 프로필 행을 잠그고({@code findWithLockByUserId} — 같은 회원의 동시 승급이 이력을 두 줄 남기지 않게) {@link #promoteOnEvent} 로 다시 판정해 올린다.</li>
     * </ol>
     *
     * @return 올라간 등급 키. 올리지 않았으면 비어 있다
     */
    @Transactional
    public Optional<String> promoteOnActivity(long userId, Instant now) {
        Optional<UserEntity> user = users.findById(userId).filter(u -> u.getStatus() == UserStatus.ACTIVE && !u.isGone());
        Optional<ProfileRepositoryQDSL.GradeState> state = profileQueries.gradeStateOf(userId); // 프로필 엔티티는 잠근 뒤에 처음 올린다(낡은 값으로 판정하지 않게)
        if (user.isEmpty() || state.isEmpty()) {
            return Optional.empty();
        }
        GradeManualPolicy policy = settings.findById(GradeSettingEntity.ROW_ID).map(GradeSettingEntity::getManualPolicy).orElse(GradeManualPolicy.CONTINUE_AUTO);
        if (state.get().gradeManual() && !policy.includesManual()) {
            return Optional.empty();
        }
        List<GradeEntity> grades = gradeQueries.findAllOrdered();
        String currentKey = state.get().gradeKey();
        int currentLevel = grades.stream().filter(g -> g.getGradeKey().equals(currentKey)).mapToInt(GradeEntity::getLevel).findFirst().orElse(1);
        long days = Duration.between(user.get().getCreatedAt(), now).toDays();
        List<GradeEntity> candidates = grades.stream().filter(g -> g.getLevel() > currentLevel && days >= g.getMinDays()).toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        long needed = candidates.stream().mapToLong(GradeEntity::getMinActivity).max().orElse(0);
        long activity = activities.countUpTo(userId, needed, now);
        if (candidates.stream().noneMatch(g -> activity >= g.getMinActivity())) {
            return Optional.empty();
        }
        profiles.findWithLockByUserId(userId);
        return promoteOnEvent(userId, activity, now);
    }

    /**
     * 회원 한 명을 주어진 활동 수로 판정해 올린다. 일수와 활동 수 조건을 모두 채운 가장 높은 등급이 지금보다 높으면 올린다.
     * 글·댓글 작성 경로는 {@link #promoteOnActivity} 를 거쳐 부른다(활동 수를 상한까지 센 값 — 상한 이상이면 조건 판정에는 같다).
     * 일수 승급 배치(SQL)와 같은 규칙이다 — 고치면 {@code R__batch_15_grade_promotion.sql} 도 고친다.
     *
     * @param activityCount 그 회원의 글+댓글 수(또는 필요한 수까지 센 값)
     * @return 올라간 등급 키. 올리지 않았으면 비어 있다
     */
    @Transactional
    public Optional<String> promoteOnEvent(long userId, long activityCount, Instant now) {
        Optional<UserEntity> user = users.findById(userId).filter(u -> u.getStatus() == UserStatus.ACTIVE && !u.isGone());
        Optional<ProfileEntity> profile = profiles.findById(userId);
        if (user.isEmpty() || profile.isEmpty()) {
            return Optional.empty();
        }
        ProfileEntity p = profile.get();
        GradeManualPolicy policy = settings.findById(GradeSettingEntity.ROW_ID).map(GradeSettingEntity::getManualPolicy).orElse(GradeManualPolicy.CONTINUE_AUTO);
        if (p.isGradeManual() && !policy.includesManual()) {
            return Optional.empty();
        }
        List<GradeEntity> grades = gradeQueries.findAllOrdered();
        int currentLevel = grades.stream().filter(g -> g.getGradeKey().equals(p.getGradeKey())).mapToInt(GradeEntity::getLevel).findFirst().orElse(1);
        long days = Duration.between(user.get().getCreatedAt(), now).toDays();
        GradeEntity target = null;
        for (GradeEntity grade : grades) {
            if (grade.getLevel() > currentLevel && days >= grade.getMinDays() && activityCount >= grade.getMinActivity()) {
                target = grade; // 서열 순서라 마지막으로 맞은 것이 가장 높은 등급이다
            }
        }
        if (target == null) {
            return Optional.empty();
        }
        String fromKey = p.getGradeKey();
        p.promoteAutomatically(target.getGradeKey(), now);
        changes.persist(MemberGradeChangeEntity.automatic(userId, fromKey, target.getGradeKey(), now)); // 승급과 같은 트랜잭션에서 이력 한 줄
        return Optional.of(target.getGradeKey());
    }

    private static Instant cutoff(Instant now, int days) {
        return now.minus(Duration.ofDays(days));
    }
}
