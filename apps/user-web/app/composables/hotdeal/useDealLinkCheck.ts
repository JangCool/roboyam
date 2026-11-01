import type { DealFormValues, DealLinkCheckState } from '~/components/hotdeal/HotdealDealFields.types';
import type { DealLinkCheck } from '~/composables/hotdeal/api/useHotdealApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';
import type { Translate } from '#layers/i18n/utils/util-i18n';

/**
 * 판정 API 오류의 안내 — 분당 한도(`429 DEAL_LINK_CHECK_RATE_LIMIT`)는 핫딜 문구, 핫딜 쓰기 자격(`403`)은 핫딜 자격 문구,
 * 칸 오류(`400 fields.url`·`fields.board`)는 서버의 그 칸 문구. 그 밖(망·서버 오류)은 빈 글자 — 딜 칸이 「확인하지 못했어요. 그래도 등록할 수 있어요」를 보인다
 */
function checkError(t: Translate, e: unknown): string {
    if (!(e instanceof ApiError)) return '';
    if (UtilHotdeal.isWriteBlockedCode(e.code)) return UtilHotdealMessages.writeBlocked(t, e.code, UtilHotdealMessages.gradeLabelOf(e));
    return UtilHotdealMessages.dealError(t, e) ?? e.field('url') ?? e.field('board') ?? '';
}

/**
 * 글쓰기·고치기의 상품 링크 판정 — 링크 칸이 바뀌면 잠시 기다렸다가(디바운스) `link-check` 를 부르고, 결과로 딜 칸을 채운다.
 * 채우는 것: 쇼핑몰 이름(글쓴이가 고치지 않았으면) · OG 이미지(글쓴이가 지우거나 올리지 않았으면 — 없음·실패면 비운다) · 제휴 강제(AFFILIATE 면 켬).
 * 화면마다 따로 쓰는 상태라 `useState` 가 아니다(페이지가 하나씩 만든다). 서버 호출은 `useHotdealApi` 한 겹.
 *
 * @param form - 딜 칸 값(페이지의 ref — 결과가 오면 이 값을 고친다).
 * @param board - 게시판 slug(판정에 싣는다).
 */
export function useDealLinkCheck(form: Ref<DealFormValues>, board: MaybeRef<string>) {
    const api = useHotdealApi();
    const t = useTranslate();
    const state = ref<DealLinkCheckState>({ status: 'idle', result: null, error: '' });

    let timer: ReturnType<typeof setTimeout> | null = null;
    let seq = 0;
    let abort: AbortController | null = null;
    /** 마지막으로 판정했거나(또는 판정하지 않기로 한) 링크 — 같은 링크로는 다시 부르지 않는다 */
    let lastUrl = '';

    function clearTimer(): void {
        if (timer) clearTimeout(timer);
        timer = null;
    }

    function apply(result: DealLinkCheck): void {
        form.value = UtilHotdeal.applyLinkCheck(form.value, result);
    }

    async function run(url: string): Promise<void> {
        clearTimer();
        const mine = ++seq;
        abort?.abort();
        abort = new AbortController();
        lastUrl = url;
        state.value = { status: 'checking', result: state.value.result, error: '' };
        try {
            const result = await api.linkCheck(url, toValue(board), abort.signal);
            if (mine !== seq || form.value.productUrl.trim() !== url) return;
            state.value = { status: 'done', result, error: '' };
            apply(result);
        } catch (e) {
            if (mine !== seq) return;
            if (e instanceof ApiError && e.code === 'ABORTED') return;
            state.value = { status: 'error', result: null, error: checkError(t, e) };
        }
    }

    watch(
        () => form.value.productUrl,
        (raw) => {
            const url = raw.trim();
            if (url === lastUrl) return;
            clearTimer();
            if (UtilHotdeal.urlFormatProblem(url) !== null) {
                // 형식이 맞지 않으면 부르지 않는다(칸 오류는 화면 검사가 말한다) — 지난 결과도 치운다
                ++seq;
                abort?.abort();
                lastUrl = '';
                state.value = { status: 'idle', result: null, error: '' };
                return;
            }
            timer = setTimeout(() => void run(url), UtilHotdeal.LINK_CHECK_DEBOUNCE_MS);
        },
    );

    onScopeDispose(() => {
        clearTimer();
        abort?.abort();
    });

    return {
        state,
        /** 「다시 시도」 — 지금 링크로 바로 다시 판정한다(OG 실패·판정 실패) */
        retry: (): void => {
            const url = form.value.productUrl.trim();
            if (UtilHotdeal.urlFormatProblem(url) === null) void run(url);
        },
        /** 고치기 — 불러온 링크는 판정하지 않는다(이미 저장된 딜). 이 링크를 「이미 본 링크」로 둔다 */
        prime: (url: string): void => {
            lastUrl = url.trim();
        },
    };
}
