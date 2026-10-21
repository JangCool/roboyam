/**
 * 소셜 로그인 키 입력의 문구(설치 마법사 5단계) — `components/install/InstallSocialCard.vue`·`utils/util-admin-social-messages.ts`·`utils/util-install.ts` 가 쓴다.
 * 키는 `social.무엇` 두 마디 — 화면 이름을 넣지 않는다. `en/social.ts` 와 같은 키, 같은 차례.
 * ⚠ 문구 안의 `@`·`|` 는 글자가 아니다(`{'@'}`·`{'|'}` 로 감싼다). `{max}` 같은 자리표시자는 값으로 채워진다.
 */
export default {
    // 공급자 이름
    providerNaver: '네이버',
    providerKakao: '카카오',
    providerGoogle: '구글',

    // 칸 이름
    clientIdLabel: '클라이언트 ID',
    clientSecretLabel: '클라이언트 시크릿',

    // 켜기 스위치
    stateOn: '켜짐',
    stateOff: '꺼짐',

    // 콜백 주소
    callbackLabel: '콜백 주소',
    callbackHint: '{provider} 개발자 콘솔에 등록',
    callbackMissing: '공개 주소(3단계)를 올바르게 입력하면 여기에 콜백 주소가 나와요.',
    copyCallbackLabel: '{provider} 콜백 주소 복사',
    copied: '{provider} 콜백 주소를 복사했어요',
    copyFailed: '복사하지 못했어요. 주소를 길게 눌러 직접 복사해 주세요.',

    // 화면이 미리 검사한 오류
    clientIdRequired: '클라이언트 ID를 입력해 주세요.',
    clientSecretRequired: '클라이언트 시크릿을 입력해 주세요.',
    clientIdLength: '클라이언트 ID는 {max}자 이하로 입력해 주세요.',
    clientSecretLength: '클라이언트 시크릿은 {max}자 이하로 입력해 주세요.',
    clientIdSpace: '클라이언트 ID에는 공백을 넣을 수 없어요.',
    clientSecretSpace: '클라이언트 시크릿에는 공백을 넣을 수 없어요.',
};
