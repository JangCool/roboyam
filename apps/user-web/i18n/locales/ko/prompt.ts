/**
 * 비회원이 쓰기를 시도했을 때 뜨는 로그인 안내 시트와 댓글 지연 노출 안내 문구 — `components/shell/LoginPromptSheet.vue`·`utils/util-comment-delay-messages.ts` 가 쓴다.
 * 키는 `prompt.무엇` 두 마디 — `{종류}Title`·`{종류}Description` 의 종류는 `LoginPromptAction`(`composables/auth/useLoginPrompt.ts`)과 같다. `en/prompt.ts` 와 같은 키, 같은 차례.
 * ⚠ 문구 안의 `@`·`|` 는 글자가 아니다(`{'@'}`·`{'|'}` 로 감싼다).
 */
export default {
    // 시트 공통
    later: '나중에',
    ageNotice: '만 14세 이상만 가입할 수 있어요',

    // 종류별 제목·설명
    writeTitle: '로그인하면 글을 쓸 수 있어요',
    writeDescription: '비회원은 읽기만 할 수 있어요. 로그인하거나 가입해서 글을 남겨 보세요.',
    commentTitle: '로그인하면 댓글을 쓸 수 있어요',
    commentDescription: '댓글은 로그인한 회원만 쓸 수 있어요. 읽기는 로그인 없이도 돼요.',
    replyTitle: '로그인하면 답글을 쓸 수 있어요',
    replyDescription: '답글은 로그인한 회원만 쓸 수 있어요. 읽기는 로그인 없이도 돼요.',
    opinionTitle: '로그인하면 의견을 쓸 수 있어요',
    opinionDescription: '찬성·반대 의견은 로그인한 회원만 쓸 수 있어요. 읽기는 로그인 없이도 돼요.',
    likeTitle: '로그인하면 추천할 수 있어요',
    likeDescription: '추천은 로그인한 회원만 할 수 있어요. 읽기는 로그인 없이도 돼요.',
    voteTitle: '로그인하면 투표할 수 있어요',
    voteDescription: '투표는 로그인한 회원만 할 수 있어요. 읽기는 로그인 없이도 돼요.',
    scrapTitle: '로그인하면 스크랩할 수 있어요',
    scrapDescription: '스크랩한 글은 내 정보의 스크랩에서 나만 볼 수 있어요.',
    blockTitle: '로그인하면 차단할 수 있어요',
    blockDescription: '회원 차단은 로그인한 회원만 할 수 있어요. 상대에게는 알리지 않아요.',
    memoTitle: '로그인하면 메모를 남길 수 있어요',
    memoDescription: '이용자 메모는 로그인한 회원만 쓸 수 있어요. 메모는 나에게만 보여요.',
    // 한도 수치(7일·20통·5명)는 시안 문구 그대로다 — 비회원은 서버 설정값을 읽을 수 없다
    dmTitle: '로그인하면 쪽지를 보낼 수 있어요',
    dmDescription: '쪽지는 가입 7일 후부터 보낼 수 있고, 하루 20통·새 상대 5명까지예요.',
    reportTitle: '로그인하면 신고할 수 있어요',
    reportDescription: '신고는 로그인한 회원만 할 수 있어요. 읽기는 로그인 없이도 돼요.',
    dealEndTitle: '로그인하면 종료를 알릴 수 있어요',
    dealEndDescription: '품절·가격 변동 알림은 일반 등급 이상의 로그인한 회원만 할 수 있어요. 읽기는 로그인 없이도 돼요.',
    // 댓글 지연 노출 — 비회원에게는 시간 숫자(N분)를 알리지 않는다
    viewTitle: '로그인하면 바로 볼 수 있어요',
    viewDescription: '방금 쓴 댓글은 로그인하지 않은 방문자에게 조금 늦게 보여요. 로그인하면 지금 달린 댓글도 바로 읽을 수 있어요.',

    // 가려진 댓글 본문 자리
    delayPlaceholder: '로그인하면 바로 볼 수 있어요',
    delayPlaceholderLabel: '{name}님의 댓글 — 로그인하면 바로 볼 수 있어요',
};
