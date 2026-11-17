import type { UnseenTitle } from '~/composables/title/api/useTitleApi.types';

/**
 * 칭호 축하 창 상태(member-title-contract 「화면」·「구현 결정」 4) — 로그인한 회원의 `GET /api/me/titles/unseen`(가벼운 API — 점검 D14) 이 있으면 축하 창(`TitleCelebrationDialog`)을 한 번 띄운다.
 * 셸(`layouts/default.vue`)은 로그인한 회원이면 첫 화면 뒤(잠깐 기다렸다가) 축하 창 코드를 받고, 축하 창이 `watchLogin` 으로 묻는다 — 이 composable 은 첫 화면 조각에 싣지 않는다.
 * 닫으면 `POST /api/me/titles/seen` 으로 서버에서 비운다(서버가 실패해도 이 탭에서는 다시 띄우지 않는다). 같은 회원은 한 번만 묻는다.
 *  - `unseen`: `undefined` = 아직 안 물어봄 · 빈 배열 = 없음.
 *  - 「대표 칭호로 쓰기」 = `PUT /api/me/titles/representative`(`makeRepresentative`).
 */
export function useTitleCelebration() {
    const api = useTitleApi();
    const { user } = useAuth();
    const unseen = useState<UnseenTitle[] | undefined>('title-unseen', () => undefined);
    /** 마지막으로 물어본 회원 — 다른 회원으로 바뀌면 다시 묻는다 */
    const askedFor = useState<string | null>('title-unseen-member', () => null);

    async function ensureLoaded(): Promise<void> {
        const id = user.value?.id ?? null;
        if (!id || askedFor.value === id) return;
        askedFor.value = id;
        try {
            const fresh = await api.unseen();
            if (user.value?.id === id) unseen.value = fresh;
        } catch {
            // 축하 창은 덤이다 — 못 받으면 띄우지 않는다
            unseen.value = [];
        }
    }

    /** 축하 창이 한 번 부른다 — 로그인한 회원이 바뀔 때마다 묻는다(로그아웃하면 비운다) */
    function watchLogin(): void {
        watch(
            () => user.value?.id ?? null,
            (id) => {
                if (id) {
                    void ensureLoaded();
                    return;
                }
                unseen.value = undefined;
                askedFor.value = null;
            },
            { immediate: true },
        );
    }

    /** 축하 창을 닫는다 — 서버에 봤다고 알린다 */
    async function dismiss(): Promise<void> {
        const had = (unseen.value?.length ?? 0) > 0;
        unseen.value = [];
        if (!had) return;
        try {
            await api.markSeen();
        } catch {
            // 다음 접속 때 다시 뜰 뿐이다
        }
    }

    /** 받은 칭호를 대표 칭호로 */
    async function makeRepresentative(key: string): Promise<void> {
        await api.setRepresentative({ key, hidden: false });
    }

    return { unseen, ensureLoaded, watchLogin, dismiss, makeRepresentative };
}
