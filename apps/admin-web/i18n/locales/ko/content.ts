/**
 * 콘텐츠 묶음(하이라이트·인기글) 화면이 같이 쓰는 글 한 칸(`ContentPostCell`) 문구. 화면별 문구는 `highlight.ts`·`popular.ts`.
 * `en/content.ts` 와 같은 키, 같은 차례.
 */
export default {
    postState: {
        PUBLIC: '공개',
        HIDDEN: '숨긴 글',
        DELETED: '지운 글',
        ADULT: '19금',
        BOARD_OFF: '꺼진 게시판',
    },
    guestAuthor: '{name} (비회원)',
    copyPostId: '글 번호 복사',
    copyPostIdLabel: '글 번호 {id} 복사',
    postIdCopied: '글 번호를 복사했어요.',
    copyFailed: '복사하지 못했어요.',
};
