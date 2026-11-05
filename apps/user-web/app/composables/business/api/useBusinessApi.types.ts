/**
 * 사업자 센터 API 의 주고받는 모양 — 신원(V111)·신청 건(V112, 서버 `BizMemberView`). 요구사항 정본 `docs/plans/biz-ad-hotdeal/00-overview.md` 「★ 확정」.
 * ⚠ ID 는 문자열이다(Snowflake 64비트). 날짜(`YYYY-MM-DD`)는 서비스 달력 날짜 글자, 시각은 ISO-8601 순간이다.
 */
import type { BusinessUserStatus } from '~/composables/auth/api/useAuthApi.types';
import type { AddressValue } from '~/components/address/AddressInput.types';
import type { PaymentCheckout } from '~/composables/payment/api/usePaymentApi.types';
import type { DealEndRequests, DealEndedBy, DealView } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { AdPlacement } from '~/composables/ad/usePublicAds.types';

/* ── 사업자 신원 ─────────────────────────────────────────── */

/** 사업자 신원 상태 — 로그인 정보의 `businessStatus` 와 같은 값 */
export type BusinessProfileStatus = BusinessUserStatus;

/** 신청 건 반려 사유 코드 — 서버 `BizRejectCode`(관리자 심사 화면과 같은 묶음). 글은 `rejectReason` 으로 따로 온다 */
export type BusinessRejectCode = 'CREATIVE_SPEC' | 'POLICY_VIOLATION' | 'LINK_BROKEN' | 'PERIOD_UNAVAILABLE' | 'FALSE_PRICE' | 'OTHER';

/** 사업자 신원 반려 사유 코드 — 서버 `BusinessRejectCode`(관리자 화면과 같은 값) */
export type BusinessProfileRejectCode = 'DOC_UNREADABLE' | 'INFO_MISMATCH' | 'NTS_CLOSED' | 'DUPLICATE' | 'OTHER';

/** 첨부 파일(사업자등록증 사본·광고 이미지) — 올리기 API 가 준다 */
export interface BusinessFile {
    fileId: string;
    name: string;
    /** 바이트 */
    size: number;
    contentType: string;
    /** 이미지면 미리보기 주소(목업은 `blob:` 주소) */
    previewUrl: string | null;
}

/** `GET /api/business/me` — 내 사업자 신원. 신청한 적이 없으면 `status: NONE` 이고 나머지 칸은 null */
export interface BusinessProfile {
    status: BusinessProfileStatus;
    companyName: string | null;
    /** 가린 사업자등록번호(`123-45-*****`) — 평문은 내려 주지 않는다 */
    bizNoMasked: string | null;
    ownerName: string | null;
    /** 개업일(`YYYY-MM-DD`, 사업자등록증 「개업연월일」 — 국세청 진위확인에 쓴다). 개업일 칸이 생기기 전 신청이면 null */
    openedOn: string | null;
    /** 가린 연락처(`010-****-5678`) */
    phoneMasked: string | null;
    email: string | null;
    address: AddressValue | null;
    certificate: Pick<BusinessFile, 'fileId' | 'name' | 'size'> | null;
    submittedAt: string | null;
    reviewedAt: string | null;
    rejectCode: BusinessProfileRejectCode | null;
    rejectReason: string | null;
    suspendReason: string | null;
    /** 승인 뒤 상호·번호를 고쳐 다시 심사 중(F7) — 이미 예약·진행 중인 건은 그대로, 새 신청만 막힌다 */
    reReview: boolean;
    /**
     * 서비스별 이용 상태(2026-11-02 — 신원은 승인인데 한 서비스만 정지할 수 있다). 신청 전이면 빈 목록.
     * 서버 순서에 기대지 않는다. 그 서비스 줄이 없으면 신원 상태를 따른다.
     */
    services: BusinessProfileService[];
    /** 등록증을 내는 길(V140) — 신청 전이면 null */
    certificateDelivery: BusinessCertificateDelivery | null;
    /** 등록증을 받았나 — 지금 등록증 사본이 있거나 이번 신청 뒤 운영자가 종이 접수를 기록했다(우편·팩스 신청의 「받으면 심사 시작」 안내, 신청 전이면 false) */
    certificateReceived: boolean;
}

/** 사업자 서비스 — 관리자 화면 `BusinessService` 와 같은 값 */
export type BusinessServiceKind = 'HOTDEAL' | 'AD';

/** 서비스 이용 상태 — 관리자 화면 `BusinessServiceStatus` 와 같은 값 */
export type BusinessServiceStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'OFF';

