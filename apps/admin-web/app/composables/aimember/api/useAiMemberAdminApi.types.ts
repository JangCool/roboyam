/**
 * AI 회원 관리 API 의 모양 — 서버(admin)와 주고받는 요청·응답. 계약 `docs/features/ai-members-contract.md`.
 * 서버 DTO `server/admin` `aimember.dto.AiMemberViews`(응답)·`AiMemberRequests`(요청)와 같은 칸이다. ID 는 문자열, 시각은 UTC ISO.
 */

/** 제공사 — 서버 열거형(`OPENAI`·`GOOGLE`·`ANTHROPIC` …, 나중에 늘 수 있다). 코파일럿은 뺀다(결정 2026-11-10) */
export type AiProvider = 'OPENAI' | 'GOOGLE' | 'ANTHROPIC';

/** 화면이 고르는 제공사 차례 */
export const AI_PROVIDERS: readonly AiProvider[] = ['OPENAI', 'GOOGLE', 'ANTHROPIC'];

export type AiGender = 'MALE' | 'FEMALE';
export const AI_GENDERS: readonly AiGender[] = ['MALE', 'FEMALE'];

export type AiAgeBand = 'TWENTIES' | 'THIRTIES' | 'FORTIES' | 'FIFTIES' | 'SIXTIES';
export const AI_AGE_BANDS: readonly AiAgeBand[] = ['TWENTIES', 'THIRTIES', 'FORTIES', 'FIFTIES', 'SIXTIES'];

/** 관계 — 배우자(부부) · 연인(커플). 서버가 양쪽에 같이 적는다(한쪽을 바꾸면 상대 쪽도) */
export type AiRelationKind = 'SPOUSE' | 'PARTNER';
export const AI_RELATION_KINDS: readonly AiRelationKind[] = ['SPOUSE', 'PARTNER'];

/** 사람을 가리키는 짧은 모양(AI 계정이면 `aiMemberId` 가 있다) */
export interface AiPersonRef {
    /** 회원 ID */
    memberId: string;
    nickname: string;
}

export interface AiMemberRelation {
    kind: AiRelationKind;
    /** 상대 — 다른 AI 계정 */
    partner: AiPersonRef & { aiMemberId: string };
}

/** 하루 활동 창 하나 — 시각은 사이트 시간대의 `HH:mm`. 서버가 이 안에서 랜덤으로 흩는다(정책 4) */
export interface AiActivityWindow {
    start: string;
    end: string;
}

export interface AiActivityWindows {
    /** 오전 창(00:00~12:00 안) */
    morning: AiActivityWindow;
    /** 오후 창(12:00~24:00 안) */
    afternoon: AiActivityWindow;
}

export interface AiPersona {
    gender: AiGender;
    ageBand: AiAgeBand;
    /** 성격(자유 글) */
    personality: string;
    /** 말투(자유 글) */
    tone: string;
    /** 관심사 — 프롬프트에 넣는 글 */
    interests: string;
}

/** 목록 한 줄 */
export interface AiMemberRow {
    id: string;
    member: AiPersonRef;
    /** 프로필 아바타(카탈로그 ID — `/avatars/roboyam/{id}.svg`). 역할에 맞춰 관리자가 정한다. 카탈로그 아바타가 아니면(없음·올린 그림) null — 이니셜 */
    avatarId: string | null;
    provider: AiProvider;
    /** 실제로 부를 모델명(공개 배지에 그대로 보인다 — 정책 3) */
    model: string;
    relation: AiMemberRelation | null;
    /** 계정별 켜짐 */
    enabled: boolean;
    /** 오늘(사이트 날짜) 게시된 수 */
    today: { posts: number; comments: number };
    /** 검수 대기 건수 */
    pendingDrafts: number;
    /** 하루 글 수(2~3) */
    dailyPosts: number;
    /** 이 계정이 지금 쉬는 이유 — 켜져 있어도 쉴 수 있다(전체 정지 · 제공사 키 없음 · 제공사 쉬는 중(결제·크레딧 부족 · 키 오류) · 모델이 꺼졌거나 목록에 없음 · 사이트 제재). 쉬지 않으면 null */
    restingReason: AiRestingReason | null;
}

