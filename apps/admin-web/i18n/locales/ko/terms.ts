/**
 * 약관 관리의 문구 — `/settings/terms`(이용약관·개인정보처리방침 초안·게시·이력, 가입·로그인 화면 약관 링크 옵션). 종류 이름은 공통 `common.terms`·`common.privacy`.
 * 서버 거절 문구(본문 비었음·시행일 규칙·초안 없음 등)는 서버가 주는 값이라 여기에 없다. `en/terms.ts` 와 같은 키, 같은 차례.
 */
export default {
    // ── 화면 ──
    title: '약관',
    subtitle: '이용약관과 개인정보처리방침을 쓰고 버전으로 게시해요. 게시하면 공개 사이트에 변경 안내가 보여요.',
    readOnly: '보기 권한만 있어요. 초안을 고치거나 게시하려면 약관 관리 권한이 필요해요.',
    loadFailed: '약관 정보를 불러오지 못했어요',
    detailLoadFailed: '초안과 이력을 불러오지 못했어요',

    // ── 링크 옵션 ──
    linksTitle: '약관 보기 링크',
    linksLabel: '가입·로그인 화면에 약관 보기 링크 표시',
    linksHelp: '끄면 가입 약관 동의의 「보기」와 로그인 카드 아래 약관 링크를 모두 숨겨요. 동의 체크는 늘 필수예요. 게시본이 없는 약관의 링크는 빈 안내 화면으로 가요.',
    linksOn: '약관 보기 링크를 켰어요',
    linksOff: '약관 보기 링크를 껐어요',
    linksFailed: '링크 설정을 바꾸지 못했어요. {reason}',

    // ── 종류·상태 ──
    kindTabs: '약관 종류',
    statusTitle: '상태',
    statusCurrent: '지금 약관',
    statusUpcoming: '시행 예정',
    statusNone: '아직 게시된 버전이 없어요',
    statusNoUpcoming: '없음',
    versionLine: '버전 {version} · 시행 {date}',
    versionNo: 'v{version}',

    // ── 초안 ──
    draftTitle: '초안',
    bodyLabel: '{name} 본문',
    bodyPlaceholder: '약관 본문을 써 주세요',
    bodyHelp: '저장한 초안을 [게시…]로 새 버전으로 내보내요. 게시한 버전은 고칠 수 없어요.',
    bodyRequired: '본문을 입력해 주세요.',
    unsavedHint: '저장하지 않은 변경이 있어요. 게시하려면 먼저 초안을 저장해 주세요.',
    draftNone: '저장한 초안 없음',
    draftSavedAt: '초안 저장 {time} · {name}',
    draftSave: '초안 저장',
    draftSaved: '초안을 저장했어요',
    draftDelete: '초안 삭제',
    draftDeleteTitle: '초안을 지울까요?',
    draftDeleteMessage: '{name} 초안을 지워요. 게시한 버전은 그대로예요.',
    draftDeleted: '초안을 지웠어요',
    unknownOperator: '알 수 없음',

    // ── 게시 ──
    publishOpen: '게시…',
    publishTitle: '{name} 게시',
    publishImmutable: '게시한 버전은 고칠 수 없어요. 바꾸려면 새 버전을 게시해요.',
    effectiveDate: '시행일',
    effectiveDateHelp: '사이트 시간대 기준이에요. 오늘 이후이고 마지막 게시본의 시행일 이후여야 해요(같은 날 가능).',
    effectiveDateRequired: '시행일을 골라 주세요.',
    effectiveDateTooEarly: '고를 수 없는 날이에요. 오늘과 마지막 게시본의 시행일 이후로 골라 주세요.',
    changeNote: '변경 요지',
    changeNoteHint: '선택 · {count}/{max}자',
    changeNoteHelp: '공개 사이트의 변경 안내에 보여요.',
    changeNoteTooLong: '{max}자 이하로 줄여 주세요.',
    publishSubmit: '게시',
    published: '{name} 버전 {version}을 게시했어요 · 시행 {date}',

    // ── 이력 ──
    historyTitle: '게시 이력',
    historyEmpty: '게시한 버전이 없어요',
    historyEmptyHint: '초안을 저장하고 게시하면 여기에 쌓여요.',
    colVersion: '버전',
    colPublishedAt: '게시 시각',
    colPublishedBy: '게시자',
    colStatus: '상태',
    status: {
        CURRENT: '지금',
        UPCOMING: '예정',
        PAST: '지난',
    },
    view: '보기',

    // ── 본문 보기 ──
    versionTitle: '{name} 버전 {version}',
    versionLoadFailed: '본문을 불러오지 못했어요',
    copyToDraft: '이 본문으로 초안 만들기',
    copyTitle: '초안을 바꿀까요?',
    copyMessage: '지금 편집기의 초안을 버전 {version} 본문으로 바꿔요. [초안 저장]을 눌러야 남아요.',
    copyConfirm: '바꾸기',
    copied: '버전 {version} 본문을 편집기에 넣었어요. [초안 저장]을 눌러야 남아요.',

    // ── 떠날 때 ──
    leaveTitle: '저장하지 않은 초안이 있어요',
    leaveMessage: '저장하지 않은 변경은 사라져요. 계속할까요?',
    leaveConfirm: '버리고 이동',
};
