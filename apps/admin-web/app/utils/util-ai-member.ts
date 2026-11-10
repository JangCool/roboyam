import type { AiActivityWindows, AiLogResult, AiMemberDetail, AiMemberSaveRequest, AiRestingReason } from '~/composables/aimember/api/useAiMemberAdminApi.types';

/** 태그 색(PrimeVue `Tag` severity) */
type Severity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

/**
 * AI 회원 관리 화면의 순수 함수 — 계약 `docs/features/ai-members-contract.md`.
 * 길이·범위는 서버 판정을 미리 알려 주는 값이다(서버 규칙 후보 `AiMemberRules` — 서버 단계에서 이름을 맞춘다). 판정은 서버다.
 */
export default class UtilAiMember {
    static readonly LIST_PATH = '/ai-members';
    static readonly DRAFTS_PATH = '/ai-members/drafts';
    static readonly SETTINGS_PATH = '/ai-members/settings';
    static readonly LOGS_PATH = '/ai-members/logs';

    /** 하루 글 수 선택지(정책 4 — 2~3건) */
    static readonly DAILY_POSTS: readonly number[] = [2, 3];
    static readonly PERSONALITY_MAX = 200;
    static readonly TONE_MAX = 100;
    static readonly INTERESTS_MAX = 300;
    static readonly BIO_MAX = 150;
    static readonly CALL_LIMIT_MIN = 10;
    static readonly CALL_LIMIT_MAX = 5000;
    static readonly LOG_PAGE_SIZE = 20;

    /** 상세 → 저장 본문(편집 칸의 처음 값) */
    static formOf(detail: AiMemberDetail): AiMemberSaveRequest {
        return {
            provider: detail.provider,
            model: detail.model,
            persona: { ...detail.persona },
            bio: detail.bio,
            relation: detail.relation ? { kind: detail.relation.kind, partnerAiMemberId: detail.relation.partner.aiMemberId } : null,
            windows: { morning: { ...detail.windows.morning }, afternoon: { ...detail.windows.afternoon } },
            dailyPosts: detail.dailyPosts,
            boards: [...detail.boards],
            groups: [...detail.groups],
        };
    }

    /** 바뀐 칸 수 — 저장 줄의 「N개 칸을 바꿨어요」 */
    static changes(a: AiMemberSaveRequest, b: AiMemberSaveRequest): number {
        const same = (x: unknown, y: unknown) => JSON.stringify(x) === JSON.stringify(y);
        const sorted = (list: string[]) => [...list].sort();
        let n = 0;
        if (a.provider !== b.provider) n++;
        if (a.model !== b.model) n++;
        for (const key of ['gender', 'ageBand', 'personality', 'tone', 'interests'] as const) if (a.persona[key] !== b.persona[key]) n++;
        if (a.bio !== b.bio) n++;
        if (!same(a.relation, b.relation)) n++;
        if (!same(a.windows.morning, b.windows.morning)) n++;
        if (!same(a.windows.afternoon, b.windows.afternoon)) n++;
        if (a.dailyPosts !== b.dailyPosts) n++;
        if (!same(sorted(a.boards), sorted(b.boards))) n++;
        if (!same(sorted(a.groups), sorted(b.groups))) n++;
        return n;
    }

    /** 활동 창이 맞는가 — 오전은 00:00~12:00, 오후는 12:00~23:59, 시작이 끝보다 앞(서버도 같은 규칙으로 거절한다) */
    static windowProblems(windows: AiActivityWindows): { morning: boolean; afternoon: boolean } {
        const ok = (v: string) => /^([01]\d|2[0-3]):[0-5]\d$/.test(v);
        const m = windows.morning;
        const a = windows.afternoon;
        return {
            morning: !ok(m.start) || !ok(m.end) || m.start >= m.end || m.end > '12:00',
            afternoon: !ok(a.start) || !ok(a.end) || a.start >= a.end || a.start < '12:00',
        };
    }

    static restingSeverity(reason: AiRestingReason | null): Severity {
        if (!reason) return 'success';
        return reason === 'DISABLED' ? 'secondary' : 'warn';
    }

    static resultSeverity(result: AiLogResult): Severity {
        switch (result) {
            case 'PUBLISHED':
                return 'success';
            case 'PENDING':
                return 'info';
            case 'DISCARDED':
                return 'secondary';
            default:
                return 'danger';
        }
    }
}
