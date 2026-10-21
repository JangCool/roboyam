/**
 * 로그인 유도 시트 — 비회원이 글쓰기·댓글을 시도할 때 아래에서 올라온다 (design 01-shell 7).
 * 읽기는 그대로 되고 쓰기 동작만 막는다. 로그인을 마치면 `next` 로 돌아온다(하려던 동작의 화면).
 * 그리는 것은 `LoginPromptSheet`(레이아웃에 한 번 놓는다)다.
 *
 * ```ts
 * const { prompt } = useLoginPrompt();
 * prompt('write', '/write?board=ai');       // 로그인하면 글쓰기 화면으로
 * prompt('comment');                          // next 를 안 주면 지금 보는 화면으로
 * prompt('opinion');                          // 홈 하이라이트 카드의 찬성·반대 의견 쓰기
 * prompt('like');                             // 추천(글·댓글)
 * prompt('vote');                             // 투표(글 상세의 투표)
 * prompt('scrap');                            // 스크랩(글 상세의 스크랩 단추)
 * prompt('block');                            // 회원 차단(⋯ 메뉴)
 * prompt('memo');                             // 이용자 메모(⋯ 메뉴)
 * prompt('dm');                               // 쪽지 보내기(프로필의 쪽지 단추)
 * prompt('report');                           // 신고(글·댓글·소모임·프로필 이미지·쪽지의 ⋯ 메뉴, 7-1)
 * prompt('dealEnd');                          // 핫딜 종료 알리기(딜 카드의 "품절·가격이 달라졌어요", H-5)
 * prompt('view', '/posts/9#comment-5');       // 비회원에게 가려진 댓글 본문(댓글 지연 노출, COM-003) — 로그인하면 그 글의 그 댓글로 돌아온다
 * ```
 */
export type LoginPromptAction = 'write' | 'comment' | 'reply' | 'opinion' | 'like' | 'vote' | 'scrap' | 'block' | 'memo' | 'dm' | 'report' | 'dealEnd' | 'view';

export interface LoginPromptState {
    open: boolean;
    action: LoginPromptAction;
    /** 로그인 뒤 돌아갈 곳(같은 사이트 안의 경로) */
    next: string;
}

export function useLoginPrompt() {
    const state = useState<LoginPromptState>('login-prompt', () => ({ open: false, action: 'write', next: '/' }));
    const route = useRoute();

    function prompt(action: LoginPromptAction, next?: string): void {
        state.value = { open: true, action, next: next ?? route.fullPath };
    }

    function close(): void {
        state.value = { ...state.value, open: false };
    }

    return { state, prompt, close };
}
