/**
 * 로보티콘 운영(관리자) API 의 요청·응답 모양 — 서버 `EmoticonAdminView`·`EmoticonSettlementView` 등(계약 `docs/features/emoticon-contract.md` 「S1~S4 구현 결과」)과 같은 모양.
 */

/** 팩 출처 — 운영(로보얌콘·로보얌콘핑크) · 회원 작가 */
export type EmoticonPackSource = 'OFFICIAL' | 'CREATOR';
export const EMOTICON_PACK_SOURCES: readonly EmoticonPackSource[] = ['OFFICIAL', 'CREATOR'];

/**
 * 팩 상태(계약 「목업 가정」 5) — 임시 → 심사 대기 → 판매 중 / 반려 · 판매 중단.
 * 관리자 화면에는 임시(`DRAFT`)가 오지 않는다(작가가 아직 내지 않은 팩).
 */
export type EmoticonPackStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
/** 팩 관리 화면의 상태 거르기 차례 */
export const EMOTICON_PACK_LIST_STATUSES: readonly EmoticonPackStatus[] = ['APPROVED', 'SUSPENDED', 'PENDING', 'REJECTED'];

/** 심사 건(신청 한 번) 상태 */
export type EmoticonReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
/** 심사 건 갈래 — 처음 신청 · 판매 중인 팩을 고친 새 판 */
export type EmoticonReviewKind = 'FIRST' | 'UPDATE';
/** 심사 화면 탭 — 심사 대기(처음) · 새 판 심사 · 반려 · 전체 */
export type EmoticonReviewTab = 'PENDING' | 'UPDATE' | 'REJECTED' | 'ALL';
export const EMOTICON_REVIEW_TABS: readonly EmoticonReviewTab[] = ['PENDING', 'UPDATE', 'REJECTED', 'ALL'];

/** 그림 형식 — GIF(움직임) · PNG(정지) */
export type EmoticonImageFormat = 'GIF' | 'PNG';

/** 작가(회원) — 회원 관리 화면 `/members?memberNo=` 로 잇는다 */
export interface EmoticonCreatorRef {
    memberNo: string;
    nickname: string;
    /** 탈퇴한 회원이면 링크를 두지 않는다 */
    withdrawn: boolean;
}

/** 로보티콘 하나 — 그림과 이름표, 그리고 서버가 업로드 때 읽어 둔 그림 정보(화면의 규격 검사가 쓴다) */
export interface EmoticonItemView {
    id: string;
    imageUrl: string;
    /** 이름표(alt) 1~10자 — 그림을 못 볼 때 읽히고 고르기 창 검색에 쓴다 */
    alt: string;
    format: EmoticonImageFormat;
    width: number;
    height: number;
    /** GIF 프레임 수 — PNG 는 null */
    frames: number | null;
    /** GIF 무한 반복인가 — PNG 는 null */
    loopForever: boolean | null;
    bytes: number;
    /** 그림 내용의 지문 — 새 판 비교에서 「그림이 바뀐 것」을 가른다 */
    contentHash: string;
}

/** 대표 이미지 정보 */
export interface EmoticonCoverView {
    /** 대표 이미지 아이디 — 운영 팩 저장 때 `coverId` 로 가리킨다 */
    id: string;
    imageUrl: string;
    format: EmoticonImageFormat;
    width: number;
    height: number;
    bytes: number;
}

/** 작가가 신청 때 체크한 동의(계약 「목업 가정」 6) — 직접 만들었거나 권리를 가졌다 · 사이트가 보이고 줄여 쓸 수 있게 허락 · 가이드·운영 정책 준수 */
export type EmoticonConsentKind = 'ORIGINAL' | 'LICENSE' | 'POLICY';
export const EMOTICON_CONSENT_KINDS: readonly EmoticonConsentKind[] = ['ORIGINAL', 'LICENSE', 'POLICY'];

export interface EmoticonConsentRecord {
    kind: EmoticonConsentKind;
    /** 체크한 시각 — 없으면 체크하지 않았다(서버가 막아 오지 않아야 한다) */
    agreedAt: string | null;
    /** 그때 보인 동의 문구 판(문구가 바뀌어도 무엇에 동의했는지 남긴다) */
    textVersion: string;
}

