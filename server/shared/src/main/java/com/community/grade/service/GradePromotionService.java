package com.community.grade.service;

import com.community.grade.dto.GradeThreshold;
import com.community.grade.dto.PromotionScope;
import com.community.grade.entity.GradeEntity;
import com.community.grade.entity.GradeManualPolicy;
import com.community.grade.entity.GradeSettingEntity;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.grade.repository.master.GradeSettingRepository;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.entity.UserStatus;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등급 자동 승급 판정 (docs/features/roles-contract.md 2단계, docs/domain/operator-permissions.md "회원 역할").
 *
 * <ul>
 *   <li><b>올리기만 한다</b> — 자동으로 내리지 않는다. 조건 두 개(가입 후 일수 · 글+댓글 수)를 <b>모두</b> 채운 가장 높은 등급으로 올린다(가정).</li>
 *   <li><b>시점</b>: 활동 수 조건은 사건(글·댓글)이 생길 때 즉시({@link #promoteOnEvent}), 가입 일수 조건은 시간이 지나야 채워지므로 배치({@link #promoteByElapsedTime}).
 *       배치 스케줄러·{@code /settings/batches} 연동은 아직 없다 — 이 메서드를 부르는 지점은 후속(스케줄러, 글·댓글 작성 서비스)이다.</li>
 *   <li><b>소급</b>: 기준을 낮추거나 등급을 추가하면 이미 조건을 채운 회원도 올린다({@link #promoteAll}) — 등급 저장 서비스가 저장 직후 부른다.</li>
 *   <li><b>대상</b>: 정상(ACTIVE) 회원만. 정지 회원은 자동 승급이 멈추고(제재로 중지) 탈퇴 회원은 대상이 아니다. 수동으로 올린 회원은 등급 옵션이
 *       {@code CONTINUE_AUTO} 일 때만 포함한다.</li>
 *   <li><b>활동 수</b>: 글·댓글 표가 아직 없어 모든 회원의 활동 수를 0 으로 본다 — 활동 조건이 0 인 등급(조건이 일수뿐인 등급)만 올라갈 수 있다.
 *       표가 생기면 {@link #activityReachable} 과 {@code GradeRepositoryQDSL} 의 대상 조건에 활동 수 집계를 더한다.</li>
 * </ul>
 */
@Service
public class GradePromotionService {

    private static final Logger log = LoggerFactory.getLogger(GradePromotionService.class);

    /** 글·댓글 데이터가 없는 동안 모든 회원이 가진 활동 수 */
    private static final long KNOWN_ACTIVITY = 0;

    private final GradeRepositoryQDSL gradeQueries;
    private final GradeSettingRepository settings;
    private final ProfileRepository profiles;
    private final UserRepository users;

    public GradePromotionService(GradeRepositoryQDSL gradeQueries, GradeSettingRepository settings, ProfileRepository profiles, UserRepository users) {
        this.gradeQueries = gradeQueries;
        this.settings = settings;
        this.profiles = profiles;
        this.users = users;
    }

    /** 활동 수 조건을 채울 수 있는 회원이 있는가 — 지금은 활동 수가 모두 0 이라 조건이 0 인 등급만 채울 수 있다 */
    public static boolean activityReachable(int minActivity) {
        return minActivity <= KNOWN_ACTIVITY;
    }

    /** 서열 순서의 조건 목록 */
    public static List<GradeThreshold> thresholdsOf(List<GradeEntity> orderedGrades) {
        return orderedGrades.stream().map(grade -> new GradeThreshold(grade.getGradeKey(), grade.getLevel(), grade.getMinDays(), grade.getMinActivity())).toList();
    }

    /** 지금 저장된 등급 옵션이 수동으로 올린 회원도 판정에 넣는가 */
    @Transactional(readOnly = true)
    public boolean includeManualNow() {
        return settings.findById(GradeSettingEntity.ROW_ID).map(setting -> setting.getManualPolicy().includesManual()).orElse(true);
    }

    /**
     * <b>배치</b> — 지금 저장된 등급 기준으로 모든 정상 회원을 판정해 올린다(가입 일수가 지나 새로 조건을 채운 회원 포함). 올린 회원 수.
     * 스케줄러가 하루 한 번(주기는 미확정) 부르는 진입점이다.
     */
    @Transactional
    public long promoteByElapsedTime(Instant now) {
        List<GradeThreshold> ordered = thresholdsOf(gradeQueries.findAllOrdered());
        long promoted = promoteAll(ordered, includeManualNow(), now);
        log.info("등급 승급 배치: {}명 올림", promoted);
        return promoted;
    }

    /**
     * 주어진 서열 순서 조건으로 모든 정상 회원을 판정해 올린다. 높은 등급부터 처리하므로 회원은 채운 가장 높은 등급으로 한 번에 올라간다(조건은 위 등급일수록 같거나 크다).
     * 소급 승급에서 일수 조건을 "다음 배치"로 미루려면 부르는 쪽이 일수를 저장 전 값 이상으로 올려 넘긴다.
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
     * <b>즉시</b> — 회원 한 명의 활동(글·댓글)이 늘었을 때 부른다. 일수와 활동 수 조건을 모두 채운 가장 높은 등급이 지금보다 높으면 올린다.
     * 글·댓글 작성 서비스가 생기면 그 안에서 부른다(아직 부르는 곳이 없다).
     *
     * @param activityCount 그 회원의 글+댓글 수
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
        p.promoteAutomatically(target.getGradeKey(), now);
        return Optional.of(target.getGradeKey());
    }

    private static Instant cutoff(Instant now, int days) {
        return now.minus(Duration.ofDays(days));
    }
}
