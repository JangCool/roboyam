import UtilDmMessages from '~/utils/util-dm-messages';
import type { DmNoticeCopy } from '~/utils/util-dm-messages';

/**
 * 보내기 뒤 입력창 위에 보일 안내 한 개 — 대화방(`useDmRoom`)과 새 쪽지 화면이 같은 규칙으로 쓴다.
 *  - `rejected(e)`: 서버가 **거절한** 오류의 안내(서버 `message` 그대로, 입력은 그대로). `RATE_LIMITED` 는 남은 시간을 붙여 세고(`cooling` 동안 보내기를 끈다), 끝나면 거둔다.
 *  - `failedLocally(e)`: 연결 끊김·5xx — 서버가 판정하지 못한 실패의 붉은 안내.
 *  - `clear`: 거둔다.
 * ⚠ setup 에서 부른다(`useCountdown` 이 타이머를 화면 수명에 묶는다).
 */
export interface DmNoticeView extends DmNoticeCopy {
    countdown: boolean;
}

export function useDmNotice() {
    const t = useTranslate();
    const values = useDisplayValues();
    const raw = ref<DmNoticeCopy | null>(null);
    const countdownShown = ref(false);
    const endsAt = ref(0);
    const retry = useCountdown(endsAt, () => {
        if (raw.value && countdownShown.value) raw.value = null;
    });

    const notice = computed<DmNoticeView | null>(() => {
        const n = raw.value;
        if (!n) return null;
        const counting = countdownShown.value && retry.active.value;
        return { ...n, text: counting ? `${n.text} ${UtilDmMessages.retryIn(t, retry.remaining.value, values.value.language)}`.trim() : n.text, countdown: counting };
    });

    function clear(): void {
        raw.value = null;
        countdownShown.value = false;
        retry.stop();
    }

    function rejected(e: unknown): void {
        const rejection = UtilDmMessages.rejection(t, e);
        raw.value = { tone: rejection.tone, title: rejection.title, text: rejection.text, link: rejection.link };
        countdownShown.value = rejection.retryAfterSeconds !== null;
        if (rejection.retryAfterSeconds !== null) retry.start(rejection.retryAfterSeconds);
        else retry.stop();
    }

    function failedLocally(e: unknown): void {
        raw.value = UtilDmMessages.localFailure(t, e);
        countdownShown.value = false;
        retry.stop();
    }

    return { notice, cooling: retry.active, clear, rejected, failedLocally };
}
