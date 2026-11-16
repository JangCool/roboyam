package com.community.home.service;

import com.community.home.dto.request.HomeLayoutSaveRequest;
import com.community.home.exception.LayoutVersionConflictException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 배치 저장의 버전 확인 — 화면이 받은 {@code updatedAt}({@code base})과 잠근 행의 지금 {@code updatedAt} 을 견준다(관리자 QA 2026-11-17 F-1).
 * 밀리초까지만 견준다 — 저장 직후 응답의 시각(자바 나노초)과 다시 읽은 시각(DB 마이크로초)의 자릿수가 달라 같은 저장도 다르게 보이지 않게.
 */
final class LayoutVersions {

    private LayoutVersions() {
    }

    /**
     * @param base    요청의 {@code base} — null 이면(옛 화면·시드 코드) 검사하지 않는다
     * @param current 잠근 행의 {@code updatedAt} — 저장한 적 없으면 null
     * @throws LayoutVersionConflictException 그사이 다른 저장이 있었을 때
     */
    static void check(HomeLayoutSaveRequest.Base base, Instant current) {
        if (base == null) {
            return;
        }
        if (!Objects.equals(millis(base.updatedAt()), millis(current))) {
            throw new LayoutVersionConflictException();
        }
    }

    private static Instant millis(Instant at) {
        return at == null ? null : at.truncatedTo(ChronoUnit.MILLIS);
    }
}
