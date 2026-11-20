import type { ReportReason } from '~/composables/report/api/useReportApi.types';
import UtilJjal from '~/utils/util-jjal';
import UtilJjalMessages from '~/utils/util-jjal-messages';
import { ApiError } from '~/composables/common/useApiClient';

/**
 * 짤 메뉴의 일 — 담기(결과 토스트)·신고. `JjalHost` 한 곳이 setup 에서 부른다(콜백 안에서 컴포저블을 부르지 않게 미리 잡아 둔다).
 * 담은 뒤 문구(계약 「담은 뒤 문구」): 「내 짤에 담았어요 13 / 50」 · 「이미 내 짤에 있어요」 · 「내 짤 50개가 꽉 찼어요」 + [정리하기] → 내 짤 칸.
 */
export function useJjalActions() {
    const t = useTranslate();
    const mine = useMyJjals();
    const reportApi = useReportApi();
    const toast = useAppToast();
    const router = useRouter();

    /** 꽉 찼다 — 토스트의 [정리하기] 가 내 짤 칸으로 보낸다 */
    function showFull(limit: number): void {
        toast.show(t('jjal.full', { limit }), { tone: 'error', life: 6000, action: { label: t('jjal.organize'), run: () => void router.push(UtilJjal.MY_SECTION_PATH) } });
    }

    /** 담기 — 처음 안내 확인은 부르는 쪽(JjalHost)이 먼저 본다 */
    async function collect(jjalId: string): Promise<void> {
        try {
            const result = await mine.collect(jjalId);
            toast.success(t('jjal.collected', { count: result.count, limit: result.limit }));
        } catch (e) {
            if (e instanceof ApiError && e.code === 'JJAL_ALREADY_IN_BOX') toast.show(t('jjal.alreadyIn'));
            else if (e instanceof ApiError && e.code === 'JJAL_LIMIT_REACHED') showFull(mine.list.value?.limit ?? UtilJjal.LIMIT);
            else toast.error(UtilJjalMessages.error(t, e), 6000);
        }
    }

    /** 짤 신고(대상 종류 `JJAL` — 사유 6종 그대로). 실패는 던진다(신고 창이 서버 문구를 보인다) */
    async function report(jjalId: string, reason: ReportReason, detail: string | null): Promise<void> {
        await reportApi.report({ targetType: 'JJAL', targetId: jjalId, reason, detail });
        toast.success(t('jjal.reported'));
    }

    return { collect, report, showFull };
}
