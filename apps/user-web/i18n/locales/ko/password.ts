/**
 * 비밀번호 화면의 문구 — 비밀번호 찾기(`/password/forgot`)·재설정(`/password/reset`)·소셜 가입자 안내(`/password/social`)·비밀번호 변경(`/settings/account/password`),
 * 그리고 가입·재설정·변경이 같이 쓰는 「비밀번호 확인」 칸. 원본(community) 각 화면과 `utils/util-account-messages.ts` 의 글을 옮겼다.
 * 비밀번호 입력칸 자체(보기/숨기기·규칙 줄)는 `login.*`. 키는 `password.무엇` 두 마디. `en/password.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 비밀번호 확인 칸(가입·재설정)
    confirmLabel: '비밀번호 확인',
    mismatch: '비밀번호가 서로 달라요.',
    match: '비밀번호가 같아요.',

    // 비밀번호 찾기
    forgotSeoDescription: '가입한 이메일로 비밀번호 재설정 링크를 받아요.',
    forgotTitle: '가입한 이메일을 입력해 주세요',
    forgotSubtitle: '비밀번호를 다시 정할 수 있는 링크를 메일로 보내 드려요.',
    forgotSubmit: '재설정 메일 받기',
    sending: '보내는 중…',
    backToLogin: '로그인으로 돌아가기',
    socialNote: '카카오·네이버·구글로 가입했다면 비밀번호가 없어요. 해당 소셜 계정으로 로그인해 주세요.',
    sentTitle: '재설정 메일을 보냈어요',
    sentText: '입력한 주소로 가입한 계정이 있다면 비밀번호 재설정 링크를 보냈어요. 메일이 오지 않으면 스팸함을 확인해 주세요.',
    resend: '다시 보내기',
    resendCooling: '다시 보내기 ({time} 후 가능)',
    sameNotice: '가입 여부를 알 수 없도록 항상 같은 안내를 보여요. 링크는 1시간 동안 쓸 수 있어요.',

    // 비밀번호 재설정
    resetSeoTitle: '비밀번호 재설정',
    resetSeoDescription: '새 비밀번호를 정해요.',
    checkingTitle: '링크를 확인하는 중이에요',
    expiredTitle: '재설정 링크가 만료됐어요',
    expiredText: '링크는 정해진 시간(1시간) 안에만 쓸 수 있어요. 재설정 메일을 다시 받아 주세요.',
    getNewMail: '재설정 메일 다시 받기',
    usedNote: '이미 한 번 쓴 링크도 같은 화면이 나와요.',
    checkErrorTitle: '링크를 확인하지 못했어요',
    doneTitle: '비밀번호를 바꿨어요',
    doneText: '새 비밀번호로 로그인해 주세요. 보안을 위해 다른 기기에서는 로그아웃돼요.',
    toLogin: '로그인하기',
    resetTitle: '새 비밀번호를 정해 주세요',
    newLabel: '새 비밀번호',
    newConfirmLabel: '새 비밀번호 확인',
    submit: '비밀번호 바꾸기',
    changing: '바꾸는 중…',

    // 소셜 가입자 안내
    socialSeoTitle: '소셜 계정 안내',
    socialSeoDescription: '소셜 계정으로 가입한 계정은 비밀번호가 없어요.',
    socialTitleKnown: '{providerRo} 가입한 계정이에요',
    socialTitleUnknown: '소셜 계정으로 가입한 계정이에요',
    socialNoPassword: '이 계정에는 비밀번호가 없어요.',
    socialLoginKnown: '{providerRo} 로그인해 주세요.',
    socialLoginUnknown: '가입할 때 쓴 소셜 계정으로 로그인해 주세요.',
    socialMailOnly: '이 화면은 소셜 가입자에게 가는 메일 속 링크로만 열려요.',

    // 비밀번호 변경(설정)
    changeSeoDescription: '{site} 비밀번호를 바꿔요.',
    currentLabel: '현재 비밀번호',
    currentRequired: '현재 비밀번호를 입력해 주세요.',
    rulesNewLabel: '새 비밀번호 조건',
    ruleDifferent: '현재 비밀번호와 다르게',
    newMismatch: '새 비밀번호가 서로 달라요',
    newMatch: '새 비밀번호와 같아요',
    changedToast: '비밀번호를 바꿨어요',
    changedTitle: '비밀번호를 바꿨어요',
    changedText: '이제 새 비밀번호로 로그인해 주세요. 이 기기는 로그인된 채로 남고, 다른 기기에서는 다시 로그인해야 해요.',
    lockedTitle: '현재 비밀번호를 여러 번 틀렸어요.',
    lockedText: '{minutes}분 뒤에 다시 시도해 주세요.',
    lockedHelp: '현재 비밀번호가 기억나지 않으면 로그아웃한 뒤 로그인 화면의 「비밀번호 찾기」를 이용해 주세요.',
    socialAccountTitle: '소셜 로그인으로 가입한 계정이에요',
    notSetText: '이 계정은 비밀번호가 없어서 변경할 수 없어요.',
    socialGuideOne: '{provider} 계정의 보안 설정은 {provider}에서 관리해 주세요.',
    socialGuideMany: '소셜 계정의 보안 설정은 각 서비스에서 관리해 주세요.',
};
