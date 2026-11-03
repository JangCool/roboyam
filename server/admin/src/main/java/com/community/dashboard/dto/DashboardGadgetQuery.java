package com.community.dashboard.dto;

import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 가젯 종류 하나를 읽을 때의 값 — 요청한 운영자(종류 안의 항목별 권한용) · 목록 개수({@code limit.<종류>}, 5|10 — 기본 5) · 추이 기간({@code days.<종류>}, 7|30 — 기본 7) ·
 * 센 시각(한 요청의 모든 종류가 같은 시각을 쓴다).
 */
public record DashboardGadgetQuery(AdminActor actor, int limit, int days, Instant now) {

    public static final int DEFAULT_LIMIT = 5;
    public static final int DEFAULT_DAYS = 7;

    /** 오늘(서비스 날짜 — 한국 시간) */
    public LocalDate today() {
        return ServiceTime.dateOf(now);
    }

    /** 그 서비스 날짜의 0시(한국 시간) */
    public static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ServiceTime.ZONE).toInstant();
    }
}
