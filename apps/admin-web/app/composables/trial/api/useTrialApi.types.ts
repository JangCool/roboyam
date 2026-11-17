/**
 * 체험단 관리 API(관리자) 가 주고받는 모양 — 서버 `TrialViews`(shared `com.community.trial.dto`)·`TrialSettingEntity.Values`·`TrialSettingsService.Request` 와 같은 이름
 * (trial-group-contract 「구현 결정」 10, admin `AdminTrialController`). 시각은 UTC ISO, 돈은 원(정수), ID 는 Snowflake 문자열.
 */

export type TrialForm = 'GIVE' | 'LEND';
export type TrialShipper = 'ADVERTISER' | 'OPERATOR';
export type TrialStatus = 'DRAFT' | 'RECRUITING' | 'SELECTING' | 'RUNNING' | 'CLOSED' | 'CANCELLED';
export type ReviewerTier = 'BRONZE' | 'SILVER' | 'GOLD';
export type TrialQuota = 'REVIEWER' | 'GENERAL';
export type TrialApplicationStatus = 'APPLIED' | 'SELECTED' | 'NOT_SELECTED' | 'CANCELLED';
export type TrialDamageStatus = 'REPORTED' | 'OBJECTED' | 'AWAITING' | 'DECIDED';
/** 판정표 4종(계약 「대여형 파손」) — 정상 마모 · 택배 파손 · 회원 과실 · 고의·숨김 반납 */
export type TrialDamageVerdict = 'NORMAL_WEAR' | 'COURIER' | 'MEMBER_FAULT' | 'INTENTIONAL';
/** 값 매기는 단위(가격 기간표 줄마다 — 계약 「돈」) — 모집 건당 · 뽑힌 1명당 */
export type TrialPriceUnit = 'PER_RECRUIT' | 'PER_PERSON';
/** 사진 종류 — 입고 · 발송 전 · 반납 · 파손 */
export type TrialPhotoKind = 'STOCK' | 'PRE_SHIP' | 'RETURN' | 'DAMAGE';
/** 고치기의 상태 바꾸기(서버 `TrialForm.action`) — 모집 열기(준비 → 모집 중) · 신청 마감(→ 뽑는 중) · 끝내기(진행 중 → 끝남) · 취소(뽑기 전). 있으면 칸은 바꾸지 않는다 */
export type TrialAction = 'PUBLISH' | 'CLOSE_APPLY' | 'CLOSE' | 'CANCEL';

/** 이름 있는 조각(서버 `TrialViews.Ref`) */
export interface TrialRef {
    id: string;
    name: string;
}

/** 게시판 조각(서버 `TrialViews.BoardRef`) */
export interface TrialBoardRef {
    id: string;
    slug: string;
    name: string;
}

/** 회원 조각(서버 `TrialViews.Member`) */
export interface TrialMember {
    id: string;
    nickname: string | null;
    avatarUrl: string | null;
}

/** 사진 한 장(서버 `TrialViews.Photo`) */
export interface TrialPhoto {
    id: string;
    kind: TrialPhotoKind;
    url: string;
    createdAt: string;
}

/** 모집 한 줄(서버 `TrialViews.TrialRow`) — `GET /api/admin/trials?status=` 는 이것의 배열 */
export interface AdminTrialRow {
    id: string;
    title: string;
    productName: string;
    form: TrialForm;
    shipper: TrialShipper;
    status: TrialStatus;
    advertiser: TrialRef;
    recruitCount: number;
    reviewerShare: number;
    applicantCount: number;
    /** 뽑은 인원(뽑기 전 null) */
    selectedCount: number | null;
    applyStartAt: string;
    applyEndAt: string;
    reviewBoard: TrialBoardRef;
    /** 광고 상품 금액(뽑기 뒤 확정 — 전 null) */
    amount: number | null;
    createdAt: string;
}

/** 입고(서버 `TrialViews.Stock` — 운영자가 대신 보낼 때만). `keepUntil` = 입고 + 보관 일수 */
export interface AdminTrialStock {
    quantity: number;
    note: string | null;
    receivedAt: string;
    keepUntil: string;
    returnCarrier: string | null;
    returnTrackingNo: string | null;
    returnShippedAt: string | null;
    photos: TrialPhoto[];
}

