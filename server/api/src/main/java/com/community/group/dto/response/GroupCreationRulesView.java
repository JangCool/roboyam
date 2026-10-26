package com.community.group.dto.response;

import java.time.Instant;

/**
 * {@code GET /api/groups/creation-rules} — 소모임 만들기 화면의 안내 재료.
 *
 * @param creationMode     APPROVAL(운영자 승인 뒤 열린다) · OPEN(바로 열린다)
 * @param minGrade         개설 최소 등급 키
 * @param minGradeLabel    그 등급 이름(등급 설정에 없으면 null — 누구나)
 * @param maxPerOwner      한 사람이 모임장일 수 있는 소모임 수
 * @param me               비로그인 null
 * @param capacityMin      정원 범위(운영 한도)
 * @param visibilityChoice 공개 범위를 고를 수 있나(공개 범위 기능 — 5단계, 꺼져 있으면 신청의 visibility 는 무시된다)
 * @param dissolvePenaltyDays 해산 패널티 일수(0 = 끔 — 개설부터 1년 이상 유지한 소모임은 면제, V96) — 만들기 화면 안내용
 */
public record GroupCreationRulesView(String creationMode, String minGrade, String minGradeLabel, int maxPerOwner, Me me, int capacityMin, int capacityMax,
                                     boolean visibilityChoice, int dissolvePenaltyDays) {

    /**
     * 개설 규칙만 본다 — 이메일 인증·정지는 신청할 때 403 으로 막힌다.
     *
     * @param reason       못 낼 때 NOT_PERMITTED(회원 역할에 group.create 없음 — 로보얌 추가) · GRADE_TOO_LOW · LIMIT_REACHED · DISSOLVE_PENALTY(V96)
     * @param grade        내 등급 키
     * @param ledCount     내가 모임장인 소모임 수(신청 중·열림·비활성)
     * @param penaltyUntil 해산 패널티 끝 시각 — 패널티 중일 때만(다른 까닭으로 막혀도 패널티 중이면 값이 온다), 아니면 null
     */
    public record Me(boolean canCreate, String reason, String grade, long ledCount, Instant penaltyUntil) {
    }
}