/** 내 서비스 한 줄 — 신원이 승인인데 `status === 'SUSPENDED'` 면 「그 서비스만 정지」(새 신청이 막힌다, 다른 서비스는 그대로) */
export interface BusinessProfileService {
    service: BusinessServiceKind;
    status: BusinessServiceStatus;
    /** 서비스만 정지한 사유 코드 — 관리자 신원 정지 코드 묶음. 서비스만 정지가 아니면 null. 화면은 글(`suspendReason`)만 보인다 */
    suspendCode: string | null;
    /** 서비스 정지 사유 글(운영자가 쓴 글 그대로) */
    suspendReason: string | null;
    /** 서비스 정지 시각(ISO) */
    suspendedAt: string | null;
}

/** `POST /api/business/me`(신청) · `PUT /api/business/me`(수정) */
export interface BusinessApplyRequest {
    companyName: string;
    /** 숫자 10자리(하이픈 없이) — 수정 때 바꾸지 않으면 null */
    bizNo: string | null;
    ownerName: string;
    /** 개업일(`YYYY-MM-DD`) — 서버가 번호·상호·대표자명과 함께 국세청 진위확인을 부른다. 맞지 않으면 400 `BUSINESS_NTS_MISMATCH`(조회 불가면 그냥 받는다) */
    openedOn: string;
    /** 수정 때 바꾸지 않으면 null */
    phone: string | null;
    email: string;
    address: AddressValue;
    /** 올린 등록증 사본 번호 — 우편·팩스로 보내면(`certificateDelivery: 'MAIL_FAX'`) null */
    certificateFileId: string | null;
    /** 동의한 약관 버전 — 서버가 저장한다(H4) */
    agreedTermsVersion: string;
    agreedPrivacyVersion: string;
    /** 등록증을 내는 길 — 비우면 `UPLOAD`. `MAIL_FAX` 는 신청·재신청만(수정 400 `fields.certificateDelivery`) */
    certificateDelivery?: BusinessCertificateDelivery;
}

/**
 * 사업자등록증을 내는 길 — 파일 올리기 · 우편·팩스로 보내기(V140 — biz-doc-intake-contract). 우편·팩스면 신청 본문에 등록증 파일이 없고,
 * 운영자가 받은 것을 접수 기록(또는 파일)으로 남겨야 승인할 수 있다 — 「받은 날부터 심사」.
 */
export type BusinessCertificateDelivery = 'UPLOAD' | 'MAIL_FAX';

/** `GET /api/business/doc-send-to` — 등록증을 보낼 곳(서버 설정값). 비어 있는 칸은 null — 주소와 팩스가 둘 다 없으면 「우편·팩스로 보내기」를 보이지 않는다 */
export interface BusinessDocSendTo {
    postcode: string | null;
    address: string | null;
    /** 받는 사람(부서) */
    recipient: string | null;
    fax: string | null;
}

/* ── 사업자 신청 건(광고·핫딜) ─────────────────────────────── */

/** 신청 건 종류(★-1) */
export type BizApplicationKind = 'AD_BANNER' | 'AD_TEXT_LINE' | 'HOTDEAL_POST' | 'HOTDEAL_PLACEMENT';

/** 신청 건 상태(★-3). 「예약 → 진행 → 종료」는 `SCHEDULED` 안에서 시각으로 계산한 `phase` 다 */
export type BizApplicationStatus = 'SUBMITTED' | 'REJECTED' | 'CANCELED' | 'AWAITING_PAYMENT' | 'EXPIRED' | 'AWAITING_DEPOSIT' | 'SCHEDULED' | 'TAKEN_DOWN';

/** `SCHEDULED` 의 시각 계산 단계 — 그 밖의 상태는 null */
export type BizSchedulePhase = 'UPCOMING' | 'RUNNING' | 'ENDED';

/** 결제 방식(★-2) */
export type BizPaymentMethod = 'ONLINE' | 'MANUAL';

/** 광고 종류(공개 이름 — 서버 `BizProduct.publicName`) — 배너 4유형 + 날개 배너(`WING`) + 한 줄 */
export type AdType = 'WIDE' | 'STRIP' | 'SQUARE' | 'SMALL' | 'WING' | 'TEXT_LINE';

/** 핫딜 노출 자리 — `HOME_TOP` 은 이름과 달리 **핫딜 게시판 첫 쪽 위**(화면 이름 「핫딜 게시판 상단」, 키 값은 그대로), `CATEGORY_TOP` 은 신청 때 고른 말머리 목록 위 */
export type DealPlacement = 'NONE' | 'HOME_TOP' | 'CATEGORY_TOP';

/** 사업자 핫딜 재고 표시 — 회원 핫딜(`DealStock`)과 같은 값(품절 제외). 옛 `PLENTY`·`LIMITED` 를 대신한다(옛 건의 값도 i18n 이 그대로 읽는다) */
export type BizDealStock = 'IN_STOCK' | 'LOW' | 'UNKNOWN';

