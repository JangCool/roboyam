/**
 * 이용자 메모 작성·수정 시트의 **공유 상태** — 셸(`layouts/default.vue`)에 시트(`MemoSheet`)가 하나 있고, 프로필의 ⋯ 메뉴·쪽지 대화방·설정 > 이용자 메모가 `open` 으로 연다.
 * 저장·삭제는 여기서 `useMemoApi` 로 하고, 끝나면 이 기기의 표시(`useBlockOverrides`)를 맞춘 뒤 연 쪽의 `onSaved` 를 부른다.
 *
 * ⚠ setup 에서 부른다. 시트는 셸에 한 번만 놓는다.
 */
export interface MemoTarget {
    id: string;
    nickname: string;
    /** 지금 남겨 둔 메모(없으면 null) */
    memo: string | null;
}

interface MemoSheetState {
    open: boolean;
    target: MemoTarget | null;
}

/** 연 쪽이 저장 결과를 받을 함수 — 직렬화하지 않는 값이라 `useState` 밖(모듈)에 둔다 */
let onSavedHandler: ((userId: string, memo: string | null) => void) | null = null;

export function useMemoSheet() {
    const api = useMemoApi();
    const overrides = useBlockOverrides();
    const state = useState<MemoSheetState>('memo-sheet', () => ({ open: false, target: null }));

    function open(target: MemoTarget, onSaved?: (userId: string, memo: string | null) => void): void {
        onSavedHandler = onSaved ?? null;
        state.value = { open: true, target };
    }

    function close(): void {
        state.value = { ...state.value, open: false };
    }

    function done(userId: string, memo: string | null): void {
        overrides.setMemo(userId, memo);
        onSavedHandler?.(userId, memo);
        close();
    }

    /** 저장 — 빈 메모를 보내면 서버가 지운다(`null`). 실패는 `ApiError` 로 던진다 */
    async function save(text: string): Promise<string | null> {
        const target = state.value.target;
        if (!target) return null;
        const saved = await api.save(target.id, text);
        done(target.id, saved.memo);
        return saved.memo;
    }

    /** 삭제 — 실패는 `ApiError` 로 던진다 */
    async function remove(): Promise<void> {
        const target = state.value.target;
        if (!target) return;
        await api.remove(target.id);
        done(target.id, null);
    }

    return { state, open, close, save, remove };
}
