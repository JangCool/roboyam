import type { HighlightPlace } from '~/composables/content/api/useHighlightApi.types';

/**
 * 하이라이트를 놓을 수 있는 곳(홈 · 팁·강좌·사용기 · 가젯 페이지) — 하이라이트 화면의 곳 고르개와 하이라이트 가젯 ⚙ 설정의 「하이라이트 곳」이 같이 쓴다.
 * 한 번 받으면 다시 묻지 않는다(가젯 페이지가 새로 생겼을 수 있는 하이라이트 화면은 들어올 때 `load(true)`). 이름은 서버가 요청 언어로 준다.
 * 실패해도 화면은 지금 곳으로 쓸 수 있어 오류 문구만 들고 있다.
 */
export function useHighlightPlaces() {
    const places = useState<HighlightPlace[]>('highlight-places', () => []);
    const loaded = useState<boolean>('highlight-places-loaded', () => false);
    const loading = ref(false);
    const error = ref('');

    const api = useHighlightApi();
    const toApiError = useApiErrorOf();

    async function load(force = false): Promise<void> {
        if ((loaded.value && !force) || loading.value) return;
        loading.value = true;
        error.value = '';
        try {
            places.value = await api.places();
            loaded.value = true;
        } catch (e) {
            error.value = toApiError(e).message;
        } finally {
            loading.value = false;
        }
    }

    return { places, loaded, loading, error, load };
}
