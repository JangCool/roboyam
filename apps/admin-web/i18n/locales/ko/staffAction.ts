/**
 * 스텝 처리 감독 문구 — 스텝 처리 감독 화면(`/staff-actions`)·서랍·되돌리기 대화상자가 쓴다(계약 `docs/features/board-staff-action-contract.md`).
 * `en/staffAction.ts` 와 같은 키, 같은 차례. 사이트 제재와 같은 문구(새로 받기·검색·기간·건수)는 `sanction.*`, 매니저·스텝 자리 이름은 `boardSanction.role.*` 를 그대로 쓴다.
 * 되돌린 사유·서버 오류 문구는 여기 없다(서버 몫).
 */
export default {
    title: '스텝 처리 감독',
    subtitle: '게시판 매니저·스텝이 맡은 게시판에서 한 글 숨기기·글 삭제·댓글 삭제를 모든 게시판에 걸쳐 보고, 필요하면 되돌려요. 처리자는 관리자 화면과 그 게시판 매니저에게만 보여요.',
    readOnly: '조회만 할 수 있어요 — 되돌리려면 스텝 처리 복구(keeper-action.restore) 권한이 필요해요.',

    // 조회
    searchLabel: '스텝 처리 검색',
    searchPlaceholder: '처리자 닉네임',
    filterRestored: '되돌림',
    restoredFilter: {
        ALL: '전체',
        OPEN: '그대로',
        RESTORED: '되돌림',
    },
    filterBoard: '게시판',
    filterBoardAll: '전체 게시판',
    filterKind: '종류',
    filterAll: '전체',
    filterPeriod: '처리일',
    sortNote: '최근 처리부터',
    loadFailed: '스텝 처리 목록을 불러오지 못했어요',
    empty: '아직 스텝 처리가 없어요',
    emptyHint: '게시판 매니저·스텝이 글·댓글 메뉴에서 숨기거나 지우면 여기에 쌓여요.',

    // 표
    col: {
        board: '게시판',
        kind: '종류',
        target: '대상',
        reason: '사유',
        actor: '처리자',
        at: '시각',
        restored: '되돌림',
        actions: '동작',
    },
    kind: {
        POST_HIDE: '글 숨기기',
        POST_DELETE: '글 삭제',
        COMMENT_DELETE: '댓글 삭제',
    },
    reason: {
        SPAM: '스팸·광고',
        ABUSE: '비방·인신공격',
        INVESTMENT: '투자 권유',
        OFF_TOPIC: '게시판 주제와 맞지 않음',
    },
    state: {
        PUBLIC: '공개',
        HIDDEN: '숨김',
        DELETED: '삭제됨',
    },
    restoredTag: '되돌림',
    openTag: '그대로',
    restoredBy: {
        MANAGER: '매니저',
        OPERATOR: '운영자',
    },
    restoredByValue: '{name}({who})',
    actorValue: '{name} · {role}',
    actorOperatorValue: '{name} ({role})',
    actorGone: '(알 수 없음)',
    authorGuest: '{name}(비회원)',
    authorGone: '(알 수 없음)',
    titleNone: '(제목 없음)',
    excerptNone: '(내용 없음)',

    // 서랍
    drawerTitle: '{kind} 처리',
    drawerTitleEmpty: '스텝 처리',
    prev: '이전 처리',
    next: '다음 처리',
    detailFailed: '처리를 불러오지 못했어요',
    infoTitle: '처리',
    targetTitle: '대상',
    openPostAdmin: '게시글 관리에서 보기',
    commentOf: '「{title}」의 댓글',
    authorLabel: '작성자 {name}',
    stateLabel: '지금',
    snapshotNote: '제목·댓글 앞부분은 처리할 때 남긴 것이에요. 「지금」은 대상의 현재 상태예요.',
    restoredTitle: '되돌림 기록',
    restoredByLabel: '되돌린 사람',
    restoredAtLabel: '되돌린 시각',
    restoreReasonNone: '사유 없음',
    needsRestore: '되돌리려면 스텝 처리 복구(keeper-action.restore) 권한이 필요해요.',

    // 되돌리기
    restore: '되돌리기',
    restoreRow: '{kind} 되돌리기',
    restoreTitle: '스텝 처리 되돌리기',
    restoreReasonLabel: '되돌리는 사유',
    restoreNotice: {
        POST_HIDE: '글의 숨김을 풀어요. 작성자에게는 알리지 않고, 감사 기록에 남아요. 처리자의 오늘 한도는 돌려주지 않아요.',
        POST_DELETE: '지운 글을 되살리고 회수한 포인트를 돌려줘요. 작성자에게는 알리지 않고, 감사 기록에 남아요. 처리자의 오늘 한도는 돌려주지 않아요.',
        COMMENT_DELETE: '지운 댓글을 되살려요. 작성자에게는 알리지 않고, 감사 기록에 남아요. 처리자의 오늘 한도는 돌려주지 않아요.',
    },
    restored: {
        POST_HIDE: '글의 숨김을 풀었어요',
        POST_DELETE: '글을 되살렸어요',
        COMMENT_DELETE: '댓글을 되살렸어요',
    },
    error: {
        BOARD_STAFF_ACTION_RESTORED: '이미 되돌린 처리예요. 목록을 새로 받았어요.',
        POST_STATE_INVALID: '그사이 글 상태가 바뀌어 되돌릴 수 없어요. 게시글 관리에서 확인해 주세요.',
        POST_PURGED: '원문을 파기한 글이라 되돌릴 수 없어요.',
    },
};
