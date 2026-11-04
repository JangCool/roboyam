/**
 * 게시판 제재 문구 — 게시판 제재 화면(`/board-sanctions` 이력·반복 검토)·서랍·회원 상세 「게시판 제재」 구획·이의제기의 게시판 제재 배지가 쓴다(계약 `docs/features/board-sanction-contract.md`).
 * `en/boardSanction.ts` 와 같은 키, 같은 차례. 사이트 제재와 같은 문구(상태 이름·검색·기간·표 머리 일부)는 `sanction.*` 를 그대로 쓴다.
 * 매니저·스텝이 덧붙인 글·운영자 메모·서버 오류 문구는 여기 없다(서버 몫).
 */
export default {
    title: '게시판 제재',
    subtitle: '게시판 매니저·스텝이 맡은 게시판에서 준 경고·쓰기 제한(1·3·7일)을 보고, 풀거나 무효로 해요. 같은 회원이 같은 게시판에서 7일 안에 3건을 받으면 반복 검토가 열려요.',
    tabsLabel: '게시판 제재 보기',
    tab: {
        history: '이력',
        reviews: '반복 검토',
    },
    openBadge: '열린 검토 {n}건',
    readOnly: '조회만 할 수 있어요 — 해제·무효·반복 검토 닫기는 제재 관리(sanction.manage) 권한이 필요해요.',

    // 조회
    searchLabel: '게시판 제재 검색',
    searchPlaceholder: {
        NICKNAME: '받은 회원 닉네임(2자 이상)',
        SANCTION_NO: '게시판 제재 번호(숫자)',
    },
    filterBoard: '게시판',
    filterBoardAll: '전체 게시판',
    loadFailed: '게시판 제재 목록을 불러오지 못했어요',
    empty: '아직 게시판 제재가 없어요',
    emptyHint: '게시판 매니저·스텝이 글·댓글 메뉴의 「작성자 제재」로 준 제재가 여기에 쌓여요.',

    // 표
    col: {
        no: '번호',
        member: '회원',
        board: '게시판',
        kind: '종류',
        reason: '사유',
        issuedBy: '처리자',
        startsAt: '시작',
        endsAt: '끝',
        status: '상태',
        appeal: '이의',
        actions: '동작',
    },
    actionsMenu: '게시판 제재 #{no} 동작',
    kind: {
        WARNING: '경고',
        RESTRICT_1: '쓰기 제한 1일',
        RESTRICT_3: '쓰기 제한 3일',
        RESTRICT_7: '쓰기 제한 7일',
    },
    reason: {
        SPAM: '스팸·광고',
        ABUSE: '비방·인신공격',
        INVESTMENT: '투자 권유',
        OFF_TOPIC: '게시판 주제와 맞지 않음',
        OTHER: '기타',
    },
    reasonLine: '{name} — {text}',
    role: {
        MANAGER: '매니저',
        STAFF: '스텝',
    },
    issuerValue: '{name} · {role}',
    issuerGone: '알 수 없는 회원',
    liftedBy: {
        MANAGER: '매니저',
        OPERATOR: '운영자',
    },
    liftedByValue: '{name}({who})',

    // 서랍
    drawerTitle: '게시판 제재 #{no} · {name}',
    drawerTitleEmpty: '게시판 제재',
    prev: '이전 게시판 제재',
    next: '다음 게시판 제재',
    detailFailed: '게시판 제재를 불러오지 못했어요',
    infoTitle: '제재 정보',
    period: '기간',
    trigger: '계기',
    triggerPost: '글 「{title}」',
    triggerComment: '「{title}」의 댓글',
    triggerUntitled: '지워진 글',
    triggerNone: '이어진 글·댓글 없음',
    acknowledged: '회원 확인',
    acknowledgedAt: '{at} 확인',
    notAcknowledged: '아직 확인하지 않았어요',
    reasonTitle: '사유',
    reasonTextNone: '덧붙인 글 없음',
    reasonSentNote: '회원에게 사유와 덧붙인 글이 그대로 전달됐어요. 처리자는 알리지 않아요.',
    liftedTitle: '해제',
    liftedLine: '{name} · {at} 해제',
    voidedTitle: '무효',
    voidedLine: '{at} · {source}',
    voidSource: {
        OPERATOR: '운영자가 무효로 함',
        REVIEW: '반복 검토에서 무효로 함',
        APPEAL: '이의제기 인용',
    },
    endedTitle: '끝난 시각',
    memberHistoryTitle: '{name} 님의 게시판 제재',
    historyEmpty: '받은 게시판 제재가 없어요.',
    needsManage: '해제·무효는 제재 관리 권한(sanction.manage)이 필요해요.',
    memberOnly: '이 회원의 게시판 제재만 보기',

    // 해제·무효
    lift: '해제',
    liftTitle: '쓰기 제한을 풀까요?',
    liftReasonLabel: '해제 사유',
    liftNotice: '회원은 이 게시판에서 바로 다시 글·댓글을 쓸 수 있어요. 이 제재는 권장 단계 계산과 반복 검토 수에 그대로 남아요.',
    lifted: '{name} 님의 쓰기 제한을 풀었어요.',
    void: '무효',
    voidTitle: '이 게시판 제재를 무효로 할까요?',
    voidReasonLabel: '무효 사유',
    voidNotice: '무효가 되면 권장 단계·반복 검토 수에서 빠지고, 진행 중인 쓰기 제한은 바로 풀려요. 되돌릴 수 없어요.',
    voided: '게시판 제재 #{no}를 무효로 했어요.',

    // 반복 검토
    review: {
        filterStatus: '상태',
        status: {
            OPEN: '열림',
            CLOSED: '닫힘',
        },
        col: {
            no: '번호',
            member: '회원',
            board: '게시판',
            recent: '7일 안',
            openedAt: '열린 시각',
            closedAt: '닫은 시각',
            result: '닫은 결과',
        },
        recentValue: '{n}건',
        multiBoard: '여러 게시판',
        multiBoardHint: '최근 7일 안에 {n}개 게시판에서 받았어요',
        sortOpen: '정렬: 열린 순(오래된 것이 위)',
        sortClosed: '정렬: 닫은 순(최근 것이 위)',
        loadFailed: '반복 검토 목록을 불러오지 못했어요',
        emptyOpen: '열린 반복 검토가 없어요',
        emptyOpenHint: '같은 회원이 같은 게시판에서 7일 안에 3건 이상 받으면 여기에 열려요.',
        emptyClosed: '닫힌 반복 검토가 없어요',
        result: {
            KEPT: '그대로 둠',
            SITE_SANCTIONED: '사이트 제재 부여',
            VOIDED: '게시판 제재 무효',
        },

        // 서랍
        drawerTitle: '반복 검토 #{no} · {name}',
        drawerTitleEmpty: '반복 검토',
        prev: '이전 검토',
        next: '다음 검토',
        detailFailed: '반복 검토를 불러오지 못했어요',
        why: '{board}에서 최근 7일 안에 {n}건을 받아 열렸어요.',
        boardSanctionsTitle: '{board}에서 받은 제재',
        inReview: '검토 대상',
        otherBoardsTitle: '다른 게시판',
        otherBoardsEmpty: '다른 게시판에서 받은 제재는 없어요.',
        otherBoardValue: '7일 안 {recent}건 · 전체 {total}건',
        activeUntil: '{at}까지 쓰기 제한',
        siteTitle: '사이트 제재 현황',
        openSiteSanctions: '사이트 제재 화면에서 보기',

        // 닫기
        closeTitle: '검토 닫기',
        closeHelp: '셋 중 하나로 닫아요. 닫으면 이 검토가 센 제재는 다음 검토에서 다시 세지 않아요.',
        choice: {
            KEPT: '그대로 둠',
            SITE_SANCTIONED: '사이트 제재 부여',
            VOIDED: '게시판 제재 무효',
        },
        choiceHelp: {
            KEPT: '처리자의 판단을 그대로 두고 닫아요.',
            SITE_SANCTIONED: '사이트 제재 대화창을 열어요. 부여하면 그 제재 번호와 함께 닫혀요.',
            VOIDED: '아래에서 고른 게시판 제재를 무효로 하고 닫아요(처리자 남용 바로잡기).',
        },
        voidPick: '무효로 할 제재',
        voidPickCount: '위 목록에서 무효로 할 제재를 골라 주세요 — {n}/{max}건 골랐어요.',
        voidPickRequired: '무효로 할 제재를 하나 이상 골라 주세요.',
        voidPickNone: '무효로 할 수 있는 제재가 없어요.',
        memoLabel: '메모(선택)',
        memoHelp: '운영자끼리만 봐요. 회원·매니저에게는 가지 않아요.',
        memoPlaceholder: '예: 처음 반복이라 지켜보기로 했어요.',
        memoTooLong: '메모는 {max}자까지 쓸 수 있어요.',
        submit: {
            KEPT: '그대로 두고 닫기',
            SITE_SANCTIONED: '사이트 제재 주기',
            VOIDED: '무효로 하고 닫기',
        },
        closedToast: '반복 검토 #{no}를 닫았어요 — {result}.',
        closedNote: '이미 닫혔어요 — 다른 운영자가 먼저 닫았어요.',
        reload: '다시 불러오기',
        siteIssuedCloseFailed: '사이트 제재 #{no}는 줬지만 검토를 닫지 못했어요. {reason}',
        readOnly: '조회만 할 수 있어요 — 검토를 닫으려면 제재 관리(sanction.manage) 권한이 필요해요.',

        // 닫은 결과
        resultTitle: '닫은 결과',
        closedByLine: '{name} · {at}',
        siteSanctionLink: '사이트 제재 #{no} 보기',
        voidedCount: '게시판 제재 {n}건을 무효로 했어요',
        memoNone: '메모 없음',
    },

    // 회원 상세
    memberSection: {
        title: '게시판 제재',
        summary: '무효 아닌 {total}건 · 진행 중 쓰기 제한 {active}건',
        openReviews: '열린 반복 검토 {n}건',
        boardLine: '경고 {w} · 쓰기 제한 {r}',
        boardsTitle: '게시판별',
        recentTitle: '최근 게시판 제재',
        viewAll: '게시판 제재 이력 보기',
        empty: '받은 게시판 제재가 없어요.',
        notReady: '게시판 제재 요약을 아직 받아 오지 못했어요.',
    },

    // 이의제기 종류 배지
    appealKind: {
        SITE: '사이트 제재',
        BOARD: '게시판 제재',
    },
};
