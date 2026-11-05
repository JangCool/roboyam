/**
 * 로보티콘 스튜디오(내 제작 팩·판매·정산) API 의 모양(`useEmoticonStudioApi`) — 계약 가정 1(가격)·4(수수료·정산)·5(정산 정보)·6(무료 팩 보상)·8(신청 상태)·9(작가 자격).
 */

/** 판매 희망 — 무료 · 유료 판매 희망(카카오 방식 — 가격은 운영자가 승인 때 가격 단계에서 고른다) */
export type EmoticonSaleWish = 'FREE' | 'PAID';

/** 신청 상태 — 임시 → 심사 대기 → 판매 중 / 반려(사유 — 고쳐 다시 신청) · 운영자 판매 중단(사유) */
export type EmoticonStudioStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';

/** 올린 그림의 형식 */
export type EmoticonImageFormat = 'GIF' | 'PNG';

/** 올린 그림 하나(이모티콘·대표 이미지) — `POST /api/me/emoticon-studio/images`(multipart `file`) 의 응답 */
export interface EmoticonStudioImage {
    /** 올린 그림 ID — 저장 때 이것으로 가리킨다 */
    imageId: string;
    url: string;
    format: EmoticonImageFormat;
    width: number;
    height: number;
    bytes: number;
    /** GIF 프레임 수(PNG 는 1) */
    frames: number;
}

/** 팩 안 로보티콘 한 개(편집 모양) */
export interface EmoticonStudioEmoticon extends EmoticonStudioImage {
    /** 이름표(1~10자) */
    label: string;
}

/** 승인된 팩을 고쳐 다시 낸 새 판(가격 바꾸기 포함) — 심사 동안 지금 판이 계속 보인다(가정 8) */
export interface EmoticonStudioRevision {
    status: 'PENDING' | 'REJECTED';
    submittedAt: string;
    rejectReason: string | null;
}

/** 내 팩 한 개(목록 줄) */
export interface EmoticonStudioPackRow {
    id: string;
    name: string;
    coverUrl: string | null;
    status: EmoticonStudioStatus;
    /** 반려 사유(REJECTED) */
    rejectReason: string | null;
    /** 판매 중단 사유(SUSPENDED) */
    suspendReason: string | null;
    /** 승인된 팩의 새 판 심사 — 없으면 null */
    revision: EmoticonStudioRevision | null;
    /** 작가가 고른 판매 희망 — 무료 · 유료 판매 희망(금액은 운영자가 정한다) */
    saleWish: EmoticonSaleWish;
    /** 운영자가 정한 가격(원) — 무료이거나 아직 정하지 않았으면 0(유료 희망 + 0 = 「가격 심사 중」) */
    price: number;
    count: number;
    /** 받은(무료)·산(유료) 수 */
    acquireCount: number;
    /** 이 팩으로 받은 보상 포인트 합계(무료 팩) */
    rewardTotal: number;
    /** 이 팩의 판매액 합계(유료 팩, 환불 뺀 값) */
    salesTotal: number;
    /** 운영자가 마지막으로 가격을 바꾼 기록(유료↔무료 포함) — 없으면 null. 이미 산 사람·지난 정산은 그대로다 */
    lastPriceChange: EmoticonPriceChange | null;
    updatedAt: string;
}

/**
 * 운영자의 가격 변경 한 건 — 유료↔무료도 바꿀 수 있다(사용자 확정 2026-11-05). 이미 산 사람은 그대로 쓰고 그 구매의 환불 규칙도 그대로,
 * 지난 판매 정산은 그대로, 무료가 되면 그 뒤 받기마다 작가 보상 포인트가 생긴다.
 */
export interface EmoticonPriceChange {
    /** 바꾸기 전 가격(원, 0 = 무료) */
    from: number;
    /** 바꾼 뒤 가격(원, 0 = 무료) */
    to: number;
    /** 운영자가 적은 사유 — 없으면 null */
    reason: string | null;
    changedAt: string;
}

/** 최근 보상 한 줄 — 원장 사유 후보 `EMOTICON_CREATOR_REWARD`(무료 팩) */
export interface EmoticonRewardEntry {
    id: string;
    packId: string;
    packName: string;
    points: number;
    createdAt: string;
}

/**
 * 이번 달 판매 요약(유료 팩) = 이번 달 정산 줄의 예정 값(S4 — 이번 달 판매 + 이번 달에서 빼는 환불, 지난 달이 확정된 뒤의 환불 포함). 이번 달 줄이 없으면 모두 0.
 * 정산 예정액은 환불 기간이 남은 판매도 넣은 추정이다
 */
