/**
 * 사업자 심사(관리자) — 서버와 주고받는 모양. 계약 `docs/features/business-contract.md`(근거 docs/plans/biz-ad-hotdeal/00-overview.md ★ 절 · 01-business.md 7-1).
 * 사업자 = 「신원 1줄 + 서비스별 이용 권한 N줄」(HOTDEAL · AD). 목록에는 가린 값만 온다(평문 칸 자체가 없다).
 */

/** 신원 상태 — 심사 중 · 승인 · 반려 · 정지 · 탈퇴 */
export type BusinessStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'WITHDRAWN';
export const BUSINESS_STATUSES: readonly BusinessStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED', 'WITHDRAWN'];

/** 서비스 — 직접홍보는 광고 TEXT_LINE 으로 합쳤다(D2) */
export type BusinessService = 'HOTDEAL' | 'AD';
export const BUSINESS_SERVICES: readonly BusinessService[] = ['HOTDEAL', 'AD'];

export type BusinessServiceStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'OFF';

/** 국세청 상태조회 결과 — 계속사업자 · 휴업 · 폐업 · 국세청에 없는 번호 · 조회 불가(장애·키 없음·아직 조회 전). 「계속」일 때만 [승인] 이 켜진다(B1) */
export type NtsBizStatus = 'CONTINUING' | 'PAUSED' | 'CLOSED' | 'NOT_FOUND' | 'UNAVAILABLE';

/**
 * 국세청 진위확인 — 등록번호·상호·대표자명·개업일이 국세청 정보와 맞는가. 일치 · 불일치(승인 잠금) · 미확인(개업일이 없는 옛 신청 — 상태조회만 했다, 사유를 적어 승인) ·
 * 조회 불가(장애·키 없음 — 사유를 적어 승인)
 */
export type NtsValidity = 'MATCH' | 'MISMATCH' | 'UNCHECKED' | 'UNAVAILABLE';

export interface NtsCheck {
    status: NtsBizStatus;
    validity: NtsValidity;
    /** 조회 시각(ISO) */
    checkedAt: string;
    /** 과세 유형(일반과세자·간이과세자 …) — 조회 불가면 null */
    taxType: string | null;
    /** 휴·폐업 일자(`YYYY-MM-DD`) */
    closedOn: string | null;
    /** 조회 불가 사유(서버 문구) */
    message: string | null;
}

export interface BusinessServiceRow {
    service: BusinessService;
    status: BusinessServiceStatus;
}

export interface BusinessRow {
    id: string;
    name: string;
    /** 가린 등록번호(`124-**-***98`) */
    maskedRegNo: string;
    status: BusinessStatus;
    /** 승인 뒤 상호·번호를 고쳐 다시 심사 중(F7 — `status` 는 `PENDING`) */
    reReview: boolean;
    services: BusinessServiceRow[];
    applicant: { memberId: string; nickname: string };
    appliedAt: string;
    nts: NtsCheck;
}

export interface BusinessCertificate {
    /** 문서 번호 — 접수 기록 묶음 `fileChannels`·폐기 스캔본과 같은 번호 */
    id: string;
    fileName: string;
    mimeType: string;
    size: number;
    uploadedAt: string;
    /** 마지막으로 연 운영자·시각 — 열람은 기록된다 */
    lastViewedBy: string | null;
    lastViewedAt: string | null;
}

/**
 * 서비스 카드 — 영향 건수(진행·예약 중인 신청 건).
 * 서비스별 정지(2026-11-02 사용자 결정 — 신원은 그대로 두고 한 서비스만 정지·해제) 칸은 서버가 늘 준다:
 *  · 「서비스만 정지」 = `suspendCode != null`(`status` 는 `SUSPENDED`). 신원 정지를 따라간 줄은 `status` 만 `SUSPENDED` 이고 사유 칸은 null —
 *    신원 정지를 풀어도 서비스만 정지된 줄은 남으므로 신원 상태가 아니라 `suspendCode` 로 가른다.
 *  · `impact` = 이 서비스만 정지하면 걸리는 건수(이 서비스 신청 건만).
 */
export interface BusinessServiceCard extends BusinessServiceRow {
    liveCount: number;
    pendingCount: number;
    /** 서비스만 정지한 사유 코드(신원 정지 코드 묶음) — 서비스만 정지가 아니면 null */
    suspendCode: BusinessSuspendCode | null;
    /** 서비스만 정지한 사유 글(사업자에게 그대로 보인다) */
    suspendText: string | null;
    /** 서비스만 정지한 시각(ISO) */
    suspendedAt: string | null;
    /** 정지한 운영자 이름 */
    suspendedByName: string | null;
    /** 이 서비스만 정지할 때의 영향 건수 */
    impact: BusinessSuspendImpact;
}

/** 정지하면 영향받는 건수 — 정지 창이 처리 방법을 고르게 보여 준다(H2) */
export interface BusinessSuspendImpact {
    /** 결제 대기 + 입금 확인 대기 */
    awaitingPayment: number;
    /** 결제·입금 끝, 시작 전 예약 */
    scheduled: number;
    /** 진행 중 */
    running: number;
}

/**
 * 처리 기록 코드(서버 `BusinessStore`). 등록증 종이 접수(V140 — biz-doc-intake-contract): 접수 · 처리 바꾸기 · 폐기 · 잘못 기록 · 운영자 등록증 올리기 —
 * `note` 는 서버가 읽는 사람 언어로 바꾼 글(접수 방식·처리·들어온 길) 또는 사유 그대로.
 */
