package com.community.site.dto.response;

import com.community.site.dto.SiteLogoUrls;
import java.time.Instant;
import java.util.List;

/**
 * 로고 보관함(묶음 목록)과 보이기 규칙 — {@code /api/admin/site/logo-library/**} 가 읽기·바꾸기마다 전체를 돌려준다(admin-web {@code SiteLogoLibraryResponse}).
 *
 * @param bundles 묶음 — 기본 제공 먼저, 그다음 올린 차례
 * @param rules   보이기 규칙(평소 로고·기념일·성별)
 */
public record SiteLogoLibraryResponse(List<Bundle> bundles, Rules rules) {

    /**
     * 묶음 한 벌.
     *
     * @param builtIn    기본 제공 묶음(「캐릭터 로고」·「글자 로고」·기념일 로고 56개) — 고치지 못한다
     * @param style      유효 유형(늘 값이 있다) — {@code CHARACTER}(캐릭터 로고 계열·기념일의 귀여운 버전) · {@code WORDMARK}(글자 로고 계열·일반 버전).
     *                   기본 제공 묶음은 그 벌, 올린 묶음은 운영자가 고른 유형(V255 — 고른 적 없으면 {@code CHARACTER})
     * @param dayKey     기념일 로고 묶음이면 그날의 키({@code newyear}·{@code samil} …) — 같은 키의 두 묶음이 짝이다. 평소 로고·올린 묶음은 null.
     *                   기념일 규칙이 이런 묶음을 가리키면 실제로는 {@code rules.holidayStyle} 에 맞는 짝이 보인다({@code AUTO} 면 평소 로고의 벌)
     * @param createdAt  올린 때 — 기본 제공 묶음은 null
     * @param logos      4칸의 {@code <img src>} — 그림 파일이면 {@code /media/…} 상대 주소, SVG 면 data URI, 빈 칸은 null
     * @param nameInLogo 로고에 사이트 이름이 들어 있음
     */
    public record Bundle(String id, String name, boolean builtIn, Instant createdAt, SiteLogoUrls logos, boolean nameInLogo, String style, String dayKey) {
    }

    /**
     * 보이기 규칙 전체 — 우선순위는 기념일(위 줄 먼저) → 성별 → 평소.
     *
     * @param holidayStyle 기념일 로고 유형(V252) — {@code AUTO}(평소 로고의 벌에 맞춤, 기본) · {@code CHARACTER}(늘 캐릭터 벌) · {@code WORDMARK}(늘 글자 벌).
     *                     기념일 규칙이 기본 제공 기념일 묶음을 가리킬 때 어느 짝을 보일지 정한다
     */
    public record Rules(String defaultBundleId, List<Anniversary> anniversaries, Gender gender, String holidayStyle) {
    }

    /** 기념일 한 줄 — {@code DATES} 면 {@code holidayName} 이 null, {@code HOLIDAY} 면 {@code startDate}·{@code endDate} 가 null 이고 {@code yearly} 는 false */
    public record Anniversary(String id, String name, boolean enabled, String mode, String startDate, String endDate, boolean yearly, String holidayName,
                              String bundleId) {
    }

    /** 성별 규칙 — 묶음 ID 또는 null */
    public record Gender(String male, String female) {
    }
}
