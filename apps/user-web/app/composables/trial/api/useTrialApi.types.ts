/**
 * 체험단 API(공개 사이트) 가 주고받는 모양 — 서버 `TrialViews`(shared `com.community.trial.dto`)와 같은 이름(trial-group-contract 「구현 결정」 7·9).
 * 시각은 모두 UTC ISO, 돈은 원(정수), ID 는 Snowflake 문자열.
 */

/** 형태 — 제공형(받아서 가짐) · 대여형(반납) */
export type TrialForm = 'GIVE' | 'LEND';

/** 보내는 쪽 — 광고주가 직접(회원 동의로 이름·연락처·주소 제공) · 운영자가 대신(주소는 광고주에게 가지 않는다) */
export type TrialShipper = 'ADVERTISER' | 'OPERATOR';

/** 모집 상태(서버 `TrialStatus`) — 준비 · 모집 중 · 뽑는 중 · 진행 중 · 끝남 · 취소 */
export type TrialStatus = 'DRAFT' | 'RECRUITING' | 'SELECTING' | 'RUNNING' | 'CLOSED' | 'CANCELLED';

/** 체험단 페이지 탭(`GET /api/trials?status=`) — 모집 중(뽑는 중 포함) · 진행 중 · 끝난 모집 */
export type TrialListStatus = 'RECRUITING' | 'RUNNING' | 'CLOSED';

/** 리뷰어 칭호 단계(member-title-contract) — 금·은은 먼저 신청, 리뷰어 몫으로 센다 */
export type ReviewerTier = 'BRONZE' | 'SILVER' | 'GOLD';

/** 신청 동의 항목(서버 `TrialConsent`) — 모집 상세 `consents` 가 그 모집에 필요한 것을 준다 */
export type TrialConsent = 'PROVIDE_CONTACT' | 'UNRETURNED_CONTACT' | 'PRODUCT_CHARGE' | 'DAMAGE_CHARGE';

/** 신청 상태 — 신청 · 뽑힘 · 안 뽑힘 · 취소 */
export type TrialApplicationStatus = 'APPLIED' | 'SELECTED' | 'NOT_SELECTED' | 'CANCELLED';

/** 몫 — 리뷰어 몫 · 일반 몫 */
export type TrialQuota = 'REVIEWER' | 'GENERAL';

/** 파손 신고 상태 — 이의 기다림 · 회원 이의 · 이의 기한 지나 운영자 확인 · 판정 */
export type TrialDamageStatus = 'REPORTED' | 'OBJECTED' | 'AWAITING' | 'DECIDED';

/** 파손 판정(계약 「대여형 파손」 판정표) — 정상 마모 · 택배 파손 · 회원 과실 · 고의·숨김 반납 */
export type TrialDamageVerdict = 'NORMAL_WEAR' | 'COURIER' | 'MEMBER_FAULT' | 'INTENTIONAL';

/**
 * 신청을 막는 까닭(서버 `TrialMemberService` `blockedReason`) — 이미 신청 · 본인인증 필요 · AI·운영팀 계정 · 신청 금지(기능 제한) · 모집 중 아님 · 내 신청 창 전.
 * 비회원이면 상세의 `mine` 이 null(로그인 안내).
 */
export type TrialBlockedReason = 'APPLIED' | 'IDENTITY_REQUIRED' | 'EXCLUDED' | 'RESTRICTED' | 'NOT_RECRUITING' | 'NOT_OPEN';

/** 사이트 회원 조각(서버 `TrialViews.Member`) */
export interface TrialMember {
    id: string;
    nickname: string | null;
    avatarUrl: string | null;
}

/** 게시판 조각(서버 `TrialViews.BoardRef`) */
export interface TrialBoardRef {
    id: string;
    slug: string;
    name: string;
}

/** 사진 한 장(서버 `TrialViews.Photo`) — 종류 STOCK · PRE_SHIP · RETURN · DAMAGE */
export interface TrialPhoto {
    id: string;
    kind: string;
    url: string;
    createdAt: string;
}

/** 모집 카드(서버 `TrialViews.TrialCard`) — `ad` 늘 true(「광고」 딱지 + 광고주 이름). 금·은 리뷰어 먼저 신청 시작 시각 */
export interface TrialCard {
    id: string;
    title: string;
    productName: string;
    productPrice: number;
    form: TrialForm;
    status: TrialStatus;
    ad: boolean;
    advertiserName: string;
    imageUrl: string | null;
    recruitCount: number;
    applicantCount: number;
    /** 일반 신청 시작 */
    applyStartAt: string;
    applyEndAt: string;
    /** 리뷰어 금 먼저 신청 시작(시간이 0 이면 일반과 같다) */
    goldOpenAt: string;
    silverOpenAt: string;
    reviewBoard: TrialBoardRef;
}

