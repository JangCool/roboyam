import type { SiteLogoSet, SiteLogoVariant } from '~/utils/util-site-logo';

/**
 * 로고 보관함 + 보이기 규칙 API(`useSiteLogoLibraryApi`)가 주고받는 모양 — 서버 `SiteLogoLibraryResponse`·`SiteLogoBundleRequest`·`SiteLogoRulesRequest`(`docs/features/site-logo-library-contract.md`).
 * 로고 묶음 한 벌 = 4칸(`SiteLogoSet` — ① 한글·밝은 ② 한글·어두운 ③ 영문·밝은 ④ 영문·어두운) + 이름 + 「로고에 사이트 이름이 들어 있음」.
 * 묶음은 지우지 않는다(올린 로고는 보관해 두고 나중에도 고른다 — 사용자 결정). 기본 제공 묶음(`builtIn`)은 고치지 못한다.
 * 보이기 규칙의 우선순위: 기념일 → 성별 → 평소.
 */

/** 로고 묶음 한 벌 */
export interface SiteLogoBundle {
    /** 숫자 글자(기본 제공 `"1"`·`"2"`, 올린 묶음은 Snowflake) — 기본 제공인지는 id 가 아니라 `builtIn` 으로 가린다 */
    id: string;
    name: string;
    /** 기본 제공 묶음(「캐릭터 로고」·「글자 로고」) — 고치지 못한다 */
    builtIn: boolean;
    /** 올린 때(ISO 시각) — 기본 제공 묶음은 null 이거나 칸이 없다 */
    createdAt?: string | null;
    /** 4칸 — 값은 그림 주소(상대 주소 또는 data URI, 그대로 `<img src>`), 빈 칸은 null(대체 순서는 `UtilSiteLogo.resolve`) */
    logos: SiteLogoSet;
    /** 로고에 사이트 이름이 들어 있음 — 켜면 공개 헤더가 로고 옆 이름 글자를 붙이지 않는다 */
    nameInLogo: boolean;
    /** 기본 제공 묶음의 벌 — `CHARACTER`(캐릭터 로고 계열·기념일의 귀여운 버전) · `WORDMARK`(글자 로고 계열·일반 버전). 올린 묶음은 null */
    style?: SiteLogoStyle | null;
    /**
     * 기념일 로고 묶음이면 그날의 키(`newyear`·`samil` …) — 같은 키의 두 묶음(캐릭터 벌·글자 벌)이 짝이다. 평소 로고·올린 묶음은 null.
     * 기념일 규칙이 이런 묶음을 가리키면 실제로는 평소 로고의 벌에 맞는 짝이 보인다(`UtilSiteLogoRule.matchStyle` — 서버 `SiteLogoResolver.matchStyle`).
     */
    dayKey?: string | null;
}

/** 기본 제공 묶음의 벌 */
export type SiteLogoStyle = 'CHARACTER' | 'WORDMARK';

/** 기념일 날짜를 정하는 법 — `DATES` 날짜 직접(시작~끝) · `HOLIDAY` 쉬는 날 표에서 고르기 */
export type SiteLogoRuleDateMode = 'DATES' | 'HOLIDAY';

/** 기념일 규칙 한 줄 — 배열에서 위에 있는 줄이 먼저다 */
export interface SiteLogoAnniversaryRule {
    /** 서버가 저장할 때마다 새로 매긴다(요청의 id 는 쓰이지 않는다) — 화면이 더한 새 줄은 임시 id */
    id: string;
    name: string;
    /** 사용 켬·끔 */
    enabled: boolean;
    mode: SiteLogoRuleDateMode;
    /**
     * `DATES` 일 때 시작·끝(끝 날 포함). `yearly` 가 꺼져 있으면 `YYYY-MM-DD`, 켜져 있으면 월·일만 `MM-DD`
     * (끝이 시작보다 앞이면 해를 넘기는 기간 — 12-30 ~ 01-02). `HOLIDAY` 면 둘 다 null.
     */
    startDate: string | null;
    endDate: string | null;
    /** 매년 반복 — `DATES` 일 때만 뜻이 있다 */
    yearly: boolean;
    /** `HOLIDAY` 일 때 쉬는 날 표의 이름(설날·추석 …) — 그 이름의 날마다 해마다 걸린다. `DATES` 면 null */
    holidayName: string | null;
    /** 보일 묶음 */
    bundleId: string;
}

/** 성별 규칙 — 로그인한 회원에게만 적용. null = 고르지 않음(평소 로고) */
export interface SiteLogoGenderRule {
    male: string | null;
    female: string | null;
}

/** 보이기 규칙 전체 — 한 번에 저장한다 */
export interface SiteLogoRules {
    /** 평소 로고 — 묶음 id */
    defaultBundleId: string;
    anniversaries: SiteLogoAnniversaryRule[];
    gender: SiteLogoGenderRule;
}

/** 보관함(묶음 목록 — 기본 제공 먼저, 그다음 올린 차례)과 보이기 규칙 */
export interface SiteLogoLibraryResponse {
    bundles: SiteLogoBundle[];
    rules: SiteLogoRules;
}

/**
 * 묶음을 만들거나 고칠 때 화면이 한 칸에 넣는 것 — 그림 파일 · SVG 소스 · `KEEP`(지금 그대로 — 고치기에서만) · null(비움).
 * API 컴포저블이 파일을 먼저 올려(`POST /api/admin/files`, purpose=SITE_LOGO) `fileId` 로 바꿔 보낸다(`SiteLogoCellRequest`).
 */
export type SiteLogoCellInput = { file: File } | { svgSource: string } | 'KEEP' | null;

/** 서버로 가는 한 칸 — `{fileId}`(올려 둔 파일) · `{svgSource}` · `"KEEP"`(지금 그대로 — 만들기에서는 빈 칸) · null(비움) */
export type SiteLogoCellRequest = { fileId: string } | { svgSource: string } | 'KEEP' | null;

/** `POST …/bundles` · `PUT …/bundles/{id}` 본문(서버 `SiteLogoBundleRequest`) */
export interface SiteLogoBundleRequest {
    name: string;
    nameInLogo: boolean;
    cells: Record<SiteLogoVariant, SiteLogoCellRequest>;
}

/** 묶음 만들기·고치기 화면 입력 */
export interface SiteLogoBundleInput {
    /** 1~30자 */
    name: string;
    nameInLogo: boolean;
    cells: Record<SiteLogoVariant, SiteLogoCellInput>;
}

/** 묶음 이름 길이 한도(서버와 같다 — 1~30자) */
export const SITE_LOGO_BUNDLE_NAME_MAX = 30;

/** 기념일 규칙 이름 길이 한도 — 화면이 미리 보는 값(판정은 서버 `fields.anniversaries[i].name`) */
export const SITE_LOGO_RULE_NAME_MAX = 30;

/** 쉬는 날 표에서 고를 수 있는 한 줄 — 이름과 그 이름이 걸린 날짜들(`YYYY-MM-DD`) */
export interface SiteLogoHolidayOption {
    name: string;
    dates: string[];
}
