package com.community.grade.dto;

import java.time.Instant;
import java.util.Collection;

/**
 * 한 등급으로 올릴 회원의 범위 — 지금 {@code fromKeys} 등급이고(그 목표 등급보다 낮은 등급들), 자동 승급 대상(정상 회원)이며,
 * 가입 시각이 {@code joinedBefore} 이전(가입 후 일수 조건을 채움)이다. 활동 수 조건은 서비스가 따로 따진다.
 *
 * @param joinedBefore         이 시각 이전에 가입한 회원(= 가입 후 일수를 채운 회원)
 * @param notJoinedBefore      null 이 아니면 이 시각 이전에 가입한 회원은 뺀다(더 높은 등급의 일수 조건도 채운 회원 — 미리보기에서 목표 등급이 맞는 회원만 세려고)
 * @param includeManual        수동으로 올린 회원도 넣는가(등급 옵션 {@code CONTINUE_AUTO})
 */
public record PromotionScope(Collection<String> fromKeys, Instant joinedBefore, Instant notJoinedBefore, boolean includeManual) {
}
