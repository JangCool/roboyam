/**
 * 운영자 회원 계정 — 운영자(관리자 앱 계정)마다 사이트 회원 계정 하나를 잇는 기능. 키는 `operatorMember.무엇`(까닭·힘은 `operatorMember.ineligible.*`·`paused.*`·`power.*`). `en/operatorMember.ts` 와 같은 키, 같은 차례.
 * 화면에서는 「사이트 계정」이라 부른다(운영자 화면은 "회원"이라는 말을 줄인다 — `operator.ts` 머리말). 회원 관리 화면 쪽 표시는 「운영자 회원 계정」.
 */
export default {
    tab: '사이트 계정',
    sectionTitle: '사이트 계정',
    intro: '이 운영자가 사이트에서 쓰는 회원 계정 하나를 연결해요.',
    introPowers: '연결된 계정은 모든 로보티콘 팩을 쓰고, 닉네임 옆에 「운영자」 표시가 붙고, 모든 게시판을 이 운영자의 관리자 권한만큼 관리해요.',
    introStop: '운영자를 사용 중지하거나 잠그거나 기간이 끝나면 바로 멈춰요.',
    loadFailed: '사이트 계정 정보를 불러오지 못했어요',
    readOnly: '사이트 계정 연결을 바꿀 권한이 없어 읽기만 할 수 있어요.',
    superOnlySelf: '최고 관리자의 사이트 계정은 본인만 바꿀 수 있어요.',

    // 연결 안 됨 — 같은 이메일로 가입한 회원
    candidateTitle: '이 운영자 이메일({email})로 가입한 회원',
    candidateNone: '같은 이메일로 가입한 회원 계정이 없어요',
    candidateNoneHint: '운영자가 사이트에서 같은 이메일로 가입한 뒤 연결하세요.',
    fieldNickname: '닉네임',
    fieldGrade: '등급',
    fieldJoinedAt: '가입일',
    fieldBoardRoles: '맡은 게시판',
    boardRoles: '매니저 {manager} · 스텝 {staff}',
    fieldUserId: '회원 ID',
    fieldLinkedAt: '연결 시각',
    fieldLinkedBy: '연결한 운영자',
    ineligibleTitle: '지금은 연결할 수 없어요',
    ineligible: {
        EMAIL_NOT_VERIFIED: '이메일 인증을 마치지 않은 회원이에요.',
        MEMBER_RESTRICTED: '정지 중이거나 탈퇴를 진행 중인 회원이에요.',
        BUSINESS_ACCOUNT: '사업자 계정은 연결할 수 없어요.',
        LINKED_TO_OTHER: '이미 다른 운영자 「{name}」에 연결된 회원이에요.',
    },
    link: '연결',
    linkLabel: '{nickname} 계정 연결',
    linkTitle: '이 회원 계정을 연결할까요?',
    linkMessage: '「{nickname}」 계정이 이 운영자의 사이트 계정이 돼요.',
    linkMessageBoards: '「{nickname}」 계정이 이 운영자의 사이트 계정이 돼요. 맡고 있던 게시판 매니저·스텝 지정 {count}개는 모두 끝나요.',
    linked: '사이트 계정을 연결했어요',

    // 연결됨
    stateActive: '사이트에서 쓰는 중',
    statePaused: '멈춤',
    pausedTitle: '멈춘 까닭',
    paused: {
        OPERATOR_UNAVAILABLE: '운영자 계정을 쓸 수 없어요(승인 안 됨·잠김·만료·사용 중지·기간 밖).',
        MEMBER_SUSPENDED: '회원 계정이 정지 중이에요.',
    },
    openMember: '회원 관리에서 보기',
    unlink: '연결 해제',
    unlinkTitle: '연결을 해제할까요?',
    unlinkMessage: '「{nickname}」 계정은 바로 일반 회원이 돼요. 모든 로보티콘 팩·운영자 표시·게시판 관리를 더는 쓸 수 없어요.',
    unlinked: '사이트 계정 연결을 해제했어요',

    // 힘 목록
    powersTitle: '연결된 계정이 할 수 있는 일',
    powersPreview: '연결하면 이렇게 쓸 수 있어요',
    powersPausedNote: '지금은 멈춤이라 아래 일을 할 수 없어요. 까닭이 풀리면 바로 돌아와요.',
    power: {
        ROBOTICON_ALL: '모든 로보티콘 팩',
        OPERATOR_BADGE: '닉네임 옆 「운영자」 표시',
        POST_MODERATE: '글 숨기기·삭제',
        COMMENT_DELETE: '댓글 삭제',
        AUTHOR_SANCTION: '작성자 제재',
        ACTION_READ: '처리 내역 보기',
        ACTION_RESTORE: '처리 되돌리기',
    },
    powerGranted: '됨',
    powerDenied: '안 됨',
    powerNeeds: '필요한 권한: {permission}',
    powerAlways: '권한과 상관없이 늘',
    dailyLimitNone: '게시판 처리에 하루 한도가 없어요.',
    powerScopeNote: '공지·고정·소모임 글과 다른 운영자 사이트 계정의 글·댓글은 처리할 수 없어요.',

    // 운영자 목록 이름 옆 딱지
    listTag: '사이트 계정',
    listTagTitle: '사이트 계정: {nickname}',
    listTagPausedTitle: '사이트 계정(멈춤): {nickname}',

    // 회원 상세
    memberTag: '운영자 회원 계정 · {name}',
    memberTagPaused: '멈춤',
    memberNote: '운영자 계정에 연결된 회원 계정이에요. 연결된 동안에는 탈퇴할 수 없어요.',
};
