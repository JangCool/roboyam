package com.community.site.dto.response;

import com.community.site.dto.SiteLogoUrls;

/**
 * {@code GET /api/site/info}(비로그인) — 공개 사이트가 읽는 사이트 설정. <b>허용한 필드만</b> 담는다(이메일·설치 정보·내부 ID·공개 주소 원문 없음).
 *
 * @param siteName 사이트 이름. 설정 행이 없으면(설치 전) null
 * @param tagline 한 줄 소개. 없으면 null
 * @param logoUrl 로고의 {@code <img src>} — ① 칸과 같은 값(하위 호환) — 그림 파일이면 저장소 주소, SVG 로고면 공개 주소 {@code /api/site/logos/koLight.svg?v=지문}(2026-11-16 — 예전엔 data URI). 없으면 null
 * @param icon 올려 둔 사이트 아이콘. 없으면 null(기본 아이콘)
 * @param ogImageUrl 링크 미리보기 이미지 — 올려 둔 것, 없으면 기본 이미지. 공개 주소가 있으면 절대 주소
 * @param displayDefaults 표시 기본값
 * @param boardDisplay 공개 게시판 표시 기본값(날짜 표시 방식·새 글 기준). 게시판 기본 설정 행이 없으면(설치 전) 처음 값
 * @param theme 운영자가 고른 공개 사이트 테마 id({@code SiteThemes}) — 설정 행이 없거나 목록에 없는 값이면 {@code default}. {@code GET /api/site/theme} 과 같은 값
 * @param analytics 방문 수집 스위치(site-analytics-contract) — 모두 기본 꺼짐
 * @param operator 운영 주체의 단계·연락 이메일·푸터 안내 문구(terms-operator-contract) — 운영 주체 행이 없으면(마이그레이션 전) 개인 단계·이메일 null·안내 처음 값
 * @param grades   등급 목록(서열 순 — V196, grade-level-contract 5절): 키·이름·서열·도트 캐릭터
 * @param about    사이트 소개가 있는가(V203) — 공개 푸터의 「사이트 소개」 링크를 보일지. 본문은 {@code GET /api/site/about}
 * @param logos    <b>오늘의 성별 무관 묶음</b>(V249 로고 보이기 규칙 — 기념일이 걸리면 그 묶음, 아니면 기본 로고)의 4칸( {@code koLight}·{@code koDark}·{@code enLight}·{@code enDark}, 그림 파일은 저장소 주소, SVG 칸은 {@code /api/site/logos/{칸}.svg?b=묶음&v=지문} — 첫 화면 HTML 에 SVG 본문을 싣지 않는다). 빈 칸은 null — 대체 순서는 화면 규칙({@code UtilSiteLogo.resolve}).
 *                 설정 행이 없으면 모두 null. {@code logoUrl} 은 하위 호환으로 남긴 ① {@code koLight} 와 같은 값
 * @param logoNameIncluded 그 묶음의 로고 그림에 사이트 이름이 들어 있는가 — 켜져 있으면 로고 옆 이름 글자를 숨긴다
 * @param genderLogos 성별 규칙의 묶음(V249) — 평소에는 남성·여성 가운데 하나라도 성별 로고가 정해져 있을 때, 기념일에는 성별 로고의 유형에 맞는 그날의 벌(V252)이
 *                 {@code logos} 와 <b>다른 묶음</b>인 성별이 하나라도 있을 때만 있다(아니면 null — 응답에 없다).
 *                 화면이 로그인한 회원의 성별({@code /api/auth/me} 의 {@code user.gender})로 고르고, 그 성별 칸이 null 이면 {@code logos} 를 쓴다
 */
public record SiteInfoResponse(String siteName, String tagline, String logoUrl, SiteIconResponse icon, String ogImageUrl,
                               SiteDisplayDefaultsView displayDefaults, BoardDisplayView boardDisplay, String theme, SiteAnalyticsView analytics,
                               SiteOperatorPublicView operator, java.util.List<SiteGradeView> grades, boolean about,
                               SiteLogoUrls logos, boolean logoNameIncluded, SiteGenderLogosView genderLogos) {

    /** 성별 묶음 없이 */
    public SiteInfoResponse(String siteName, String tagline, String logoUrl, SiteIconResponse icon, String ogImageUrl,
                            SiteDisplayDefaultsView displayDefaults, BoardDisplayView boardDisplay, String theme, SiteAnalyticsView analytics,
                            SiteOperatorPublicView operator, java.util.List<SiteGradeView> grades, boolean about,
                            SiteLogoUrls logos, boolean logoNameIncluded) {
        this(siteName, tagline, logoUrl, icon, ogImageUrl, displayDefaults, boardDisplay, theme, analytics, operator, grades, about, logos, logoNameIncluded, null);
    }
}