/** 팩 규칙 — 서버 설정의 지금 값(바꿀 수 있는 칸). 고정 규격(200×200 · 24프레임 · 2MB …)은 `UtilEmoticon.SPEC` */
export interface EmoticonPackRules {
    itemMin: number;
    itemMax: number;
    /** 유료 판매가 켜져 있나 · 운영자가 고르는 가격 단계(원, 오름차순) */
    paidSalesEnabled: boolean;
    priceTiers: number[];
}

/** 작가의 판매 희망 — 무료 · 유료 판매 희망(금액은 운영자가 승인 때 가격 단계에서 고른다 — 카카오 방식) */
export type EmoticonSaleWish = 'FREE' | 'PAID';

/** 판 하나의 내용 — 이름·설명·태그·대표·로보티콘들 + 작가의 판매 희망 */
export interface EmoticonPackVersionView {
    version: number;
    saleWish: EmoticonSaleWish;
    /** 작가가 남긴 가격 변경 요청 메모(새 판 신청 때만, 없으면 null) — 가격은 운영자만 바꾼다 */
    priceRequestMemo: string | null;
    name: string;
    description: string;
    tags: string[];
    cover: EmoticonCoverView;
    items: EmoticonItemView[];
}

/** 처리 기록(승인·반려) */
export interface EmoticonReviewDecision {
    status: Exclude<EmoticonReviewStatus, 'PENDING'>;
    /** 반려 사유(작가에게 그대로 보인다) — 승인이면 null */
    reason: string | null;
    decidedAt: string;
    /** 처리한 운영자 이름 — 작가 탈퇴로 시스템이 닫았으면 null */
    decidedBy: string | null;
    /** 누가 닫았나 — 운영자 · 작가 탈퇴로 시스템이 반려(후속 결정 6) */
    cause: EmoticonReviewDecisionCause;
}

/** 심사 건을 닫은 까닭 — 운영자 처리 · 작가 탈퇴로 시스템이 반려 */
export type EmoticonReviewDecisionCause = 'OPERATOR' | 'CREATOR_WITHDRAWN';

/** `GET /api/admin/emoticons/reviews?status=` 의 한 줄 */
export interface EmoticonReviewRow {
    id: string;
    packId: string;
    packName: string;
    coverUrl: string;
    creator: EmoticonCreatorRef;
    itemCount: number;
    /** 이 신청의 판매 희망 */
    saleWish: EmoticonSaleWish;
    /** 지금 팩 가격(원, 0 = 무료) — 처음 신청이면 0 */
    currentPrice: number;
    submittedAt: string;
    kind: EmoticonReviewKind;
    /** 이 신청의 판 번호(처음 = 1) */
    version: number;
    status: EmoticonReviewStatus;
}

/** `GET /api/admin/emoticons/reviews?status=` 응답 — 오래된 신청이 위(심사 대기는 기다린 차례대로) */
export interface EmoticonReviewList {
    items: EmoticonReviewRow[];
    /** 탭 숫자 — 심사 대기(처음) · 새 판 심사 · 반려 */
    counts: Record<Exclude<EmoticonReviewTab, 'ALL'>, number>;
}

/** `GET /api/admin/emoticons/reviews/{id}` 응답 */
export interface EmoticonReviewDetail extends EmoticonReviewRow {
    /** 이 신청의 내용 */
    next: EmoticonPackVersionView;
    /** 새 판 심사면 지금 상점에 나오는 판(비교용), 처음 신청이면 null */
    current: EmoticonPackVersionView | null;
    consents: EmoticonConsentRecord[];
    /** 심사 때 기준(서버 설정의 지금 값) */
    rules: EmoticonPackRules;
    decision: EmoticonReviewDecision | null;
    /** 이 작가의 지금 심사 대기 건수(한 사람 상한 안내용) */
    creatorPendingCount: number;
    /** 작가의 로보티콘 상품화 계약 동의(유료 희망일 때) — 동의한 판·시각. 없으면 null */
    contractConsent: { version: number; agreedAt: string; current: boolean } | null;
    /** 작가 정산 정보(본인인증·개인/사업자·계좌) 등록 여부 */
    payoutRegistered: boolean;
}