export interface EmoticonSalesSummary {
    /** `YYYY-MM`(서비스 시간대) */
    month: string;
    salesCount: number;
    salesAmount: number;
    refundAmount: number;
    /** 부가세(사이트가 낸다) = (판매 − 환불) − 정산 기준 */
    vatAmount: number;
    /** 정산 기준 = (판매 − 환불) ÷ 1.1 */
    baseAmount: number;
    /** 수수료 = 정산 기준 × 수수료율 */
    feeAmount: number;
    /** 원천징수 전 작가 몫 = 정산 기준 − 수수료 — 확정된 달의 판매가 이번 달에 환불되어 음수일 수 있다(다음 달 정산에서 뺀다) */
    expectedPayout: number;
}

/** `GET /api/me/emoticon-studio` */
export interface EmoticonStudioOverview {
    packs: EmoticonStudioPackRow[];
    /** 모든 팩의 받은·산 수 합 */
    acquireTotal: number;
    /** 받은 보상 포인트 합계(무료 팩) */
    rewardTotal: number;
    /** 오늘 받은 보상(하루 상한과 견준다) */
    rewardToday: number;
    /** 최근 보상(최근 것 먼저, 10줄) */
    recentRewards: EmoticonRewardEntry[];
    /** 지금 심사 대기 팩 수(상한 `rules.maxPending`) */
    pendingCount: number;
    /** 이번 달 판매 */
    sales: EmoticonSalesSummary;
}

/** 정산 상태 — 예정 → 확정 → 지급 완료(운영자가 이체 후 기록) / 보류(사유) */
export type EmoticonSettlementStatus = 'ESTIMATED' | 'CONFIRMED' | 'PAID' | 'HELD';

/**
 * 정산 한 줄(한 달) — 금액은 원. 확정된 달의 판매가 뒤에 환불되어 판매보다 환불이 많은 달은 부가세·정산 기준·수수료·작가 몫이 음수일 수 있다(같은 식을 부호만 바꿔 쓴다).
 * 정산 줄 ID 는 서버가 정한다(`yyyyMM-회원번호`) — 화면은 키로만 쓴다
 */
export interface EmoticonSettlement {
    id: string;
    /** 판매 달 `YYYY-MM` */
    month: string;
    salesCount: number;
    salesAmount: number;
    refundAmount: number;
    /** 부가세(사이트가 낸다) */
    vatAmount: number;
    /** 정산 기준 = (판매 − 환불) ÷ 1.1 */
    baseAmount: number;
    feeAmount: number;
    /** 원천징수 전 작가 몫 = 정산 기준 − 수수료 */
    shareAmount: number;
    /** 원천징수(개인 3.3%, 사업자는 0 — 세금계산서). 넘긴 달은 0 */
    withholdingAmount: number;
    /** 지급액 = (작가 몫 + 넘어온 금액) − 원천징수 — 넘긴 달은 0 */
    payoutAmount: number;
    /** 앞 달에서 넘어온 **원천징수 전** 금액 — 음수면 앞 달의 음수(확정 뒤 환불)를 이 달에서 뺀다 */
    carriedIn: number;
    /** 다음 달로 넘겼다(합이 최소 지급액 미만이거나 0 이하 — 음수 이월도 true) */
    carriedOver: boolean;
    /** 다음 달로 넘긴 금액(원천징수 전) — 음수면 다음 달에서 뺀다. 넘기지 않았으면 0 */
    carriedOut: number;
    status: EmoticonSettlementStatus;
    /** 보류 사유(HELD) */
    holdReason: string | null;
    /** 지급(예정)일 `YYYY-MM-DD` — 넘긴 달은 null */
    payDate: string | null;
}

/** `GET /api/me/emoticon-studio/settlements` — 처음 달 ~ 이번 달, 최근 달 먼저(이번 달 예정 줄이 맨 앞) */
export interface EmoticonSettlementList {
    items: EmoticonSettlement[];
}

/** 작가 구분 — 개인(원천징수 3.3%) · 사업자(세금계산서) */
export type EmoticonPayoutType = 'INDIVIDUAL' | 'BUSINESS';

/**
 * `GET /api/me/emoticon-studio/payout-account` — 정산 정보(서버가 암호화 저장, 화면에는 가린 값만). 등록 전이면 `registered: false` 이고 나머지 칸은 null.
 * 예금주는 본인인증한 이름과 같아야 한다(서버 검사 — 이름은 화면에 내려오지 않아 `identityNameMasked` 만 온다).
 */
export interface EmoticonPayoutAccount {
    registered: boolean;
    type: EmoticonPayoutType | null;
    /** 사업자 번호(가림 — `123-45-*****`) */
    businessNumberMasked: string | null;
    bankCode: string | null;
    /** 계좌 끝 네 자리만(`****1234`) */
    accountMasked: string | null;
    /** 예금주(가림 — `홍*동`) */
    holderMasked: string | null;
    /** 본인인증한 이름(가림) — 예금주와 같아야 한다는 안내에 쓴다. 미인증이면 null */
    identityNameMasked: string | null;
    updatedAt: string | null;
}