export type BusinessHistoryAction =
    | 'APPLIED'
    | 'NTS_CHECKED'
    | 'APPROVED'
    | 'REJECTED'
    | 'SUSPENDED'
    | 'UNSUSPENDED'
    | 'SERVICE_SUSPENDED'
    | 'SERVICE_RESUMED'
    | 'REVEALED'
    | 'CERT_VIEWED'
    | 'UPDATED'
    | 'WITHDRAWN'
    | 'PAPER_RECEIVED'
    | 'PAPER_HANDLING'
    | 'PAPER_DISPOSED'
    | 'PAPER_VOIDED'
    | 'CERT_UPLOADED';

/** 등록증을 내는 길 — 파일 올리기 · 우편·팩스로 보냄(V140) */
export type BusinessCertificateDelivery = 'UPLOAD' | 'MAIL_FAX';

export interface BusinessHistory {
    at: string;
    /** `SERVICE_SUSPENDED`·`SERVICE_RESUMED` = 서비스별 정지·해제(`service` 칸에 그 서비스, `note` 는 서버가 읽기 좋게 만든 글) */
    action: BusinessHistoryAction;
    actor: string;
    note: string | null;
    /** 서비스 하나에 대한 처리면 그 서비스(서비스별 정지·해제), 아니면 null */
    service: BusinessService | null;
}

export interface BusinessDetail extends BusinessRow {
    /** 가린 신원 — 대표자·주소·연락처·이메일 */
    masked: { representative: string; address: string; phone: string; email: string };
    /** 개업일(`YYYY-MM-DD`, 가리지 않는다) — 개업일 칸이 생기기 전 신청이면 null(진위 미확인) */
    openedOn: string | null;
    certificate: BusinessCertificate | null;
    serviceCards: BusinessServiceCard[];
    impact: BusinessSuspendImpact;
    rejection: { code: BusinessRejectCode; text: string } | null;
    suspension: { code: BusinessSuspendCode; text: string; at: string } | null;
    history: BusinessHistory[];
    /**
     * 등록증을 내는 길 — 우편·팩스(`MAIL_FAX`)인데 아직 받지 않았으면(지금 등록증 사본도, 이번 신청 뒤 종이 접수도 없음) 승인 409 `BUSINESS_CERT_NOT_RECEIVED`.
     */
    certificateDelivery: BusinessCertificateDelivery;
    /** 등록증을 받았나(서버 판정 — 지금 등록증 사본 · 이번 신청 뒤 잘못 기록이 아닌 종이 접수). 승인 잠금 `UtilDocIntake.certificateAwaiting` 이 쓴다 */
    certificateReceived: boolean;
}

export interface BusinessQuery {
    status?: BusinessStatus;
    service?: BusinessService;
    q?: string;
    page: number;
    size: number;
}

export interface BusinessPage {
    items: BusinessRow[];
    total: number;
    counts: Record<BusinessStatus, number>;
}

/** 평문 — 10분 뒤 화면이 지운다. 복사 단추 없음 */
export interface BusinessReveal {
    regNo: string;
    representative: string;
    address: string;
    phone: string;
    email: string;
    /** 이 시각이 지나면 화면이 가린다(ISO) */
    hideAt: string;
}

/** 사업자등록증 사본 — 공개 주소가 없어 `data:` 주소로 온다(열 때마다 열람 기록) */
export interface BusinessCertificateFile {
    url: string;
    mimeType: string;
    fileName: string;
}

/** 정지 해제 때 정지로 내려간 진행 중 건 — 남은 기간 연장 후 되돌리기 · 환불 유지(H2) */
export type UnsuspendRestore = 'EXTEND_RESTORE' | 'KEEP_REFUND';

export interface BusinessUnsuspendRequest {
    restore: UnsuspendRestore;
    note?: string;
}

export interface BusinessApproveRequest {
    /** 국세청 조회 불가·진위 미확인인데 승인할 때만 — 사유(감사) */
    ntsOverrideReason?: string;
}

export type BusinessRejectCode = 'DOC_UNREADABLE' | 'INFO_MISMATCH' | 'NTS_CLOSED' | 'DUPLICATE' | 'OTHER';
export const BUSINESS_REJECT_CODES: readonly BusinessRejectCode[] = ['DOC_UNREADABLE', 'INFO_MISMATCH', 'NTS_CLOSED', 'DUPLICATE', 'OTHER'];

export type BusinessSuspendCode = 'POLICY_VIOLATION' | 'FRAUD' | 'NTS_CLOSED' | 'OTHER';
export const BUSINESS_SUSPEND_CODES: readonly BusinessSuspendCode[] = ['POLICY_VIOLATION', 'FRAUD', 'NTS_CLOSED', 'OTHER'];

export interface BusinessRejectRequest {
    code: BusinessRejectCode;
    text: string;
}

/** 정지 때 걸린 건 처리(H2 기본값 = 자동 취소 · 전액 환불 · 긴급 내림 + 일할 환불 심사) */
export type SuspendPendingAction = 'CANCEL' | 'KEEP';
export type SuspendScheduledAction = 'REFUND_FULL' | 'HOLD';
export type SuspendRunningAction = 'TAKEDOWN_PRORATE' | 'TAKEDOWN_ONLY';

export interface BusinessSuspendRequest {
    code: BusinessSuspendCode;
    text: string;
    pendingAction: SuspendPendingAction;
    scheduledAction: SuspendScheduledAction;
    runningAction: SuspendRunningAction;
}
