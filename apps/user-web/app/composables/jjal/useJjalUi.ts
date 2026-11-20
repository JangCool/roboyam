/**
 * 짤 화면 상태 — 글·댓글의 짤을 누르면 뜨는 짤 메뉴, 고르기 창의 「올리기」. 그리는 것은 `JjalHost`(레이아웃에 한 번 — 처음 열릴 때 코드를 받는다)다.
 * 여기는 「무엇을 열어 달라」는 요청만 남긴다(`seq` 가 오르면 `JjalHost` 가 처리). 처음 안내 창·올리기 창·크게 보기·신고 창은 `JjalHost` 가 차례로 연다.
 * `open` = 한 번이라도 열렸다(레이아웃의 `opened` 가 이 값을 보고 `JjalHost` 를 그린다 — `useState` 키 `jjal-ui`).
 */

/** 짤 메뉴의 대상 — 누른 짤 */
export interface JjalMenuTarget {
    id: string;
    /** 「내 짤에 담기」를 보일까(그 글 맥락의 서버 `collectable` && 19금 게시판이 아님 — 내려간 짤은 메뉴가 뜨지 않는다) */
    collectable: boolean;
    /** 짤이 보이는 글 — 담기 요청에 함께 보낸다(글 맥락이 없으면 null, 그때는 담기가 없다) */
    postId: string | null;
    /** 댓글의 짤이면 그 댓글(글 본문이면 null) */
    commentId: string | null;
}

export type JjalUiRequest = { kind: 'menu'; target: JjalMenuTarget } | { kind: 'upload' };

export interface JjalUiState {
    open: boolean;
    /** 요청 차례 — 같은 짤을 다시 눌러도 새 요청이 되게 */
    seq: number;
    request: JjalUiRequest | null;
}

/** 짤 메뉴를 띄울 기준 — DOM 요소라 `useState` 밖(모듈)에 둔다(SPA 라 서버와 공유될 일이 없다) */
let anchor: { el: HTMLElement | null; event: Event | null } = { el: null, event: null };

export function useJjalUi() {
    const state = useState<JjalUiState>('jjal-ui', () => ({ open: false, seq: 0, request: null }));

    function send(request: JjalUiRequest): void {
        state.value = { open: true, seq: state.value.seq + 1, request };
    }

    /** 짤 메뉴를 연다 — 데스크톱은 누른 짤 옆 팝업, 휴대폰은 아래 시트 */
    function openMenu(target: JjalMenuTarget, el: HTMLElement | null, event: Event | null): void {
        anchor = { el, event };
        send({ kind: 'menu', target });
    }

    /** 올리기 창을 연다(처음이면 안내 창 먼저) */
    function openUpload(): void {
        anchor = { el: null, event: null };
        send({ kind: 'upload' });
    }

    /** (JjalHost 전용) 짤 메뉴 기준 */
    function menuAnchor(): { el: HTMLElement | null; event: Event | null } {
        return anchor;
    }

    return { state, openMenu, openUpload, menuAnchor };
}
