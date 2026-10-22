import type { BoardDateDisplay } from '~/composables/board/api/useBoardApi.types';
import type { PostRow } from '~/composables/board/api/usePostApi.types';

/**
 * 게시판 목록 모양 세 가지(`BoardPostList` LIST · `BoardPostCards` CARD · `BoardPostGallery` GALLERY)가 같이 지키는 속성 계약 — themes.md 2-3(같은 슬롯은 같은 계약).
 * 테마가 목록 모양을 바꿀 때(`postList:LIST` 등) 이 계약을 그대로 받는다. 그리기만 하고 조회·쪽 나누기는 페이지가 한다.
 */
export interface BoardPostListProps {
    /** 일반 글 */
    rows: PostRow[];
    /** 고정 공지 — 맨 위에 따로 */
    pinned: PostRow[];
    /** 날짜 표시 방식 */
    dateDisplay: BoardDateDisplay;
    /** 「새 글」 기준(시간) */
    newPostHours: number;
}
