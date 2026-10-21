/**
 * 조직 구성(팀)의 문구 — `/settings/teams`. 팀은 이름·설명·사용 여부·정렬 순서를 갖고, 팀원은 관리자 운영자 계정이다. 알림 이벤트의 「추가 수신 팀」으로 쓴다.
 * 서버 거절 문구(이름 중복 · 팀원이 있는 팀 삭제 등)는 서버가 주는 값이라 여기에 없다. 키는 `team.무엇` 두 마디. `en/team.ts` 와 같은 키, 같은 차례.
 */
export default {
    // ── 화면 ──
    title: '조직 구성',
    subtitle: '팀을 만들고 팀원을 정해요. 알림 이벤트의 「추가 수신 팀」으로 쓸 수 있어요.',
    reload: '다시 조회',
    loadFailed: '조직 구성을 불러오지 못했어요',
    emptyTitle: '아직 팀이 없어요',
    emptyBody: '팀을 만들고 운영자를 팀원으로 넣어 두면, 알림을 팀 단위로 받을 수 있어요.',
    readOnly: '조직 구성을 바꿀 권한이 없어 보기만 할 수 있어요.',

    // ── 팀 목록 ──
    listTitle: '팀 {n}개',
    newTeam: '새 팀',
    searchTeam: '팀 이름·설명 찾기',
    noMatch: '조건에 맞는 팀이 없어요.',
    memberCount: '팀원 {n}명',
    stopped: '사용 안 함',
    moveUp: '{name} 위로',
    moveDown: '{name} 아래로',
    pickOneTitle: '팀을 골라 주세요',
    pickOneBody: '왼쪽 목록에서 팀을 고르면 팀 정보와 팀원을 볼 수 있어요.',

    // ── 팀 정보 ──
    infoTitle: '팀 정보',
    fieldName: '팀 이름',
    fieldDescription: '설명',
    fieldStatus: '사용',
    nameHint: '최대 {max}자',
    descHint: '선택, 최대 {max}자',
    statusOnHint: '알림의 추가 수신 팀으로 고를 수 있어요.',
    statusOffHint: '멈춘 팀은 알림을 받지 않고, 새로 고를 수도 없어요.',
    deleteBlocked: '팀원이 있는 팀은 지울 수 없어요. 대신 「사용 안 함」으로 멈춰 주세요.',
    nameRequired: '팀 이름을 입력해 주세요.',
    nameTooLong: '팀 이름은 {max}자 이하로 입력해 주세요.',
    descTooLong: '설명은 {max}자 이하로 입력해 주세요.',
    saved: '팀 정보를 저장했어요',
    deleteTitle: '팀을 지울까요?',
    deleteConfirm: '「{name}」 팀을 지워요. 되돌릴 수 없어요.',
    deleteConfirmUsed: '「{name}」 팀을 지워요. 이 팀을 추가 수신 팀으로 고른 알림 이벤트 {n}개에서도 빠져요. 되돌릴 수 없어요.',
    deleted: '팀을 지웠어요',

    // ── 새 팀 ──
    dialogCreate: '새 팀 만들기',
    create: '만들기',
    created: '팀을 만들었어요',

    // ── 팀원 ──
    membersTitle: '팀원 {n}명',
    addMember: '팀원 추가',
    searchMember: '이름·이메일로 찾기',
    noMatchMember: '조건에 맞는 팀원이 없어요.',
    noMembersTitle: '아직 팀원이 없어요',
    noMembersBody: '[팀원 추가]로 운영자를 팀원으로 넣어 보세요.',
    removeMember: '{name} 팀원에서 빼기',
    removeTitle: '팀에서 뺄까요?',
    removeConfirm: '{name} 님을 「{team}」 팀에서 빼요. 운영자 계정은 그대로 남아요.',
    remove: '빼기',
    memberRemoved: '{name} 님을 팀에서 뺐어요',
    memberDialogTitle: '「{name}」 팀원 추가',
    searchOperator: '운영자 이름·이메일로 찾기',
    alreadyMember: '이미 팀원',
    noMatchOperator: '조건에 맞는 운영자가 없어요.',
    noCandidates: '팀원으로 넣을 수 있는 운영자가 없어요.',
    chosenCount: '{n}명 선택',
    addChosen: '선택한 운영자 추가',
    candidatesLoadFailed: '운영자 목록을 불러오지 못했어요',
    membersAdded: '{n}명을 팀원으로 넣었어요',

    // ── 저장하지 않고 넘어갈 때 ──
    leaveTitle: '저장하지 않은 변경이 있어요',
    leaveMessage: '저장하지 않고 넘어가면 바꾼 내용이 사라져요.',
    leaveConfirm: '넘어가기',
    leaveCancel: '계속 편집',
};
