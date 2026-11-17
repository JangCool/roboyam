/**
 * 회원 칭호(공개 사이트) API 가 주고받는 모양 — 계약 `docs/features/member-title-contract.md` 「구현 결정」 7 그대로(칸 이름을 바꾸지 않는다 — 서버와 맞춘다).
 * 같은 세션에 만든 api 서버 DTO(`server/api/.../title/dto/response` — `MyTitlesView`·`TitleCatalogItemView`·`HallOfFameView`·`BoardTitleApplicationView`, 2026-11-17)와 칸 이름을 대조했다.
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO.
 */
import type { TitleTier } from '#layers/member-title/utils/util-title-icon';

/** 칩 색 — 보라 = 수여·심사, 초록 = 자동 */
export type TitleChipColor = 'PURPLE' | 'GREEN';

/** 받는 방법 — 자동 · 신청 심사 · 운영자 수여 */
export type TitleMethod = 'AUTO' | 'APPLY' | 'AWARD';

/** 신청 상태 */
export type TitleApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/**
 * 대표 칭호 한 개 — 작성자 DTO 의 `title`(`{ key, label, icon, tier, chip } | null`). 이달의 작가의 `label` 은 「11월의 작가」(가장 최근 받은 달)처럼 서버가 줄여 준다.
 * 「안 보이기」·제재 기간·휴면이면 서버가 null 로 준다.
 */
export interface AuthorTitle {
    key: string;
    label: string;
    /** 영어 표시 이름(서버 `labelEn` — 운영자가 넣지 않았으면 null, 화면은 `label` 로 되돌린다 · `useLocalizedName`) */
    labelEn?: string | null;
    /** 그림 키(`pen`·`trophy`·`wreath`·`mag`·`mic`·`popular`·`cal`·`medal`·`bulb`·`heart`) */
    icon: string;
    tier: TitleTier | null;
    chip: TitleChipColor;
}

/** 공개 프로필의 칭호 한 줄 — 같은 칭호를 여러 번 받았으면 `count`(「이달의 작가 ×4」)와 받은 기간(`periods` — 이달의 작가 `YYYY-MM`, 올해의 작가 `YYYY`, 그 밖 빈 배열) */
export interface ProfileTitle extends AuthorTitle {
    count: number;
    periods: string[];
}

/** 도감의 단계 한 칸 */
export interface TitleCatalogTier {
    tier: TitleTier;
    /** 이 단계가 되는 기준 값(인기글 진입 10·30·100번 등) */
    threshold: number;
    holders: number;
}

/** `GET /api/titles` 한 줄 — 칭호 도감(누구나) */
export interface TitleCatalogItem {
    key: string;
    label: string;
    labelEn?: string | null;
    icon: string;
    chip: TitleChipColor;
    method: TitleMethod;
    /** 동·은·금 단계가 있는 칭호인가 */
    tiered: boolean;
    /** 받는 방법 안내 한 줄(서버가 요청 언어로 준다) */
    howTo: string;
    /** 가진 회원 수 */
    holders: number;
    /** 가진 회원 비율(0~1) */
    ratio: number;
    tiers: TitleCatalogTier[];
}

/** 명예의 전당의 회원 한 명 — 계약은 「id·닉네임·아바타」. 칸 이름(`nickname`·`avatarUrl`)은 다른 공개 응답과 같게 **가정**했다 */
export interface HallOfFameMember {
    id: string;
    nickname: string;
    avatarUrl: string | null;
}

/** `GET /api/hall-of-fame` — 명예 작가 · 역대 올해의 작가(최근 해가 위) · 역대 이달의 작가(최근 달이 위) */
export interface HallOfFame {
    honorary: HallOfFameMember[];
    yearly: { year: string; members: HallOfFameMember[] }[];
    monthly: { month: string; members: HallOfFameMember[] }[];
}

/** 조건 한 줄(서버 `ConditionView`) — `label` 요청 언어, `current`·`target` 은 세는 조건만(그 밖 null) */
export interface TitleConditionCheck {
    type: string;
    label: string;
    met: boolean;
    current: number | null;
    target: number | null;
}

/** 다음 칭호(단계)까지의 진행(서버 `ProgressView`) — `current / target`. 다 받은 칭호는 빠진다 */
export interface MyTitleProgress {
    key: string;
    label: string;
    labelEn: string | null;
    icon: string;
    chip: TitleChipColor;
    /** 다음에 받을 단계(단계 없는 칭호는 null) */
    tier: TitleTier | null;
    current: number;
    target: number;
}

/** 신청형 칭호 한 줄(서버 `ApplicableView`) — 아직 없는 것만. 조건별 채움 · 지금 신청할 수 있나 · 다시 신청할 수 있는 날 · 심사 중인가 */
export interface MyApplicableTitle {
    key: string;
    label: string;
    labelEn: string | null;
    icon: string;
    chip: TitleChipColor;
    conditions: TitleConditionCheck[];
    canApply: boolean;
    /** 다시 신청할 수 있는 날(UTC ISO) — 없으면 null */
    reapplyAt: string | null;
    /** 심사 중인 신청이 있다 */
    pending: boolean;
}

/** 내 신청 한 줄(서버 `ApplicationView` — 최신 순 50개). 거절이면 `reason`·`reapplyAt` */
export interface MyTitleApplication {
    id: string;
    key: string;
    label: string;
    status: TitleApplicationStatus;
    reason: string | null;
    reapplyAt: string | null;
    createdAt: string;
}

/** 축하 창에 띄울 새로 받은 칭호(서버 `UnseenView`) — `ratio` = 이 칭호를 가진 회원 비율(0~1) */
export interface UnseenTitle extends AuthorTitle {
    ratio: number;
}

/** `GET /api/me/titles`(서버 `MyTitlesView`) — `PUT /api/me/titles/representative` 도 이 모양을 돌려준다 */
export interface MyTitles {
    /** 대표 칭호(고른 것, 고르지 않았으면 기본으로 잡힌 작가 칭호) — 「안 보이기」·없음이면 null */
    representative: { key: string } | null;
    /** 「안 보이기」를 골랐나 */
    hidden: boolean;
    owned: ProfileTitle[];
    progress: MyTitleProgress[];
    applicable: MyApplicableTitle[];
    applications: MyTitleApplication[];
    unseen: UnseenTitle[];
}

/** `PUT /api/me/titles/representative` — `key: null` 이면 기본(가장 높은 작가 칭호), `hidden: true` 면 안 보이기(key 무시) */
export interface RepresentativeTitleRequest {
    key: string | null;
    hidden: boolean;
}

/** 심사 사유 — 승인은 비워도 되고 거절은 필수(서버가 판정) */
export interface TitleReviewRequest {
    reason: string;
}

/**
 * 게시판 매니저의 칭호 신청 한 줄(서버 `BoardTitleApplicationView`) — `GET /api/boards/{slug}/staff/title-applications?status=` 는 이 줄의 배열.
 * 그 게시판에 연결된 칭호의 신청만, `mine` = 내 신청(심사할 수 없다), `conditions` = 신청자의 조건별 채움(지금 기준).
 */
export interface BoardTitleApplication {
    id: string;
    key: string;
    label: string;
    icon: string;
    chip: TitleChipColor;
    member: HallOfFameMember;
    status: TitleApplicationStatus;
    reason: string | null;
    reapplyAt: string | null;
    createdAt: string;
    mine: boolean;
    conditions: TitleConditionCheck[];
}