/** 뽑힌 한 줄(서버 `TrialViews.Participant`) — 받는 분·연락처·주소는 볼 수 있을 때만(운영자 = 운영자가 보내는 모집), 파기 뒤 null */
export interface AdminTrialParticipant {
    id: string;
    applicationId: string;
    member: TrialMember;
    quota: TrialQuota;
    recipient: string | null;
    phone: string | null;
    postcode: string | null;
    address1: string | null;
    address2: string | null;
    memo: string | null;
    addressPurged: boolean;
    carrier: string | null;
    trackingNo: string | null;
    shippedAt: string | null;
    shippedBy: string | null;
    receivedAt: string | null;
    receivedBy: 'MEMBER' | 'AUTO' | null;
    reviewPost: TrialRef | null;
    reviewDueAt: string | null;
    returnDueAt: string | null;
    returnedAt: string | null;
    kept: boolean | null;
    reviewLateAt: string | null;
    unreturnedAt: string | null;
    contactProvidedAt: string | null;
    /** 그 줄의 사진(발송 전 · 반납 · 파손 — `kind` 로 가른다) */
    photos: TrialPhoto[];
}

/** 파손 신고(서버 `TrialViews.DamageReport`) */
export interface AdminTrialDamageReport {
    id: string;
    participantId: string;
    member: TrialMember | null;
    reporterType: 'OPERATOR' | 'ADVERTISER';
    description: string;
    status: TrialDamageStatus;
    objectionDueAt: string;
    objection: string | null;
    objectedAt: string | null;
    verdict: TrialDamageVerdict | null;
    decisionNote: string | null;
    decidedAt: string | null;
    photos: TrialPhoto[];
    createdAt: string;
}

/** 단계별 수(서버 `TrialViews.Counts`) */
export interface AdminTrialCounts {
    applied: number;
    selected: number;
    shipped: number;
    received: number;
    reviewed: number;
    returned: number;
    kept: number;
    unreturned: number;
}

/** `GET /api/admin/trials/{id}` — 모집 상세(서버 `TrialViews.TrialDetail`) */
export interface AdminTrialDetail {
    id: string;
    title: string;
    productName: string;
    productPrice: number;
    form: TrialForm;
    shipper: TrialShipper;
    status: TrialStatus;
    advertiser: TrialRef;
    bizApplication: TrialRef | null;
    reviewBoard: TrialBoardRef;
    recruitCount: number;
    reviewerShare: number;
    reviewerSlots: number;
    reviewerGoldHours: number;
    reviewerSilverHours: number;
    applyStartAt: string;
    applyEndAt: string;
    reviewDays: number;
    returnDays: number | null;
    description: string;
    imageKey: string | null;
    imageUrl: string | null;
    /** 만들 때의 요금표 값 사본 */
    priceUnit: TrialPriceUnit;
    unitPrice: number;
    shippingFeePerPerson: number;
    selectedCount: number | null;
    amount: number | null;
    counts: AdminTrialCounts;
    stock: AdminTrialStock | null;
    participants: AdminTrialParticipant[];
    damageReports: AdminTrialDamageReport[];
    createdAt: string;
    updatedAt: string;
}

/**
 * `POST /api/admin/trials` · `PUT /api/admin/trials/{id}` 본문(서버 `TrialViews.TrialForm`) — 칸 오류는 `400 fields.<칸>`(제공형 상한 넘음은 서버가 정한 칸).
 * `action` 은 고치기에서만(있으면 상태만 바꾼다). 만들면 준비(DRAFT)로 시작한다.
 */
export interface AdminTrialRequest {
    advertiserId: string;
    title: string;
    productName: string;
    productPrice: number;
    form: TrialForm;
    reviewBoardId: string;
    recruitCount: number;
    reviewerShare: number;
    reviewerGoldHours: number;
    reviewerSilverHours: number;
    applyStartAt: string;
    applyEndAt: string;
    reviewDays: number;
    /** 대여형만(제공형 null) */
    returnDays: number | null;
    shipper: TrialShipper;
    description: string;
    imageKey: string | null;
    action?: TrialAction | null;
}

