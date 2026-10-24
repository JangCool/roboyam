package com.community.grade.service;

import com.community.grade.entity.GradeEntity;
import com.community.grade.entity.GradeManualPolicy;
import com.community.user.entity.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 등급 진행도의 셈 — 관리자 회원 상세({@code MemberGradePointAssembler}, admin)와 공개 사이트 내 등급({@code GET /api/me/grade}, api)이 <b>같은 정의</b>를 쓰게 한 곳에 둔다.
 * 승급 판정({@link GradePromotionService}, {@code R__batch_15_grade_promotion.sql})의 대상 규칙·일수 셈과 같다 — 그쪽을 고치면 여기도 고친다.
 * 활동 수(글+댓글)는 {@code MemberActivityRepository#countOf} 의 글 수 + 댓글 수다(즉시 승급과 같은 정의).
 */
public final class GradeProgress {

    /** 자동 승급 켜짐 */
    public static final String ACTIVE = "ACTIVE";
    /** 정지(기간·영구) 중이라 보류 — 운영자 고정보다 먼저 */
    public static final String PAUSED_SANCTION = "PAUSED_SANCTION";
    /** 운영자가 올려 둔 회원이고 등급 옵션이 「자동 승급 중지」 */
    public static final String PAUSED_ADMIN = "PAUSED_ADMIN";

    private GradeProgress() {
    }

    /** 자동 승급 상태 — 정지가 우선(승급 판정은 정상(ACTIVE) 회원만, 수동 상향 회원은 옵션이 CONTINUE_AUTO 일 때만 본다) */
    public static String autoPromotion(UserStatus status, boolean gradeManual, GradeManualPolicy policy) {
        if (status != UserStatus.ACTIVE) {
            return PAUSED_SANCTION;
        }
        return gradeManual && !policy.includesManual() ? PAUSED_ADMIN : ACTIVE;
    }

    /** 가입 후 지난 일수 — 24시간 단위 내림, 상한 없음(승급 판정 「가입 시각 &lt;= 지금 - 일수」와 같은 셈) */
    public static long elapsedDays(Instant joinedAt, Instant now) {
        return Math.max(0, Duration.between(joinedAt, now).toDays());
    }

    /**
     * 지금 등급 바로 위 등급.
     *
     * @param ordered 서열 순서의 등급 목록({@code GradeRepositoryQDSL#findAllOrdered})
     * @return 지금 등급을 목록에서 못 찾거나 가장 높은 등급이면 비어 있다
     */
    public static Optional<GradeEntity> next(List<GradeEntity> ordered, String currentKey) {
        GradeEntity current = ordered.stream().filter(grade -> grade.getGradeKey().equals(currentKey)).findFirst().orElse(null);
        if (current == null) {
            return Optional.empty();
        }
        return ordered.stream().filter(grade -> grade.getLevel() > current.getLevel()).findFirst(); // 서열 순서라 처음 만나는 것이 바로 위 등급
    }
}
