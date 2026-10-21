/**
 * 설치 마법사(`/install`)의 문구 — 단계 제목·안내, 점검 결과 설명, 입력 오류, 확인 화면, 완료 화면.
 * `pages/install.vue`·`components/install/*`·`utils/util-install-messages.ts` 가 쓴다. 키는 `install.무엇` 두 마디 — 화면 이름을 넣지 않는다. `en/install.ts` 와 같은 키, 같은 차례.
 * ⚠ 문구 안의 `@`·`|` 는 글자가 아니다(`{'@'}`·`{'|'}` 로 감싼다). `{remaining}` 같은 자리표시자는 값으로 채워진다.
 * ⚠ 토큰·비밀번호·시크릿 값은 어떤 문구에도 넣지 않는다(확인 화면은 "입력함"만).
 */
export default {
    // 화면 바깥(제목·진행)
    pageTitle: '설치',
    pageDescription: '{site} 설치 마법사예요.',
    progressLabel: '설치 진행',
    progress: '{current} / {total} · {label}',
    installButton: '설치',
    installing: '설치하는 중이에요. 화면을 닫지 마세요.',

    // 단계의 이름·제목·설명
    stepTokenLabel: '설치 확인',
    stepTokenTitle: '설치 확인',
    stepTokenDescription: '서버를 처음 띄울 때 로그에 나온 설치 토큰을 입력해 주세요. 환경 변수 ROBOYAM_INSTALL_TOKEN 으로 정했다면 그 값이에요.',
    stepRootLabel: '루트 계정',
    stepRootTitle: '루트 계정',
    stepRootDescription: '이 사이트를 운영할 첫 최고 관리자 계정을 만들어요. 이메일 인증은 건너뛰고, 인증된 계정으로 만들어져요.',
    stepSiteLabel: '사이트',
    stepSiteTitle: '사이트',
    stepSiteDescription: '사이트 이름·한 줄 소개(선택)·공개 주소를 정해요. 나중에 관리자 앱의 설정 화면에서 고칠 수 있어요.',
    stepMailLabel: '메일 서버',
    stepMailTitle: '메일 서버',
    stepMailDescription: '가입 인증·비밀번호 재설정 메일을 보낼 서버예요. 건너뛰어도 되고, 나중에 관리자 앱의 설정 화면에서 넣을 수 있어요.',
    stepSocialLabel: '소셜 로그인',
    stepSocialTitle: '소셜 로그인 키',
    stepSocialDescription: '네이버·카카오·구글 로그인에 쓸 클라이언트 ID·시크릿이에요. 건너뛰어도 되고, 나중에 관리자 앱의 소셜 로그인 설정 화면에서 넣을 수 있어요.',
    stepBoardsLabel: '게시판 구성',
    stepBoardsTitle: '기본 게시판 구성',
    stepBoardsDescription: '처음 만들어 둘 게시판을 골라요. 설치한 뒤에도 관리자 앱의 게시판 설정에서 게시판과 말머리를 언제든 바꿀 수 있어요.',
    stepReviewLabel: '확인',
    stepReviewTitle: '확인',
    stepReviewDescription: '입력한 내용을 확인하고 설치해 주세요. 비밀번호와 시크릿은 가려서 보여 줘요.',

    // 1단계: 설치 토큰 · 점검
    tokenLabel: '설치 토큰',
    tokenRequired: '설치 토큰을 입력해 주세요.',
    tokenOk: '설치 토큰을 확인했어요.',
    tokenLocked: '설치 토큰을 여러 번 틀려 잠시 잠겼어요. {remaining} 뒤에 다시 시도할 수 있어요.',
    sessionExpired: '설치 인증이 만료됐어요. 설치 토큰을 다시 입력해 주세요. 입력해 둔 내용은 그대로 남아 있어요.',
    checksLoading: '설치 점검 중',
    checksList: '설치 점검 결과',
    checksFailed: '설치 점검 결과를 불러오지 못했어요.',
    recheck: '다시 점검',
    checkBlockingNote: '막는 항목이 있어 설치를 계속할 수 없어요. 서버 설정을 고치고 서버를 다시 시작한 뒤 다시 점검해 주세요.',
    checkCryptoNote: '암호화 키가 없어 메일 비밀번호와 소셜 로그인 키는 지금 저장할 수 없어요. 설치는 계속할 수 있어요. 키를 설정한 뒤 관리자 앱의 설정 화면에서 넣을 수 있어요.',
    checkLineOk: '{label}: 통과',
    checkLineWarn: '{label}: 경고',
    checkLineFail: '{label}: 실패',

    // 암호화 키가 없을 때
    cryptoLockMail: '암호화 키가 없어 아이디·비밀번호는 저장할 수 없어요. 인증이 필요 없는 메일 서버만 등록할 수 있고, 인증이 필요하면 건너뛰어 주세요.',
    cryptoLockSocial: '암호화 키가 없어 시크릿을 저장할 수 없어요. 이 단계는 건너뛰어 주세요. 키를 설정한 뒤 관리자 앱의 소셜 로그인 설정 화면에서 넣을 수 있어요.',
    cryptoDropped: '암호화 키가 없어 메일 아이디·비밀번호와 소셜 로그인 키는 저장할 수 없어요. 그 입력을 비우고 건너뛴 것으로 바꿨으니 확인한 뒤 다시 설치해 주세요.',

    // 2단계: 루트 계정
    emailLabel: '이메일',
    nicknameLabel: '닉네임',
    nicknameHint: '{min}~{max}자',
    nicknameHelp: '한글·영문·숫자·밑줄과 글자 사이의 공백을 쓸 수 있어요. 운영자 같은 이름도 괜찮아요.',
    passwordLabel: '비밀번호',
    passwordConfirmLabel: '비밀번호 확인',
    passwordMatch: '비밀번호가 같아요.',
    passwordMismatch: '비밀번호가 서로 달라요.',
    passwordRequired: '비밀번호를 입력해 주세요.',
    passwordRule: '{min}~{max}자, 영문과 숫자를 함께 사용해 주세요.',
    passwordTooLong: '비밀번호가 너무 길어요. UTF-8 기준 {max}바이트 이하로 입력해 주세요.',
    passwordConfirmRequired: '비밀번호를 한 번 더 입력해 주세요.',

    // 4단계: 메일 서버
    mailSkippedTitle: '메일 서버를 건너뛰었어요',
    mailSet: '메일 서버 설정하기',

    // 5단계: 소셜 로그인 키
    socialSkippedTitle: '소셜 로그인 키를 건너뛰었어요',
    socialSkippedBody: '로그인·가입 화면에 소셜 로그인 버튼이 나오지 않아요. 나중에 관리자 앱의 소셜 로그인 설정 화면에서 넣을 수 있어요.',
    socialSet: '소셜 로그인 키 입력하기',
    socialIntro: '쓰려는 공급자만 켜고 클라이언트 ID·시크릿을 입력해 주세요. 꺼 둔 공급자는 쓰지 않아요.',

    // 6단계: 기본 게시판 구성
    boardsLoading: '고를 수 있는 구성을 불러오는 중이에요.',
    boardsFailed: '기본 게시판 구성 목록을 불러오지 못했어요. 이대로 설치하면 서버의 기본 구성(중립)으로 만들어져요.',
    boardsDefaultMark: '기본',
    boardsNote: '핫딜·직접홍보 게시판은 기능 게시판이라 어느 구성에서나 함께 만들어져요. 이 선택은 설치할 때 한 번만 적용돼요.',
    boardsServerDefault: '서버 기본 구성',
    boardsSummary: '게시판 {count}개 보기',

    // 7단계: 확인
    filled: '입력함',
    skipped: '건너뜀',
    reviewServer: '서버',
    reviewSecurity: '보안',
    reviewNoAuth: '없음(인증 안 함)',
    reviewTagline: '한 줄 소개',
    reviewPreset: '구성',
    reviewSocialClientId: '{provider} 클라이언트 ID',
    reviewSocialSecret: '{provider} 시크릿',
    reviewFill: '입력하기',
    reviewEditLabel: '{title} 수정',
    reviewFillLabel: '{title} 입력하기',
    installNote: '설치하면 루트 계정이 만들어지고 이 화면은 닫혀요. 설치 직후에는 로그인 화면으로 가서 방금 만든 계정으로 로그인해요.',

    // 완료
    doneTitle: '설치가 끝났어요',
    doneText: '{email} 계정으로 로그인해 시작해 주세요. 사이트·메일·소셜 로그인 설정은 로그인한 뒤 내 정보의 최고 관리자 도구에서 고칠 수 있어요.',
    goLogin: '로그인하러 가기',

    // 설치 상태를 못 받았다
    statusFailedTitle: '설치 상태를 확인하지 못했어요',
    statusFailedDescription: '서버에 연결하지 못했어요. 서버가 켜져 있는지 확인한 뒤 다시 시도해 주세요.',
    statusBanner: '서버의 설치 상태를 확인하지 못했어요. 일부 화면이 동작하지 않을 수 있어요.',
    statusRecheck: '다시 확인',
};