/** 금액(원) — 공급가 + 부가세 10% = 합계 */
export interface BizAmount {
    supply: number;
    vat: number;
    total: number;
}

/** 광고 소재 — 배너는 이미지·링크·대체 글, 한 줄은 문구·링크 */
export interface BizCreative {
    /** 심사 승인 때 고정되는 소재 버전(A2) */
    version: number;
    imageUrl: string | null;
    imageName: string | null;
    linkUrl: string;
    altText: string | null;
    text: string | null;
    /** 지금 배너 이미지의 파일 ID — 소재 바꾸기에서 이미지를 다시 고르지 않으면 그대로 보낸다(서버도 비우면 지금 이미지를 쓴다) */
    imageFileId: string | null;
}

/** 사업자 핫딜 정보 */
export interface BizDeal {
    linkUrl: string;
    shopName: string;
    listPrice: number | null;
    dealPrice: number;
    /** 0 = 무료, null = 모름 */
    shippingFee: number | null;
    /** 마감 날짜(없으면 null) */
    endsOn: string | null;
    stock: BizDealStock;
    body: string;
    placement: DealPlacement;
    /** 받는 핫딜 게시판(`DealProduct.boards[].id`) — 게시판이 하나면 화면이 자동으로 고른다(2차 — 옛 건은 없을 수 있다) */
    boardId: string;
    /** 그 게시판의 말머리 — 게시판에 말머리가 있으면 필수(`CATEGORY_TOP` 은 이 말머리 목록 위에 나온다), 없으면 null */
    prefixId: string | null;
    /** 상품 이미지(`POST /api/business/ad-images` 가 준 ID) — 선택, 없으면 기본 아이콘 */
    imageFileId: string | null;
    /** (상세만 — 2차 서버가 준다) 올린 상품 이미지 주소. 없으면 null·없음 */
    imageUrl?: string | null;
}

/** 입금 계좌·계약서(수동 결제) */
export interface BizDepositInfo {
    bankName: string;
    accountNo: string;
    /** 예금주 — 서버 설정(`roboyam.biz.deposit-account`)이 비면 null */
    accountHolder: string | null;
    /** 입금자명 — 이 이름으로 넣어 달라고 안내한다 */
    depositorName: string;
    /** 계약서 사본 — 운영팀이 올린다(F3). `WAITING` 아직 · `RECEIVED` 받음 */
    contract: 'WAITING' | 'RECEIVED';
    depositConfirmed: boolean;
}

/**
 * 취소·환불(H1) — 상태가 아니라 신청 건의 하위 값이다(세션 정리 결정). 시작 전 요청 = 신청 건 CANCELED + 이 값 APPROVED(FULL_AUTO),
 * 진행 중 요청 = 중단(V127 — 바로 내림 + REQUESTED `STOP_PRORATED`) → 운영팀이 APPROVED·DENIED. 돈이 실제로 돌아간 것은 처리 기록의 `REFUNDED`·`REFUND_TRANSFERRED`.
 * 운영팀 추가 환불(`CLAIM` — V128, biz-pause-contract 「추가 환불」): 운영팀이 클레임에 따라 남은 금액을 바로 승인 — 사유(`reason`·`decisionReason`)는 공개 응답에서 null(금액만).
 * 서버 `BizMemberView.Refund`.
 */
export interface BizRefundRequest {
    /**
     * `FULL_AUTO` 시작 전 전액 자동 · `PRORATED_REVIEW` 진행 중 일할(운영팀 확인 — 옛 [취소·환불 요청]·긴급 내림 보상·사업자 정지 일할) ·
     * `STOP_PRORATED` 광고주 요청 중단 일할 − 중도 해지 수수료(운영팀 확인 — 기준 시각 `basisAt`, 핫딜 글은 게시비 빼고 노출 자리만) ·
     * `CLAIM` 운영팀 추가 환불(바로 승인 — 금액만, 사유 없음)
     */
    type: 'FULL_AUTO' | 'PRORATED_REVIEW' | 'STOP_PRORATED' | 'CLAIM';
    status: 'REQUESTED' | 'APPROVED' | 'DENIED';
    /** 요청 사유 — `CLAIM` 이면 null */
    reason: string | null;
    requestedAt: string;
    /** 제안 금액(일할 계산 — 시작 전이면 전액, `STOP_PRORATED` 는 일할 − 수수료 = 환불 예상액) */
    proposedAmount: number;
    /** 승인 금액(승인 뒤) */
    refundAmount: number | null;
    /** 운영팀의 승인·거절 사유 */
    decisionReason: string | null;
    /** `STOP_PRORATED` 만 — 남은 기간 일할 금액(다른 종류는 null) */
    proratedAmount: number | null;
    /** `STOP_PRORATED` 만 — 그 건에 고정된 중도 해지 수수료율(%) */
    feeRate: number | null;
    /** `STOP_PRORATED` 만 — 중도 해지 수수료 = floor(일할 × 비율 / 100) */
    feeAmount: number | null;
    /** `STOP_PRORATED` 만 — 일할 기준 시각(정지 중 전환이면 멈춘 시각) */
    basisAt: string | null;
}

