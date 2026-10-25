package com.community.nicknameimage.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.nicknameimage.dto.request.NicknameImageSettingsRequest;
import com.community.nicknameimage.dto.response.NicknameImageGradeOptionView;
import com.community.nicknameimage.dto.response.NicknameImageReviewerView;
import com.community.nicknameimage.dto.response.NicknameImageSettingsView;
import com.community.nicknameimage.entity.NicknameImageRequirementMode;
import com.community.nicknameimage.entity.NicknameImageSettingEntity;
import com.community.nicknameimage.repository.master.NicknameImageSettingRepository;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 닉네임 그림 신청 조건 설정 — 조회 {@code nickname-image.read}, 저장 {@code nickname-image.manage}(컨트롤러가 선언). 판정은 api {@code NicknameImageService} 가 요청마다 이 행을 읽어 한다
 * (사본이 없어 저장 즉시 반영된다).
 *
 * <ul>
 *   <li><b>검사</b>: {@code mode} 필수(GRADE·CUSTOM) · GRADE 면 {@code gradeKey} 필수·있는 등급 · CUSTOM 이면 {@code gradeKey} null 또는 있는 등급 · {@code minDays} 0~3650 ·
 *       {@code minActivity} 0~100000 · {@code minPoints} 0~100000000 · {@code requireEmailVerified} 필수. 틀린 칸을 모아 {@code 400 VALIDATION fields.<칸>} — 하나라도 틀리면 저장하지 않는다.</li>
 *   <li><b>GRADE 로 저장</b>: 이메일 인증은 true 로 저장한다. CUSTOM 칸(일수·활동·포인트)은 검사한 뒤 받은 값을 그대로 보관한다(판정에는 쓰지 않는다 — 다시 CUSTOM 으로 바꿀 때 앞 값이 보인다).</li>
 *   <li>바뀐 칸이 없으면 쓰지 않는다(마지막 저장 시각도 그대로). 감사 {@code NICKNAME_IMAGE_SETTINGS_UPDATE} 는 바뀐 칸만 {@code before}/{@code after}.</li>
 *   <li>이미 적용 중인 그림은 조건이 바뀌어도 그대로다(판정은 새 신청 때만).</li>
 * </ul>
 */
@Service
public class NicknameImageSettingsService {

    private static final Logger log = LoggerFactory.getLogger(NicknameImageSettingsService.class);

    private final NicknameImageSettingRepository settings;
    private final GradeRepositoryQDSL grades;
    private final OperatorRepository operators;
    private final AuditLogService audit;

    public NicknameImageSettingsService(NicknameImageSettingRepository settings, GradeRepositoryQDSL grades, OperatorRepository operators, AuditLogService audit) {
        this.settings = settings;
        this.grades = grades;
        this.operators = operators;
        this.audit = audit;
    }

    /** 검사를 통과한 값 */
    record Values(NicknameImageRequirementMode mode, String gradeKey, int minDays, int minActivity, int minPoints, boolean requireEmailVerified) {

        static Values of(NicknameImageSettingEntity row) {
            return new Values(row.getMode(), row.getGradeKey(), row.getMinDays(), row.getMinActivity(), row.getMinPoints(), row.isRequireEmailVerified());
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("mode", mode.name());
            map.put("gradeKey", gradeKey);
            map.put("minDays", minDays);
            map.put("minActivity", minActivity);
            map.put("minPoints", minPoints);
            map.put("requireEmailVerified", requireEmailVerified);
            return map;
        }
    }

    @Transactional(readOnly = true)
    public NicknameImageSettingsView load() {
        NicknameImageSettingEntity row = settings.findById(NicknameImageSettingEntity.ROW_ID)
                .orElseThrow(() -> new IllegalStateException("nickname_image_settings 행이 없다"));
        return view(row, gradeOptions());
    }

