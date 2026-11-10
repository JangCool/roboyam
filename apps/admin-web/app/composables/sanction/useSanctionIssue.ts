import type { ApiError } from '~/composables/common/useApiClient';
import type { SanctionCreateRequest, SanctionLine, SanctionPreview, SanctionTarget } from '~/composables/sanction/api/useSanctionApi.types';
import type { SanctionIssueMember, SanctionIssueView } from '~/composables/sanction/useSanctionIssue.types';
import UtilSanction from '~/utils/util-sanction';

/** 다시 받아야 하는 오류 — 그 사이 회원 상태가 바뀌었다(현황을 새로 받고 대화창 안에 서버 문구) */
const STALE = new Set(['SANCTION_STEP_TOO_LOW', 'SANCTION_ALREADY_SUSPENDED', 'SANCTION_ALREADY_BANNED']);
/** 칸에 붙는 서버 오류 */
const FIELD_KEYS = ['kind', 'reason', 'skipStep', 'confirmNickname'];

/**
 * [제재하기] 대화창(`SanctionIssueDialog`)의 흐름 — **회원 찾기(닉네임 2자 이상) → 현황(`preview`) → 단계·사유 → 부여**. 제재 화면·회원 관리(회원 상세)가 부른다.
 * 서버 호출은 `useSanctionApi` 한 겹, 대화창은 `view` 로 그리고 이벤트로 알린다. 화면마다 새 상태다.
 *  - `start(member?)`: 열면서 칸을 비운다. 회원이 정해져 있으면 찾기 없이 그 회원의 현황을 받는다.
 *  - `submit(body)`: 성공하면 만든 제재(`SanctionLine`)를 돌려주고 닫는다. `SANCTION_STEP_TOO_LOW`·`SANCTION_ALREADY_SUSPENDED`·`SANCTION_ALREADY_BANNED` 는 현황을 새로 받고
 *    대화창 안에 서버 문구, `400 fields.*` 는 칸 아래, 그 밖은 대화창 안 한 줄(쓴 사유는 그대로).
 * ⚠ 컴포넌트 setup 에서 부른다.
 */
export function useSanctionIssue() {
    const api = useSanctionApi();
    const errorOf = useApiErrorOf();

    const open = ref(false);
    /** 회원이 처음부터 정해져 있었다(찾기를 보이지 않는다) */
    const fixed = ref(false);
    const member = ref<SanctionIssueMember | null>(null);
    const targets = ref<SanctionTarget[] | null>(null);
    const targetsLoading = ref(false);
    const targetsError = ref('');
    const preview = ref<SanctionPreview | null>(null);
    const previewLoading = ref(false);
    const previewError = ref<ApiError | null>(null);
    const busy = ref(false);
    const error = ref('');
    const fieldErrors = ref<Record<string, string>>({});
    /** 근거 신고 사건(연결만) */
    const reportCaseId = ref<string | null>(null);
    let targetTicket = 0;
    let previewTicket = 0;

    function reset(): void {
        targetTicket++;
        previewTicket++;
        targets.value = null;
        targetsLoading.value = false;
        targetsError.value = '';
        preview.value = null;
        previewLoading.value = false;
        previewError.value = null;
        error.value = '';
        fieldErrors.value = {};
        busy.value = false;
    }

    function start(target: SanctionIssueMember | null = null, options: { reportCaseId?: string | null } = {}): void {
        reset();
        fixed.value = target !== null;
        member.value = target;
        reportCaseId.value = options.reportCaseId ?? null;
        open.value = true;
        if (target) void loadPreview();
    }

    async function search(q: string): Promise<void> {
        const mine = ++targetTicket;
        targetsLoading.value = true;
        targetsError.value = '';
        try {
            const found = await api.targets(q);
            if (mine === targetTicket) targets.value = found;
        } catch (e) {
            if (mine !== targetTicket) return;
            const failure = errorOf(e);
            targets.value = null;
            targetsError.value = failure.field('q') ?? failure.message;
        } finally {
            if (mine === targetTicket) targetsLoading.value = false;
        }
    }

    function pick(target: SanctionTarget): void {
        member.value = { id: target.id, nickname: target.nickname };
        error.value = '';
        fieldErrors.value = {};
        void loadPreview();
    }

    /** 고른 회원을 지우고 다시 찾는다(정해진 회원은 바꾸지 않는다) */
    function unpick(): void {
        if (fixed.value) return;
        previewTicket++;
        member.value = null;
        preview.value = null;
        previewError.value = null;
        previewLoading.value = false;
        error.value = '';
        fieldErrors.value = {};
    }

    async function loadPreview(): Promise<void> {
        const target = member.value;
        if (!target) return;
        const mine = ++previewTicket;
        previewLoading.value = true;
        previewError.value = null;
        try {
            const found = target.anonymous ? await api.previewAnonymous(target.anonymous) : await api.preview(target.id);
            if (mine === previewTicket) preview.value = found;
        } catch (e) {
            if (mine !== previewTicket) return;
            preview.value = null;
            previewError.value = errorOf(e);
        } finally {
            if (mine === previewTicket) previewLoading.value = false;
        }
    }

    async function submit(body: Omit<SanctionCreateRequest, 'userId' | 'reportCaseId'>): Promise<SanctionLine | null> {
        const target = member.value;
        if (!target || busy.value) return null;
        busy.value = true;
        error.value = '';
        fieldErrors.value = {};
        try {
            // 봉인된 익명 작성자 — 회원 ID 대신 그 글·댓글을 보낸다(서버가 작성자를 찾는다, anonymous-board-contract 「API 계약」 제재)
            const who = target.anonymous ? UtilSanction.anonymousTargetFields(target.anonymous) : { userId: target.id };
            const created = await api.create({ ...body, ...who, reportCaseId: reportCaseId.value });
            open.value = false;
            return created;
        } catch (e) {
            const failure = errorOf(e);
            if (STALE.has(failure.code)) {
                error.value = failure.message;
                void loadPreview();
                return null;
            }
            const fields = failure.status === 400 ? (failure.fields ?? {}) : {};
            const mine = Object.fromEntries(Object.entries(fields).filter(([k]) => FIELD_KEYS.includes(k)));
            fieldErrors.value = mine;
            error.value = Object.keys(mine).length > 0 ? '' : failure.message;
            return null;
        } finally {
            busy.value = false;
        }
    }

    const view = computed<SanctionIssueView>(() => ({
        fixed: fixed.value,
        member: member.value,
        targets: targets.value,
        targetsLoading: targetsLoading.value,
        targetsError: targetsError.value,
        preview: preview.value,
        previewLoading: previewLoading.value,
        previewError: previewError.value,
        busy: busy.value,
        error: error.value,
        fieldErrors: fieldErrors.value,
    }));

    return { open, view, start, search, pick, unpick, loadPreview, submit };
}