/** `POST /api/admin/emoticons/reviews/{id}/approve` 요청 — 가격(원, 0 = 무료). 유료 희망 팩은 가격 단계 중 하나 */
export interface EmoticonApproveRequest {
    price: number;
}

/** `POST /api/admin/emoticons/packs/{id}/price` 요청 — 판매 중 작가 팩의 가격 바꾸기(0 = 무료로 전환, 그 밖은 단계 중 하나 · 사유 5~200자). 이미 산 사람의 금액·환불 규칙은 그대로, 지난 정산도 그대로 */
export interface EmoticonPriceChangeRequest {
    price: number;
    reason: string;
}

/** `POST /api/admin/emoticons/reviews/{id}/reject` 요청 — 사유 5~200자(작가에게 그대로 간다) */
export interface EmoticonReasonRequest {
    reason: string;
}

/** 판매 중단 기록 */
export interface EmoticonSuspension {
    /** 판매 중단 사유 — 작가 탈퇴로 멈춘 팩은 서버 고정 문구(화면은 `suspendKind` 로 문구를 고른다) */
    reason: string;
    at: string;
    /** 처리한 운영자 이름 — 작가 탈퇴로 멈춘 팩은 null(시스템) */
    by: string | null;
}

/** 판매 중단의 종류 — 운영자 판단 · 작가 탈퇴(새 판매만 멈춤, 되살리지 못한다 — 후속 결정 6) */
export type EmoticonSuspendKind = 'OPERATOR' | 'CREATOR_WITHDRAWN';

/** `GET /api/admin/emoticons/packs` 의 한 줄 */
export interface EmoticonPackRow {
    id: string;
    name: string;
    coverUrl: string;
    source: EmoticonPackSource;
    /** 운영 팩은 null */
    creator: EmoticonCreatorRef | null;
    itemCount: number;
    /** 지금 판의 가격(원, 0 = 무료) */
    price: number;
    /** 받은 수 — 무료 받기 + 유료 구매(받기 해제·환불한 사람도 센다 — 가정) */
    acquireCount: number;
    /** 작가에게 준 받기 보상 합계(P, 무료 팩만) — 운영 팩은 null(보상 없음) */
    rewardTotal: number | null;
    /** 유료 판매 수(환불 뺀 결제 완료) — 무료로만 나온 팩은 null */
    salesCount: number | null;
    /** 유료 판매액(원, 환불 뺀 합계) — 무료로만 나온 팩은 null */
    salesAmount: number | null;
    status: EmoticonPackStatus;
    /** 새 판 심사가 걸려 있나 — 심사 중에도 옛 판이 계속 나온다 */
    pendingUpdate: boolean;
    updatedAt: string;
    /** 판매 중단의 종류 — 판매 중단이 아니면 null */
    suspendKind: EmoticonSuspendKind | null;
}

export interface EmoticonPackQuery {
    source?: EmoticonPackSource;
    status?: EmoticonPackStatus;
    /** 팩 이름 · 작가 닉네임 · 태그 */
    q?: string;
    /** 그 작가(회원 번호)의 팩만 — 회원 상세의 「로보티콘 작가」 링크 */
    creator?: string;
}

/** `GET /api/admin/emoticons/packs` 응답 — 운영 팩이 위, 그다음 최근 고친 순 */
export interface EmoticonPackList {
    items: EmoticonPackRow[];
}

/** `GET /api/admin/emoticons/packs/{id}` 응답 */
export interface EmoticonPackDetail extends EmoticonPackRow {
    description: string;
    tags: string[];
    cover: EmoticonCoverView;
    items: EmoticonItemView[];
    suspension: EmoticonSuspension | null;
    /** 새 판 심사 건 — 심사 화면으로 잇는다 */
    pendingReviewId: string | null;
    approvedAt: string | null;
    /** 작가 팩의 유료 판매 준비(무료 → 유료 전환 조건) — 운영 팩은 null */
    paidReady: { contractAgreed: boolean; payoutRegistered: boolean } | null;
    /** 가격 전환 기록(최근이 앞) */
    priceEvents: EmoticonPriceEvent[];
    /** 「다시 판매」를 받는가 — 운영자 판매 중단만 true(작가 탈퇴로 멈춘 팩은 false) */
    resumable: boolean;
}

