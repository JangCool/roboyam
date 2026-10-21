/**
 * 내 정보 화면(`/me`)의 문구. 서버 오류 문구는 서버 `message` 를 그대로 쓰므로 여기에 없다.
 * 키는 `profile.무엇` 두 마디. `en/profile.ts` 와 같은 키, 같은 차례.
 */
export default {
    seoTitle: '내 정보',
    title: '내 정보',
    subtitle: '내 계정 정보와 화면 설정, 로그인 이력을 확인해요.',
    menuLabel: '내 정보 메뉴',
    tabGeneral: '일반',
    tabPersonal: '개인 설정',
    tabLogin: '로그인 이력',

    // 기본 정보
    basicInfo: '기본 정보',
    accountId: '아이디',
    joinedAt: '가입일',
    lastLoginAt: '최근 로그인',

    // 회원 정보
    memberInfo: '회원 정보',
    nickname: '닉네임',
    nicknameHint: '2~12자',
    nicknameHelp: '한글, 영문, 숫자, 밑줄(_)을 쓸 수 있고 단어 사이에는 공백 한 칸까지 쓸 수 있어요.',
    email: '이메일',
    emailHelp: '이메일은 바꿀 수 없어요.',
    role: '역할',

    // 개인 설정 — 내 표시 형식(언어·시간대·시간/날짜 형식): 기본 설정 ← 내 설정. 어투는 공유 레이어(display-time)와 같은 "~요" 체
    displayUseMine: '내 표시 형식 사용',
    displayMineOn: '내가 고른 대로 보여요.',
    displayMineOnTail: '기본 설정이 어떻든 아래 값이 우선해요.',
    displayMineOff: '기본 설정대로 보여요.',
    displayMineOffTail: '켜면 아래에서 고른 값으로 보여요. 꺼도 고른 값은 그대로 남아요.',
    displayAppliedNow: '지금 화면에는 {from} 값이 적용돼요.',
    displayFromSystem: '기본 설정',
    displayFromUser: '내 설정',
    displaySection: '언어 및 지역 · 시간 및 날짜 형식',
    displayMineSaved: '내가 고른 표시 형식으로 보여요',
    displayMineOffSaved: '기본 설정대로 보여요',
    displayLoadFailed: '내 표시 형식을 불러오지 못했어요',

    // 개인 설정 — 알림 설정
    notifications: '알림 설정',
    emailNotification: '이메일 알림 수신',
    emailNotificationHint: '관리자 알림을 이메일로도 받아요.',
    emailNotificationOn: '알림 메일을 받아요',
    emailNotificationOff: '알림 메일을 받지 않아요',

    // 로그인 이력
    loginHistory: '최근 로그인 이력',
    noLoginHistory: '기록이 없어요',
    noLoginHistoryBody: '로그인 기록이 쌓이면 이곳에 보여요.',
    loginHistoryLoadFailed: '로그인 이력을 불러오지 못했어요',
    signInOk: '성공',
    signInFail: '실패',
    reasonInvalidCredentials: '이메일 또는 비밀번호가 맞지 않았어요',
    reasonLoginLocked: '로그인이 잠시 제한된 상태였어요',

    // 로그인 이력 — 접속 환경(User-Agent 를 풀어 보인다)
    agentUnknown: '알 수 없음',
    device_desktop: '데스크톱',
    device_mobile: '모바일',
    device_tablet: '태블릿',

    // 입력 안내
    nicknameRequired: '닉네임을 입력해 주세요.',
    nicknameLength: '닉네임은 2~12자로 입력해 주세요.',
    nicknameChars: '닉네임은 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있고 단어 사이에는 공백 한 칸만 쓸 수 있어요.',

    // 상태
    loadFailed: '내 정보를 불러오지 못했어요',
    emptyTitle: '보여 드릴 내 정보가 없어요',
    emptyBody: '잠시 후 다시 열어 주세요.',
};
