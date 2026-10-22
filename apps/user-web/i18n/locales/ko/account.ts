/**
 * 계정·로그인 연결 화면(`/settings/account`)의 문구 — 로그인 수단(비밀번호 유무)과 소셜 계정 연결·해제, 연결에서 돌아온 결과 안내.
 * 실패 사유(`reason=`) 문구는 로그인 화면과 같이 쓰는 `login.reason*`. 사용자 메뉴의 항목 글도 `account.title` 이다.
 * 키는 `account.무엇` 두 마디. `en/account.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '계정·로그인 연결',
    seoDescription: '{site} 계정의 로그인 수단과 연결한 소셜 계정을 관리해요.',
    loading: '연결 정보를 불러오는 중',
    loadFailed: '연결 정보를 불러오지 못했어요',

    // 로그인 수단
    methodsTitle: '로그인 수단',
    passwordRow: '이메일·비밀번호',
    passwordOn: '이메일과 비밀번호로 로그인할 수 있어요.',
    passwordOff: '비밀번호가 없는 계정이에요. 연결한 소셜 계정으로만 로그인해요.',

    // 소셜 계정 연결
    socialTitle: '소셜 계정 연결',
    linked: '연결됨',
    notLinked: '연결 안 됨',
    unavailable: '지금은 연결할 수 없어요',
    lastMethod: '다른 로그인 수단이 없어 해제할 수 없어요',
    lastMethodHelp: '다른 소셜 계정을 먼저 연결하면 해제할 수 있어요.',
    link: '연결',
    unlink: '해제',
    linkLabel: '{provider} 연결',
    unlinkLabel: '{provider} 연결 해제',
    unlinkTitle: '{provider} 연결을 해제할까요?',
    unlinkMessage: '해제하면 이 소셜 계정으로는 로그인할 수 없어요. 나중에 다시 연결할 수 있어요.',
    unlinked: '{provider} 연결을 해제했어요',
    linkStartFailed: '연결을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.',

    // 연결에서 돌아온 결과
    linkedToast: '{provider} 계정을 연결했어요',
    linkTakenTitle: '연결하지 못했어요.',
    linkTaken: '이 {provider} 계정은 이미 다른 회원 계정에 연결돼 있어요.',
    linkFailedTitle: '{provider} 연결을 마치지 못했어요.',
};