/** 가격 전환 한 줄 — 언제·누가·얼마 → 얼마·사유 */
export interface EmoticonPriceEvent {
    at: string;
    /** 처리한 운영자 이름 — 지워진 운영자는 null */
    by: string | null;
    /** 원(0 = 무료) */
    from: number;
    to: number;
    /** 운영자가 적은 사유 — 새 판 승인으로 바뀐 가격은 null(화면은 「새 판 승인」으로 대신 쓴다) */
    reason: string | null;
}

/** `PUT /api/admin/emoticons/packs/{id}`(운영 팩만) · `POST /api/admin/emoticons/packs`(운영 팩 만들기) 요청 — 로보티콘은 올린 것(`id`)을 차례대로, 이름표와 함께 */
export interface EmoticonOfficialPackSaveRequest {
    name: string;
    description: string;
    tags: string[];
    items: { id: string; alt: string }[];
    /** 대표 이미지 — 지금 대표의 `id`(그대로) 또는 `uploads/cover` 로 올린 것의 `id` · 필수 */
    coverId: string;
}

/** 설정의 숫자 칸(저장 본문 = 이 칸들 + `paidSalesEnabled`) */
export interface EmoticonSettingNumbers {
    /** 무료 팩을 받을 때마다 작가에게 주는 적립 포인트(0 이면 주지 않는다) */
    rewardPerAcquire: number;
    /** 작가 한 사람 하루 상한(P) */
    rewardDailyCap: number;
    itemMin: number;
    itemMax: number;
    /** 한 사람 심사 대기 팩 상한 */
    pendingLimit: number;
    /** 수수료율(%) — 결제 대행 수수료 포함, 정산 기준(부가세 제외)에 곱한다 */
    feeRatePercent: number;
    /** 정산 지급일(매월 N일) */
    settlementDay: number;
    /** 최소 지급액(원) — 미만이면 다음 달로 넘긴다 */
    payoutMinimum: number;
    /** 회원 셀프 환불 기간(일) — 정산은 이 기간이 끝난 판매만 모은다 */
    refundDays: number;
}

/**
 * 로보티콘 상품화 계약(작가 ↔ 사이트) 요약 — 문서 자체는 약관 종류 `ROBOTIKON_CONTRACT`(약관 관리 `/settings/terms`)가 판·시행일·본문을 맡는다.
 * 여기는 지금 판과 그 판에 동의한 작가 수만(동의 기록은 로보티콘 쪽 표). 게시본이 없으면 null.
 */
export interface EmoticonContractView {
    /** 약관 게시본 버전 번호 */
    version: number;
    /** 시행일 `YYYY-MM-DD` */
    effectiveDate: string;
    /** 지금 판에 동의한 작가 수 */
    agreedCount: number;
}

/** `GET /api/admin/emoticons/settings` 응답 */
export interface EmoticonSettingsView extends EmoticonSettingNumbers {
    /** 유료 판매 켜기 — 끄면 작가가 유료 희망을 고를 수 없고 새 구매를 받지 않는다(이미 산 것은 그대로 — 가정) */
    paidSalesEnabled: boolean;
    /** 가격 단계(원, 오름차순) — 운영자가 승인·가격 바꾸기 때 여기서 고른다 */
    priceTiers: number[];
    /** 판매 중 팩이 쓰는 단계(빼기 막기) */
    priceTiersInUse: number[];
    contract: EmoticonContractView | null;
    /** 기본값(「기본값 N」 안내) */
    defaults: EmoticonSettingNumbers & { paidSalesEnabled: boolean; priceTiers: number[] };
    updatedAt: string | null;
    updatedBy: string | null;
}

/** `PUT /api/admin/emoticons/settings` 요청 */
export interface EmoticonSettingsSaveRequest extends EmoticonSettingNumbers {
    paidSalesEnabled: boolean;
    priceTiers: number[];
}

