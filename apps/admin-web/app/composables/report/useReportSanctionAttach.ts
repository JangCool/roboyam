import type { ReportSanctionContext } from '~/components/report/ReportActionDialog.types';
import type { SanctionWithReport } from '~/composables/sanction/api/useSanctionApi.types';
import type { SanctionStepValue } from '~/composables/sanction/useSanctionIssue.types';
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

/** `useReportSanctionAttach` 가 읽는 값 — 모두 함수(읽는 순간의 값을 본다) */
export interface ReportSanctionAttachInput {
    /** 작성자 현황(없으면 제재 구획 없음) */
    context: () => ReportSanctionContext | null;
    /** 이 처리에 제재를 곁들일 수 있나(무시 등은 false) */
    allowed: () => boolean;
    /** 「제재만」 — 체크 없이 늘 제재를 보낸다 */
    only: () => boolean;
    /** 처리 글 — 제재 사유를 고치기 전에는 이것과 같다 */
    note: () => string;
    /** 서버 칸 오류(`sanction.kind`·`sanction.reason`·`sanction.skipStep`·`sanction.confirmNickname`) */
    fieldErrors: () => Record<string, string>;
    /** 한 번 보내려 했나(칸 검사 문구를 보인다) */
    tried: () => boolean;
}

/**
 * 처리에 곁들이는 작성자 제재의 상태 — 신고 처리 대화창(`ReportActionDialog`)과 체험단 협찬 숨김 확인(`ReviewQueueList`, 점검 D2)이 같이 쓴다.
 * 그리는 것은 `ReportSanctionSection`. 서버 요청 모양은 신고 처리의 `sanction { kind, skipStep, confirmNickname, reason }`(member-sanction-contract 결정 4) — `reason` null 이면 서버가 처리 글을 쓴다.
 * 서버를 부르지 않는다(작성자 현황은 부른 화면이 `context` 로 준다). ⚠ 컴포넌트 setup 에서 부른다.
 */
export function useReportSanctionAttach(input: ReportSanctionAttachInput) {
    const t = useTranslate();

    const withSanction = ref(false);
    const step = ref<SanctionStepValue>({ kind: null, skipConfirmed: false, confirmNickname: '' });
    const reason = ref('');
    const reasonTouched = ref(false);

    /** 제재를 함께 보낸다 — 「제재만」은 늘, 그 밖 처리는 체크했을 때 */
    const on = computed(() => {
        const ctx = input.context();
        return Boolean(input.allowed() && ctx?.eligible && UtilSanction.canIssue(ctx.perm) && (input.only() || withSanction.value));
    });
    /** 제재 사유 칸 — 고치기 전에는 처리 글과 같다 */
    const reasonShown = computed(() => (reasonTouched.value ? reason.value : input.note()));
    const stepFieldErrors = computed(() => {
        const f = input.fieldErrors();
        return { kind: f['sanction.kind'] ?? f.sanction ?? '', skipStep: f['sanction.skipStep'] ?? '', confirmNickname: f['sanction.confirmNickname'] ?? '' };
    });
    const reasonError = computed(() => {
        const f = input.fieldErrors();
        if (f['sanction.reason']) return f['sanction.reason'];
        if (!input.tried()) return '';
        return UtilSanctionMessages.textProblem(t, UtilSanction.textProblem(reasonShown.value), t('report.sanctionReasonLabel'));
    });
    /** 고를 수 있는 단계가 하나라도 있나(제재를 보내지 않으면 늘 true) */
    const ready = computed(() => {
        const ctx = input.context();
        if (!on.value || !ctx?.preview) return !on.value;
        return UtilSanction.hasAnyOption(ctx.preview);
    });

    function onReason(value: string | undefined): void {
        reasonTouched.value = true;
        reason.value = value ?? '';
    }

    /** 열 때마다 비운다(앞 대상에 적던 값이 딸려 가면 안 된다) */
    function reset(): void {
        const ctx = input.context();
        withSanction.value = false;
        reason.value = '';
        reasonTouched.value = false;
        step.value = ctx ? UtilSanction.emptyStep(ctx.preview, ctx.perm) : { kind: null, skipConfirmed: false, confirmNickname: '' };
    }

    // 작성자 현황이 늦게 오거나 새로 받으면 누를 수 있는 다음 단계를 골라 둔다 — 고른 단계가 여전히 누를 수 있으면 그대로
    watch(
        () => input.context()?.preview ?? null,
        (preview) => {
            const ctx = input.context();
            if (!ctx) return;
            const keep = preview && step.value.kind && UtilSanction.choices(preview, ctx.perm).some((c) => c.enabled && c.option.kind === step.value.kind);
            if (!keep) step.value = UtilSanction.emptyStep(preview, ctx.perm);
        },
    );

    /** 보낼 제재 — `valid` false 면 칸을 고쳐야 한다(「제재만」인데 제재를 못 보내는 경우 포함) */
    function build(): { valid: boolean; sanction: SanctionWithReport | null } {
        if (on.value) {
            const ctx = input.context();
            const preview = ctx?.preview ?? null;
            if (!ctx || !preview || !UtilSanction.stepReady(step.value, preview, ctx.perm)) return { valid: false, sanction: null };
            if (UtilSanction.textProblem(reasonShown.value)) return { valid: false, sanction: null };
            return { valid: true, sanction: { ...UtilSanction.stepRequest(step.value, preview), reason: reasonTouched.value ? reason.value.trim() : null } };
        }
        return { valid: !input.only(), sanction: null };
    }

    return { withSanction, step, reasonTouched, on, reasonShown, stepFieldErrors, reasonError, ready, onReason, reset, build };
}
