/**
 * 핫딜 설정(`deal_settings` 한 행) — 계약 「핫딜 설정」 표(05 §3 보강 포함). 서버 DTO 는 이 칸 이름 그대로.
 * 숫자 칸의 범위·처음 값은 `UtilHotdeal.SETTINGS`(서버와 같은 규칙), 켬/끔 칸은 `ogFetchEnabled` 하나. 감사 `HOTDEAL_SETTINGS_UPDATE`(바뀐 칸만).
 * 종료 요청 보상 포인트·하루 지급 횟수는 포인트 정책(`point_settings.dealEndRequestPoints`·`dealEndRequestDailyLimit` — 포인트 › 포인트 정책) 소유라 여기서는 읽기만 한다.
 * OG 보안 값(시간 제한·크기·동시 수)은 설정 화면에 두지 않는다(서버 규칙 1).
 * 제휴 링크 방식 `affiliateMode`(D5)도 이 행의 칸이다(바꾸면 감사 `HOTDEAL_SETTINGS_UPDATE` 의 바뀐 칸). 사이트 제휴 몰 표는 따로 저장한다(`useHotdealSiteAffiliateApi`).
 */
import type { AffiliateMode } from '~/composables/hotdeal/api/useHotdealApi.types';

export interface HotdealSettingsNumbers {
    /** 의심으로 바꾸는 서로 다른 회원의 종료 요청 수 1~20(기본 3) */
    endRequestThreshold: number;
    /** 의심 뒤 자동 종료까지 유예 시간 0~168(기본 12, 0 = 자동 종료 안 함) */
    endGraceHours: number;
    /** 회원 한 명의 하루 종료 요청 한도 1~100(기본 20) */
    endRequestDailyLimit: number;
    /** 같은 작성자 글에 한 회원이 기간 안 보낼 수 있는 요청 수 0~20(기본 3, 0 = 없음) */
    endRequestPerAuthorLimit: number;
    /** 위 한도를 세는 기간 1~30일(기본 7) */
    endRequestPerAuthorDays: number;
    /** 글마다 「아직 진행 중」 최대 0~10(기본 2, 0 = 없음) — 넘으면 「운영자 확인 중」 */
    keepMaxCount: number;
    /** 회원 하루 핫딜 글 0~50(기본 5, 0 = 없음) */
    memberDailyPostLimit: number;
    /** 같은 판매처 연속 글 간격 0~1440분(기본 10, 0 = 없음) */
    sameStoreIntervalMinutes: number;
    /** 마감 없는 딜의 기간 종료 0~365일(기본 30, 0 = 끔) */
    autoExpireDays: number;
    /** 딜가·정상가·배송비 상한 1,000,000~1,000,000,000원(기본 1억) */
    priceMaxWon: number;
    /** 할인율 경고 50~99%(기본 90) — 넘으면 경고 + 가격 기준 필수 */
    discountWarnPercent: number;
    /** HOT 표시 추천 수 1~100(기본 10) */
    hotLikeThreshold: number;
    /** 위장 홍보(회원) — 같은 판매처 글 수 0~50(기본 3, 0 = 끔) */
    suspicionMinPosts: number;
    /** 위장 홍보(회원) — 세는 기간 1~90일(기본 7) */
    suspicionWindowDays: number;
    /** 위장 홍보(판매처) — 같은 비대형몰을 올린 서로 다른 회원 수 0~20(기본 3, 0 = 끔) */
    storeSuspicionMembers: number;
    /** 위장 홍보(판매처) — 세는 기간 1~90일(기본 30) */
    storeSuspicionWindowDays: number;
    /** 위장 홍보(판매처) — 그 회원들의 다른 핫딜 비율이 이 % 미만이면 후보 0~100(기본 20) */
    storeSuspicionOtherRatioPercent: number;
}

export type HotdealSettingsField = keyof HotdealSettingsNumbers;

export interface HotdealSettingsValues extends HotdealSettingsNumbers {
    /** 링크 OG 상품 이미지 가져오기(기본 켬) — 끄면 쓰기 화면에 「꺼짐」 안내, 직접 올리기만 */
    ogFetchEnabled: boolean;
    /** 제휴 링크 방식(기본 `MEMBER`) — 바꾸면 새 글부터 적용, 기존 글은 구매 링크 클릭 때 지금 방식으로 변환(저장된 `productUrl` 은 그대로) */
    affiliateMode: AffiliateMode;
}

/**
 * `GET /api/admin/hotdeal-settings/affiliate-impact?mode=` — 방식을 `mode` 로 바꾸면 영향받는 진행 중(ACTIVE·FLAGGED) 공개 글 수(저장하지 않는다).
 * `mode=SITE`: 개인 제휴 글 N(클릭 때 개인 제휴를 뗀다) · 그중 켠 사이트 제휴 몰 M(사이트 제휴로 바뀜, 나머지 N−M 은 제휴 없이 원래 링크).
 * `mode=MEMBER`: 개인 제휴 글 N(다시 작성자 링크로) · 지금 사이트 제휴로 바뀌던 글(`siteStoreActive`)은 원래 링크로.
 */
export interface HotdealAffiliateImpact {
    mode: AffiliateMode;
    /** 진행 중 개인 제휴 글 수 */
    memberAffiliateActive: number;
    /** 그중 상품·최종 호스트가 켠 사이트 제휴 몰인 글 수 */
    siteStoreMatched: number;
    /** 진행 중 글 중 켠 사이트 제휴 몰로 가는 전체 글 수 — 개인 제휴가 아닌 글 포함(관리자 덧붙임) */
    siteStoreActive: number;
}

/** `GET /api/admin/hotdeal-settings` · `PUT` 응답 */
export interface HotdealSettingsView extends HotdealSettingsValues {
    /** 요청자 보상 포인트 — 포인트 정책의 값(읽기 전용) */
    rewardPoints: number;
    /** 보상 하루 지급 횟수 — 포인트 정책의 값(읽기 전용, 관리자 덧붙임) */
    rewardDailyLimit: number;
    updatedAt: string | null;
    updatedBy: string | null;
}

/** `PUT /api/admin/hotdeal-settings` — 400 `fields.<칸>`(범위) */
export type HotdealSettingsRequest = HotdealSettingsValues;
