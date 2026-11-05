import type { MyEmoticonPack } from '~/composables/emoticon/api/useMyEmoticonsApi.types';

/**
 * 내가 가진 로보티콘 팩(받은 무료 팩 + 산 유료 팩 + 내가 만든 팩) — 고르기 창(댓글·글 에디터)·팩 상세·내 로보티콘이 같이 본다. 받거나 사면 `reload()` 해 고르기 창에 바로 보인다.
 * undefined = 아직 안 물어봄 · null = 로그인 안 함(고르기 창은 이모지 탭만) · 배열 = 받은 팩.
 * `allPacks` = 운영자 회원 계정(서버 응답 최상위 `allPacks` — 없으면 false) — 판매 중 팩을 모두 가진 것으로 본다(나머지 판매 중 팩은 `useOnSaleEmoticonPacks`).
 * 로그인한 사람이 바뀌면(다른 계정·로그아웃) 다시 받는다.
 */
let inflight: Promise<void> | null = null;

export function useMyEmoticons() {
    const api = useMyEmoticonsApi();
    const { user } = useAuth();
    const packs = useState<MyEmoticonPack[] | null | undefined>('my-emoticons', () => undefined);
    /** 운영자 회원 계정 — 판매 중 팩을 모두 쓴다(받기 전·로그인 안 함은 false) */
    const allPacks = useState<boolean>('my-emoticons-all-packs', () => false);
    /** 받은 목록의 주인 — 로그인한 사람이 바뀌면 다시 받는다 */
    const owner = useState<string | null>('my-emoticons-owner', () => null);
    const error = useState<boolean>('my-emoticons-error', () => false);

    async function load(): Promise<void> {
        const me = user.value?.id ?? null;
        if (!me) {
            packs.value = null;
            allPacks.value = false;
            owner.value = null;
            return;
        }
        if (inflight) return inflight;
        inflight = (async () => {
            try {
                const result = await api.list();
                packs.value = result.packs;
                allPacks.value = result.allPacks === true;
                owner.value = me;
                error.value = false;
            } catch {
                error.value = true;
                if (packs.value === undefined) packs.value = [];
                // 다른 사람의 값이 남지 않게 — 이 사람의 것을 받은 적이 없으면 끈다
                if (owner.value !== me) allPacks.value = false;
            } finally {
                inflight = null;
            }
        })();
        return inflight;
    }

    /** 아직 안 받았거나 사람이 바뀌었으면 받는다 */
    function ensureLoaded(): Promise<void> {
        const me = user.value?.id ?? null;
        if (packs.value !== undefined && owner.value === me && !(me && packs.value === null)) return Promise.resolve();
        return load();
    }

    /** 받은 팩인가(운영자 회원 계정의 「판매 중 팩 모두」는 보지 않는다 — 그쪽은 `useOnSaleEmoticonPacks`) */
    function has(packId: string): boolean {
        return Boolean(packs.value?.some((p) => p.id === packId));
    }

    /** 목록에서 뺀다 — 성공하면 상태에서도 뺀다(실패는 부르는 쪽이 알린다) */
    async function remove(packId: string): Promise<void> {
        await api.remove(packId);
        if (packs.value) packs.value = packs.value.filter((p) => p.id !== packId);
    }

    return { packs, allPacks, error, ensureLoaded, reload: load, has, remove };
}
