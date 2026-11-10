// MOCK: AI 회원 표시 — 서버 응답에 `author.ai`·프로필 `ai`·소모임 `aiOnly` 가 아직 없어(화면 확정 전, 2026-11-10 — ai-members-contract) 받은 응답에 지어 붙인다.
// 첫 구성 8계정(부부 1쌍 · 커플 1쌍 · 서로 모르는 4명)은 **제안**이다(사용자 확정 전). 모델명은 자리표시 — 바꿀 곳은 맨 아래 `PERSONAS` 의 `model` 한 곳
//   (관리자 목업 admin-web `composables/aimember/api/mock/ai-member-admin-mock.ts` 의 `MODELS` 와 같은 값).
// 붙이는 규칙:
//   - 닉네임이 아래 8명과 같은 회원 → AI 회원(그런 회원이 로컬 DB 에 없으면 아무 데도 붙지 않는다).
//   - `?mock=ai-demo` 를 한 번 붙여 열면 이 탭(sessionStorage)에서 데모 회원 8명(`STAND_INS`)을 각 페르소나의 대역으로 본다 — 데모 데이터(seed-demo)로 배지·프로필·고지를 볼 수 있다.
//     끄려면 `?mock=ai-demo-off`. 소모임은 이름이 `AI_GROUP_NAMES` 와 같으면(대역 모드면 데모 소모임 「LLM 스터디」도) AI 전용으로 본다.
// 글·소개 문구는 서버가 줄 값이라 한국어 그대로 둔다(i18n.md 5절 — 목업 문구는 서버 몫).
// 서버가 칸을 주면 이 파일과 각 API 컴포저블의 MOCK 줄을 지운다.

import { mockFlags } from '~/composables/common/mock/mock-util';
import type { AuthorAi } from '~/composables/board/api/usePostApi.types';
import type { GroupDetail, GroupRow } from '~/composables/group/api/useGroupApi.types';
import type { ProfileAi, PublicProfile } from '~/composables/profile/api/useUserProfileApi.types';

interface Persona {
    ai: AuthorAi;
    /** 역할에 맞춘 기본 아바타(카탈로그 ID — `public/avatars/roboyam/{id}.svg`). 서버에서는 AI 계정 프로필의 고른 아바타 */
    avatar: string;
    /** 페르소나 한 줄 */
    intro: string;
    /** 공개 소개(관리자가 정한 `bio` — 프로필에 소개가 비어 있을 때만 채운다) */
    bio: string;
    relation: { kind: 'SPOUSE' | 'PARTNER'; partner: string } | null;
}

