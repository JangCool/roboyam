/**
 * 운영 주체(사이트를 운영하는 사람·사업자) API 의 요청·응답 모양 — 서버 `server/admin` `SiteOperatorRequest`·`SiteOperatorResponse` 와 칸 이름·null 여부가 같다(terms-operator-contract).
 * 일반 설정 「운영 주체」 구획(`SiteOperatorSettings`)이 읽고 저장하고, 약관 화면이 자리표시(`{{운영자}}` 등 — `UtilTermsPlaceholder`)를 바꿀 값으로 읽는다.
 * 시각(`changedAt`·`updatedAt`)은 ISO-8601.
 */

/**
 * 운영 주체 단계 — 개인(사업자등록 전) → 개인사업자(사업자등록 후) → 법인(법인 설립 후).
 * 약관 속 호칭: 개인·개인사업자 「운영자」, 법인 「회사」.
 */
export type SiteOperatorStage = 'INDIVIDUAL' | 'SOLE_PROPRIETOR' | 'CORPORATION';

/** 단계 차례(화면의 라디오 카드 차례) */
export const SITE_OPERATOR_STAGES: readonly SiteOperatorStage[] = ['INDIVIDUAL', 'SOLE_PROPRIETOR', 'CORPORATION'];

/** 사업자 정보 — 개인사업자·법인일 때만(개인은 null) */
export interface SiteOperatorBusiness {
    /** 상호(법인은 회사명) */
    businessName: string;
    /** 대표자 — 커머스 사업자 정보에서 옮겨 온 행은 비어(null) 있을 수 있다(저장할 때는 필수) */
    representative: string | null;
    /** 사업자등록번호 `123-45-67890` — 옮겨 온 행은 null 일 수 있다(저장할 때는 필수) */
    registrationNumber: string | null;
    /** 사업장 주소(평문 한 줄) — 옮겨 온 행은 null 일 수 있다(저장할 때는 필수) */
    address: string | null;
    /** 통신판매업 신고번호 — 없으면 공구·충전·상점 판매를 켤 수 없다(판정은 서버) */
    mailOrderNumber: string | null;
    /** 통신판매업 신고 기관(예: 서울특별시 강남구) */
    mailOrderAuthority: string | null;
    /** 법인등록번호 — 법인만(그 밖에는 null) */
    corporateNumber: string | null;
}

/** 운영 주체 값 — 저장 요청과 응답이 같은 모양이다 */
export interface SiteOperatorProfile {
    stage: SiteOperatorStage;
    /** 운영자 이름(1~30자, 저장한 적 없으면 빈 글자) — 개인정보처리방침에 개인정보 보호책임자로 공개된다(같음일 때) */
    operatorName: string;
    /** 연락 이메일(저장한 적 없으면 빈 글자) */
    contactEmail: string;
    /** 전화번호 — 개인은 선택(null), 개인사업자·법인은 필수 */
    phone: string | null;
    /** 개인정보 보호책임자 = 운영자(true 면 아래 두 칸은 null) */
    privacyOfficerSameAsOperator: boolean;
    privacyOfficerName: string | null;
    privacyOfficerEmail: string | null;
    /** 호스팅 사업자(선택, 예: Amazon Web Services) */
    hostingProvider: string | null;
    /** 개인사업자·법인일 때만 — 개인은 null */
    business: SiteOperatorBusiness | null;
}

/** 단계 전환 이력 한 줄 */
export interface SiteOperatorStageChange {
    changedAt: string;
    fromStage: SiteOperatorStage;
    toStage: SiteOperatorStage;
    /** 처리한 운영자 표시 이름(없으면 null) */
    changedBy: string | null;
}

/** `GET /api/admin/site/operator` · `PUT` 응답 — 전환 이력은 최신 먼저 */
export interface SiteOperatorResponse extends SiteOperatorProfile {
    /** 마지막 저장 시각(저장한 적 없으면 null) */
    updatedAt: string | null;
    history: SiteOperatorStageChange[];
}

/** `PUT /api/admin/site/operator` 본문 — 개인 단계면 `business` null, 「같음」이면 보호책임자 두 칸 null(서버도 같은 값으로 비운다) */
export type SiteOperatorRequest = SiteOperatorProfile;
