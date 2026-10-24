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
    hiddenByStaff: '운영자가 숨긴 댓글이에요',
    likeLabel: '추천 {n}',
    alreadyLiked: '이미 추천한 댓글이에요.',

    editTitle: '댓글 고치기',
    deleteTitle: '이 댓글을 지울까요?',
    deleteMessage: '지운 댓글은 되돌릴 수 없어요.',
    deletedToast: '댓글을 지웠어요.',
    passwordDescription: '댓글을 쓸 때 정한 비밀번호를 입력해 주세요.',

    // ── 하이라이트 토론(찬성·반대 의견) ──
    stance: {
        PRO: '찬성',
        CON: '반대',
    },
    kindLabel: '댓글 구분',
    kind: {
        ALL: '전체',
        GENERAL: '일반',
        PRO: '찬성',
        CON: '반대',
    },
    kindEmpty: '이 구분의 댓글이 아직 없어요',
    debateClosed: '토론이 끝났어요. 이미 쓴 의견만 볼 수 있어요.',
    debateGuestHint: '찬성·반대 의견은 로그인한 회원만 쓸 수 있어요.',
    stancePickLabel: '어떤 입장으로 쓸까요?',
    stanceGeneral: '일반',
    stancePickHint: '찬성·반대로 처음 쓰면 그 입장이 정해져요. 정한 뒤에도 바꿀 수 있고, 바꾸면 이미 쓴 의견도 함께 바뀌어요.',
    myStance: '내 입장',
    myStanceHint: '이 글에 쓰는 댓글·답글에 이 입장 배지가 붙어요.',
    /** 단추 — 바뀔 입장을 쓴다(키 = 바뀔 입장) */
    changeStanceTo: {
        PRO: '찬성으로 바꾸기',
        CON: '반대로 바꾸기',
    },
    /** 확인창 제목 — 키 = 바뀔 입장 */
    changeStanceTitle: {
        PRO: '반대 → 찬성으로 바꿀까요?',
        CON: '찬성 → 반대로 바꿀까요?',
    },
    /** 확인창 본문 — 옮겨 갈 내 의견이 있을 때(`n` = `debate.myOpinionCount`) */
    changeStanceMoves: {
        PRO: '지금까지 쓴 반대 의견 {n}개도 찬성으로 바뀌어요.',
        CON: '지금까지 쓴 찬성 의견 {n}개도 반대로 바뀌어요.',
    },
    /** 확인창 본문 — 옮겨 갈 의견이 없을 때 */
    changeStanceFuture: {
        PRO: '앞으로 쓰는 의견에 찬성 배지가 붙어요.',
        CON: '앞으로 쓰는 의견에 반대 배지가 붙어요.',
    },
    stanceChanged: {
        PRO: '찬성으로 바꿨어요.',
        CON: '반대로 바꿨어요.',
    },
    /** 성공 토스트 — `n` = 응답 `moved` */
    stanceChangedMoved: {
        PRO: '찬성으로 바꿨어요. 내 의견 {n}개도 찬성으로 옮겼어요.',
        CON: '반대로 바꿨어요. 내 의견 {n}개도 반대로 옮겼어요.',
    },
    mention: '회원 지정',
    mentionSearch: '닉네임으로 찾기',
    mentionHelp: '이 글에 댓글을 쓴 회원을 고르면 그 회원의 최근 댓글에 답글로 달려요.',
    mentionEmpty: '고를 수 있는 회원이 없어요.',
    mentionTarget: "{'@'}{name}님에게 답글로 써요",
    mentionClear: '지정 풀기',
};
