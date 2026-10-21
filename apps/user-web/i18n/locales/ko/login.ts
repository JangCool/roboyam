/**
 * 로그인·인증 화면의 문구 — 로그인 폼, 소셜 결과 안내, 잠금, 이메일 인증 배너, 비밀번호 입력칸·규칙.
 * 공급자 이름이 들어가는 말은 `provider.ts`. 키는 `login.무엇` 두 마디. `en/login.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 화면 공통
    seoDescription: '{site} 에 로그인해요. 이메일 또는 카카오·네이버·구글 계정으로 들어올 수 있어요.',
    homeLabel: '{site} 홈',
    socialAccounts: '소셜 계정',
    socialDivider: '소셜 계정으로 계속하기',
    browseWithout: '로그인 없이 둘러보기',
    guestNote: '비회원은 읽기만 할 수 있어요',

    // 폼
    emailLabel: '이메일',
    forgotPassword: '비밀번호 찾기',
    passwordRequired: '비밀번호를 입력해 주세요.',
    submitting: '로그인 중…',
    submitWait: '잠시만 기다려 주세요. 화면을 닫지 마세요.',
    lockWarning: '비밀번호를 여러 번 틀리면 잠시 로그인이 제한돼요. (5회 연속 실패 시 10분)',
    expiredTitle: '로그인 시간이 지났어요.',
    expiredBody: '다시 로그인하면 보던 화면으로 돌아가요.',

    // 시도 제한
    lockedTitle: '로그인이 잠시 제한됐어요.',
    lockedBody: '비밀번호를 여러 번 틀려서 계정을 지키기 위해 잠갔어요.',
    lockedUntil: '다시 시도할 수 있을 때까지',
    remainingTime: '남은 시간 {time}',
    lockedButton: '잠시 후 다시 시도할 수 있어요',
    lockedHelpLead: '지금 바로 들어가려면',
    lockedHelpAction: '{reset} 또는 소셜 계정을 이용해 주세요',
    passwordReset: '비밀번호 재설정',

    // 소셜 로그인 결과
    emailExistsTitle: '이 이메일로 가입한 계정이 있어요',
    emailExistsBody: '{provider}에서 받은 이메일이 이미 {site} 계정에 쓰이고 있어요.',
    emailExistsHint: '이메일로 로그인한 뒤 {where}에서 {providerEul} 연동하면 다음부터 {providerRo}도 로그인할 수 있어요.',
    emailExistsWhere: '설정 › 계정',
    noAutoMerge: '본인 확인 없이 계정을 자동으로 합치지 않아요.',
    loginWithEmail: '이메일로 로그인',
    signupOther: '다른 계정으로 가입',
    socialFailedTitle: '{provider} 로그인이 끝나지 않았어요',
    socialFailedBody: '로그인을 취소했거나 연결하지 못했어요. 다시 시도하거나 다른 방법을 골라 주세요.',
    socialExpired: '소셜 가입 시간이 지났어요. 처음부터 다시 시도해 주세요.',

    // 이메일 인증 배너
    verifyLabel: '이메일 인증 안내',
    verifyPending: '이메일 인증이 아직이에요.',
    verifyBenefit: '인증을 마치면 글·댓글·추천·쪽지를 쓸 수 있어요.',
    verifySentTo: '{email} 로 보낸 메일의 링크를 눌러 주세요.',
    verifyGuide: '안내 보기',
    resendLabel: '인증 메일 다시 받기',
    resendCooling: '다시 받기 ({time} 후 가능)',
    resendSent: '인증 메일을 다시 보냈어요. 메일이 안 보이면 스팸함을 확인해 주세요.',
    resendAlready: '이미 인증이 끝났어요.',

    // 비밀번호 입력칸·규칙
    passwordLabel: '비밀번호',
    showPassword: '비밀번호 보기',
    hidePassword: '비밀번호 숨기기',
    rulesLabel: '비밀번호 규칙',
    ruleLength: '{min}~{max}자',
    ruleLengthNow: '{min}~{max}자 (지금 {count}자)',
    ruleBytes: 'UTF-8 {max}바이트 이하 (한글은 약 {hangul}자)',
    ruleBytesNow: 'UTF-8 {max}바이트 이하 (지금 {count}바이트)',
    ruleMix: '영문과 숫자를 함께 사용',
    ruleIdle: '(아직 입력 전)',
    ruleOk: '(충족)',
    ruleNo: '(충족하지 않음)',
};
