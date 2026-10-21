/**
 * 역할 관리 (hamonica3 의 「역할 관리」를 옮김). 키는 `role.무엇` 두 마디. `en/role.ts` 와 같은 키, 같은 차례.
 * 영역·세부 유형·권한 이름은 `permission.*` (`ko/permission.ts`).
 * 용어: **화면 종류 = 운영자 역할 / 회원 역할 / 등급 역할(메뉴 하위 항목)**, **영역 = 운영자 역할의 대분류(탭)**, **세부 유형 = 영역 아래의 24개 분류(역할 하나가 속하는 유형, 권한 후보를 정한다)**, **유형(회원) = 등급·회원 유형·게시판 단위·사용자 정의**. 섞어 쓰지 않는다.
 */
export default {
    // 화면 종류 — 메뉴 「역할 관리」의 하위 항목(`/settings/roles/<종류>`)마다 하나
    kindTitle: {
        operator: '운영자 역할',
        member: '회원 역할',
        grade: '등급 역할',
    },
    kindSubtitle: {
        operator: '관리자 앱에서 운영자가 무엇을 할 수 있는지 정합니다. 운영자는 역할을 여러 개 받을 수 있어요.',
        member: '공개 사이트에서 회원이 무엇을 할 수 있는지 정합니다. 회원은 역할을 여러 개 받을 수 있어요.',
        grade: '회원 등급마다 무엇을 할 수 있는지 정합니다. 등급과 승급 조건은 등급 설정에서 정해요.',
    },

    // 탭
    tabsLabel: '역할 구분',
    typeBuiltin: {
        memberGrade: '등급',
        memberKind: '회원 유형',
        memberBoard: '게시판 단위',
        memberCustom: '사용자 정의',
    },
    operatorTabNote: '역할은 세부 유형 하나에 속해요. 유형을 고르면 그 유형의 권한만 줄 수 있어요. 한 운영자에게 역할을 여러 개 줄 수 있어요.',
    memberAllTabNote: '회원 유형 · 게시판 단위 · 사용자 정의 역할을 한 목록에 모아 보여요. 역할을 고르면 평소처럼 권한을 볼 수 있어요.',
    typeNoDescription: '설명이 없어요.',
    typeEmpty: '아직 역할이 없어요',
    typeEmptyHint: '「역할 추가」로 첫 역할을 만드세요.',
    sectionEmpty: '이 유형에는 아직 역할이 없어요',
    sectionAdd: '{type}에 역할 추가',
    derivedNote: '등급 역할은 등급 설정에서 만들어요. 여기서는 권한만 고쳐요.',
    derivedLink: '등급 설정으로 가기',
    fixedNote: '이 유형의 역할은 제품이 정해 두었어요. 여기서는 권한만 고쳐요.',
    addRole: '역할 추가',
    addMemberTip: '사용자 정의 회원 역할을 만들어요',

    // 목록
    listTitle: '역할',
    loadFailed: '역할 목록을 불러오지 못했어요',
    builtIn: '제품이 제공하는 역할이에요',
    builtInTag: '제공 역할',
    permCountLabel: '권한 {count}개',

    // 고른 역할
    detailTitle: '역할 정보',
    pick: '역할을 선택하세요',
    pickHint: '왼쪽에서 역할을 선택하면 가진 권한이 나와요.',
    pickForMenus: '역할을 선택하면 그 역할로 보이는 메뉴가 나와요.',
    noDescription: '설명이 없어요.',
    operatorCount: '이 역할을 받은 운영자 {count}명',
    generalNote: '종합 관리 역할은 유형 제한 없이 여러 영역의 권한을 함께 가져요. 최고 관리자만 만들고 고칠 수 있어요.',
    generalNoteSuper: '종합 관리 역할은 유형 제한 없이 여러 영역의 권한을 함께 가져요. 최고 관리자인 내가 만들고 고칠 수 있어요.',
    granted: '가진 권한',
    grantedCount: '{count}개',
    grantedNone: '아직 준 권한이 없어요',
    grantedNoneHint: '「수정」에서 이 역할이 무엇을 할 수 있는지 정하세요.',
    generalSuperOnly: '종합 관리 역할은 최고 관리자만 고치고 지울 수 있어요',
    deleteBuiltinTip: '제품이 제공하는 역할은 지울 수 없어요',
    deleteTitle: '역할을 지울까요?',
    deleteMessage: '「{name}」을 지워요. 되돌릴 수 없어요. 이 역할을 쓰는 운영자나 회원이 있으면 지울 수 없어요.',
    deleted: '역할을 지웠어요',
    deleteFailed: '역할을 지우지 못했어요',

    // 이 역할이 보는 메뉴
    menuTitle: '이 역할이 보는 메뉴',
    menuBasis: '이 역할이 가진 권한 기준이에요. 「수정」에서 권한을 고치면 바뀌어요.',
    menuKeyNote: '열쇠가 붙은 메뉴는 이 역할의 권한 때문에 보여요. 나머지는 로그인만 하면 누구나 봐요.',
    menuNeeds: '{permission} 권한이 있어야 보여요',
    menuNone: '권한이 걸린 메뉴가 없어요.',
    menuNoneHint: '권한을 하나도 주지 않으면 권한이 걸린 메뉴는 모두 사라져요.',
    menuCommon: '공통',
    menuDraftTitle: '저장하면 보이는 메뉴',

    // 역할 추가·수정
    dialogCreate: '역할 추가',
    dialogEdit: '역할 수정',
    fieldRoleType: '역할 유형',
    fieldId: '역할 아이디',
    idHelp: '만든 뒤에는 바꿀 수 없어요. 앞의 ROLE_ 는 자동으로 붙어요.',
    idPlaceholder: 'ANALYST',
    idRequired: '역할 아이디를 입력해 주세요.',
    idTooLong: '역할 아이디는 {max}자까지예요(ROLE_ 포함).',
    fieldName: '역할 이름',
    namePlaceholder: '예: 배치 운영',
    nameRequired: '역할 이름을 입력해 주세요.',
    nameTooLong: '역할 이름은 {max}자까지예요.',
    fieldDescription: '설명',
    descTooLong: '설명은 {max}자까지예요.',
    fieldArea: '영역',
    areaPlaceholder: '영역을 고르세요',
    areaRequired: '영역을 골라 주세요.',
    fieldType: '세부 유형',
    typePlaceholder: '세부 유형을 고르세요',
    typeRequired: '세부 유형을 골라 주세요.',
    superOnly: '최고 관리자만',
    typeHelpCreate: '이 역할이 속할 세부 유형이에요. 유형을 고르면 그 유형의 권한만 아래에 나와요. 만든 뒤에는 바꿀 수 없어요.',
    typeGeneralHint: '종합 관리는 유형 제한 없이 모든 권한 중에서 고르고, 최고 관리자만 만들고 고칠 수 있어요. 만든 뒤에는 유형을 바꿀 수 없어요.',

    // 권한 고르기
    permTitle: '권한',
    permCount: '{picked}/{total} 선택',
    permPickType: '세부 유형을 고르면 고를 수 있는 권한이 나와요.',
    permNoGrant: '권한을 주려면 「역할 변경(권한 상승)」 권한이 필요해요.',
    permLater: '지금 고르지 않아도 돼요 — 만든 뒤 「수정」에서 언제든 고쳐요.',
    pickAll: '전체 선택',
    unpickAll: '전체 해제',
    pickAllOf: '{unit} 전체 선택',
    unpickAllOf: '{unit} 전체 해제',
    outsideOfMine: '내 권한 밖이에요',
    permPreparing: '준비 중',

    // 회원 역할
    memberBuiltinLocked: '제품이 제공하는 회원 역할이라 이름·설명은 고칠 수 없어요. 권한만 고칠 수 있어요.',
    memberNoMenu: '회원 역할은 관리자 앱 메뉴를 보지 않아요. 공개 사이트에서 할 수 있는 일을 정해요.',
    memberDoesTitle: '이 역할이 하는 일',
    memberCount: '이 역할을 가진 회원 {count}명',
    promoTitle: '승급 조건',
    promoNote: '이 등급이 되는 조건이에요. 여기서는 보기만 해요.',
    promoLink: '등급 설정에서 바꿔요',
    promoNoPermission: '승급 조건은 등급 설정을 볼 수 있는 권한이 있어야 볼 수 있어요.',
    promoLoadFailed: '승급 조건을 불러오지 못했어요',
    metaGrantMode: '부여 방식',
    metaScope: '범위',
    grantMode: {
        AUTO: '자동',
        APPOINTED: '지정',
        MANUAL: '수동',
    },
    grantModeHint: {
        AUTO: '활동 기준·가입에 따라 자동으로 받아요',
        APPOINTED: '게시판마다 지정해요',
        MANUAL: '운영자가 회원에게 직접 줘요',
    },
    scope: {
        SITE: '사이트 전체',
        BOARD: '게시판 하나',
    },
    boardLimitsTitle: '기본 한도 (읽기 전용)',
    boardLimitPostActions: '글 숨기기·삭제 하루 {count}건',
    boardLimitCommentDeletes: '댓글 삭제 하루 {count}건',
    boardLimitSanctions: '게시판 제재 하루 {count}건',
    boardLimitHeadcount: '게시판당 매니저 {managers}명 · 스텝 최대 {staff}명',
    boardLimitsNote: '게시판 기능에서 정해요. 지금은 community 기본값을 안내로만 보여 줘요.',

    // 역할 결과
    added: '역할을 추가했어요',
    updated: '역할을 수정했어요',
    createdButPermFailed: '역할은 만들었지만 권한을 붙이지 못했어요',
    createdButPermFailedTail: '「수정」에서 다시 골라 주세요.',
};