/** 처리 기록 한 줄 */
export interface BizHistoryEntry {
    at: string;
    actor: 'ME' | 'OPERATOR' | 'SYSTEM';
    action:
        | 'SUBMITTED'
        | 'CREATIVE_CHANGED'
        | 'APPROVED_ONLINE'
        | 'APPROVED_MANUAL'
        | 'REJECTED'
        | 'CANCELED'
        | 'PAID'
        | 'EXPIRED'
        | 'MANUAL_SWITCH'
        | 'CONTRACT_RECEIVED'
        | 'DEPOSIT_CONFIRMED'
        | 'DEPOSIT_DEADLINE'
        | 'SCHEDULED'
        | 'TAKEN_DOWN'
        | 'RESTORED'
        | 'REFUND_REQUESTED'
        | 'REFUND_APPROVED'
        | 'REFUND_DENIED'
        | 'REFUNDED'
        | 'REFUND_TRANSFERRED'
        | 'AUTO_CANCELED'
        | 'FORFEITED'
        // 사업자 핫딜 게시(2차) — `POST_FAILED` 의 note 는 실패 사유 코드(`BizDealFailReason`)
        | 'POSTED'
        | 'POST_FAILED'
        | 'POSTED_WITHOUT_PREFIX'
        | 'POST_HIDDEN'
        | 'POST_RESTORED'
        | 'DEAL_ENDED_BY_BUSINESS'
        | 'DEAL_AUTO_ENDED'
        // 사업자 글 종료 요청 확인(운영팀) — `DEAL_REVIEW_NEEDED` 는 note 없음, 나머지 note = 운영자 사유 글(없으면 null)
        | 'DEAL_REVIEW_NEEDED'
        | 'DEAL_KEPT_BY_OPERATOR'
        | 'DEAL_ENDED_BY_OPERATOR'
        | 'DEAL_REOPENED_BY_OPERATOR'
        // 일시 정지·중단(광고주 요청) — note: 운영팀 대신 처리면 요청 경로(`PHONE`·`EMAIL`·`OTHER` — 공개 응답은 메모를 자른다),
        // `STOPPED` 가 정지 중 전환이면 맨 앞 `FROM_PAUSE`, `RESUMED` 는 `MIN:<멈춘 분>`(선택). 토큰은 ` · ` 로 잇는다
        | 'PAUSED'
        | 'RESUMED'
        | 'STOPPED'
        // 운영팀 추가 환불(클레임 — V128) — note = 금액(원)
        | 'CLAIM_REFUNDED'
        // 서버는 기록하지 않는다 — 화면이 `phase` 로 덧붙인다(`UtilBusiness.withPhaseHistory`)
        | 'STARTED'
        | 'ENDED';
    /** 코드별 덧붙임(사유 코드·금액·`PERIOD_MOVED` 등) 또는 사유 글 — `UtilBusinessMessages.historyNote` 가 읽는다 */
    note: string | null;
}

/** 목록 한 줄 */
export interface BizApplicationSummary {
    id: string;
    kind: BizApplicationKind;
    title: string;
    adType: AdType | null;
    placement: DealPlacement | null;
    startDate: string;
    /** 끝 날짜(포함) */
    endDate: string;
    status: BizApplicationStatus;
    phase: BizSchedulePhase | null;
    paymentMethod: BizPaymentMethod;
    /** 확정 금액이 있으면 그것, 없으면 예상 금액 */
    amount: BizAmount;
    amountConfirmed: boolean;
    /** 결제 기한(`AWAITING_PAYMENT`) */
    paymentDueAt: string | null;
    /** 입금 기한(`AWAITING_DEPOSIT`) */
    depositDueAt: string | null;
    submittedAt: string;
    refundRequested: boolean;
    /** 내림(`TAKEN_DOWN`)·중단 뒤 취소의 까닭(목록 Tag 「일시 정지」·「중단」) — 내려간 적 없으면 null */
    takedownCause: BizTakedownCause | null;
}

/* ── 일시 정지·중단(광고주 요청) ─────────────────────────────── */

/**
 * 내림 까닭 — 운영 긴급 내림 · 사업자 정지 · 광고주 일시 정지([다시 켜기] → `SCHEDULED`) · 광고주 중단(되돌리기 없음, 일할 환불 심사).
 * 정지·중단은 기존 `TAKEN_DOWN` 상태 + 이 까닭으로 나타낸다(`biz-pause-contract.md`). 중단 건은 환불 승인 뒤 `CANCELED` 에도 까닭이 남는다.
 */