/**
 * `PUT /api/me/emoticon-studio/payout-account` — 오류 차례: 작가 자격 403 → 본인인증 `403 IDENTITY_REQUIRED` → 인증 이름 없음 `409 EMOTICON_IDENTITY_NAME_MISSING`
 * → `400 VALIDATION fields.type|businessNumber|bankCode|accountNumber|holderName`(예금주 비었거나 인증 이름과 다름 · 사업자 번호 10자리+검증 숫자 · 계좌 숫자 10~14자리) → `503 DATA_KEY_MISSING`
 */
export interface EmoticonPayoutAccountRequest {
    type: EmoticonPayoutType;
    /** 사업자일 때만(숫자 10자리) */
    businessNumber: string | null;
    bankCode: string;
    /** 숫자만 */
    accountNumber: string;
    holderName: string;
}

/** `GET /api/me/emoticon-studio/packs/{id}` — 편집 화면. 승인된 팩은 지금 판(또는 심사 중인 새 판)의 값 */
export interface EmoticonStudioPack extends EmoticonStudioPackRow {
    description: string;
    tags: string[];
    cover: EmoticonStudioImage | null;
    emoticons: EmoticonStudioEmoticon[];
    /** 승인된 유료 팩의 가격 변경 요청 메모(작가가 남김 — 바꾸는 것은 운영자) */
    priceRequestMemo: string | null;
}

/** 저장 본문 — 임시 저장(`POST`·`PUT …/packs[/{id}]`)과 심사 요청(`POST …/{id}/submit`)이 같은 모양. 금액은 보내지 않는다(운영자가 정한다) */
export interface EmoticonStudioSaveRequest {
    name: string;
    description: string;
    tags: string[];
    coverImageId: string | null;
    emoticons: { imageId: string; label: string }[];
    /** 판매 희망 — 유료 희망이면 정산 정보 + 상품화 계약 동의가 있어야 심사 요청이 된다(`409 EMOTICON_PAYOUT_REQUIRED`·`EMOTICON_CONTRACT_REQUIRED`). 승인된 팩은 바꿀 수 없다(운영자만) */
    saleWish: EmoticonSaleWish;
    /** 승인된 유료 팩의 가격 변경 요청 메모(선택, 200자) — 없으면 null */
    priceRequestMemo: string | null;
}

/** 심사 요청 본문 — 저장 본문 + 동의 셋(가정 9) + 유료 희망이면 상품화 계약 동의 판 */
export interface EmoticonStudioSubmitRequest extends EmoticonStudioSaveRequest {
    agreeOwnership: boolean;
    agreeLicense: boolean;
    agreePolicy: boolean;
    /**
     * 유료 희망(또는 이미 유료로 나온 팩의 새 판)일 때 동의한 상품화 계약 판(약관 게시본 버전, 정수 — 화면 「v1」) — 지금 판과 다르면 `409 EMOTICON_CONTRACT_REQUIRED`
     * (이미 지금 판에 동의했으면 null 이어도 통과). 무료면 null
     */
    agreeContractVersion: number | null;
}

/** 상품화 계약 동의 한 건 — 추가만 하는 기록(판이 바뀌어 다시 동의하면 한 줄 더) */
export interface EmoticonContractAgreement {
    /** 동의한 게시본 버전(정수) */
    version: number;
    agreedAt: string;
}

/**
 * `GET /api/me/emoticon-studio/contract` — 로보티콘 상품화 계약(판이 있는 문서, 계약 가정 1). 작가가 사이트에 판매·표시·축소 이용을 허락하고 사이트가 판매 수익을 나눈다.
 * 문서는 약관 종류 `ROBOTIKON_CONTRACT`(운영자가 약관 관리에서 판을 올린다)의 시행 중 게시본 — 게시본이 없으면 응답이 null 이다(`useEmoticonStudioApi.contract`).
 * 제목 = 본문 첫 `h2`·`h3`(없으면 서버 기본 문구), 요약 = 본문 첫 목록의 항목(최대 8), 전문 = 서버가 HTML 을 평문으로 바꾼 것. 내가 동의한 판·시각이 함께 온다 — 판이 바뀌면 다음 유료 신청 때 다시 동의한다.
 */
export interface EmoticonCreatorContract {
    /** 지금 판 — 약관 종류 `ROBOTIKON_CONTRACT` 의 게시본 버전(정수, 화면은 `v1`) */
    version: number;
    /** 시행일 `YYYY-MM-DD` */
    effectiveDate: string;
    title: string;
    /** 요약 몇 줄(신청 화면에 그대로) */
    summary: string[];
    /** 전문(평문) */
    body: string;
    /** 내 동의 기록(오래된 것 먼저, 추가만) — 「내 최근 동의」는 `UtilEmoticon.latestAgreement` 로 읽는다. 동의한 적 없으면 빈 목록 */
    agreements: EmoticonContractAgreement[];
}
