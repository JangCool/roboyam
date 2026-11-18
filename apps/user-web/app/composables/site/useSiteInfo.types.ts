import type { DisplayValues } from '#layers/display-time/utils/util-time';
import type { SiteIconResponse, SiteOperatorPublicResponse } from '~/composables/site/api/useSiteInfoApi.types';
import type { BoardDisplaySettings } from '~/composables/board/api/useBoardApi.types';
import type { SiteGrade } from '~/composables/grade/api/useGradeApi.types';
import type { VisitCollectSwitch } from '#layers/analytics/utils/util-visit-signal';
import type { SiteLogoSet } from '~/utils/util-site-logo';

/** 로고 묶음 한 벌의 공개 모양 — 4칸과 「로고에 사이트 이름이 들어 있음」 */
export interface SiteLogoBundleView {
    logos: SiteLogoSet;
    nameIncluded: boolean;
}

/** 성별 규칙의 묶음 — 정해지지 않은 쪽은 null */
export interface SiteGenderLogos {
    male: SiteLogoBundleView | null;
    female: SiteLogoBundleView | null;
}

/**
 * 화면이 쓰는 사이트 정보 — `GET /api/site/info` 응답을 `UtilSiteInfo.fromResponse` 로 걸러 둔 것(쓸 수 없는 칸은 null).
 * 링크 미리보기 이미지(`ogImageUrl`)는 서버가 `<head>` 에 직접 넣으므로 화면 상태에는 두지 않는다.
 */
export interface SiteInfo {
    /** 사이트 이름 — 1~50자. 없으면 null(화면은 `UtilSiteName.DEFAULT`) */
    siteName: string | null;
    /** 한 줄 소개 — 1~100자. 없으면 null(그 줄을 그리지 않는다) */
    tagline: string | null;
    /** 로고 ① 칸과 같은 값(서버 하위 호환 칸) — 상대 주소 또는 이미지 data URI. 없으면 null. 화면은 `logos` 를 쓴다 */
    logoUrl: string | null;
    /** 로고 4칸 — 칸마다 상대 주소·이미지 data URI 또는 null. 네 칸이 모두 비면 이름 글자만 보인다(기본 그림으로 대신 채우지 않는다) */
    logos: SiteLogoSet;
    /** 로고 그림에 사이트 이름이 들어 있음(옆 이름 글자 숨김) — `true` 일 때만 켬 */
    logoNameIncluded: boolean;
    /**
     * 성별 규칙의 로고 묶음(`site-logo-library-contract.md`) — 기념일이 안 걸린 날이고 성별 묶음이 정해져 있을 때만 있다(아니면 null).
     * 로그인 회원의 성별이 남성·여성이고 그쪽 값이 있으면 `SiteBrand` 가 위 `logos`·`logoNameIncluded` 대신 이것을 쓴다.
     */
    genderLogos: SiteGenderLogos | null;
    /** 올린 아이콘 — 없으면 null */
    icon: SiteIconResponse | null;
    /** 표시 기본값(사용자 개인 값이 없을 때의 폴백) — 못 읽으면 null(시스템 폴백) */
    displayDefaults: DisplayValues | null;
    /** 공개 게시판 표시 기본값 — 못 읽으면 null(게시판 화면은 표준형·24시간) */
    boardDisplay: BoardDisplaySettings | null;
    /** 방문 수집 스위치 — 칸이 없으면(옛 서버) null, 그러면 방문 신호를 보내지 않는다(`plugins/04.analytics.client.ts`) */
    analytics: VisitCollectSwitch | null;
    /** 운영 주체(단계·연락 이메일) — 칸이 없으면(옛 서버) null. 연락 이메일이 없거나 쓸 수 없는 모양이면 `contactEmail` null(푸터 문의 줄을 숨긴다) */
    operator: SiteOperatorPublicResponse | null;
    /** 등급 목록(서열 순, grade-level-contract 5절) — 칸이 없으면(옛 서버) null, 그러면 기본 4키를 서열 1·3·6·9 의 도트로 그린다(`useGrades`) */
    grades: SiteGrade[] | null;
    /** 사이트 소개가 있는가 — true 일 때만 푸터 「사이트 소개」 링크(`/about`). 칸이 없으면(옛 서버) 숨김 */
    about?: boolean;
}