export type BizTakedownCause = 'EMERGENCY' | 'SUSPENDED' | 'ADVERTISER_PAUSE' | 'ADVERTISER_STOP';

/** 운영팀이 대신 정지·중단했을 때의 요청 경로 */
export type BizPauseChannel = 'PHONE' | 'EMAIL' | 'OTHER';

/** 신청 건 상세의 `pause` — 정지할 수 있는 종류(날짜형 자리·사업자 핫딜 글)의 결제 뒤 건만, 아니면 null. 서버 `BizMemberView.Pause` */
export interface BizPause {
    /** 쓴 정지 횟수(지금 정지 포함) */
    used: number;
    /** 한 건에 정지할 수 있는 횟수(설정값 — 3) */
    limit: number;
    /** 지금 정지 중이면 그 시작 시각 */
    pausedAt: string | null;
    /** 지금 정지를 누가 했나 — 나 · 운영팀(전화·메일 요청을 받아 대신) */
    pausedBy: 'ME' | 'OPERATOR' | null;
    /** 운영팀 대신 처리면 요청 경로 */
    channel: BizPauseChannel | null;
    /** 지금까지 멈춘 시간 합(분, 안내용 — 기간은 늘지 않는다) */
    pausedMinutes: number;
    /** 중단 시각 */
    stoppedAt: string | null;
    /** 일할 기준 시각 — 정지 중 전환이면 멈춘 시각, 아니면 중단 시각 */
    refundFrom: string | null;
    /** 일할 대상 금액(부가세 포함, 결제액 기준 — 수동은 입금 확인 금액) — 광고 = 결제액, 핫딜 글 = 노출 자리 몫(게시비 제외, 노출 없으면 0) */
    proratableTotal: number;
    /** 이 건에 고정된 중도 해지 수수료율(%) — 신청할 때의 비율(옛 규정 `2026-10-30` 동의 건은 0) */
    feeRate: number;
}

/** `GET /api/business/refund-policy` — 지금 환불 규정 버전 + 중도 해지 수수료율(신청 화면 동의 문구). 서버 `BizMemberView.RefundPolicy` */
export interface BizRefundPolicy {
    version: string;
    stopFeeRate: number;
}

/** `GET /api/business/applications/{id}` */
export interface BizApplicationDetail extends BizApplicationSummary {
    estimate: BizAmount;
    /** 승인 때 확정한 금액(조정했으면 사유와 함께) */
    confirmed: BizAmount | null;
    adjustReason: string | null;
    creative: BizCreative | null;
    deal: BizDeal | null;
    rejectCode: BusinessRejectCode | null;
    rejectReason: string | null;
    takedownReason: string | null;
    deposit: BizDepositInfo | null;
    refund: BizRefundRequest | null;
    history: BizHistoryEntry[];
    /** 사업자 핫딜(`HOTDEAL_POST`)의 게시된 글 — 결제 전이거나 핫딜이 아니면 null. 2차 칸이라 옛 응답에는 없다(없으면 「게시된 글」 칸을 감춘다) */
    post?: BizDealPost | null;
    /** 일시 정지·중단 정보 — 정지할 수 없는 종류·결제 전이면 null */
    pause: BizPause | null;
    /** 환불 내역 전부(오래된 차례 — 중단 환불·추가 환불 등, 사유 없음). `refund` 는 그중 가장 최근 줄 */
    refunds: BizRefundLine[];
    /** 결제액·승인된 환불 합 — 결제 전이면 null */
    refundTotals: { paid: number; refunded: number } | null;
}

/** 공개 환불 내역 한 줄 — 서버 `BizMemberView.RefundLine` */
export interface BizRefundLine {
    type: BizRefundRequest['type'];
    status: BizRefundRequest['status'];
    /** 승인 금액(승인 뒤) 또는 제안 금액(심사 중·거절) */
    amount: number;
    requestedAt: string;
    decidedAt: string | null;
    /** 돈이 실제로 돌아간 시각(온라인 카드 취소 완료 · 수동 송금 완료) — 아직이면 null */
    returnedAt: string | null;
}

/* ── 사업자 핫딜 게시(2차) ─────────────────────────────────── */

/** 게시 상태 — 시작 시각 전 · 핫딜 게시판에 올라감 · 올리지 못함(운영팀 확인 중 → 취소·전액 환불) · 숨김(취소·환불·긴급 내림 — 되돌리면 다시 `POSTED`) */
export type BizDealPostStatus = 'WAITING' | 'POSTED' | 'FAILED' | 'HIDDEN';

