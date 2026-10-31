import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { BoardDateDisplay } from '~/composables/board/api/useBoardApi.types';
import type { PostRow } from '~/composables/board/api/usePostApi.types';

/** 목록 안 한 줄 광고(인피드) — 일반 글 `afterIndex`(0부터) 줄 **뒤에** 한 줄. 공지·고정 글은 세지 않는다(페이지가 `useBoardFeedAd` 로 정한다) */
export interface BoardFeedAd {
    ad: PublicAd;
    afterIndex: number;
}

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
    /**
     * 목록 안 한 줄 광고(목업 — 2026-10-31) — 없으면 null. 목록형(기본·다음식·네이버식)은 그 목록 줄과 같은 글꼴의 **한 줄**(꼬리표 + 문구), 카드형은 두 열을 가로지르는 얇은 한 줄,
     * 갤러리형은 넣지 않는다(이미지 격자에서 문구 칸이 튄다 — 받아도 그리지 않는다)
     */
    feedAd?: BoardFeedAd | null;
}
