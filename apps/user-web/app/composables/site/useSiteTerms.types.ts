/**
 * 서비스 이름(용어) — 운영자가 각 기능 설정 화면의 이름 구획에서 정하는 기능 이름 6개와 운영진 역할 화면의 공식 뱃지 이름 2개(ADM-001) (`GET /api/site/terms`, 라운드3 계약 E + 4차 계약 F `ADULT_LABEL`).
 * 바꾸지 않은 이름은 기본값(지금의 글자 그대로, `siteTerms.*` 로케일 키(`UtilSiteTerms.DEFAULT_KEYS`))이다 — 서버가 8개를 모두 채워 주지만 화면은 없는 키·깨진 값에도 기본값으로 읽는다.
 * (옛 `MENU_TOPIC`·`MENU_PLAZA`·`MENU_NOTICE` 는 없어졌다 — 메뉴 그룹 이름은 메뉴 구성이 주인이고 `GET /api/menu` 로 온다. 서버가 옛 응답에 실어도 `UtilSiteTerms.sanitize` 가 모르는 키라 버린다.)
 */

/** 용어 키 8개 — 새 키는 서버 `SiteTerm` 열거형에 먼저 생기므로 화면은 모르는 키를 건너뛴다 */
export type SiteTermKey =
    | 'GROUP_BUY'
    | 'GROUPS'
    | 'ITEM_SHOP'
    | 'POPULAR'
    | 'LATEST'
    /** 19금 표시의 이름(기본 "19금") — 글·목록·상세의 표시, 글쓰기의 체크, 성인 확인 안내가 쓴다 */
    | 'ADULT_LABEL'
    /** 최고 관리자 공식 뱃지의 글자(기본 "운영자", ADM-001) — 글쓴이 이름 옆 뱃지와 등급 안내 문장이 쓴다 */
    | 'ADMIN_BADGE'
    /** 운영진 공식 뱃지의 글자(기본 "운영진", ADM-001) — 글쓴이 이름 옆 뱃지와 등급 안내·지정 알림·지킴이 종료 안내 문장이 쓴다 */
    | 'STAFF_BADGE';

/** 키마다 이름이 하나씩 채워진 표 */
export type SiteTerms = Record<SiteTermKey, string>;

/** `GET /api/site/terms` 응답 */
export interface SiteTermsResponse {
    terms: Partial<Record<SiteTermKey, string>>;
    /** 사이트 이름(설치 마법사·운영자 설정) — 탭 제목에 붙는다. 옛 서버 응답에는 없을 수 있다 */
    siteName?: string;
    /** 운영자가 올린 사이트 아이콘의 주소들(docs/api-site-icon.md) — 올리지 않았으면 null/없음(기본 아이콘을 그대로 둔다). 옛 서버 응답에는 없을 수 있다 */
    siteIcon?: { version?: string; favicon32?: string; favicon48?: string; appleTouch?: string } | null;
    /** 사이트 슬로건(로고 밑 한 줄 소개, 0~100자) — 없으면 null(그 줄을 그리지 않는다). 옛 서버 응답에는 칸이 없을 수 있다 */
    tagline?: string | null;
}