/** 시작 시각에 글을 만들지 못한 까닭 — `SERVICE_SUSPENDED` 는 핫딜 서비스만 정지(서비스가 다시 승인되면 다음 배치가 다시 올린다) */
export type BizDealFailReason = 'BOARD_CLOSED' | 'LINK_BLOCKED' | 'BUSINESS_SUSPENDED' | 'SERVICE_SUSPENDED' | 'OTHER';

/** 노출 자리 상태 — 대기 · 노출 중 · 멈춤(`stopReason`) · 끝남(기간 끝) */
export type BizDealPlacementStatus = 'WAITING' | 'SHOWING' | 'STOPPED' | 'ENDED';

/** 노출 자리가 멈춘 까닭 — 딜이 끝나서(종료 요청 자동 종료·사업자 종료 — 환불 없음) · 글이 내려가서 · 광고주가 일시 정지해서(다시 켜면 같이 나간다) */
export type BizDealStopReason = 'DEAL_ENDED' | 'POST_HIDDEN' | 'PREFIX_MISSING' | 'PAUSED';

/** 같은 신청 건에 묶인 노출 자리 */
export interface BizDealPlacementState {
    product: 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY';
    status: BizDealPlacementStatus;
    stopReason: BizDealStopReason | null;
}

/** 종료 요청 사유별 수(요청자 신원 없음 — 회원 핫딜의 `DealEndRequests` 에서 `mine` 을 뺀 것) */
export type BizDealEndRequests = Pick<DealEndRequests, 'total' | 'bySoldOut' | 'byPriceChanged' | 'byLinkDead'>;

/** 신청 건 상세 `post` · 사업자 종료 응답 — 시작 시각에 핫딜 게시판에 올라간 사업자 글(`source=BUSINESS`) */
export interface BizDealPost {
    status: BizDealPostStatus;
    /** 올라간 글 — 게시 전·실패면 null */
    postId: string | null;
    boardSlug: string;
    boardName: string;
    prefixName: string | null;
    /** 게시(예정) 시각 — 신청 기간 시작 */
    publishAt: string;
    /** 딜 상태(회원 핫딜과 같은 계산) — 게시 전·실패면 null. 딜 마감 = 신청 기간 끝(지나면 `EXPIRED`) */
    view: DealView | null;
    /** `ENDED` 일 때 끝낸 쪽 — `AUTHOR` 사업자 직접 · `MANAGER` 운영진 · `AUTO` 종료 요청으로 자동(사업자 직접·자동은 환불 없음) */
    endedBy: DealEndedBy | null;
    /** 게시 전·실패면 null */
    endRequests: BizDealEndRequests | null;
    failReason: BizDealFailReason | null;
    /** 노출 자리를 같이 샀으면 그 상태 — 없으면 null */
    placement: BizDealPlacementState | null;
    /** 사업자 글은 종료 요청으로 자동 종료하지 않아 늘 null(의심이면 운영팀이 확인한다) */
    autoEndAt?: string | null;
    /** (2차 서버가 준다) 끝난 시각. 없으면 null·없음 */
    endedAt?: string | null;
}

/** 받는 핫딜 게시판 — `GET /api/business/deal-products` 의 `boards` */
export interface BizDealBoard {
    id: string;
    slug: string;
    name: string;
    /** 말머리 — 없으면 빈 목록 */
    prefixes: { id: string; name: string }[];
}

/** 목록 거르기(화면 SelectButton) — 상태 묶음 */
export type BizApplicationBucket = 'ALL' | 'REVIEW' | 'AWAITING_PAYMENT' | 'AWAITING_DEPOSIT' | 'UPCOMING' | 'RUNNING' | 'ENDED' | 'CLOSED';

/** `GET /api/business/applications?bucket=` */
export interface BizApplicationPage {
    items: BizApplicationSummary[];
    total: number;
    /** 묶음별 건수(SelectButton 옆 숫자) */
    counts: Record<BizApplicationBucket, number>;
}

/* ── 광고 신청 ─────────────────────────────────────────── */

/** `GET /api/business/ad-products` — 광고 종류 하나 */
export interface AdProduct {
    type: AdType;
    /** 하루 단가(공급가, 원) */
    dailyPrice: number;
    /** 한 번에 신청할 수 있는 최대 일수 */
    maxDays: number;
    /** 며칠 뒤까지 미리 신청할 수 있나 */
    maxLeadDays: number;
    /** 동시 노출 한도 */
    capacity: number;
    /** 앞으로 30일 중 빈 날 수(요약) */
    openDays: number;
    /** 신청 받기 꺼짐 — 흐리게 「지금은 받지 않아요」 */
    accepting: boolean;
}

/** 남은 칸을 셀 자리 — 광고 종류 또는 핫딜 노출 자리(광고 건 한도 엔진 하나 — X8) */
export type AvailabilitySlot = AdType | Exclude<DealPlacement, 'NONE'>;