/** 만들기 서랍 고를 거리 — 화면이 광고주 목록·게시판 목록·체험단 설정으로 모은다(`useTrialFormOptions`) */
export interface AdminTrialFormOptions {
    advertisers: { id: string; name: string }[];
    /** 사용기 게시판 후보 — 서버 게시판 목록에 「체험단 사용기 받기」 칸이 있으면 켠 것만, 없으면 활성 일반 게시판 전부 */
    reviewBoards: { id: string; slug: string; name: string }[];
    /** 제공형 상한(설정 — 못 받으면 계약 기본 5만) */
    givePriceLimit: number;
    /** 사용기 기한 처음 값(설정) */
    defaultReviewDays: number;
}

/** 신청자 숫자(서버 `TrialViews.ApplicantStats`) — 사용기 수 · 평균 추천 · 약속 지킨 비율 %(판정 없으면 null) · 판정된 줄 수 */
export interface AdminTrialApplicantStats {
    reviewPosts: number;
    averageLikes: number;
    keptRate: number | null;
    judged: number;
}

/** 신청자 한 줄(서버 `TrialViews.Applicant`) */
export interface AdminTrialApplication {
    id: string;
    member: TrialMember;
    status: TrialApplicationStatus;
    quota: TrialQuota;
    reviewerTier: ReviewerTier | null;
    recommended: boolean;
    stats: AdminTrialApplicantStats;
    consents: string[];
    createdAt: string;
}

/** `GET /api/admin/trials/{id}/applications`(서버 `TrialViews.ApplicantPage`) — 리뷰어 몫·일반 몫 인원 */
export interface AdminTrialApplicantPage {
    recruitCount: number;
    reviewerSlots: number;
    generalSlots: number;
    items: AdminTrialApplication[];
}

/** 올린 사진·대표 그림(서버 `TrialViews.Uploaded`) */
export interface TrialUploaded {
    id: string;
    kind: string;
    key: string;
    url: string;
}

/** `POST /api/admin/trials/{id}/select` — 리뷰어 몫·일반 몫 따로 세며 인원을 넘으면 400 → 바뀐 상세 */
export interface AdminTrialSelectRequest {
    applicationIds: string[];
}

/** `POST /api/admin/trials/{id}/stock` — 입고(사진 1장 이상 필수) → 바뀐 상세 */
export interface AdminTrialStockRequest {
    quantity: number;
    note: string | null;
    photoIds: string[];
}

/** 송장 — `PUT …/participants/{id}/shipment` · 광고주 반송 `POST …/{id}/return-shipment`(서버 `ShipmentRequest` — 사진은 광고주 발송에만) */
export interface AdminTrialShipmentRequest {
    carrier: string;
    trackingNo: string;
    photoIds?: string[];
}

/** `POST …/participants/{id}/returned` — 반납 받음 + 사진(서버 `PhotosRequest`) */
export interface AdminTrialReturnedRequest {
    photoIds: string[];
}

/** `POST …/participants/{id}/damage-reports` */
export interface AdminTrialDamageRequest {
    description: string;
    photoIds: string[];
}

/** `POST /api/admin/trials/damage-reports/{id}/decide` */
export interface AdminTrialDecideRequest {
    verdict: TrialDamageVerdict;
    note: string;
}

/** 체험단 설정(서버 `TrialSettingEntity.Values` — 행 하나). PUT 본문도 같은 칸(`TrialSettingsService.Request`) */
export interface TrialSettings {
    givePriceLimit: number;
    disclosureText: string;
    sponsorDisclosureText: string;
    returnRemindFirstDays: number;
    returnRemindSecondDays: number;
    unreturnedDays: number;
    reviewDays: number;
    lateBanDays: number;
    lateRevokeStrikes: number;
    proofBonusPoints: number;
    proofPhotoDays: number;
    stockKeepDays: number;
}
