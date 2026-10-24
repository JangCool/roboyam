package com.community.grade.service;

import com.community.grade.dto.response.GradeRefView;
import com.community.grade.dto.response.GradeRequirementView;
import com.community.grade.dto.response.GradeRuleView;
import com.community.grade.dto.response.GradeRulesResponse;
import com.community.grade.dto.response.MyGradeResponse;
import com.community.grade.dto.response.NextGradeView;
import com.community.grade.entity.GradeEntity;
import com.community.grade.entity.GradeManualPolicy;
import com.community.grade.entity.GradeSettingEntity;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.grade.repository.master.GradeSettingRepository;
import com.community.member.repository.master.MemberActivityRepository;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등급 안내(공개 사이트) — 등급 조건표({@code GET /api/grade-rules}, 비회원도)와 내 등급·다음 등급 진행도({@code GET /api/me/grade}, 로그인).
 *
 * <ul>
 *   <li>등급은 운영자가 정하는 2~10단계 목록({@code grades}, 서열 순)이고 조건은 가입 후 일수 · 글+댓글 수 두 가지다(인기글 조건 없음). 이름은 운영자가 입력한 한 언어다.</li>
 *   <li>셈은 관리자 회원 상세({@code MemberGradePointAssembler})·승급 판정과 같은 정의다 — {@link GradeProgress}(자동 승급 상태·일수·다음 등급)와
 *       {@link MemberActivityRepository#countOf}(지금 보이는 회원 글 + 댓글). 다른 점은 기준이 0 인 조건을 싣지 않는 것뿐이다(채울 것이 없다).</li>
 *   <li>내 등급은 정지 중·이메일 인증 전에도 읽는다. 탈퇴했거나 회원 행이 없으면 {@code 401 UNAUTHENTICATED}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PublicGradeService {

    static final String DAYS = "DAYS";
    static final String ACTIVITY = "ACTIVITY";

    private final GradeRepositoryQDSL grades;
    private final GradeSettingRepository settings;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final MemberActivityRepository activities;

    public PublicGradeService(GradeRepositoryQDSL grades, GradeSettingRepository settings, UserRepository users, ProfileRepository profiles,
                              MemberActivityRepository activities) {
        this.grades = grades;
        this.settings = settings;
        this.users = users;
        this.profiles = profiles;
        this.activities = activities;
    }

    public GradeRulesResponse rules() {
        return new GradeRulesResponse(grades.findAllOrdered().stream()
                .map(g -> new GradeRuleView(g.getGradeKey(), g.getLabel(), g.getMinDays(), g.getMinActivity()))
                .toList());
    }

    public MyGradeResponse mine(long userId) {
        UserEntity user = users.findById(userId).filter(u -> !u.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        ProfileEntity profile = profiles.findById(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        Instant now = Instant.now();
        List<GradeEntity> ordered = grades.findAllOrdered();
        String key = profile.getGradeKey();
        String label = ordered.stream().filter(g -> g.getGradeKey().equals(key)).map(GradeEntity::getLabel).findFirst().orElse(key);
        GradeManualPolicy policy = settings.findById(GradeSettingEntity.ROW_ID).map(GradeSettingEntity::getManualPolicy).orElse(GradeManualPolicy.CONTINUE_AUTO);
        String autoPromotion = GradeProgress.autoPromotion(user.getStatus(), profile.isGradeManual(), policy);

        NextGradeView next = GradeProgress.next(ordered, key).map(g -> {
            List<GradeRequirementView> requirements = new ArrayList<>(2);
            if (g.getMinDays() > 0) {
                long days = GradeProgress.elapsedDays(user.getCreatedAt(), now);
                requirements.add(new GradeRequirementView(DAYS, days, g.getMinDays(), days >= g.getMinDays()));
            }
            if (g.getMinActivity() > 0) {
                MemberActivityRepository.Counts counts = activities.countOf(userId, now, now);
                long activity = counts.posts() + counts.comments();
                requirements.add(new GradeRequirementView(ACTIVITY, activity, g.getMinActivity(), activity >= g.getMinActivity()));
            }
            return new NextGradeView(g.getGradeKey(), g.getLabel(), requirements);
        }).orElse(null);
        return new MyGradeResponse(new GradeRefView(key, label), autoPromotion, next);
    }
}