/** `GET /api/business/availability?slot=&from=&to=` — 날짜별 남은 칸 */
export interface AvailabilityDay {
    date: string;
    remaining: number;
    capacity: number;
}

/** `POST /api/business/applications/ads` */
export interface AdApplyRequest {
    adType: AdType;
    startDate: string;
    endDate: string;
    imageFileId: string | null;
    linkUrl: string;
    altText: string | null;
    text: string | null;
    paymentMethod: BizPaymentMethod;
    agreedAdPolicyVersion: string;
    agreedRefundPolicyVersion: string;
}

/* ── 사업자 핫딜 신청 ─────────────────────────────────────── */

/** `GET /api/business/deal-link-check?url=` */
export interface DealLinkCheck {
    host: string;
    shopName: string | null;
    /** 올릴 수 없는 쇼핑몰(단축 URL·차단 도메인) */
    blocked: boolean;
    blockedReason: string | null;
    /** 제휴 링크라 「제휴」 표시가 자동으로 켜진다 */
    affiliate: boolean;
}

/** `GET /api/business/deal-products` — 사업자 핫딜 요금(게시 1건 + 노출 자리별 하루 단가) */
export interface DealProduct {
    /** 게시 1건 공급가(원) */
    postPrice: number;
    /** 글 게시 신청 받기 — 꺼지면 신청 화면 대신 「준비 중」 안내(사업자 핫딜 닫음 V121 — 게시 기능이 생기면 다시 연다) */
    accepting: boolean;
    /** 노출 자리별 — `accepting` 꺼짐이면 흐리게 「지금은 받지 않아요」 */
    placements: { placement: Exclude<DealPlacement, 'NONE'>; dailyPrice: number; capacity: number; maxDays: number; accepting: boolean }[];
    /** 받는 핫딜 게시판(2차) — 하나면 화면이 자동으로 고르고 칸을 감춘다. 옛 응답에는 없다(없거나 비면 칸을 감추고 `boardId` 는 빈 글자) */
    boards?: BizDealBoard[];
}

/** `POST /api/business/applications/deals` — 노출을 고르면 같은 신청 건에 묶는다(목업 가정) */
export interface DealApplyRequest {
    title: string;
    deal: BizDeal;
    placementStartDate: string | null;
    placementEndDate: string | null;
    paymentMethod: BizPaymentMethod;
    agreedAdPolicyVersion: string;
    agreedRefundPolicyVersion: string;
}

/** 신청 접수 결과 */
export interface BizApplicationCreated {
    id: string;
}

/**
 * `PUT /api/business/applications/{id}/creative` — 소재 바꾸기(종류에 맞는 칸만 본다). 심사 중이면 새 버전, 승인 뒤 결제 전(결제 대기·입금 기록 없는 입금 대기)이면
 * 심사 대기로 돌아간다(자리 반납). 핫딜 글은 노출 자리·기간을 바꿀 수 없다(400 `fields.deal.placement`).
 */
export interface BizCreativeRequest {
    /** 배너 이미지(`POST /api/business/ad-images` 가 준 ID) */
    imageFileId?: string | null;
    linkUrl?: string | null;
    altText?: string | null;
    /** 한 줄 문구(40자) */
    text?: string | null;
    /** 핫딜 글 제목 */
    title?: string | null;
    deal?: BizDeal | null;
}

/* ── 결제(온라인) ───────────────────────────────────────── */

/** `POST /api/business/applications/{id}/payment` — 결제 시작(결제창 정보). `payment` 모듈 한 길(`PaymentOrderType.BIZ_APPLICATION`) */
export interface BizPaymentStart {
    orderType: 'BIZ_APPLICATION';
    orderRefId: string;
    expiresAt: string;
    checkout: PaymentCheckout;
    /** 결제창(포트원) 구매자 — 서버가 사업자 신원의 상호·연락처(숫자)·이메일로 채운다(본인 건만). 연락처가 없으면 결제 시작이 400 `BIZ_CONTACT_MISSING` */
    customer: { fullName: string; phoneNumber: string; email: string } | null;
}

/* ── 광고 성과(2026-11-02 — 서버 DTO 정본 shared `ad/dto/AdStatsView`·api `bizapplication/dto/BizAdStatsView`) ─────────────── */

/**
 * 집계 자리 — 공개 노출 집계(shared V118 `ad_daily_stats.placement`)의 값. 공개 광고의 `AdPlacement` 와 같은 값이라 그것을 쓴다(복사하지 않는다).
 * 화면 이름은 `ad.statPlacement.<값>`.
 */
export type AdStatPlacement = AdPlacement;

