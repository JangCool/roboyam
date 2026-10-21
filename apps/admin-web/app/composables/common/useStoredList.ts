/**
 * 이 브라우저에 기억하는 선택 목록 — 표에 보일 칸·화면에 보일 조건 줄처럼 "사람이 고른 것"을 새로고침 뒤에도 유지한다.
 * 저장은 localStorage 이고 계정에 올리지 않는다(서버 동기화는 계정 설정 API 가 생길 때). 읽고 쓰기가 막혀 있어도(사생활 보호 모드 등) 이번 방문 동안은 메모리에서 동작한다.
 * 저장된 값은 `sanitize` 로 지금 정의에 맞춘다 — 없어진 항목을 버리고 필수 항목을 채운다. 저장된 것이 없거나 깨졌으면 `defaults()`.
 *
 * @param key - localStorage 열쇠(`roboyam:admin:…`).
 * @param defaults - 저장된 것이 없을 때의 기본 선택.
 * @param sanitize - 저장소에서 읽은 값(JSON 으로 푼 것)을 쓸 수 있는 선택으로 고치는 함수.
 * @returns 선택 목록의 `ref` — 바꾸면 저장된다.
 */
export function useStoredList<T extends string>(key: string, defaults: () => T[], sanitize: (stored: unknown) => T[]): Ref<T[]> {
    const list = ref<T[]>(defaults()) as Ref<T[]>;

    if (import.meta.client) {
        try {
            const raw = window.localStorage.getItem(key);
            if (raw !== null) list.value = sanitize(JSON.parse(raw));
        } catch {
            /* 저장소를 못 읽거나 깨졌으면 기본값으로 뜬다 */
        }
        watch(
            list,
            (value) => {
                try {
                    window.localStorage.setItem(key, JSON.stringify(value));
                } catch {
                    /* 못 적으면 이번 방문에서만 기억한다 */
                }
            },
            { deep: true },
        );
    }

    return list;
}