/** `GET /api/admin/emoticons/creators/{memberNo}` 응답(회원 상세 한 줄) — 작가가 아니면 `null`(서버는 204 또는 404 후보) */
export interface EmoticonCreatorSummary {
    memberNo: string;
    /** 낸 팩 수(임시 제외) */
    packCount: number;
    /** 받은 보상 합계(P, 무료 팩) */
    rewardTotal: number;
    /** 유료 판매액 합계(원, 환불 뺀 것) */
    salesAmount: number;
}

// ── 판매 내역(유료 팩 실결제) ─

/** 판매 상태 — 결제 완료 · 환불됨 */
export type EmoticonSaleStatus = 'PAID' | 'REFUNDED';
export const EMOTICON_SALE_STATUSES: readonly EmoticonSaleStatus[] = ['PAID', 'REFUNDED'];
/** 환불 종류 — 회원 셀프(7일 안·안 씀) · 운영자 예외 */
export type EmoticonRefundKind = 'SELF' | 'EXCEPTION';

/** 판매 줄의 회원(작가·구매 회원) — 프로필이 없으면 `nickname` 이 null(서버 S3) */
export interface EmoticonSaleMemberRef {
    memberNo: string;
    nickname: string | null;
    /** 탈퇴한 회원이면 링크를 두지 않는다 */
    withdrawn: boolean;
}

/** 구매 회원 */
export type EmoticonBuyerRef = EmoticonSaleMemberRef;

/** `GET /api/admin/emoticons/sales` 의 한 줄 */
export interface EmoticonSaleRow {
    id: string;
    paidAt: string;
    packId: string;
    /** 산 때의 팩 이름(사본 — 뒤에 이름이 바뀌어도 그대로) */
    packName: string;
    /** 지금 상점 판의 대표 그림 */
    coverUrl: string;
    creator: EmoticonSaleMemberRef;
    buyer: EmoticonBuyerRef;
    /** 결제 금액(원) */
    amount: number;
    /** 결제 주문 번호 — 결제 화면(`/payments/orders?payment=`)으로 잇는다 */
    paymentId: string;
    /** 처음 쓴 시각 — 한 번도 안 썼으면 null */
    firstUsedAt: string | null;
    status: EmoticonSaleStatus;
    /** 셀프 환불 판정(결제 완료 줄만) — 기한 · 막힌 까닭 */
    selfRefund: { allowed: boolean; until: string; blockedBy: 'USED' | 'EXPIRED' | null } | null;
    refund: { kind: EmoticonRefundKind; at: string; by: string | null; reason: string | null } | null;
    /** 이 판매가 들어간 정산 달(`YYYY-MM`, 확정된 줄) — 아직 확정되지 않았으면 null(지금 환불하면 그 달 예정에서 빠진다). 확정된 뒤 환불은 환불한 달 정산에서 뺀다 */
    settlementMonth: string | null;
}

export interface EmoticonSaleQuery {
    /** UTC ISO 순간 — 이상 */
    from?: string;
    /** UTC ISO 순간 — 미만 */
    to?: string;
    packId?: string;
    status?: EmoticonSaleStatus;
    page: number;
    size: number;
}

export interface EmoticonSalePage {
    items: EmoticonSaleRow[];
    total: number;
    /** 거른 결과 전체의 결제 완료 합계(원 — 환불된 판매는 빠진 보유 중 금액) */
    paidAmount: number;
}

/** `POST /api/admin/emoticons/sales/{id}/refund` 응답 */
export interface EmoticonSaleRefundResult {
    sale: EmoticonSaleRow;
}

// ── 작가 정산 ─

/** 정산 상태 — 예정 → 확정 → 지급 완료 / 보류 */
export type EmoticonSettlementStatus = 'ESTIMATED' | 'CONFIRMED' | 'PAID' | 'HELD';
export const EMOTICON_SETTLEMENT_STATUSES: readonly EmoticonSettlementStatus[] = ['ESTIMATED', 'CONFIRMED', 'PAID', 'HELD'];
/** 작가 갈래 — 개인(원천징수 3.3%) · 사업자(세금계산서). 서버 S2 정산 정보·user-web 과 같은 이름 */
export type EmoticonPayeeType = 'INDIVIDUAL' | 'BUSINESS';

