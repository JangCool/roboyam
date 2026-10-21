/**
 * 소셜 로그인 공급자 — 이름, 그리고 이름에 조사가 붙는 버튼 글·안내.
 * `{providerRo}`·`{providerEul}` 은 한국어 조사를 붙인 이름(`UtilProvider.params` 가 계산)이라 한국어 문장만 쓰고, 영어는 `{provider}` 만 쓴다.
 * 키는 `provider.무엇` 두 마디. `en/provider.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 이름
    kakao: '카카오',
    naver: '네이버',
    google: '구글',
    other: '소셜',

    // 버튼 글 (SocialLoginButtons 의 mode)
    continueWith: '{providerRo} 계속하기',
    loginWith: '{providerRo} 로그인',
    signupWith: '{providerRo} 가입',

    // 목업 안내
    mockNotice: '{provider} 로그인은 서버를 연결한 뒤에 쓸 수 있어요 (목업)',
};
