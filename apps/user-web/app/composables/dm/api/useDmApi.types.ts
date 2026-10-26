/**
 * 쪽지 API 가 주고받는 모양 — 서버(api)도 이 모양으로 준다. 모양은 community `docs/api-dm.md` 그대로다(닉네임 이미지 칸은 로보얌 작성자 표시에 없어 뺐다).
 * 계약 후보: `docs/features/member-social-contract.md`. ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO, **하루는 한국 시간 0시에 시작**한다.
 */
import type { AuthorDecoration } from '~/composables/pointshop/api/usePointShopApi.types';
import type { Role } from '~/composables/auth/api/useAuthApi.types';

/** 쪽지를 보낼 수 없는 이유(보낼 수 있으면 null) — 여러 이유가 겹치면 서버가 이 순서로 하나만 알린다: SELF → EMAIL_NOT_VERIFIED → ACCOUNT_RESTRICTED → BUSINESS_RESTRICTED → NOT_PERMITTED → TOO_NEW → RECIPIENT_WITHDRAWN → UNAVAILABLE → RECIPIENT_REFUSES → DAILY_LIMIT → NEW_PARTNER_LIMIT */
export type DmReason =
    | 'SELF'
    | 'EMAIL_NOT_VERIFIED'
    | 'ACCOUNT_RESTRICTED'
    | 'BUSINESS_RESTRICTED'
    | 'NOT_PERMITTED'
    | 'TOO_NEW'
    | 'RECIPIENT_WITHDRAWN'
    | 'UNAVAILABLE'
    | 'RECIPIENT_REFUSES'
    | 'DAILY_LIMIT'
    | 'NEW_PARTNER_LIMIT';

/** 대화 상대 — 작성자 표시와 같은 모양. **탈퇴한 회원은 `id: null`·`withdrawn: true`·`nickname: "탈퇴한 회원"`**(프로필 링크 없음). `memo` 는 내가 남긴 이용자 메모 */
export interface DmPartner {
    id: string | null;
    nickname: string;
    /** 등급 키(`gradeKey`) */
    grade: string;
    gradeLabel: string;
    role: Role;
    withdrawn: boolean;
    memo: string | null;
    /** 상대가 장착한 장식(닉네임 색·테두리) — 쪽지에서는 닉네임 색만 그린다. 탈퇴·장착 없음은 null, 옛 응답에는 없을 수 있다 */
    decoration?: AuthorDecoration | null;
    /** 상대의 프로필 아바타 그림 주소 — 쪽지에는 지금 그리지 않는다(작성자 모양을 맞추려고 넘긴다) */
    avatarUrl?: string | null;
}

/** 오늘(한국 시간)의 한도 — `resetsInSeconds` 는 다음 한국 시간 0시까지 남은 초. 전송이 실패하면 한도에 들어가지 않는다 */
export interface DmQuota {
    dailyLimit: number;
    sentToday: number;
    remainingToday: number;
    newPartnerLimit: number;
    newPartnersToday: number;
    remainingNewPartners: number;
    resetsInSeconds: number;
}

/** 쪽지 한 통 — 일반 글자(HTML 아님, 줄바꿈 그대로) 500자 이하 */
export interface DmMessage {
    id: string;
    mine: boolean;
    body: string;
    createdAt: string;
}

/** 받는 사람과 상관없이 **나 때문에** 못 보내는 이유 — `availableFrom` 은 `TOO_NEW` 일 때 보낼 수 있게 되는 시각(가입 + 7일) */
export interface DmSenderState {
    canSend: boolean;
    reason: Extract<DmReason, 'EMAIL_NOT_VERIFIED' | 'ACCOUNT_RESTRICTED' | 'BUSINESS_RESTRICTED' | 'NOT_PERMITTED' | 'TOO_NEW' | 'DAILY_LIMIT'> | null;
    availableFrom: string | null;
}

/** `GET /api/me/dm` — 수신 설정·보낼 자격·한도·안 읽은 대화 수(상한 100)·규칙 */
export interface MyDm {
    acceptDm: boolean;
    sender: DmSenderState;
    quota: DmQuota;
    unreadThreads: number;
    rules: { minAccountAgeDays: number; bodyMax: number; retentionDays: number };
}

/** `GET /api/dm/unread-count` — 안 읽은 쪽지가 있는 대화 수(상한 100) */
export interface DmUnreadCount {
    count: number;
}

/** `GET·PUT /api/me/dm/settings` */
export interface DmSettings {
    acceptDm: boolean;
}

/** 쪽지함 한 줄의 마지막 쪽지 — `preview` 는 앞 40자(줄바꿈은 한 칸). `mine` 이면 화면이 "나: " 를 붙인다 */
export interface DmLastMessage {
    id: string;
    mine: boolean;
    preview: string;
    createdAt: string;
}

/** 쪽지함 한 줄 — `unreadCount` 는 상대가 보낸 안 읽은 쪽지 수 */
export interface DmThreadItem {
    id: string;
    partner: DmPartner;
    last: DmLastMessage;
    unreadCount: number;
}

/** `GET /api/dm/threads?size&cursor` — 마지막 쪽지가 최근인 대화가 위 */
export interface DmThreadList {
    items: DmThreadItem[];
    hasNext: boolean;
    nextCursor: string | null;
}

/** 대화방 머리 — `canSend`/`reason` 은 지금 이 대화에 보낼 수 있는지, `contactAllowed` 는 링크·외부 연락처를 보낼 수 있는지(상대가 이 대화에서 쪽지를 보낸 적이 있다) */
export interface DmThreadHeader {
    id: string;
    partner: DmPartner;
    canSend: boolean;
    reason: DmReason | null;
    availableFrom: string | null;
    contactAllowed: boolean;
}

/** `GET /api/dm/threads/{id}?size&cursor` — `items` 는 **최근 쪽지가 앞**(화면이 뒤집어 그린다). `hasNext` 는 더 오래된 쪽 */
export interface DmRoom {
    thread: DmThreadHeader;
    items: DmMessage[];
    hasNext: boolean;
    nextCursor: string | null;
    quota: DmQuota;
}

/** 쪽지함·대화방 쿼리 — `size` 기본 20(쪽지함)·30(대화방), 1~50 */
export interface DmPageQuery {
    size?: number;
    cursor?: string;
}

/** 받는 사람 찾기 한 줄 — `threadId` 가 있으면 이미 대화 중 */
export interface DmRecipient {
    user: DmPartner;
    threadId: string | null;
}

/** `GET /api/dm/recipients?q=` — 최대 10명. 나·탈퇴한 회원·차단 관계의 회원은 나오지 않는다 */
export interface DmRecipientList {
    items: DmRecipient[];
}

/** `GET /api/users/{id}/dm` — 이 회원에게 보낼 수 있나(읽기만 한다) */
export interface DmCanSend {
    canSend: boolean;
    reason: DmReason | null;
    availableFrom: string | null;
    threadId: string | null;
    newPartner: boolean;
    contactAllowed: boolean;
    quota: DmQuota;
}

/** `POST /api/users/{id}/dm` 의 `201` — `created` 는 이 쪽지로 대화가 새로 만들어졌는가(새 상대) */
export interface DmSendResult {
    threadId: string;
    created: boolean;
    message: DmMessage;
    quota: DmQuota;
}