/** 정산 계좌(가린 값 — 지금 정산 정보) */
export interface EmoticonPayoutAccountMasked {
    /** 은행 코드 — 이름은 `UtilEmoticon.bank()` */
    bank: string;
    /** `****1234` 꼴(끝 네 자리) */
    maskedNumber: string;
    /** 예금주(가운데 가림 — 김*나) */
    holderMasked: string;
}

/** 정산 한 줄의 팩별 내역 */
export interface EmoticonSettlementPackLine {
    packId: string;
    packName: string;
    coverUrl: string;
    price: number;
    salesCount: number;
    salesAmount: number;
    refundCount: number;
    refundAmount: number;
}

/** 정산 처리 기록 — 최근이 앞, 맨 끝 `CREATED` 는 그 달 첫 판매·환불 시각(서버가 만들어 넣는다) */
export interface EmoticonSettlementEvent {
    action: 'CREATED' | 'CONFIRMED' | 'HELD' | 'UNHELD' | 'PAID' | 'ACCOUNT_VIEWED';
    at: string;
    /** 운영자 이름 — null 이면 시스템(`CREATED`) */
    by: string | null;
    /** 보류·계좌 보기 사유 · 지급 메모 */
    note: string | null;
}

/**
 * `GET /api/admin/emoticons/settlements?month=` 의 한 줄 — 작가 한 사람의 그 달 정산. ID 는 `yyyyMM-작가회원번호`(서버가 정한다 — 화면은 받은 값을 그대로 넘긴다).
 * 확정 뒤 환불로 판매보다 환불이 많은 달은 부가세·정산 기준·수수료·작가 몫이 음수일 수 있다(같은 식을 부호만 바꿔 — 음수 작가 몫은 지급하지 않고 다음 달로 넘겨 뺀다).
 */
export interface EmoticonSettlementRow {
    id: string;
    /** 판매 달 `YYYY-MM` */
    month: string;
    creator: EmoticonCreatorRef;
    /** 굳힌 줄은 확정 때 구분, 예정은 지금 정산 정보 — 정산 정보가 없으면 null(원천징수는 개인 기준으로 어림한 값) */
    payeeType: EmoticonPayeeType | null;
    /** 사업자면 가린 사업자 번호 */
    businessNoMasked: string | null;
    salesCount: number;
    /** 판매액(원) */
    salesAmount: number;
    refundCount: number;
    /** 이 달 정산에서 빼는 환불(원) — 확정 전 환불은 판매 달, 확정 뒤 환불은 환불한 달 */
    refundAmount: number;
    /** 부가세(원) = (판매액 − 환불) − 정산 기준 — 사이트가 판매자라 사이트가 낸다 */
    vatAmount: number;
    /** 정산 기준(원) = (판매액 − 환불) ÷ 1.1, 원 미만 버림 */
    baseAmount: number;
    /** 수수료율(%) — 굳힌 줄은 확정 때 값, 예정은 지금 설정 */
    feeRatePercent: number;
    /** 수수료(원) = 정산 기준 − 작가 몫 */
    feeAmount: number;
    /** 원천징수 전 작가 몫(원) = 정산 기준 × (100 − 수수료율) ÷ 100, 원 미만 버림 */
    shareAmount: number;
    /** 원천징수(원) — 개인 3.3%(10원 미만 버림), 사업자 0. 넘긴 줄은 0(다음 달 합에서 뗀다) */
    withholdingAmount: number;
    /** 앞 달에서 넘어온 원천징수 전 금액(원) — 음수면 앞 달 음수를 이 달에서 뺀다 */
    carriedIn: number;
    /** 이번에 지급할 금액(원) — 넘기면 0 */
    payoutAmount: number;
    /** 다음 달로 넘기는 원천징수 전 금액(원) — 합이 최소 지급액 미만이거나 0 이하일 때(음수면 다음 달에서 뺀다) */
    carriedOut: number;
    status: EmoticonSettlementStatus;
    /** 지급 예정일(`YYYY-MM-DD`, 다음 달 정산일 — 확정 때 굳는다) */
    payoutDate: string;
    /** 확정 가능 시각 = max(다음 달 1일 0시 KST, 그 줄 판매의 환불 기한 최댓값) — 이 뒤에야 확정할 수 있다(서버는 건너뛴다 `NOT_CLOSED`) */
    closesAt: string;
    /** 확정 시각 — 확정 전이면 null */
    confirmedAt: string | null;
    /** 지금 정산 정보의 가린 계좌 — 정산 정보가 없으면 null */
    account: EmoticonPayoutAccountMasked | null;
    /** 운영자 이름이 null 이면 지워진 운영자 */
    hold: { reason: string; at: string; by: string | null } | null;
    paid: { transferDate: string; memo: string | null; at: string; by: string | null } | null;
}