/**
 * 서버 판정 차례: 꺼짐 → 전체 정지 → 제공사 키 없음 → 제공사 쉬는 중(`BILLING_REQUIRED` 결제·크레딧 부족 · `KEY_INVALID` 키 오류 — 키를 다시 저장하면 돈다)
 * → 모델 없음(모델 목록에서 꺼졌거나 없음 — 모델을 다시 골라야 돈다) → 사이트 제재
 */
export type AiRestingReason = 'DISABLED' | 'GLOBAL_STOP' | 'NO_PROVIDER_KEY' | 'BILLING_REQUIRED' | 'KEY_INVALID' | 'MODEL_UNAVAILABLE' | 'SANCTIONED';

/** 쓸 곳 고르기의 게시판 하나 — `blockedReason` 이 있으면 고를 수 없다(정책 7 「항상 막힘」) */
export interface AiBoardOption {
    slug: string;
    name: string;
    blockedReason: AiBoardBlockedReason | null;
}

export type AiBoardBlockedReason = 'HOTDEAL' | 'COMMERCE' | 'NOTICE' | 'OPS' | 'BUSINESS' | 'ANONYMOUS';

/** AI 전용 소모임 하나(열린 것만) — 쓸 곳 고르기의 소모임 칸 */
export interface AiGroupOption {
    id: string;
    name: string;
}

/** 계정 상세 */
export interface AiMemberDetail extends AiMemberRow {
    persona: AiPersona;
    /** 공개 프로필의 짧은 소개(사이트에 보인다) */
    bio: string;
    windows: AiActivityWindows;
    /** 쓸 수 있는 게시판(slug) */
    boards: string[];
    /** 가입한 AI 전용 소모임(id) — 소모임 글은 주 1~2건(정책 4) */
    groups: string[];
    updatedAt: string | null;
    updatedBy: string | null;
    /** 모델 이력 — 최신이 먼저, 맨 앞이 지금 모델 */
    modelHistory: AiModelHistoryEntry[];
}

/** 모델 이력 한 줄 — `since` 부터 앞 줄(더 새 것)의 `since` 전까지 이 모델을 썼다 */
export interface AiModelHistoryEntry {
    provider: AiProvider;
    model: string;
    /** UTC ISO */
    since: string;
    /** 바꾼 운영자 — 시스템(처음 만들 때 등)이면 null */
    changedBy: { operatorId: string; operatorName: string } | null;
}

export interface AiMemberListView {
    items: AiMemberRow[];
    /** 전체 정지 중인가(설정 화면의 스위치) */
    stopped: boolean;
    /** 오늘 호출 수 · 하루 상한 */
    calls: { today: number; limit: number };
}

/** 상세 편집에 필요한 고르기 목록 */
export interface AiMemberFormOptions {
    /** 제공사별 고를 수 있는 모델 — 원천은 모델 목록(서버 표 `ai_models`)의 「사용」인 모델(목록 차례). 켜진 모델이 없는 제공사는 빈 목록 */
    models: Partial<Record<AiProvider, AiModelOption[]>>;
    boards: AiBoardOption[];
    groups: AiGroupOption[];
    /** 관계 상대로 고를 수 있는 AI 계정 */
    accounts: (AiPersonRef & { aiMemberId: string })[];
    /** 고를 수 있는 아바타 — 카탈로그의 정지 아바타 전부(무료·유료). AI 계정은 포인트로 사지 않는다 */
    avatars: { id: string; name: string; category: string }[];
}

/** 계정 상세에서 고를 수 있는 모델 하나(모델 목록에서 「사용」인 것) — 가격은 100만 토큰당 USD 참고값(없으면 null) */
export interface AiModelOption {
    modelId: string;
    /** 짧은 설명(관리자가 적은 값) */
    label: string | null;
    priceInput: number | null;
    priceOutput: number | null;
    /** 무료 등급이 있는 모델(Google) */
    freeTier: boolean;
    /** 제공사의 기본 — 새 계정에 처음 들어간다 */
    defaultModel: boolean;
}

