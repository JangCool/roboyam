package com.community.grade.entity;

import java.util.Optional;

/**
 * 운영자가 등급을 <b>수동으로 올린</b> 회원의 이후 동작 — 값은 전역 하나({@code grade_settings.manual_policy})지만 수동으로 올린 회원에게만 적용된다.
 * 수동으로 올린 적 없는 회원은 이 값과 상관없이 조건을 채우면 올라간다.
 * <ul>
 *   <li>{@link #CONTINUE_AUTO} 계속 자동 승급(기본) — 수동으로 올린 회원도 이후 더 높은 등급의 조건을 채우면 자동으로 올린다.</li>
 *   <li>{@link #STOP_AUTO} 자동 승급 중지 — 수동으로 올린 회원은 이후 어떤 자동 승급 조치도 하지 않는다.</li>
 * </ul>
 */
public enum GradeManualPolicy {
    CONTINUE_AUTO,
    STOP_AUTO;

    /** 문자열로 찾는다 — 모르는 값이면 비어 있다 */
    public static Optional<GradeManualPolicy> ofName(String name) {
        for (GradeManualPolicy policy : values()) {
            if (policy.name().equals(name)) {
                return Optional.of(policy);
            }
        }
        return Optional.empty();
    }

    /** 수동으로 올린 회원도 자동 승급 판정에 넣는가 */
    public boolean includesManual() {
        return this == CONTINUE_AUTO;
    }
}