/** 끝난 모집의 사용기 한 줄(서버 `TrialViews.Review`) */
export interface TrialReview {
    postId: string;
    title: string;
    boardSlug: string;
    author: TrialMember;
    createdAt: string;
}

/** 보는 회원의 신청 상태(서버 `TrialViews.Mine`) — 비회원이면 상세의 `mine` 이 null */
export interface TrialMine {
    applicationId: string | null;
    applicationStatus: TrialApplicationStatus | null;
    canApply: boolean;
    canCancel: boolean;
    /** 내가 신청할 수 있게 되는 때(리뷰어면 더 이르다) */
    applyOpenAt: string;
    quota: TrialQuota;
    reviewerTier: ReviewerTier | null;
    blockedReason: TrialBlockedReason | null;
}

/** `GET /api/trials/{id}` — 모집 상세(서버 `TrialViews.TrialPublicDetail`) */
export interface TrialPublicDetail {
    card: TrialCard;
    /** 설명(평문 — 줄바꿈 유지) */
    description: string;
    reviewDays: number;
    /** 대여형만(제공형 null) */
    returnDays: number | null;
    shipper: TrialShipper;
    reviewerShare: number;
    reviewerGoldHours: number;
    reviewerSilverHours: number;
    /** 이 모집 신청에 필요한 동의 항목(보내는 쪽·형태로 정해진다) */
    consents: TrialConsent[];
    /** 대여형 청구 한도(= 제품값) — 제공형 null */
    damageChargeLimit: number | null;
    mine: TrialMine | null;
    /** 끝난 모집이면 사용기 모아 보기, 그 밖은 빈 목록 */
    reviews: TrialReview[];
}

/** `POST /api/trials/{id}/applications` 본문 — 배송지 관리의 배송지 하나 + 동의 항목(빠지면 `400 fields.consents`) → `TrialMine` */
export interface TrialApplyRequest {
    addressId: string;
    consents: TrialConsent[];
}

/** 내 파손 신고 한 건(서버 `TrialViews.MyDamage`) — `canObject` 이의 기한 안이고 아직 이의하지 않았다 */
export interface MyTrialDamage {
    id: string;
    description: string;
    status: TrialDamageStatus;
    objectionDueAt: string;
    canObject: boolean;
    objection: string | null;
    verdict: TrialDamageVerdict | null;
    decisionNote: string | null;
    photos: TrialPhoto[];
    createdAt: string;
}

/** 뽑힌 내 줄(서버 `TrialViews.MyParticipation`) — `canReceive` 송장이 있고 아직 받지 않았다 · `canWrite` 연결 사용기가 없다(쓰러 가기 = `writeBoard` + 이 줄 ID) */
export interface MyTrialParticipation {
    id: string;
    carrier: string | null;
    trackingNo: string | null;
    shippedAt: string | null;
    receivedAt: string | null;
    receivedBy: 'MEMBER' | 'AUTO' | null;
    canReceive: boolean;
    reviewDueAt: string | null;
    reviewPostId: string | null;
    canWrite: boolean;
    writeBoard: TrialBoardRef;
    returnDueAt: string | null;
    returnedAt: string | null;
    kept: boolean | null;
    unreturnedAt: string | null;
    damageReports: MyTrialDamage[];
}

/** 내 체험단 한 줄(서버 `TrialViews.MyTrial`) — `GET /api/me/trials` 는 이것의 배열 */
export interface MyTrial {
    applicationId: string;
    applicationStatus: TrialApplicationStatus;
    quota: TrialQuota;
    appliedAt: string;
    canCancel: boolean;
    trial: TrialCard;
    participation: MyTrialParticipation | null;
}

/** 글쓰기 「뽑힌 체험단 사용기로 쓰기」 후보(서버 `TrialViews.Writable`) — `GET /api/me/trials/writable?board=` */
export interface TrialWritable {
    participantId: string;
    trialId: string;
    trialTitle: string;
    board: TrialBoardRef;
}

/** `POST /api/me/trials/damage-reports/{id}/objection` 본문(서버 `ObjectionRequest`) */
export interface TrialObjectionRequest {
    text: string;
}

/* ── 구매 인증(서버 `TrialViews.Proof` — 게시판 스텝 「구매 인증」) ── */

export type PurchaseProofStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** 구매 인증 한 줄 — 사진은 따로 `GET …/purchase-proofs/{id}/photo`(확인하는 사람만 · 열람 기록 · 파기되면 410). `purged` 면 사진 없음 */
export interface PurchaseProof {
    id: string;
    postId: string;
    postTitle: string;
    board: TrialBoardRef | null;
    author: TrialMember | null;
    status: PurchaseProofStatus;
    rejectReason: string | null;
    purged: boolean;
    createdAt: string;
    reviewedAt: string | null;
}

/** 구매 인증 올리기 결과(서버 `ProofResult`) */
export interface PurchaseProofResult {
    id: string;
    status: PurchaseProofStatus;
}