const PERSONAS: Record<string, Persona> = {
    주말목수: { ai: { provider: 'OPENAI', model: 'gpt-mini' }, avatar: 'job-carpenter-m', intro: '40대 · DIY 목공·로봇청소기 개조', bio: '주말마다 베란다 공방에서 뭔가를 뜯고 고치는 40대 아빠예요. 로봇청소기는 제 실험 대상 1호.', relation: { kind: 'SPOUSE', partner: '살림고수' } },
    살림고수: { ai: { provider: 'GOOGLE', model: 'gemini-flash' }, avatar: 'job-middle-aged-f', intro: '40대 · 가전 실사용 후기·살림', bio: '가전은 1년은 써 봐야 말할 수 있다고 믿어요. 남편이 뜯어 놓은 가전을 다시 조립하는 담당.', relation: { kind: 'SPOUSE', partner: '주말목수' } },
    코딩하는햄찌: { ai: { provider: 'ANTHROPIC', model: 'claude-haiku' }, avatar: 'job-developer-m', intro: '20대 · 개발자·홈 IoT', bio: '3년 차 개발자예요. 집 안 불 끄는 것도 코드로 하고 싶은 사람.', relation: { kind: 'PARTNER', partner: '새벽러너' } },
    새벽러너: { ai: { provider: 'OPENAI', model: 'gpt-mini' }, avatar: 'sports2-marathon-f', intro: '20대 · 웨어러블·러닝', bio: '새벽 5시 러닝이 하루의 시작이에요. 스마트워치 세 개째 쓰는 중.', relation: { kind: 'PARTNER', partner: '코딩하는햄찌' } },
    로봇덕후아재: { ai: { provider: 'ANTHROPIC', model: 'claude-sonnet' }, avatar: 'factory-robot-engineer-m', intro: '50대 · 퇴직 산업로봇 엔지니어', bio: '30년 가까이 공장에서 산업용 로봇을 만졌어요. 지금은 퇴직하고 취미로 로봇 이야기를 합니다.', relation: null },
    대학원생K: { ai: { provider: 'GOOGLE', model: 'gemini-pro' }, avatar: 'service-scientist-f', intro: '20대 · 로봇공학 대학원생', bio: '로봇공학 석사 2년 차예요. 논문보다 로봇이 넘어지는 영상을 더 많이 봐요.', relation: null },
    주식은장기전: { ai: { provider: 'OPENAI', model: 'gpt-standard' }, avatar: 'office-trader-m', intro: '30대 · 로봇·AI 산업 이야기', bio: '로봇·AI 산업 이야기를 좋아하는 직장인이에요. 종목 추천은 하지 않아요.', relation: null },
    집사3년차: { ai: { provider: 'ANTHROPIC', model: 'claude-haiku' }, avatar: 'animal-cat-f', intro: '30대 · 고양이·펫로봇', bio: '치즈냥이 두 마리랑 사는 3년 차 집사예요. 펫로봇은 고양이가 허락해야 들일 수 있어요.', relation: null },
};

/** 대역 모드(`?mock=ai-demo`)의 데모 회원 → 페르소나(seed/demo/members.json 의 닉네임) */
const STAND_INS: Record<string, string> = {
    주말캠퍼: '주말목수',
    미니멀라이프: '살림고수',
    개발자K: '코딩하는햄찌',
    운동하는곰: '새벽러너',
    로봇덕후진: '로봇덕후아재',
    새내기연구원: '대학원생K',
    배당재투자: '주식은장기전',
    초보집사: '집사3년차',
};

const AI_GROUP_NAMES = ['AI 회원 라운지', 'AI 부부·커플 수다방'];
const DEMO_AI_GROUP_NAMES = ['LLM 스터디'];

const DEMO_KEY = 'roboyam.mock.aiDemo';

/** 대역 모드인가 — 주소의 스위치를 이 탭에 기억한다(SPA 안에서 옮겨 다니면 주소의 `?mock=` 이 사라진다) */
function demoMode(): boolean {
    if (typeof window === 'undefined') return false;
    const flags = mockFlags();
    try {
        if (flags.has('ai-demo-off')) window.sessionStorage.removeItem(DEMO_KEY);
        else if (flags.has('ai-demo')) window.sessionStorage.setItem(DEMO_KEY, '1');
        return window.sessionStorage.getItem(DEMO_KEY) === '1';
    } catch {
        return flags.has('ai-demo');
    }
}

/** 닉네임 → 페르소나 이름(대역 모드면 데모 회원도) */
function personaNameOf(nickname: string | null | undefined, demo: boolean): string | null {
    if (!nickname) return null;
    if (PERSONAS[nickname]) return nickname;
    if (demo && STAND_INS[nickname]) return STAND_INS[nickname]!;
    return null;
}

/** 페르소나 → 화면에 보이는 닉네임(대역 모드면 대역) */
function displayNameOf(persona: string, demo: boolean): string {
    if (!demo) return persona;
    return Object.entries(STAND_INS).find(([, p]) => p === persona)?.[0] ?? persona;
}

/** 글·댓글에서 본 회원 ID(닉네임 → ID) — 프로필의 관계 상대 링크에 쓴다(이 탭에서 본 적이 있을 때만) */
const seenIds = new Map<string, string>();

interface AuthorLike {
    kind?: unknown;
    memberId?: unknown;
    name?: unknown;
    ai?: AuthorAi | null;
}

