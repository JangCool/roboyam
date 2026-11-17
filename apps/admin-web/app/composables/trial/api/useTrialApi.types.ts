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
/**
 * 광고 결제 상태(서버 `TrialViews.Payment.status` — V242 「유료 결제 연결」) — 뽑기 전(금액 미정) · 0원(결제 없음) · 운영자 승인 대기(광고 심사 큐) ·
 * 온라인 결제 대기 · 수동 입금 대기 · 결제 끝(예약) · 취소·반려·만료(신청 건이 닫힘)
 */
export type TrialPaymentStatus = 'BEFORE_SELECTION' | 'NOT_REQUIRED' | 'AWAITING_APPROVAL' | 'AWAITING_PAYMENT' | 'AWAITING_DEPOSIT' | 'PAID' | 'CANCELED';
export const TRIAL_PAYMENT_STATUSES: readonly TrialPaymentStatus[] = ['BEFORE_SELECTION', 'NOT_REQUIRED', 'AWAITING_APPROVAL', 'AWAITING_PAYMENT', 'AWAITING_DEPOSIT', 'PAID', 'CANCELED'];

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

/** 회원 조각(서버 `TrialViews.Member`) — `memberNo` 는 회원 관리(`/members?memberNo=`)로 가는 번호 */
export interface TrialMember {
    id: string;
    memberNo?: string;
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
    /** 광고 결제 상태(V242) */
    paymentStatus: TrialPaymentStatus;
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
    /** 운영자가 취소한 줄(V242 — 발송 전 뽑힌 줄 취소·모집 취소). 취소하지 않았으면 null */
    cancelledAt: string | null;
    /** 취소 사유(회원에게도 보인다) */
    cancelReason: string | null;
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
    /** 운영자가 취소한 뽑힌 줄(V242 — `selected` 는 취소하지 않은 줄만) */
    cancelled: number;
}

/**
 * 광고 결제(서버 `TrialViews.Payment` — V242 「유료 결제 연결」). 관리자·광고주 모집 상세가 같이 쓴다.
 *  - `required` 뽑기를 마쳤고 금액 > 0 — 결제가 끝나야 입고·발송(아니면 409 `TRIAL_PAYMENT_PENDING`)
 *  - `shippingAllowed` 입고·발송을 할 수 있나(결제 필요 없음 또는 결제 끝)
 *  - `bizApplicationId` 광고 신청 건 — 관리자 광고 심사 `/ad-reviews?id=` · 광고주 결제 `/business/applications/{id}`
 *  - 금액은 승인 뒤엔 확정 금액, 그 전엔 뽑기 견적 · `method`·`deadlineAt` 은 승인 뒤만
 *  - `payOnline` 광고주가 사업자 센터에서 온라인 결제를 할 수 있다(온라인 결제 대기 · 기한 전 · 광고주 회원이 그 건의 주인)
 */
export interface TrialPayment {
    required: boolean;
    status: TrialPaymentStatus;
    shippingAllowed: boolean;
    bizApplicationId: string | null;
    bizApplicationNo: string | null;
    /** 신청 건 저장 상태(SUBMITTED·AWAITING_PAYMENT·… — 화면 보조) */
    bizStatus: string | null;
    supply: number;
    vat: number;
    total: number;
    method: 'ONLINE' | 'MANUAL' | null;
    deadlineAt: string | null;
    paidAt: string | null;
    payOnline: boolean;
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
    /** 광고 결제 상태(V242) */
    payment: TrialPayment;
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
    /** 사용기 게시판 후보 — 서버 게시판 목록의 「체험단 사용기 받기」(`trialReviewsEnabled`)를 켠 활성 일반 게시판 */
    reviewBoards: { id: string; slug: string; name: string }[];
    /** 제공형 상한(설정 — 못 받으면 계약 기본 5만) */
    givePriceLimit: number;
    /** 사용기 기한 처음 값(설정) */
    defaultReviewDays: number;
    /**
     * 받지 못한 고를 거리 — 광고주 목록(`ad.read`) · 게시판 목록(`board.read`) · 체험단 설정. 권한이 없거나(403) 실패하면 빈 후보·기본값으로 그리고 서랍이 안내한다
     */
    unavailable: TrialFormOptionSource[];
}

/** 모집 서랍 고를 거리의 출처 */
export type TrialFormOptionSource = 'advertisers' | 'boards' | 'settings';

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

/** `POST /api/admin/trials/participants/{id}/cancel` — 뽑힌 줄 운영자 취소(발송 전만 · 진행 중 모집만). 사유 1~200자(400 `fields.reason`) → 바뀐 상세 */
export interface AdminTrialCancelParticipantRequest {
    reason: string;
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
