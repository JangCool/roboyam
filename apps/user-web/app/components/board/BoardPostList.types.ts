import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { BoardDateDisplay, PostChipPosition } from '~/composables/board/api/useBoardApi.types';
import type { PostRow } from '~/composables/board/api/usePostApi.types';

/** 일반 글 `afterIndex`(0부터) 줄 뒤에 한 줄 — 공지·고정 글은 세지 않는다. 목록 한 쪽에 최대 2개(자리 설정 `boardList.count`·`gap`, V155) */
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
    /** 목록 안 한 줄 광고(최대 2개, 차례대로) — 갤러리형은 받아도 그리지 않는다. 줄마다 `UtilAd.feedAdAt` 로 찾는다 */
    feedAd?: BoardFeedAd[] | null;
    /**
     * 말머리·상태 칩 위치(게시판 정보의 `chipPosition` — 서버가 사이트 기본까지 푼 값, post-chip-position-contract). 기본 `ABOVE`.
     * 여러 줄 글 줄(모바일 목록 줄·카드형)만 따른다 — PC 표형 한 줄·갤러리 카드 이름 줄·고정 공지 한 줄·핫딜 목록은 늘 제목 앞
     */
    chipPosition?: PostChipPosition;
}
