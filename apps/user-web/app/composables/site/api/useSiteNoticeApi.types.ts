/** 공지 띠의 글 한 줄 — 공지 게시판 글이면 `postId` 가 있고 링크는 그 글, 운영자가 직접 쓴 문구면 `postId` 가 없고 링크는 선택 */
export interface SiteNoticeItem {
    /** 공지 게시판 글 ID — 직접 입력 문구면 null */
    postId: string | null;
    /** 띠에 보일 한 줄(글 제목 또는 직접 입력 문구) — 텍스트 보간으로만 그린다 */
    title: string;
    /** 누르면 갈 곳 — `/`로 시작하면 사이트 안, `http(s)://` 면 새 창. null 이면 글자만 보인다(공지 글이면 서버가 `/posts/{id}` 를 채운다) */
    link: string | null;
    /** 글 등록 시각(ISO) — 직접 입력 문구면 null */
    publishedAt: string | null;
}

/**
 * `GET /api/site/notice-bar` 의 `data` — 운영자 설정 > 일반 「공지 띠」가 정한다.
 * 꺼져 있거나 보일 글이 없으면 `items` 가 비어 있고 띠를 그리지 않는다.
 */
export interface SiteNoticeBarResponse {
    /** 지금 공지 묶음의 표식 — 띠에 보이는 글·문구가 바뀌면 바뀐다(닫음 기록은 이 값별로 한다). 서버가 만든 값을 그대로 비교만 한다 */
    version: string;
    /** 최신 순(공지 게시판 글이면 최근 N개 — N 은 운영자가 5·10 중 고른다) */
    items: SiteNoticeItem[];
}
