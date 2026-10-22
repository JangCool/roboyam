/**
 * 댓글 문구 — `components/board/Comment*.vue` 가 쓴다. 가려진 댓글 자리의 문구는 `prompt.ts`(`delayPlaceholder`).
 * `en/comment.ts` 와 같은 키, 같은 차례. ⚠ 문구 안의 `@`·`|` 는 `{'@'}`·`{'|'}` 로 감싼다.
 */
export default {
    title: '댓글',
    loading: '댓글을 불러오는 중',
    loadFailed: '댓글을 불러오지 못했어요',
    sortLabel: '댓글 정렬',
    sortOldest: '등록순',
    sortLatest: '최신순',
    bestTitle: '베스트 댓글',
    bestBadge: 'BEST',
    emptyTitle: '아직 댓글이 없어요',
    emptyText: '첫 댓글을 남겨 보세요.',

    inputLabel: '댓글 입력',
    placeholder: '댓글을 남겨 보세요',
    submit: '등록',
    replySubmit: '답글 등록',
    replyingTo: '{name}님에게 답글',
    required: '댓글 내용을 입력해 주세요.',
    tooLong: '댓글은 {max}자까지 쓸 수 있어요.',
    posted: '댓글을 남겼어요.',
    updated: '댓글을 고쳤어요.',

    reply: '답글',
    edited: '(수정됨)',
    deleted: '삭제된 댓글이에요',
    hidden: '신고로 숨겨진 댓글이에요',
    likeLabel: '추천 {n}',
    alreadyLiked: '이미 추천한 댓글이에요.',

    editTitle: '댓글 고치기',
    deleteTitle: '이 댓글을 지울까요?',
    deleteMessage: '지운 댓글은 되돌릴 수 없어요.',
    deletedToast: '댓글을 지웠어요.',
    passwordDescription: '댓글을 쓸 때 정한 비밀번호를 입력해 주세요.',
};
