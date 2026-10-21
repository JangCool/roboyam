/**
 * 메일 서버 입력의 문구(설치 마법사 4단계) — `components/common/MailFields.vue`·`utils/util-mail-messages.ts`·`components/install/InstallMailStep.vue` 가 쓴다.
 * 키는 `mail.무엇` 두 마디 — 화면 이름을 넣지 않는다. `en/mail.ts` 와 같은 키, 같은 차례.
 * ⚠ 문구 안의 `@`·`|` 는 글자가 아니다(`{'@'}`·`{'|'}` 로 감싼다). `{max}` 같은 자리표시자는 값으로 채워진다.
 * ⚠ 서버 응답 원문·자격 증명은 어디에도 싣지 않는다(서버도 `reason` 코드만 준다).
 */
export default {
    // 칸 이름·힌트
    hostLabel: '서버 주소',
    securityLabel: '보안 방식',
    portLabel: '포트',
    usernameLabel: '아이디',
    passwordLabel: '비밀번호',
    fromLabel: '보내는 주소',
    optionalHint: '선택',
    fromHint: '메일 발신자',
    securityOption: '{label} · 보통 {port}',

    // 안내
    checkButton: '연결 확인',
    checkNote: '접속과 인증까지만 확인해요. 메일은 보내지 않아요.',
    noMailNote: '메일 서버가 없으면 가입 인증 메일과 비밀번호 재설정 메일이 나가지 않아요. 가입은 그대로 되고, 나중에 설정 화면에서 넣을 수 있어요.',
    usernameHelp: '메일 서버가 로그인을 요구할 때만 입력해요. 인증이 필요 없으면 아이디와 비밀번호를 둘 다 비워 두세요.',
    passwordHelpRegistered: '등록됨 — 바꾸려면 새 값을 입력해 주세요. 비워 두면 지금 값을 그대로 써요.',
    passwordHelpNew: '저장하면 다시 보여 주지 않아요.',
    passwordPlaceholderRegistered: '등록됨 · 바꾸려면 새 값 입력',

    // 화면이 미리 검사한 오류
    hostRequired: '메일 서버 주소를 입력해 주세요.',
    portRequired: '포트를 입력해 주세요.',
    securityRequired: '보안 방식을 입력해 주세요.',
    usernameRequired: '아이디를 입력해 주세요.',
    passwordRequired: '비밀번호를 입력해 주세요.',
    fromRequired: '보내는 주소를 입력해 주세요.',
    pairUsername: '비밀번호와 함께 아이디도 입력해 주세요. 인증이 필요 없으면 둘 다 비워 두세요.',
    pairPassword: '인증을 쓰려면 아이디와 함께 비밀번호도 입력해 주세요. 인증이 필요 없으면 둘 다 비워 두세요.',
    hostLength: '서버 주소는 {max}자 이하로 입력해 주세요.',
    hostSpace: '서버 주소에는 공백을 넣을 수 없어요. 예: smtp.example.com',
    portRange: '포트는 {min}~{max} 사이의 숫자로 입력해 주세요.',
    fromLength: '보내는 주소는 254자 이하로 입력해 주세요.',
    fromFormat: "보내는 주소의 이메일 형식을 확인해 주세요. 예: no-reply{'@'}example.com",

    // 연결 확인 결과
    checkConnected: '메일 서버에 연결했어요. 접속과 인증이 모두 됐어요.',
    checkFailed: '메일 서버에 연결하지 못했어요. 입력한 내용을 확인해 주세요.',
    reasonConnectFailed: '메일 서버에 연결하지 못했어요. 서버 주소와 포트가 맞는지, 방화벽이 막고 있지 않은지 확인해 주세요.',
    reasonAuthFailed: '아이디나 비밀번호가 맞지 않아요. 다시 확인해 주세요.',
    reasonTlsFailed: '보안 연결(TLS)에 실패했어요. 보안 방식과 포트가 서로 맞는지 확인해 주세요. 예: STARTTLS 는 587, SSL/TLS 는 465.',
    reasonTimeout: '메일 서버가 시간 안에 응답하지 않았어요. 서버 주소와 포트를 확인하고 잠시 뒤 다시 시도해 주세요.',
};
