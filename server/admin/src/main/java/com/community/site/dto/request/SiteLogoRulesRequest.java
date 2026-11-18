package com.community.site.dto.request;

import java.util.List;

/**
 * {@code PUT /api/admin/site/logo-library/rules} — 로고 보이기 규칙 전체(한 번에 저장). 검사는 서비스가 칸 경로와 함께 모은다.
 *
 * @param defaultBundleId 평소 로고 — 묶음 ID
 * @param anniversaries   기념일 줄(차례 = 우선순위, 없으면 빈 목록)
 * @param gender          성별 규칙(없으면 둘 다 고르지 않음)
 * @param holidayStyle    기념일 로고 유형(V252, 선택) — {@code AUTO}(평소 로고에 맞춤) · {@code CHARACTER} · {@code WORDMARK}. 없으면(null) 지금 값 그대로
 */
public record SiteLogoRulesRequest(String defaultBundleId, List<Anniversary> anniversaries, Gender gender, String holidayStyle) {

    /**
     * 기념일 한 줄.
     *
     * @param id          화면이 든 줄 ID — 서버는 쓰지 않는다(저장할 때마다 줄을 갈아 끼우고 새 ID 를 매긴다)
     * @param enabled     사용 켬·끔(없으면 켬)
     * @param mode        {@code DATES}(날짜 직접) · {@code HOLIDAY}(쉬는 날 표의 이름)
     * @param startDate   {@code DATES}: {@code yearly} 면 {@code MM-DD}, 아니면 {@code YYYY-MM-DD}
     * @param endDate     {@code DATES}: 끝 날(포함). {@code yearly} 에서 시작보다 앞이면 해를 넘기는 기간
     * @param yearly      매년 반복({@code DATES} 일 때만 뜻이 있다)
     * @param holidayName {@code HOLIDAY}: 쉬는 날 표의 이름
     * @param bundleId    보일 묶음 ID
     */
    public record Anniversary(String id, String name, Boolean enabled, String mode, String startDate, String endDate, Boolean yearly, String holidayName,
                              String bundleId) {
    }

    /** 성별 규칙 — 묶음 ID 또는 null(고르지 않음 = 평소 로고) */
    public record Gender(String male, String female) {
    }
}
