/**
 * 가입 화면의 문구 — 소셜 가입 마무리(`/signup/social`)와 약관 동의(`TermsAgreement`, 이메일 가입 화면도 같이 쓸 부품).
 * 원본(community) `pages/signup/social.vue`·`components/auth/TermsAgreement.vue` 의 글을 그대로 옮겼다. 닉네임 규칙 안내는 `auth.ts`.
 * 키는 `signup.무엇` 두 마디. `en/signup.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 소셜 가입 마무리
    socialTitle: '가입 마무리',
    socialSeoDescription: '소셜 계정으로 가입하는 마지막 단계예요. 닉네임을 정하고 약관에 동의해 주세요.',
    socialLoading: '소셜 계정을 확인하는 중이에요…',
    socialLoadFailed: '가입 정보를 불러오지 못했어요',
    socialChecked: '{provider} 계정 확인이 끝났어요.',
    socialRemaining: '닉네임과 약관 동의만 남았어요.',
    socialEmail: '받은 이메일: {email}',
    emailUnverified: '이 이메일은 아직 확인되지 않았어요. 가입하면 이 주소로 인증 메일을 보내요 — 메일의 링크를 눌러 인증을 마치면 글·댓글을 쓸 수 있어요.',
    nicknameLabel: '닉네임',
    nicknameHint: '{min}~{max}자',
    nicknameHelp: '실명·연락처는 입력하지 않아도 돼요. 한글·영문·숫자·밑줄과 글자 사이의 공백을 쓸 수 있어요.',
    submit: '시작하기',
    submitting: '가입하는 중…',
    leave: '가입하지 않고 나가기',
    toLogin: '로그인 화면으로',
    welcome: '가입을 마쳤어요. 반가워요!',

    // 약관 동의
    termsLegend: '약관 동의',
    agreeAll: '전체 동의',
    requiredMark: '필수',
    view: '보기',
    viewNewTab: '({label}, 새 탭)',
    ageConfirmedLabel: '만 14세 이상입니다',
    agreeTermsMissing: '이용약관에 동의해 주세요.',
    agreePrivacyMissing: '개인정보처리방침에 동의해 주세요.',
    ageConfirmedMissing: '만 14세 이상 확인이 필요해요. 만 14세 미만은 가입할 수 없어요.',
    ageNote: '만 14세 미만은 가입할 수 없어요. 확인되면 계정이 탈퇴 처리될 수 있어요.',
};
