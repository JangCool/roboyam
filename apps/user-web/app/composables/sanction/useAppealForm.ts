import type { AppealCreated, MySanctions } from '~/composables/sanction/api/useSanctionApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import type { AppealOutcome } from '~/utils/util-sanction';
import UtilSanction from '~/utils/util-sanction';
import type { SanctionCopy } from '~/utils/util-sanction-messages';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

/**
 * `/sanction/appeal?sanctionId=…`(이의제기)의 흐름 — **내 제재 불러오기 → 그 제재 찾기 → 화면 갈래 → 사유 쓰기 → 접수** (community `useAppealForm` 에서 게시판 한정 제재를 뺐다).
 * 제재 하나를 따로 읽는 API 는 없어 내 제재(`GET /api/me/sanctions`)에서 ID 로 찾는다. 한 화면의 값이라 `useState` 에 두지 않는다.
 * `submit`: 사유가 10~500자일 때만, 보내는 중에는 다시 보내지 않는다. 결과는 오류 **코드**로 가른다(`UtilSanction.appealOutcomeOf`) —
 * 이미 접수함·무효·없는 제재 → 최신 상태를 다시 받아 그 화면으로 · 입력 오류 → 서버 `fields.reason` 을 칸 아래에 · 그 밖 → 붉은 안내(쓴 사유는 그대로).
 * **기각된 뒤에는 폼을 다시 열지 않는다** — 제재 하나에 1번이다.
 * ⚠ setup 에서 부른다 — 번역은 `useTranslate` 로 잡아 둔다.
 */
export type AppealSubmitResult = 'DONE' | 'IGNORED' | AppealOutcome;

export function useAppealForm(sanctionId: () => unknown) {
    const api = useSanctionApi();
    const t = useTranslate();
    const { refresh } = useAuth();

    const status = ref<'loading' | 'ready' | 'error'>('loading');
    const data = ref<MySanctions | null>(null);
    const errorMessage = ref('');
    const reason = ref('');
    const touched = ref(false);
    const sending = ref(false);
    const submitted = ref<AppealCreated | null>(null);
    const failure = ref<SanctionCopy | null>(null);
    const serverReasonError = ref('');

    let seq = 0;
    let abort: AbortController | null = null;

    const id = computed(() => UtilSanction.sanctionIdOf(sanctionId()));
    const sanction = computed(() => (data.value ? UtilSanction.findById(data.value.items, id.value) : null));
    const screen = computed(() => (status.value === 'ready' ? UtilSanction.appealScreenOf(sanction.value, submitted.value !== null) : null));
    const length = computed(() => UtilSanction.appealReasonLength(reason.value));
    const problem = computed(() => UtilSanction.appealReasonProblem(reason.value));
    /** 사유 칸 아래 안내 — 500자 초과는 바로, 비었거나 짧다는 것은 칸을 떠난 뒤, 서버가 지목한 오류는 그대로 */
    const reasonMessage = computed(() => {
        if (problem.value === 'TOO_LONG' || (touched.value && problem.value)) return UtilSanctionMessages.reasonProblem(t, problem.value!, length.value);
        return serverReasonError.value;
    });
    const canSubmit = computed(() => screen.value === 'FORM' && !sending.value && UtilSanction.canAppeal(sanction.value) && problem.value === null);

    watch(reason, () => {
        failure.value = null;
        serverReasonError.value = '';
    });
    // 다른 제재로 옮겨 가면 쓰던 사유와 접수 결과를 비운다 — 옛 제재의 글이 새 제재에 붙어 나가지 않게
    watch(id, () => {
        reason.value = '';
        touched.value = false;
        submitted.value = null;
        failure.value = null;
        serverReasonError.value = '';
    });

    async function load(options: { silent?: boolean } = {}): Promise<void> {
        const mine = ++seq;
        abort?.abort();
        const controller = new AbortController();
        abort = controller;
        if (!options.silent) {
            status.value = 'loading';
            errorMessage.value = '';
        }
        try {
            const res = await api.mine(controller.signal);
            if (mine !== seq) return;
            data.value = res;
            status.value = 'ready';
            void refresh().catch(() => undefined);
        } catch (e) {
            if (mine !== seq) return;
            if (e instanceof ApiError && e.code === 'ABORTED') return;
            if (options.silent && data.value) return;
            errorMessage.value = e instanceof ApiError ? e.message : t('sanction.loadError');
            status.value = 'error';
        }
    }

    function cancel(): void {
        seq++;
        abort?.abort();
    }

    function touch(): void {
        touched.value = true;
    }

    async function submit(): Promise<AppealSubmitResult> {
        const target = sanction.value;
        if (!target || sending.value || screen.value !== 'FORM') return 'IGNORED';
        if (!UtilSanction.canAppeal(target) || problem.value !== null) {
            touched.value = true; // 눌러 보았으니 무엇이 모자란지 보인다
            return 'IGNORED';
        }
        sending.value = true;
        failure.value = null;
        serverReasonError.value = '';
        try {
            submitted.value = await api.appeal(UtilSanction.toAppealRequest(target.id, reason.value));
        } catch (e) {
            const outcome = UtilSanction.appealOutcomeOf(e);
            sending.value = false;
            if (outcome === 'ALREADY' || outcome === 'NOT_APPEALABLE' || outcome === 'NOT_FOUND') await load();
            else if (outcome === 'VALIDATION' && e instanceof ApiError && e.field('reason')) serverReasonError.value = e.field('reason') ?? '';
            else if (outcome !== 'LOGIN') failure.value = UtilSanctionMessages.submitFailure(t, outcome, e);
            return outcome;
        }
        sending.value = false;
        // 접수일을 보이려고 뒤에서 최신 상태를 받는다 — 못 받아도 「접수 완료」 화면은 그대로다
        await load({ silent: true });
        return 'DONE';
    }

    return { status, errorMessage, sanction, screen, reason, sending, submitted, failure, length, problem, reasonMessage, canSubmit, load, cancel, touch, submit };
}
