import type { DisplayValues } from '#layers/display-time/utils/util-time';
import type { SiteIconResponse, SiteOperatorPublicResponse } from '~/composables/site/api/useSiteInfoApi.types';
import type { BoardDisplaySettings } from '~/composables/board/api/useBoardApi.types';
import type { VisitCollectSwitch } from '#layers/analytics/utils/util-visit-signal';

/**
 * 화면이 쓰는 사이트 정보 — `GET /api/site/info` 응답을 `UtilSiteInfo.fromResponse` 로 걸러 둔 것(쓸 수 없는 칸은 null).
 * 링크 미리보기 이미지(`ogImageUrl`)는 서버가 `<head>` 에 직접 넣으므로 화면 상태에는 두지 않는다.
 */
export interface SiteInfo {
    /** 사이트 이름 — 1~50자. 없으면 null(화면은 `UtilSiteName.DEFAULT`) */
    siteName: string | null;
    /** 한 줄 소개 — 1~100자. 없으면 null(그 줄을 그리지 않는다) */
    tagline: string | null;
    /** 로고 `<img src>` — 상대 주소 또는 이미지 data URI. 없으면 null(이름 글자) */
    logoUrl: string | null;
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
}