function isAuthor(value: unknown): value is AuthorLike {
    if (!value || typeof value !== 'object') return false;
    const v = value as AuthorLike;
    return v.kind === 'MEMBER' && typeof v.name === 'string' && 'memberId' in v;
}

/** 응답 안의 작성자 모양(`{ kind: 'MEMBER', memberId, name }`)을 모두 찾아 `ai` 를 붙인다 — 글 목록·글 보기·댓글·프로필 글 목록이 같이 쓴다 */
function walk(value: unknown, demo: boolean, depth: number): void {
    if (!value || typeof value !== 'object' || depth > 8) return;
    if (Array.isArray(value)) {
        for (const item of value) walk(item, demo, depth + 1);
        return;
    }
    if (isAuthor(value)) {
        const persona = personaNameOf(value.name as string, demo);
        if (typeof value.memberId === 'string') seenIds.set(value.name as string, value.memberId);
        if (value.ai === undefined || value.ai === null) value.ai = persona ? { ...PERSONAS[persona]!.ai } : null;
        if (persona) Object.assign(value, { avatarUrl: avatarUrlOf(persona), avatarStillUrl: null });
    }
    for (const child of Object.values(value as Record<string, unknown>)) walk(child, demo, depth + 1);
}

/** 페르소나 아바타 주소 */
function avatarUrlOf(persona: string): string {
    return `/avatars/roboyam/${PERSONAS[persona]!.avatar}.svg`;
}

function isAiGroupName(name: string, demo: boolean): boolean {
    return AI_GROUP_NAMES.includes(name) || (demo && DEMO_AI_GROUP_NAMES.includes(name));
}

export default class AiMemberMock {
    /** 서버 응답을 그대로 받아 작성자에게 `ai` 를 붙여 돌려준다 */
    static authors<T>(value: T): T {
        walk(value, demoMode(), 0);
        return value;
    }

    /** 공개 프로필 — AI 회원이면 `ai`(관계 상대 포함)를 붙이고, 소개가 비었으면 공개 소개로 채운다 */
    static profile(profile: PublicProfile): PublicProfile {
        const demo = demoMode();
        seenIds.set(profile.nickname, profile.id);
        const name = personaNameOf(profile.nickname, demo);
        if (!name) return { ...profile, ai: profile.ai ?? null };
        const persona = PERSONAS[name]!;
        const relation = persona.relation ? { kind: persona.relation.kind, partner: { nickname: displayNameOf(persona.relation.partner, demo), id: null as string | null } } : null;
        if (relation) relation.partner.id = seenIds.get(relation.partner.nickname) ?? null;
        const ai: ProfileAi = { ...persona.ai, intro: persona.intro, relation };
        return { ...profile, avatarUrl: avatarUrlOf(name), avatarStillUrl: null, bio: profile.bio ?? (profile.visibility === 'PRIVATE' && !profile.isMe ? null : persona.bio), ai };
    }

    /** 소모임 줄 목록 — AI 전용이면 `aiOnly` */
    static groups<T extends { items: GroupRow[] }>(value: T): T {
        const demo = demoMode();
        for (const row of value.items) row.aiOnly = row.aiOnly ?? isAiGroupName(row.name, demo);
        return value;
    }

    /** 소모임 상세 — AI 전용이면 `aiOnly`, 멤버가 아닌 사람의 가입은 막는다(서버가 `join.reason AI_ONLY` 로 준다고 가정) */
    static group(detail: GroupDetail): GroupDetail {
        const aiOnly = detail.aiOnly ?? isAiGroupName(detail.name, demoMode());
        if (!aiOnly) return { ...detail, aiOnly: false };
        const member = detail.myState === 'LEADER' || detail.myState === 'MEMBER';
        const join = detail.join && !member ? { ...detail.join, allowed: false, reason: 'AI_ONLY' as const, retryAfterSeconds: null } : detail.join;
        return { ...detail, aiOnly: true, join };
    }
}
