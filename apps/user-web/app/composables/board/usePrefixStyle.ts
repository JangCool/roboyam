import type { ComputedRef, InjectionKey } from 'vue';
import type { PostPrefixStyle } from '~/composables/board/api/useBoardApi.types';
import UtilBoard from '~/utils/util-board';

/** 이 글 목록의 말머리 모양 문맥 — 게시판 목록·소모임 글 목록이 게시판 정보의 `prefixStyle` 로 건다 */
const KEY: InjectionKey<() => PostPrefixStyle> = Symbol('prefix-style');

/**
 * 아래 글 줄들의 말머리 모양을 정한다(mobile-nav-prefix-style-contract 1절) — 게시판 목록(`/boards/{slug}`)·소모임 글 목록은 게시판 정보의 `prefixStyle`(서버가 사이트 기본까지 푼 값),
 * 가젯 글 줄(`GadgetPostRow`)은 놓인 곳과 상관없이 사이트 기본을 건다. 걸지 않은 곳(최신글·인기글·검색·회원 작성글·팁)은 섞인 목록 기본값을 따른다(`usePrefixStyle`).
 * 핫딜 목록 줄은 게시판 목록 화면이 건 그 게시판 값을 따른다.
 * setup 에서 부른다.
 * @param {() => unknown} raw - 서버 값을 읽는 함수(모르는 값·옛 응답은 `TEXT`).
 */
export function providePrefixStyle(raw: () => unknown): void {
    provide(KEY, () => UtilBoard.prefixStyle(raw()));
}

/**
 * 말머리·게시판 이름을 그릴 모양 — 위에서 건 값(`providePrefixStyle`), 없으면 섞인 목록 기본값(`GET /api/site/info` 의 `boardDisplay.listPrefixStyle`, 못 받았으면 `TEXT`).
 * setup 에서 부른다.
 * @returns {ComputedRef<PostPrefixStyle>} 그릴 모양.
 */
export function usePrefixStyle(): ComputedRef<PostPrefixStyle> {
    const injected = inject(KEY, null);
    if (injected) return computed(() => injected());
    const { display } = useBoardDisplay();
    return computed(() => display.value.listPrefixStyle);
}