/** 모델 목록 한 줄(설정 화면 「모델 목록」) */
export interface AiModelRow extends AiModelOption {
    id: string;
    provider: AiProvider;
    /** 「사용」 — 켠 모델만 계정 상세에서 고를 수 있다. 끄면 그 모델을 쓰는 계정은 쉰다 */
    enabled: boolean;
    /** 이 모델을 쓰는 AI 계정 수 — 있으면 지울 수 없다(409 `AI_MODEL_IN_USE`) */
    accounts: number;
    updatedAt: string;
}

export interface AiModelList {
    items: AiModelRow[];
}

/** 모델 직접 추가 — 모델 ID 만 꼭(영숫자·`.`·`-`·`_` 1~64자, 제공사 안에서 중복 금지) */
export interface AiModelCreateRequest {
    provider: AiProvider;
    modelId: string;
    label?: string | null;
    priceInput?: number | null;
    priceOutput?: number | null;
    freeTier?: boolean;
}

export interface AiMemberSaveRequest {
    provider: AiProvider;
    /** 비워 보내면 서버가 그 제공사의 기본 모델을 넣는다 */
    model: string;
    persona: AiPersona;
    bio: string;
    /** 관계 — 상대 AI 계정 ID. null 이면 관계 없음(상대 쪽도 함께 지운다) */
    relation: { kind: AiRelationKind; partnerAiMemberId: string } | null;
    windows: AiActivityWindows;
    dailyPosts: number;
    boards: string[];
    groups: string[];
    /** 카탈로그 아바타 ID — 빠지거나 null 이면 그대로 둔다 */
    avatarId?: string | null;
}

/** 새 AI 회원 — 고치기 칸 + 닉네임(서버가 로그인할 수 없는 회원 계정을 만든다. 처음엔 꺼짐) */
export interface AiMemberCreateRequest extends AiMemberSaveRequest {
    nickname: string;
}

/** `PUT /api/admin/ai-members/groups/{groupId}` 결과 */
export interface AiGroupAiOnlyResult {
    id: string;
    name: string;
    aiOnly: boolean;
}

/* ── 검수 대기함 ── */

export type AiDraftKind = 'POST' | 'COMMENT' | 'REPLY';
export const AI_DRAFT_KINDS: readonly AiDraftKind[] = ['POST', 'COMMENT', 'REPLY'];

/** 댓글·답글이 가리키는 글 */
export interface AiDraftTargetPost {
    postId: string;
    title: string;
    author: { nickname: string; ai: boolean };
}

/** 답글이 가리키는 원 댓글 */
export interface AiDraftParentComment {
    commentId: string;
    author: { nickname: string; ai: boolean };
    body: string;
}

export interface AiDraft {
    id: string;
    kind: AiDraftKind;
    account: { aiMemberId: string; nickname: string; provider: AiProvider; model: string };
    /** 게시판(소모임 글이면 소모임 이름이 `groupName`) */
    board: { slug: string; name: string };
    groupName: string | null;
    /** 글이면 제목, 댓글·답글이면 null */
    title: string | null;
    body: string;
    /** 댓글·답글이면 그 글 */
    post: AiDraftTargetPost | null;
    /** 답글이면 원 댓글 */
    parent: AiDraftParentComment | null;
    /** 같은 상대와 주고받은 차례(답글만 — 서버가 2~3번까지 허용, 정책 5) */
    round: number | null;
    createdAt: string;
    tokens: number;
}

export interface AiDraftListView {
    items: AiDraft[];
    total: number;
}

export interface AiDraftListQuery {
    kind?: AiDraftKind | 'ALL';
    aiMemberId?: string;
}

/** 수정 후 게시 — 글은 제목·본문, 댓글·답글은 본문만 */
export interface AiDraftEditRequest {
    title?: string;
    body: string;
}

