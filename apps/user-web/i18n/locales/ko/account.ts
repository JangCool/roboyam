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

    // 로그인 정보 — 이메일·인증 상태·비밀번호 유무·이메일/비밀번호 변경으로 가는 줄
    methodsTitle: '로그인 정보',
    emailRow: '이메일',
    noEmail: '등록된 이메일이 없어요',
    verified: '인증됨',
    unverified: '미인증',
    resendDone: '인증 메일을 다시 보냈어요',
    resendWait: '{time} 뒤에 다시 보낼 수 있어요',
    checkSpam: '메일이 안 보이면 스팸함도 확인해 주세요.',
    emailChange: '이메일 변경',
    emailChangeHint: '새 이메일로 인증 메일을 보내요',
    passwordChange: '비밀번호 변경',
    passwordChangeHint: '이메일로 가입한 계정만 쓸 수 있어요',
    noEmailChangeReason: '소셜 로그인으로 가입한 계정은 이메일을 바꿀 수 없어요',
    noPasswordReason: '소셜 로그인으로 가입한 계정은 쓸 수 없어요',
    notAvailable: '사용 불가',
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

    // 이메일 변경(`/settings/account/email`) — 비밀번호 변경 화면과 같이 쓰는 말 포함
    backToAccount: '계정으로 돌아가기',
    accountLoading: '계정 정보를 불러오는 중',
    accountLoadFailed: '계정 정보를 불러오지 못했어요',
    emailSeoDescription: '{site} 로그인 이메일을 바꿔요.',
    currentEmail: '현재 이메일',
    newEmail: '새 이메일',
    newEmailHelp: '새 이메일로 인증 메일을 보내요. 메일의 링크를 눌러야 변경이 끝나요.',
    passwordHelp: '본인 확인을 위해 현재 비밀번호를 한 번 더 입력해요',
    resendPasswordHelp: '다시 보내려면 본인 확인을 위해 현재 비밀번호를 한 번 더 입력해요',
    emailSend: '인증 메일 보내기',
    emailResend: '인증 메일 다시 보내기',
    sending: '보내는 중…',
    sendFailed: '인증 메일을 보내지 못했어요.',
    sentTitle: '인증 메일을 보냈어요',
    sentText: '메일의 링크를 누르면 변경이 끝나요. 그때까지는 지금 이메일로 로그인해요.',
    wrongEmailNote: '이메일을 잘못 적었다면 처음부터 다시 입력해 주세요.',
    retype: '다른 이메일로 다시 입력하기',
    pendingNote: '{email} 주소로 보낸 변경 요청이 아직 끝나지 않았어요. 새로 보내면 이전 링크는 쓸 수 없게 돼요.',
};