    @Transactional
    public NicknameImageSettingsView save(AdminActor actor, NicknameImageSettingsRequest body) {
        List<NicknameImageGradeOptionView> options = gradeOptions();
        Values wanted = check(body, options.stream().map(NicknameImageGradeOptionView::key).toList());
        NicknameImageSettingEntity row = settings.findWithLockById(NicknameImageSettingEntity.ROW_ID)
                .orElseThrow(() -> new IllegalStateException("nickname_image_settings 행이 없다"));
        Map<String, Object> current = Values.of(row).toMap();
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : wanted.toMap().entrySet()) {
            Object old = current.get(entry.getKey());
            if (!Objects.equals(old, entry.getValue())) {
                before.put(entry.getKey(), old);
                after.put(entry.getKey(), entry.getValue());
            }
        }
        if (after.isEmpty()) {
            return view(row, options);
        }
        row.change(wanted.mode(), wanted.gradeKey(), wanted.minDays(), wanted.minActivity(), wanted.minPoints(), wanted.requireEmailVerified(), actor.operatorId(),
                Instant.now());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NICKNAME_IMAGE_SETTINGS_UPDATE, AuditResult.SUCCESS)
                .target("NICKNAME_IMAGE_SETTINGS", "nickname-image").with("before", before).with("after", after));
        log.info("닉네임 그림 신청 조건을 저장했다 칸={} by operatorId={}", after.keySet(), actor.operatorId());
        return view(row, options);
    }

    /** 칸마다 검사 — 틀린 칸을 모아 400 */
    static Values check(NicknameImageSettingsRequest body, List<String> gradeKeys) {
        NicknameImageSettingsRequest b = body == null ? new NicknameImageSettingsRequest(null, null, null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        NicknameImageRequirementMode mode = modeOf(b.mode());
        if (mode == null) {
            fields.put("mode", new MessageRef("nicknameImage.settings.mode.invalid"));
        }
        String gradeKey = b.gradeKey() == null || b.gradeKey().isBlank() ? null : b.gradeKey().trim();
        if (gradeKey == null && mode == NicknameImageRequirementMode.GRADE) {
            fields.put("gradeKey", new MessageRef("nicknameImage.settings.grade.required"));
        } else if (gradeKey != null && !gradeKeys.contains(gradeKey)) {
            fields.put("gradeKey", new MessageRef("nicknameImage.settings.grade.missing"));
        }
        Integer minDays = ranged(fields, "minDays", b.minDays(), NicknameImageRules.MAX_MIN_DAYS);
        Integer minActivity = ranged(fields, "minActivity", b.minActivity(), NicknameImageRules.MAX_MIN_ACTIVITY);
        Integer minPoints = ranged(fields, "minPoints", b.minPoints(), NicknameImageRules.MAX_MIN_POINTS);
        if (b.requireEmailVerified() == null) {
            fields.put("requireEmailVerified", new MessageRef("nicknameImage.settings.requireEmailVerified.required"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        boolean email = mode == NicknameImageRequirementMode.GRADE || b.requireEmailVerified();
        return new Values(mode, gradeKey, minDays, minActivity, minPoints, email);
    }

    private static NicknameImageRequirementMode modeOf(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return NicknameImageRequirementMode.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 0~max 정수면 그 값, 아니면 칸 오류를 더하고 null */
    private static Integer ranged(Map<String, MessageRef> fields, String field, Number raw, int max) {
        Integer value = integerOf(raw);
        if (value == null || value < 0 || value > max) {
            fields.put(field, new MessageRef("nicknameImage.settings.range", "0", String.format("%,d", max)));
            return null;
        }
        return value;
    }

    /** 정수면 그 값, 소수·빈 값·너무 큰 수는 null */
    private static Integer integerOf(Number raw) {
        if (raw == null) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.toString());
            if (value.stripTrailingZeros().scale() > 0 || value.abs().compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                return null;
            }
            return value.intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private List<NicknameImageGradeOptionView> gradeOptions() {
        return grades.findAllOrdered().stream().map(g -> new NicknameImageGradeOptionView(g.getGradeKey(), g.getLabel(), g.getLevel())).toList();
    }

    private NicknameImageSettingsView view(NicknameImageSettingEntity row, List<NicknameImageGradeOptionView> options) {
        boolean gradeMissing = row.getGradeKey() != null && options.stream().noneMatch(o -> o.key().equals(row.getGradeKey()));
        NicknameImageReviewerView by = row.getUpdatedBy() == null ? null
                : new NicknameImageReviewerView(Long.toString(row.getUpdatedBy()), operators.findById(row.getUpdatedBy()).map(o -> o.getOperatorName()).orElse(null));
        return new NicknameImageSettingsView(row.getMode().name(), row.getGradeKey(), row.getMinDays(), row.getMinActivity(), row.getMinPoints(),
                row.isRequireEmailVerified(), gradeMissing, options, row.getUpdatedAt(), by);
    }
}