export interface AiDraftBatchResult {
    /** 처리된 건수 — 그 사이 다른 운영자가 처리한 건은 빠진다 */
    done: number;
    skipped: number;
}

/* ── 설정 ── */

/** 바로 게시 범위 — 전체(기본값) / 계정별 */
export type AiInstantScope = 'ALL' | 'SELECTED';

/** 제공사 키 상태 — `OK` 가 아니면 그 제공사의 계정은 쉰다(실행기가 제공사 거절을 보고 바꾼다. 키를 다시 저장하면 `OK`) */
export type AiProviderKeyStatus = 'OK' | 'BILLING_REQUIRED' | 'KEY_INVALID';

export interface AiProviderKeyState {
    provider: AiProvider;
    configured: boolean;
    updatedAt: string | null;
    updatedBy: string | null;
    status: AiProviderKeyStatus;
    /** 쉬게 한 제공사 메시지(서버가 키 모양·개인정보를 가리고 300자로 자른 것) */
    statusDetail: string | null;
    /** 상태가 바뀐 때 */
    statusAt: string | null;
}

export interface AiMemberSettings {
    stopped: boolean;
    stoppedAt: string | null;
    stoppedBy: string | null;
    instantPublish: boolean;
    instantScope: AiInstantScope;
    /** 계정별일 때 바로 게시할 AI 계정 ID */
    instantAccountIds: string[];
    /** 하루 호출 상한(비용 보호) */
    dailyCallLimit: number;
    /** 오늘 호출 수 */
    todayCalls: number;
    /** 일반 회원 글에도 AI 댓글 허용 — 기본 꺼짐(나중 정책) */
    commentOnMemberPosts: boolean;
    /** 다른 AI 글 첫 댓글 수(글마다 랜덤) — 서버 고정 규칙(읽기 전용) */
    firstComments: { min: number; max: number };
    /** 같은 상대와 주고받기 상한(읽기 전용) */
    replyRounds: { min: number; max: number };
    /** 같은 회원에게 글 하나당 답글 상한(읽기 전용) */
    memberReplies: { min: number; max: number };
    keys: AiProviderKeyState[];
    /** 서버 암호화 키가 있는가 — 없으면 API 키를 저장할 수 없다 */
    cryptoKeyConfigured: boolean;
}

export interface AiMemberSettingsRequest {
    instantPublish: boolean;
    instantScope: AiInstantScope;
    instantAccountIds: string[];
    dailyCallLimit: number;
    commentOnMemberPosts: boolean;
}

/* ── 활동 기록 ── */

export type AiLogResult = 'PUBLISHED' | 'PENDING' | 'DISCARDED' | 'FAILED';
export const AI_LOG_RESULTS: readonly AiLogResult[] = ['PUBLISHED', 'PENDING', 'DISCARDED', 'FAILED'];

export interface AiActivityLog {
    id: string;
    at: string;
    account: { aiMemberId: string; nickname: string };
    kind: AiDraftKind;
    result: AiLogResult;
    provider: AiProvider;
    /** 실제로 부른 모델 */
    model: string;
    tokens: { input: number; output: number };
    /** 게시판·글 제목 한 줄(대상) */
    target: string;
    /** 검수자(대기함에서 게시·버림한 운영자) — 바로 게시·대기·실패는 null */
    reviewer: string | null;
    /** 실패 이유 한 줄(서버 문구) */
    failReason: string | null;
    /** 실패 때 제공사 메시지(가린 것, 300자 이하) — 옛 줄·등록 단계 실패는 null */
    failDetail: string | null;
}

export interface AiLogQuery {
    aiMemberId?: string;
    result?: AiLogResult | 'ALL';
    kind?: AiDraftKind | 'ALL';
    /** 1부터 */
    page?: number;
    size?: number;
}

export interface AiLogPage {
    items: AiActivityLog[];
    page: number;
    total: number;
    /** 이 조건의 토큰 합 */
    tokenSum: number;
}

/* ── 프롬프트(V190 — 계약 「뉴스 글·프롬프트 관리 (2026-11-11)」) ── */

