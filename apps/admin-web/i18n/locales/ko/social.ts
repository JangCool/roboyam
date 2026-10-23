/**
 * 소셜 로그인 설정 화면(`/settings/social-login`)의 문구. 서버 오류 문구는 서버 `message`·`fields` 를 그대로 쓰므로 여기에 없다.
 * 키는 `social.무엇` 두 마디(공급자 이름 `provider_<코드>` 는 서버 목록을 아직 못 받았을 때만 쓴다). `en/social.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '소셜 로그인',
    subtitle: '카카오·네이버·구글 로그인에 쓸 클라이언트 ID·시크릿을 등록하고, 공개 사이트 로그인 화면에 버튼을 켜고 꺼요.',
    readOnly: '소셜 로그인 키를 바꿀 권한이 없어서 조회만 할 수 있어요.',
    loadFailed: '소셜 로그인 설정을 불러오지 못했어요',
    cryptoMissingTitle: '암호화 키가 없어 저장할 수 없어요',
    cryptoMissingText: '서버에 암호화 키(ROBOYAM_CRYPTO_KEY)를 설정하고 서버를 다시 시작한 뒤 이 화면을 새로 고쳐 주세요. 그동안 공개 사이트에는 소셜 로그인 버튼이 나오지 않아요.',
    footnote: '저장하면 바로 공개 사이트 로그인 화면에 적용돼요. 시크릿은 암호화해 저장하고 다시 보여 주지 않아요.',
    historyHint: '설치 마법사와 이 화면에서 바꾼 기록이에요. 시크릿은 값 없이 입력·변경·삭제 여부만 남고, 클라이언트 ID 는 끝 네 자리만 보여요.',
    provider_KAKAO: '카카오',
    provider_NAVER: '네이버',
    provider_GOOGLE: '구글',

    // 상태
    badgeActive: '켜짐',
    badgeInactive: '켜 둠 · 안 보임',
    badgeOff: '꺼 둠',
    badgeNone: '미등록',
    statusNone: '아직 등록하지 않아 로그인·가입 화면에 {name} 버튼이 나오지 않아요.',
    statusOff: '등록은 했지만 꺼 둬서 로그인·가입 화면에 {name} 버튼이 나오지 않아요.',
    statusActive: '지금 공개 사이트 로그인·가입 화면에 {name} 버튼이 보여요.',
    statusNoCryptoKey: '켜 두었지만 서버에 암호화 키가 없어 {name} 버튼이 나오지 않아요.',
    statusNotConfigured: '켜 두었지만 클라이언트 ID·시크릿이 다 갖춰지지 않아 {name} 버튼이 나오지 않아요.',
    updatedBy: '마지막 저장 {at} · {name}',
    updatedAt: '마지막 저장 {at}',
    secretReenterNotice: '저장된 시크릿을 지금 서버 키로 읽을 수 없어요. 시크릿을 다시 입력해 주세요.',

    // 콜백 주소
    callbackLabel: '콜백 주소',
    callbackHint: '{name} 개발자 콘솔에 등록',
    copy: '복사',
    copyAria: '{name} 콜백 주소 복사',
    copied: '{name} 콜백 주소를 복사했어요',
    copyFailed: '복사하지 못했어요. 주소를 직접 선택해 복사해 주세요.',
    callbackMissing: '사이트 공개 주소가 없어 콜백 주소를 만들 수 없어요. 공개 주소를 먼저 정해 주세요.',
    callbackMissingLink: '사이트 일반 설정',

    // 입력칸
    clientIdLabel: '클라이언트 ID',
    clientSecretLabel: '클라이언트 시크릿',
    clientSecretLabelRequired: '클라이언트 시크릿(필수)',
    secretKeep: '비워 두면 지금 값을 그대로 써요.',
    secretReenter: '저장된 값을 읽을 수 없어요. 저장하려면 시크릿을 다시 입력해 주세요.',
    secretNew: '각 사 개발자 콘솔에서 발급받은 값이에요. 저장하면 다시 보여 주지 않아요.',
    enabledLabel: '켜기',
    enabledHelp: '켜 두면 로그인·가입 화면에 {name} 버튼이 나와요.',
    remove: '설정 삭제',

    // 화면이 미리 검사한 오류
    clientIdRequired: '클라이언트 ID 를 입력해 주세요.',
    clientSecretRequired: '클라이언트 시크릿을 입력해 주세요.',
    clientIdLength: '클라이언트 ID 는 {max}자 이하로 입력해 주세요.',
    clientSecretLength: '클라이언트 시크릿은 {max}자 이하로 입력해 주세요.',
    clientIdSpace: '클라이언트 ID 에는 공백이나 제어 문자를 넣을 수 없어요.',
    clientSecretSpace: '클라이언트 시크릿에는 공백이나 제어 문자를 넣을 수 없어요.',

    // 결과
    savedActive: '{name} 로그인 설정을 저장했어요. 로그인 화면에 버튼이 나와요.',
    savedInactive: '{name} 로그인 설정을 저장했어요. 켜 두었지만 아직 버튼은 나오지 않아요.',
    savedOff: '{name} 로그인 설정을 저장했어요. 꺼 둬서 버튼은 나오지 않아요.',
    removeTitle: '{name} 로그인 설정을 지울까요?',
    removeMessage: '저장된 클라이언트 ID·시크릿이 지워지고 로그인·가입 화면에서 {name} 버튼이 사라져요. 이미 {name} 계정을 연결해 가입한 회원의 연결은 그대로 남아요.',
    removed: '{name} 로그인 설정을 지웠어요',
    leaveTitle: '저장하지 않은 변경이 있어요',
    leaveMessage: '이 화면을 떠나면 입력한 값이 사라져요.',
    leaveConfirm: '저장하지 않고 나가기',
};
