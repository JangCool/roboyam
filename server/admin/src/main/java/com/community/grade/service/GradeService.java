package com.community.grade.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.grade.dto.GradePlan;
import com.community.grade.dto.GradeThreshold;
import com.community.grade.dto.PlannedGrade;
import com.community.grade.dto.request.GradesSaveRequest;
import com.community.grade.dto.response.GradeMoveItem;
import com.community.grade.dto.response.GradePreviewItem;
import com.community.grade.dto.response.GradePreviewResponse;
import com.community.grade.dto.response.GradeView;
import com.community.grade.dto.response.GradesResponse;
import com.community.grade.entity.GradeEntity;
import com.community.grade.entity.GradeSettingEntity;
import com.community.grade.repository.master.GradeRepository;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.grade.repository.master.GradeSettingRepository;
import com.community.permission.dto.AdminActor;
import com.community.role.service.GradeRoleSynchronizer;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등급 설정 조회·일괄 저장·저장 전 영향 미리보기 (docs/features/roles-contract.md 2단계).
 *
 * <ul>
 *   <li><b>저장은 한 번에</b>: 목록 순서가 곧 서열이고, 지금 저장된 목록과 비교해 추가({@code gradeKey} 없음 — 서버가 {@code GRADE_<번호>} 를 발급)·삭제(요청에 빠진 기존 등급)·순서·수정을 처리한다.
 *       검사({@link GradeSavePlanner})를 모두 통과한 뒤에야 쓰기 시작하고, 한 트랜잭션이라 중간에 실패하면 아무것도 저장되지 않는다. 같은 설정을 동시에 저장하는 요청은 옵션 행 잠금으로 줄 세운다.</li>
 *   <li><b>삭제</b>: 그 등급의 회원은 저장돼 있는 서열에서 <b>바로 아래 남아 있는 등급</b>으로 이동한 뒤 등급이 지워진다. 첫 등급은 지울 수 없다.</li>
 *   <li><b>등급 역할(파생)</b>: 같은 트랜잭션에서 {@code ROLE_GRADE_<키>} 역할을 만들고(권한은 바로 아래 등급 역할의 것을 복사), 이름이 바뀌면 따라가고, 지워진 등급의 역할은 지운다.</li>
 *   <li><b>소급 승급</b>: 저장 직후 새 기준으로 이미 조건을 채운 회원을 올린다. 가입 일수 조건은 시간이 지나야 채워지는 조건이라 <b>저장 직후에는 기준을 낮춘 몫을 적용하지 않고</b>(저장 전 일수와 새 일수 중 큰 값으로 판정)
 *       다음 배치({@link GradePromotionService#promoteByElapsedTime})가 올린다. 활동 수 조건·새 등급·옵션을 "계속 자동 승급"으로 바꾼 경우는 저장 직후 올린다. 이동한 회원도 판정 대상이다.</li>
 *   <li>변경이 있으면 감사 기록({@code GRADE_UPDATE})을 남긴다 — 추가·삭제(이동 인원)·이름 변경·순서·옵션·소급 승급 인원.</li>
 * </ul>
 * 입구의 권한(읽기는 grade.read · member.read · role.read 중 하나, 저장·미리보기는 grade.manage)은 컨트롤러가 선언한다.
 */
@Service
public class GradeService {

    private static final Logger log = LoggerFactory.getLogger(GradeService.class);

    private final GradeRepository grades;
    private final GradeRepositoryQDSL gradeQueries;
    private final GradeSettingRepository settings;
    private final GradeSavePlanner planner;
    private final GradePromotionService promotion;
    private final GradeRoleSynchronizer gradeRoles;
    private final AuditLogService audit;
    private final EntityManager entityManager;

    public GradeService(GradeRepository grades, GradeRepositoryQDSL gradeQueries, GradeSettingRepository settings, GradeSavePlanner planner,
                        GradePromotionService promotion, GradeRoleSynchronizer gradeRoles, AuditLogService audit, EntityManager entityManager) {
        this.grades = grades;
        this.gradeQueries = gradeQueries;
        this.settings = settings;
        this.planner = planner;
        this.promotion = promotion;
        this.gradeRoles = gradeRoles;
        this.audit = audit;
        this.entityManager = entityManager;
    }

    /** 등급 목록(서열 순서)과 등급별 회원 수, 옵션 */
    @Transactional(readOnly = true)
    public GradesResponse load() {
        List<GradeEntity> current = gradeQueries.findAllOrdered();
        Map<String, Long> counts = gradeQueries.memberCounts();
        List<GradeView> views = current.stream().map(grade -> view(grade, counts.getOrDefault(grade.getGradeKey(), 0L))).toList();
        return new GradesResponse(views, settingOf().getManualPolicy().name());
    }

    /**
     * 저장 전 영향 — 저장하지 않고 센다. {@code items}: 이 기준으로 저장하면 각 등급(첫 등급 제외)이 될 회원 수(새 등급 포함, 이동하는 회원은 제외),
     * {@code willMove}: 지우는 등급마다 이동할 회원 수.
     */
    @Transactional(readOnly = true)
    public GradePreviewResponse preview(GradesSaveRequest request) {
        List<GradeEntity> current = gradeQueries.findAllOrdered();
        GradePlan plan = planner.plan(request, current);
        Instant now = Instant.now();

        Map<String, GradeEntity> byKey = new HashMap<>();
        current.forEach(grade -> byKey.put(grade.getGradeKey(), grade));
        Map<String, PlannedGrade> plannedByKey = new HashMap<>();
        plan.grades().stream().filter(g -> g.gradeKey() != null).forEach(g -> plannedByKey.put(g.gradeKey(), g));
        Map<String, Long> counts = gradeQueries.memberCounts();

        List<GradeMoveItem> willMove = new ArrayList<>();
        plan.removedTo().forEach((fromKey, toKey) -> willMove.add(new GradeMoveItem(fromKey, byKey.get(fromKey).getLabel(), toKey,
                plannedByKey.get(toKey).label(), counts.getOrDefault(fromKey, 0L))));

        List<GradeThreshold> thresholds = plan.grades().stream().map(g -> new GradeThreshold(g.gradeKey(), g.level(), g.minDays(), g.minActivity())).toList();
        boolean includeManual = plan.policy().includesManual();
        List<GradePreviewItem> items = new ArrayList<>();
        long total = 0;
        for (int i = 1; i < plan.grades().size(); i++) {
            PlannedGrade target = plan.grades().get(i);
            // 새 목록에서 이 등급보다 낮은 서열에 있는 기존 등급의 회원만 이 등급이 될 수 있다
            List<String> below = plan.grades().subList(0, i).stream().map(PlannedGrade::gradeKey).filter(key -> key != null).toList();
            long count = promotion.countPromotedTo(thresholds, i, below, includeManual, now);
            items.add(new GradePreviewItem(target.gradeKey(), target.label(), count));
            total += count;
        }
        return new GradePreviewResponse(items, total, willMove);
    }

    /** 등급 목록과 옵션을 통째로 저장하고 저장된 모양을 돌려준다 */
    @Transactional
    public GradesResponse save(AdminActor actor, GradesSaveRequest request) {
        GradeSettingEntity setting = settings.findWithLockById(GradeSettingEntity.ROW_ID).orElseThrow(() -> new IllegalStateException("grade_settings 행이 없다"));
        List<GradeEntity> current = gradeQueries.findAllOrdered();
        GradePlan plan = planner.plan(request, current);

        Instant now = Instant.now();
        Map<String, GradeEntity> byKey = new HashMap<>();
        current.forEach(grade -> byKey.put(grade.getGradeKey(), grade));

        // 1) 지우는 등급의 회원을 바로 아래 등급으로 옮긴다(등급 행을 지우기 전에 — profiles 가 grade_key 를 참조한다)
        List<Map<String, Object>> deleted = new ArrayList<>();
        for (Map.Entry<String, String> removed : plan.removedTo().entrySet()) {
            long count = gradeQueries.memberCountOf(removed.getKey());
            gradeQueries.moveMembers(removed.getKey(), removed.getValue(), now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("gradeKey", removed.getKey());
            detail.put("movedTo", removed.getValue());
            detail.put("movedMembers", count);
            deleted.add(detail);
        }

        // 2) 추가·수정·순서 — 서열의 유일성은 커밋 때 검사한다(DEFERRABLE)
        List<GradeEntity> finalGrades = new ArrayList<>();
        List<String> created = new ArrayList<>();
        List<String> renamed = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        int nextSerial = setting.getNextSerial();
        Map<String, Integer> oldDays = new HashMap<>();
        for (PlannedGrade planned : plan.grades()) {
            GradeEntity grade;
            if (planned.gradeKey() == null) {
                String key = "GRADE_" + nextSerial++;
                grade = GradeEntity.create(key, planned.level(), planned.label(), planned.minDays(), planned.minActivity(), planned.badgeColor(), planned.badgeIcon(), now);
                grades.persist(grade);
                created.add(key);
            } else {
                grade = byKey.get(planned.gradeKey());
                oldDays.put(grade.getGradeKey(), grade.getMinDays());
                if (!grade.getLabel().equals(planned.label())) {
                    renamed.add(grade.getGradeKey());
                }
                if (differs(grade, planned)) {
                    changed.add(grade.getGradeKey());
                }
                grade.change(planned.level(), planned.label(), planned.minDays(), planned.minActivity(), planned.badgeColor(), planned.badgeIcon(), now);
            }
            finalGrades.add(grade);
        }
        for (String removedKey : plan.removedTo().keySet()) {
            grades.delete(byKey.get(removedKey));
        }
        boolean policyChanged = setting.getManualPolicy() != plan.policy();
        String previousPolicy = setting.getManualPolicy().name();
        boolean anyChange = !created.isEmpty() || !deleted.isEmpty() || !changed.isEmpty() || policyChanged;
        if (anyChange) {
            setting.change(plan.policy(), nextSerial, actor.operatorId(), now);
        }
        entityManager.flush(); // 등급 행(추가·수정·삭제)을 먼저 내보낸다 — 아래 역할·승급이 이 표를 읽는다

        // 3) 등급 역할(파생)
        gradeRoles.sync(finalGrades, plan.removedTo().keySet(), now);

        // 4) 소급 승급 — 일수 조건은 저장 전 값과 새 값 중 큰 쪽으로 판정해 기준을 낮춘 몫은 다음 배치가 올린다
        long promoted = 0;
        if (anyChange) {
            List<GradeThreshold> immediate = finalGrades.stream()
                    .map(grade -> new GradeThreshold(grade.getGradeKey(), grade.getLevel(), Math.max(grade.getMinDays(), oldDays.getOrDefault(grade.getGradeKey(), 0)),
                            grade.getMinActivity()))
                    .toList();
            promoted = promotion.promoteAll(immediate, plan.policy().includesManual(), now);
        }

        if (anyChange) {
            AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.GRADE_UPDATE, AuditResult.SUCCESS).target("GRADE", "SETTINGS")
                    .with("grades", finalGrades.stream().map(GradeEntity::getGradeKey).toList())
                    .with("promoted", promoted);
            if (!created.isEmpty()) {
                event = event.with("created", created);
            }
            if (!deleted.isEmpty()) {
                event = event.with("deleted", deleted);
            }
            if (!changed.isEmpty()) {
                event = event.with("changed", changed);
            }
            if (!renamed.isEmpty()) {
                event = event.with("renamed", renamed);
            }
            if (policyChanged) {
                event = event.with("manualPolicy", Map.of("from", previousPolicy, "to", plan.policy().name()));
            }
            audit.record(event);
            log.info("등급 설정을 저장했다 추가={}개 삭제={}개 수정={}개 승급={}명 operatorId={}", created.size(), deleted.size(), changed.size(), promoted, actor.operatorId());
        }

        return load();
    }

    private GradeSettingEntity settingOf() {
        return settings.findById(GradeSettingEntity.ROW_ID).orElseThrow(() -> new IllegalStateException("grade_settings 행이 없다"));
    }

    private static boolean differs(GradeEntity grade, PlannedGrade planned) {
        return grade.getLevel() != planned.level() || !grade.getLabel().equals(planned.label()) || grade.getMinDays() != planned.minDays()
                || grade.getMinActivity() != planned.minActivity() || !grade.getBadgeColor().equals(planned.badgeColor())
                || !Objects.equals(grade.getBadgeIcon(), planned.badgeIcon());
    }

    private static GradeView view(GradeEntity grade, long memberCount) {
        return new GradeView(grade.getGradeKey(), grade.getLevel(), grade.getLabel(), grade.getMinDays(), grade.getMinActivity(), grade.getBadgeColor(),
                grade.getBadgeIcon(), memberCount);
    }
}