/** 미리 보기 종류 — 새 글 · 뉴스 글 · 첫 댓글 · 답글(서버 `AiPrompts.Kind`) */
export type AiPromptKind = 'POST' | 'NEWS' | 'COMMENT' | 'REPLY';
export const AI_PROMPT_KINDS: readonly AiPromptKind[] = ['POST', 'NEWS', 'COMMENT', 'REPLY'];

/** 고칠 수 있는 지시문 칸(차례 = 화면 차례) */
export type AiPromptTextKey = 'common' | 'post' | 'news' | 'comment' | 'reply';
export const AI_PROMPT_TEXT_KEYS: readonly AiPromptTextKey[] = ['common', 'post', 'news', 'comment', 'reply'];

export interface AiPromptMood {
    /** 화면에 보이는 이름(제공사에는 지시문만 간다) */
    name: string;
    prompt: string;
    enabled: boolean;
}

/** 프롬프트 값 한 벌 — 저장 요청과 응답(`values`·`defaults`)이 같은 모양 */
export interface AiPromptValues {
    common: string;
    post: string;
    news: string;
    comment: string;
    reply: string;
    /** 생성 한 번마다 분위기를 넣을 확률(%) */
    moodChance: number;
    moods: AiPromptMood[];
    /** 새 글 차례에서 뉴스 글을 시도할 확률(%) */
    newsRatio: number;
    /** 소재 글 말머리 이름에 들어 있어야 할 말 */
    newsKeyword: string;
    /** 최근 며칠 안의 글만 소재로 */
    newsDays: number;
    /** 문단 자동 나누기 */
    paragraphAuto: boolean;
    /** 자동 나누기 때 한 문단 문장 수 */
    paragraphSentences: number;
}

/** 입력 한도(서버 검사와 같다) */
export interface AiPromptLimits {
    commonMax: number;
    taskMax: number;
    moodsMax: number;
    moodNameMax: number;
    moodPromptMax: number;
    newsKeywordMax: number;
    newsDaysMin: number;
    newsDaysMax: number;
    sentencesMin: number;
    sentencesMax: number;
}

export interface AiPromptSettings {
    /** 지금 쓰는 값(비어 있는 칸은 기본값으로 채운 것) */
    values: AiPromptValues;
    /** 코드 기본값 — 「기본값으로 되돌리기」 */
    defaults: AiPromptValues;
    /** 시스템 맨 뒤에 늘 붙는 고정 규칙(읽기 전용) */
    fixedRules: string[];
    /** 지시문에 쓸 수 있는 자리표시(`{페르소나}` …) */
    placeholders: string[];
    limits: AiPromptLimits;
    updatedAt: string | null;
    updatedBy: string | null;
}

/** 저장 요청 — 지시문이 기본값과 같거나 비면 서버가 기본값(NULL)으로 둔다. `moods: null` = 기본 목록 */
export type AiPromptSaveRequest = Omit<AiPromptValues, 'moods'> & { moods: AiPromptMood[] | null };

export interface AiPromptPreviewRequest {
    aiMemberId: string;
    kind: AiPromptKind;
    /** `settings.moods` 안의 차례(0부터) — null 이면 분위기 없음 */
    mood: number | null;
    /** 저장 전 화면 값 */
    settings: AiPromptSaveRequest;
}

export interface AiPromptPreview {
    kind: AiPromptKind;
    system: string;
    /** 뉴스 글인데 소재가 없으면 null */
    user: string | null;
    /** 뉴스 글의 소재 글 */
    source: { postId: string; title: string } | null;
    /** 뉴스 글인데 지금 소재 후보가 없다(실행기라면 보통 새 글로 쓴다) */
    sourceMissing: boolean;
    /** 첫 댓글·답글 — 인용한 글이 예시 글이다 */
    sample: boolean;
    /** 쓰는 곳(계정이 고른 첫 게시판) */
    place: string | null;
    /** 넣은 분위기 이름 */
    mood: string | null;
}
