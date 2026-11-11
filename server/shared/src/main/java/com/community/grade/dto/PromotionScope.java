package com.community.grade.dto;

import java.time.Instant;
import java.util.Collection;

/**
 * 한 등급으로 올릴 회원의 범위 — 지금 {@code fromKeys} 등급이고(그 목표 등급보다 낮은 등급들), 자동 승급 대상(정상 회원)이며,
 * 가입 시각이 {@code joinedBefore} 이전(가입 후 일수 조건을 채움)이고 레벨이 {@code minLevel} 이상(V196)이다. 활동 수 조건은 서비스가 따로 따진다.
 *
 * @param joinedBefore         이 시각 이전에 가입한 회원(= 가입 후 일수를 채운 회원)
 * @param minLevel             이 레벨 이상인 회원(= 최소 레벨 조건을 채운 회원)
 * @param upper                null 이 아니면 바로 위 등급의 조건(일수 AND 레벨)도 채운 회원은 뺀다 — 미리보기에서 목표 등급이 맞는 회원만 세려고
 * @param includeManual        수동으로 올린 회원도 넣는가(등급 옵션 {@code CONTINUE_AUTO})
 * @param curveBase            null 이면 저장된 레벨({@code profiles.level})로 레벨 조건을 본다. 값이 있으면 그 곡선으로 센 경계(경험치 ≥ {@code XpCurve.levelXp(minLevel, base)})로
 *                             본다 — 저장 전 미리보기에서 곡선 값을 바꿨을 때(두 판정은 곡선이 같으면 같은 뜻이다: 레벨 ≥ L ⇔ 경험치 ≥ L 의 시작 경험치)
 */
public record PromotionScope(Collection<String> fromKeys, Instant joinedBefore, int minLevel, Upper upper, boolean includeManual, Integer curveBase) {

    /** 저장된 레벨로 보는 범위 */
    public PromotionScope(Collection<String> fromKeys, Instant joinedBefore, int minLevel, Upper upper, boolean includeManual) {
        this(fromKeys, joinedBefore, minLevel, upper, includeManual, null);
    }

    /** 바로 위 등급의 조건 — 이 시각 이전에 가입했고 이 레벨 이상이면 그 위 등급이 된다 */
    public record Upper(Instant joinedBefore, int minLevel) {
    }
}
