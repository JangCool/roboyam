package com.community.grade.service;

import com.community.grade.dto.GradePlan;
import com.community.grade.dto.PlannedGrade;
import com.community.grade.dto.request.GradeRequest;
import com.community.grade.dto.request.GradesSaveRequest;
import com.community.grade.entity.GradeEntity;
import com.community.grade.entity.GradeManualPolicy;
import com.community.grade.exception.GradeFirstProtectedException;
import com.community.grade.exception.GradeLimitException;
import com.community.grade.exception.GradeNameDuplicatedException;
import com.community.grade.exception.GradeRulesNotMonotonicException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 등급 저장 요청을 검사해 계획({@link GradePlan})으로 바꾼다 — 저장({@code PUT})과 미리보기({@code POST …/preview})가 같은 규칙을 쓴다. admin-web 의 {@code util-grade.ts} 규칙과 같다.
 *
 * <p>검사 순서(어디서 멈추는지가 계약이다): 목록이 있는가(400 {@code grades}) → 등급 수 2~10(409 {@code GRADE_LIMIT}) → 첫 등급이 요청에 남아 있는가(409 {@code GRADE_FIRST_PROTECTED}) →
 * 칸별 입력 검사를 모아(400 {@code VALIDATION}, {@code grades[i].label|gradeKey|minDays|minActivity|badgeColor|badgeIcon}·{@code manualPolicy}) →
 * 이름 중복(409 {@code GRADE_NAME_DUPLICATED}) → 위 등급일수록 같거나 큰가(409 {@code GRADE_RULES_NOT_MONOTONIC}, 삭제·이동·추가 뒤 <b>남은 목록</b> 기준).
 * 하나라도 틀리면 아무것도 저장하지 않는다(저장 서비스가 이 검사를 끝낸 뒤에야 쓰기 시작한다).
 */
@Component
public class GradeSavePlanner {

    public static final int MIN_GRADES = 2;
    public static final int MAX_GRADES = 10;
    public static final int LABEL_MAX = 8;
    public static final int DAYS_MAX = 3650;
    public static final int ACTIVITY_MAX = 100_000;

    /** 이름에 쓸 수 없는 문자 — 꺾쇠(&lt; &gt;)와 제어 문자 */
    private static final Pattern LABEL_FORBIDDEN = Pattern.compile("[<>\\u0001-\\u001f\\u007f-\\u009f]");

    /**
     * @param current 지금 저장된 등급(서열 순서)
     */
    public GradePlan plan(GradesSaveRequest request, List<GradeEntity> current) {
        List<GradeRequest> rows = request == null ? null : request.grades();
        if (rows == null) {
            throw ValidationException.of("grades", new MessageRef("grade.grades.required"));
        }
        if (rows.size() < MIN_GRADES || rows.size() > MAX_GRADES) {
            throw new GradeLimitException(MIN_GRADES, MAX_GRADES);
        }
        String firstKey = current.get(0).getGradeKey();
        if (rows.stream().noneMatch(row -> row != null && firstKey.equals(row.gradeKey()))) {
            throw new GradeFirstProtectedException();
        }

        Set<String> currentKeys = new HashSet<>();
        current.forEach(grade -> currentKeys.add(grade.getGradeKey()));
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        List<PlannedGrade> planned = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            GradeRequest row = rows.get(i);
            String prefix = "grades[" + i + "].";
            if (row == null) {
                fields.put(prefix + "label", new MessageRef("grade.label.required"));
                planned.add(new PlannedGrade(null, i + 1, "", 0, 0, "gray", null));
                continue;
            }

            String label = row.label() == null ? "" : row.label().strip();
            if (label.isEmpty()) {
                fields.put(prefix + "label", new MessageRef("grade.label.required"));
            } else if (label.codePointCount(0, label.length()) > LABEL_MAX) {
                fields.put(prefix + "label", new MessageRef("grade.label.max", String.valueOf(LABEL_MAX)));
            } else if (LABEL_FORBIDDEN.matcher(label).find()) {
                fields.put(prefix + "label", new MessageRef("grade.label.invalid"));
            }

            if (row.gradeKey() != null && (!currentKeys.contains(row.gradeKey()) || !usedKeys.add(row.gradeKey()))) {
                fields.put(prefix + "gradeKey", new MessageRef("grade.key.invalid"));
            }
            if (i == 0 && !firstKey.equals(row.gradeKey())) {
                fields.put(prefix + "gradeKey", new MessageRef("grade.key.firstPosition"));
            }

            int minDays = 0;
            int minActivity = 0;
            if (i > 0) {
                if (row.minDays() == null || row.minDays() < 0 || row.minDays() > DAYS_MAX) {
                    fields.put(prefix + "minDays", new MessageRef("grade.minDays.range", String.valueOf(DAYS_MAX)));
                } else {
                    minDays = row.minDays();
                }
                if (row.minActivity() == null || row.minActivity() < 0 || row.minActivity() > ACTIVITY_MAX) {
                    fields.put(prefix + "minActivity", new MessageRef("grade.minActivity.range", String.valueOf(ACTIVITY_MAX)));
                } else {
                    minActivity = row.minActivity();
                }
            }

            if (row.badgeColor() == null || !GradeBadgeCatalog.COLORS.contains(row.badgeColor())) {
                fields.put(prefix + "badgeColor", new MessageRef("grade.badgeColor.invalid"));
            }
            String icon = row.badgeIcon() == null || row.badgeIcon().isBlank() ? null : row.badgeIcon();
            if (icon != null && !GradeBadgeCatalog.ICONS.contains(icon)) {
                fields.put(prefix + "badgeIcon", new MessageRef("grade.badgeIcon.invalid"));
            }

            planned.add(new PlannedGrade(row.gradeKey(), i + 1, label, minDays, minActivity, row.badgeColor(), icon));
        }

        GradeManualPolicy policy = request.manualPolicy() == null ? null : GradeManualPolicy.ofName(request.manualPolicy()).orElse(null);
        if (policy == null) {
            fields.put("manualPolicy", new MessageRef("grade.manualPolicy.invalid"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }

        Set<String> names = new HashSet<>();
        for (int i = 0; i < planned.size(); i++) {
            if (!names.add(planned.get(i).label().toLowerCase(Locale.ROOT))) {
                throw new GradeNameDuplicatedException(i + 1);
            }
        }
        for (int i = 1; i < planned.size(); i++) {
            PlannedGrade lower = planned.get(i - 1);
            PlannedGrade upper = planned.get(i);
            if (upper.minDays() < lower.minDays() || upper.minActivity() < lower.minActivity()) {
                throw new GradeRulesNotMonotonicException(i + 1);
            }
        }

        return new GradePlan(List.copyOf(planned), policy, removedTo(current, usedKeys));
    }

    /**
     * 지울 등급(요청에 없는 기존 등급)과 그 회원이 갈 곳 — 저장돼 있는 서열에서 바로 아래 남아 있는 등급.
     * 첫 등급은 지울 수 없어(검사가 먼저 막는다) 아래 등급이 늘 있다.
     */
    private static Map<String, String> removedTo(List<GradeEntity> current, Set<String> keptKeys) {
        Map<String, String> removed = new LinkedHashMap<>();
        String lowerKept = null;
        for (GradeEntity grade : current) {
            if (keptKeys.contains(grade.getGradeKey())) {
                lowerKept = grade.getGradeKey();
            } else {
                removed.put(grade.getGradeKey(), lowerKept);
            }
        }
        return removed;
    }
}
