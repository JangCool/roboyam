import type { MyGrade } from '~/composables/grade/api/useGradeApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 내 등급·진행도 — 등급 안내(`/me/grade`)와 내 프로필(`/users/{내 ID}`)이 같이 읽는 **공유 상태**. 서버와 말하는 것은 `useGradeApi` 다.
 * - 받은 값은 그 회원의 것으로 기억한다 — 다른 사람이 로그인하면 지운다(`ownerId`).
 * - `load` 는 늘 새로 받는다(받아 둔 값이 있으면 그대로 보이면서 바꾼다 — 실패해도 보던 값은 지우지 않는다). 늦게 온 옛 응답은 요청 번호로 버린다.
 * ⚠ setup 에서 부른다(`await` 뒤에는 Nuxt 문맥이 없다) — 번역은 `useTranslate`(뒤에서도 안전)로 잡아 둔다.
 */
type GradeStatus = 'idle' | 'loading' | 'ready' | 'error';

let request = 0;

export function useMyGrade() {
    const api = useGradeApi();
    const t = useTranslate();
    const { user } = useAuth();

    const grade = useState<MyGrade | null>('my-grade', () => null);
    const status = useState<GradeStatus>('my-grade-status', () => 'idle');
    const errorMessage = useState<string>('my-grade-error', () => '');
    const ownerId = useState<string | null>('my-grade-owner', () => null);

    function reset(): void {
        request++;
        grade.value = null;
        status.value = 'idle';
        errorMessage.value = '';
        ownerId.value = null;
    }

    async function load(): Promise<void> {
        const me = user.value?.id ?? null;
        if (!me) return reset();
        if (ownerId.value !== me) reset();
        const mine = ++request;
        if (!grade.value) status.value = 'loading';
        try {
            const result = await api.myGrade();
            if (mine !== request) return;
            grade.value = result;
            ownerId.value = me;
            errorMessage.value = '';
            status.value = 'ready';
        } catch (e) {
            if (mine !== request) return;
            if (!grade.value) {
                errorMessage.value = UtilBoardMessages.of(t, e);
                status.value = 'error';
            }
        }
    }

    return { grade, status, errorMessage, load, reset };
}
