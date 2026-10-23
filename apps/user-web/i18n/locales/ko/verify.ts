/**
 * 이메일 인증 화면(`/verify-email`)의 문구 — 메일 링크 확인 결과(인증·이메일 변경·만료·쓸 수 없음·오류)와 "인증 메일을 보냈어요" 안내.
 * 원본(community) `pages/verify-email.vue` 의 글을 옮겼다. 재발송 단추·결과 글은 안내 배너와 같은 `login.resend*`.
 * 키는 `verify.무엇` 두 마디. `en/verify.ts` 와 같은 키, 같은 차례.
 */
export default {
    seoTitle: '이메일 인증',
    seoDescription: '이메일 인증을 마쳐요.',

    // 링크 확인
    verifyingTitle: '이메일을 확인하는 중이에요',
    doneTitle: '이메일 인증이 끝났어요',
    doneText: '이제 글과 댓글을 쓸 수 있어요.',
    start: '시작하기',
    loginToStart: '로그인하기',
    otherDevice: '메일 링크를 다른 기기에서 열었다면 원래 기기에서 로그인해 주세요.',
    changedTitle: '이메일을 바꿨어요',
    changedText: '이제 새 이메일로 로그인해요.',
    takenTitle: '이메일을 바꾸지 못했어요',
    takenText: '그 사이 다른 회원이 이 이메일을 사용 중이에요. 다른 이메일로 다시 시도해 주세요.',
    retryOtherEmail: '다른 이메일로 다시 시도',
    loginAndRetry: '로그인하고 다시 시도',
    alreadyTitle: '이메일 인증이 끝난 계정이에요',
    alreadyText: '글과 댓글을 쓸 수 있어요.',
    home: '홈으로',
    expiredTitle: '인증 링크가 만료됐어요',
    expiredText: '링크는 24시간 동안만 쓸 수 있어요. 새 인증 메일을 받아 주세요.',
    invalidTitle: '인증 링크를 쓸 수 없어요',
    invalidText: '링크가 올바르지 않거나 이미 사용됐어요. 새 인증 메일을 받아 주세요.',
    getNew: '새 인증 메일 받기',
    loginAndGetNew: '로그인하고 새 인증 메일 받기',
    alreadyHint: '이미 인증했다면 로그인만 하면 돼요.',
    errorTitle: '인증을 확인하지 못했어요',

    // 인증 메일 발송 안내
    sentTitle: '인증 메일을 보냈어요',
    sentTo: '{email} 으로 보낸 메일의 링크를 눌러 주세요.',
    validHours: '링크는 24시간 동안 쓸 수 있어요.',
    spamNote: '메일이 안 보이면 스팸함을 확인해 주세요. 발신 전용 주소라 답장은 받을 수 없어요.',
    readOnlyNote: '인증을 마칠 때까지는 글을 읽을 수만 있어요.',
    changeEmail: '이메일을 잘못 적었나요? 이메일 바꾸기',
    browseHome: '홈으로 가서 둘러보기',
};
