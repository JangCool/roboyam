import type { ReportReason } from '~/composables/report/api/useReportApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 소모임 신고(community `useReport` 의 `GROUP` 대상) — 소모임 상세의 ⋯ 메뉴와 일정 상세의 오프라인 고지 아래 「신고하기」가 같이 쓴다.
 * 시트는 글·댓글과 같은 `BoardReportDialog`(target `GROUP`), 서버 호출은 `useReportApi().report({ targetType: 'GROUP', targetId: 소모임 ID, … })` 한 겹.
 * 부를 때마다 새 상태다(`useState` 가 아니다). 비회원은 시트 대신 로그인 안내.
 *
 * 오류(글 상세의 신고와 같은 규칙):
 * - `401 UNAUTHENTICATED` → 시트를 닫고 로그인 안내 · `fields.detail` → 그 칸 아래.
 * - 다시 눌러도 안 되는 거절(이미 신고 `409 ALREADY_REPORTED` · 내 소모임 `400 CANNOT_REPORT_OWN` · 닫힌·없는 소모임 `404 REPORT_TARGET_NOT_FOUND` · 신고 끔 · 신고 차단 · 하루 한도 · 정지 · 권한 없음)
 *   → 시트를 닫고 서버 문구 토스트. 대상 없음이면 `onGone`(부른 화면이 소모임을 다시 받는다).
 * - 그 밖(`429 REPORT_TOO_FAST` 등)은 시트 안 한 줄.
 *
 * ⚠ setup 에서 부른다.
 */
export interface GroupReportOptions {
    groupId: MaybeRef<string>;
    /** 소모임이 그 사이 닫혔거나 없어졌다(`REPORT_TARGET_NOT_FOUND`) */
    onGone?: () => void;
}

/** 다시 시도해도 결과가 같은 거절 — 시트 대신 토스트. 내 소모임은 글·댓글과 같은 `CANNOT_REPORT_OWN`(문구만 소모임용) */
const FINAL: ReadonlySet<string> = new Set([
    'ALREADY_REPORTED',
    'CANNOT_REPORT_OWN',
    'REPORT_TARGET_NOT_FOUND',
    'REPORT_DISABLED',
    'REPORT_BLOCKED',
    'REPORT_DAILY_LIMIT',
    'ACCOUNT_RESTRICTED',
    'BOARD_RESTRICTED',
    'FORBIDDEN',
]);

export function useGroupReport(options: GroupReportOptions) {
    const api = useReportApi();
    const toast = useAppToast();
    const t = useTranslate();
    const { user } = useAuth();
    const { prompt } = useLoginPrompt();

    const visible = ref(false);
    const loading = ref(false);
    const error = ref('');
    const fieldError = ref('');

    /** 시트를 연다 — 비회원은 로그인 안내 */
    function open(): void {
        if (!user.value) {
            prompt('report');
            return;
        }
        error.value = '';
        fieldError.value = '';
        visible.value = true;
    }

    async function submit(value: { reason: ReportReason; detail: string | null }): Promise<void> {
        if (loading.value) return;
        loading.value = true;
        error.value = '';
        fieldError.value = '';
        try {
            await api.report({ targetType: 'GROUP', targetId: toValue(options.groupId), reason: value.reason, detail: value.detail });
            visible.value = false;
            toast.success(t('group.reported'));
        } catch (e) {
            if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') {
                visible.value = false;
                prompt('report');
            } else if (e instanceof ApiError && e.field('detail')) fieldError.value = e.field('detail') ?? '';
            else if (e instanceof ApiError && FINAL.has(e.code)) {
                visible.value = false;
                toast.error(e.message, 8000);
                if (e.code === 'REPORT_TARGET_NOT_FOUND') options.onGone?.();
            } else error.value = UtilBoardMessages.of(t, e);
        } finally {
            loading.value = false;
        }
    }

    return { visible, loading, error, fieldError, open, submit };
}