/** 노출·클릭 수 — 클릭률은 화면 계산(`UtilAdStats.ctr` — 노출 0 이면 「—」, 소수 한 자리 %). 클릭은 그날 노출 여부를 보지 않아 클릭률이 드물게 100% 를 넘을 수 있다 */
export interface AdStatCounts {
    impressions: number;
    clicks: number;
}

/** 그날 상태 — 그날 일부라도 광고주 일시 정지(`ADVERTISER_PAUSE`)가 걸쳤으면 `PAUSED`, 그 밖의 내림(긴급·사업자 정지·중단)이 걸쳤으면 `TAKEN_DOWN` */
/** 진행 중인데 노출이 멈춘 까닭(사업자 핫딜 글 건 — 서버 `BizDealPostReader.exposureStop`) */
export type AdExposureStop = 'DEAL_ENDED' | 'POST_HIDDEN' | 'PREFIX_MISSING' | 'NOT_POSTED';

export type AdStatDayState = 'SHOWING' | 'PAUSED' | 'TAKEN_DOWN';

/** 하루 숫자 — 날짜는 한국 날짜(`YYYY-MM-DD`) */
export interface AdStatDay extends AdStatCounts {
    date: string;
    state: AdStatDayState;
}

/** 정지·내림 구간(처리 기록에서 계산) — 정지·내림 중에는 공개 노출에 나가지 않아 집계가 0 이다 */
export interface AdStatDowntime {
    /** ISO 순간 */
    from: string;
    /** ISO 순간 — 아직 멈춰 있으면 null */
    to: string | null;
    cause: BizTakedownCause;
}

/** 자리 한 줄 */
export interface AdStatPlacementRow extends AdStatCounts {
    placement: AdStatPlacement;
}

/** 소재 버전 한 줄 — `current` = 지금 나가는 버전 */
export interface AdStatVersionRow extends AdStatCounts {
    version: number;
    /** 그 버전을 승인한 시각(ISO) — 모르면 null */
    approvedAt: string | null;
    current: boolean;
}

/** `GET /api/business/applications/{id}/stats?from&to` — 한 건의 성과(본인 건만, 없으면 404 `BIZ_APPLICATION_NOT_FOUND`) */
export interface AdApplicationStats {
    /** 실제로 센 기간(한국 날짜, 끝 포함) — 빠지면 서버 기본 = 시작 날짜 ~ min(끝 날짜, 오늘) */
    range: { from: string; to: string };
    /** 응답을 만든 시각(ISO) — 오늘 숫자는 계속 늘어난다(「오늘은 집계 중」) */
    asOf: string;
    totals: AdStatCounts;
    /** 기간 안 모든 날짜(0 포함, 오래된 차례) */
    days: AdStatDay[];
    /** 자리별 — 0 인 자리는 뺌, 노출 많은 차례 */
    byPlacement: AdStatPlacementRow[];
    /** 소재 버전별(버전 차례) */
    byVersion: AdStatVersionRow[];
    /** 기간과 겹치는 정지·내림 구간(오래된 차례) */
    downtimes: AdStatDowntime[];
    /** 진행 중 사업자 핫딜 글 건의 노출 자리가 지금 나가지 않는 까닭(공개 노출 판정과 같음 — 딜 종료·글 숨김·말머리 없음·게시 전), 노출 중이거나 다른 종류면 null */
    exposureStop: AdExposureStop | null;
}

/** 사업자 센터 「진행 중 광고」 한 줄 */
export interface BizAdStatsSummaryItem {
    id: string;
    title: string;
    kind: BizApplicationKind;
    adType: AdType | null;
    placement: DealPlacement | null;
    status: BizApplicationStatus;
    phase: BizSchedulePhase | null;
    takedownCause: BizTakedownCause | null;
    /** (설계 노트 모양에 더함) 상태 딱지의 「정지한 채 끝남」 판정용 */
    startDate: string;
    endDate: string;
    /** 최근 7일(오늘 포함) 합 */
    last7: AdStatCounts;
    /** 최근 7일 날짜별(오래된 차례, 마지막 = 오늘) — 설계 노트의 `AdStatCounts[]` 에 날짜·상태를 더했다(미니 차트의 오늘 빗금·정지 띠) */
    spark: AdStatDay[];
    /** 진행 중 사업자 핫딜 글 건의 노출 자리가 지금 나가지 않는 까닭(공개 노출 판정과 같음 — 딜 종료·글 숨김·말머리 없음·게시 전), 노출 중이거나 다른 종류면 null */
    exposureStop: AdExposureStop | null;
}

/** `GET /api/business/ads/stats-summary` — 진행 중·정지 중 건 최대 5개(노출 자리가 있는 건만). 없으면 빈 목록 */
export interface BizAdStatsSummary {
    asOf: string;
    items: BizAdStatsSummaryItem[];
}