export interface EmoticonSettlementList {
    month: string;
    items: EmoticonSettlementRow[];
    /** 그 달 합계 */
    totals: { salesAmount: number; refundAmount: number; vatAmount: number; feeAmount: number; withholdingAmount: number; payoutAmount: number; carriedOut: number };
    /** 고를 수 있는 달(최근이 앞) */
    months: string[];
    /** 지금 설정의 규칙(굳힌 줄의 수수료율은 줄의 `feeRatePercent`) */
    rules: { feeRatePercent: number; payoutMinimum: number; refundDays: number; settlementDay: number };
}

/** `GET /api/admin/emoticons/settlements/{id}` 응답 */
export interface EmoticonSettlementDetail extends EmoticonSettlementRow {
    /** 팩별 내역 — `packName` 은 그 달 마지막 구매의 산 때 이름, `coverUrl` 은 지금 판, `price` 는 그 달 마지막 판매 금액 */
    packs: EmoticonSettlementPackLine[];
    events: EmoticonSettlementEvent[];
}

/** 확정에서 건너뛴 까닭 — 서버가 이 차례로 본다 */
export type EmoticonSettlementSkipReason = 'NOT_FOUND' | 'NOT_ESTIMATED' | 'NOT_CLOSED' | 'PREVIOUS_OPEN' | 'PAYOUT_ACCOUNT_MISSING';

/** `POST …/settlements/confirm { ids }`(1~200개, 아니면 `400 fields.ids`) 응답 — 맞지 않는 줄만 건너뛴다(한 트랜잭션) */
export interface EmoticonSettlementBulkResult {
    confirmed: number;
    skipped: number;
    /** 건너뛴 줄과 까닭 */
    skippedItems: { id: string; reason: EmoticonSettlementSkipReason }[];
}

/**
 * `POST …/settlements/{id}/paid` 요청 — `400 fields.transferDate`(형식·없는 날·오늘(KST) 뒤) · `fields.memo`(200자)
 * → 404 → 확정이 아니면 `409 EMOTICON_SETTLEMENT_NOT_CONFIRMED` → 지급액 0 `409 EMOTICON_SETTLEMENT_NOTHING_TO_PAY`
 */
export interface EmoticonSettlementPaidRequest {
    /** 이체한 날 `YYYY-MM-DD` */
    transferDate: string;
    /** 빈 메모는 서버가 null 로 둔다 */
    memo: string;
}

/** 정산 정보의 작가 구분(서버 S2 정산 정보) — 개인 · 사업자. S4 정산 줄의 `EmoticonPayeeType` 과 같은 이름 */
export type EmoticonPayoutType = 'INDIVIDUAL' | 'BUSINESS';

/**
 * 정산 계좌 원문 보기 응답 — S2 작가 단위 `POST /api/admin/emoticons/creators/{memberNo}/payout-account/reveal { reason }` 와
 * S4 정산 줄 단위 `POST …/settlements/{id}/account/reveal { reason }`(처리 기록 `ACCOUNT_VIEWED`) 가 같은 모양. 화면 메모리에만 두고 `expiresAt`(10분 뒤)이 지나면 지운다.
 * `bank` 는 은행 코드(`UtilEmoticon.bank()` 로 이름), 사업자 번호는 사업자만(개인은 null).
 */
export interface EmoticonPayoutAccountReveal {
    type: EmoticonPayoutType;
    bank: string;
    number: string;
    holder: string;
    businessNumber: string | null;
    expiresAt: string;
}
