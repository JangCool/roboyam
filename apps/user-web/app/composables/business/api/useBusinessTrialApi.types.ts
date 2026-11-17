/**
 * 사업자 센터 「체험단」(광고주 화면 — trial-group-contract 「구현 결정」 8) 이 주고받는 모양 — 서버 `TrialViews`(`BusinessTrialController`)와 같은 이름.
 * 광고주에게 보이는 회원 주소·연락처는 동의 범위 안(보내는 쪽 광고주 + 뽑힘, 또는 과실·미반납으로 이름·연락처를 넘긴 뒤)만, 파기됐으면 칸이 null 이고 `addressPurged` true.
 */
import type {
    ReviewerTier,
    TrialApplicationStatus,
    TrialBoardRef,
    TrialDamageStatus,
    TrialDamageVerdict,
    TrialForm,
    TrialMember,
    TrialPhoto,
    TrialQuota,
    TrialShipper,
    TrialStatus,
} from '~/composables/trial/api/useTrialApi.types';

/** 광고 결제 상태(서버 `TrialViews.Payment.status` — V242 「유료 결제 연결」) */
export type BizTrialPaymentStatus = 'BEFORE_SELECTION' | 'NOT_REQUIRED' | 'AWAITING_APPROVAL' | 'AWAITING_PAYMENT' | 'AWAITING_DEPOSIT' | 'PAID' | 'CANCELED';

/**
 * 광고 결제(서버 `TrialViews.Payment`) — `shippingAllowed` false 면 송장을 넣을 수 없다(서버 409 `TRIAL_PAYMENT_PENDING`).
 * `payOnline` 이면 신청 건 화면(`/business/applications/{bizApplicationId}`)에서 온라인 결제. 금액은 승인 뒤 확정 금액, 그 전엔 뽑기 견적
 */
export interface BizTrialPayment {
    required: boolean;
    status: BizTrialPaymentStatus;
    shippingAllowed: boolean;
    bizApplicationId: string | null;
    bizApplicationNo: string | null;
    bizStatus: string | null;
    supply: number;
    vat: number;
    total: number;
    method: 'ONLINE' | 'MANUAL' | null;
    deadlineAt: string | null;
    paidAt: string | null;
    payOnline: boolean;
}

/** 이름 있는 조각(서버 `TrialViews.Ref`) */
export interface TrialRef {
    id: string;
    name: string;
}

/** 내 모집 한 줄(서버 `TrialViews.TrialRow`) — `GET /api/business/trials` 는 이것의 배열 */
export interface BizTrialRow {
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
    /** 뽑기를 마친 인원(전이면 null) */
    selectedCount: number | null;
    applyStartAt: string;
    applyEndAt: string;
    reviewBoard: TrialBoardRef;
    amount: number | null;
    createdAt: string;
    /** 광고 결제 상태(V242) */
    paymentStatus: BizTrialPaymentStatus;
}

/** 뽑힌 한 줄(서버 `TrialViews.Participant`) — 사진은 종류(`kind` PRE_SHIP · RETURN · …)로 가른다 */
export interface BizTrialParticipant {
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
    photos: TrialPhoto[];
    /** 운영자가 취소한 줄(V242 — 보내지 않는다). 아니면 null */
    cancelledAt: string | null;
    cancelReason: string | null;
}

/** 파손 신고(서버 `TrialViews.DamageReport`) */
export interface BizTrialDamageReport {
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

/** 모집 상세(서버 `TrialViews.TrialDetail` — 광고주에게 필요한 칸만 적었다) */
export interface BizTrialDetail {
    id: string;
    title: string;
    productName: string;
    productPrice: number;
    form: TrialForm;
    shipper: TrialShipper;
    status: TrialStatus;
    advertiser: TrialRef;
    reviewBoard: TrialBoardRef;
    recruitCount: number;
    reviewerShare: number;
    reviewerSlots: number;
    applyStartAt: string;
    applyEndAt: string;
    reviewDays: number;
    returnDays: number | null;
    description: string;
    imageUrl: string | null;
    selectedCount: number | null;
    participants: BizTrialParticipant[];
    damageReports: BizTrialDamageReport[];
    /** 광고 결제(V242) */
    payment: BizTrialPayment;
}

/** 신청자 숫자(서버 `TrialViews.ApplicantStats`) — 사용기 수 · 평균 추천 · 약속 지킨 비율 %(판정 없으면 null) · 판정된 줄 수 */
export interface BizTrialApplicantStats {
    reviewPosts: number;
    averageLikes: number;
    keptRate: number | null;
    judged: number;
}

/** 신청자 한 줄(서버 `TrialViews.Applicant`) — 후보 추천만(최종 선정은 운영자) */
export interface BizTrialApplicant {
    id: string;
    member: TrialMember;
    status: TrialApplicationStatus;
    quota: TrialQuota;
    reviewerTier: ReviewerTier | null;
    recommended: boolean;
    stats: BizTrialApplicantStats;
    consents: string[];
    createdAt: string;
}

/** 신청자 목록(서버 `TrialViews.ApplicantPage`) */
export interface BizTrialApplicantPage {
    recruitCount: number;
    reviewerSlots: number;
    generalSlots: number;
    items: BizTrialApplicant[];
}

/** `GET /api/business/trials/{id}` 응답(서버 `TrialViews.BusinessTrialDetail`) */
export interface BizTrialView {
    trial: BizTrialDetail;
    applicants: BizTrialApplicantPage;
}

/** 사진 올리기 종류(광고주) — 발송 전 · 반납 · 파손 */
export type BizTrialPhotoKind = 'PRE_SHIP' | 'RETURN' | 'DAMAGE';

/** 올린 사진(서버 `TrialViews.Uploaded`) — `id` 를 처리 본문 `photoIds` 에 싣는다 */
export interface BizTrialUploaded {
    id: string;
    kind: string;
    key: string;
    url: string;
}

/** `PUT /api/business/trials/participants/{id}/shipment` 본문 — 발송 전 사진 1장 이상 필수(`400 fields.photoIds`) */
export interface BizTrialShipmentRequest {
    carrier: string;
    trackingNo: string;
    photoIds: string[];
}

/** `POST /api/business/trials/participants/{id}/returned` 본문 — 반납 사진(대여형) */
export interface BizTrialReturnedRequest {
    photoIds: string[];
}

/** `POST /api/business/trials/participants/{id}/damage-reports` 본문 — 사진 1장 이상 + 설명 */
export interface BizTrialDamageRequest {
    description: string;
    photoIds: string[];
}
