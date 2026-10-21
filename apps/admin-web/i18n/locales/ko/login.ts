/**
 * 관리자 로그인 화면의 문구 — 이메일 + 비밀번호만(소셜·가입 없음), 세션 만료, 잠금.
 * 키는 `login.무엇` 두 마디. `en/login.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 화면 공통
    title: '관리자 로그인',
    subtitle: '운영진 또는 최고 관리자 계정으로 로그인해 주세요.',
    seoTitle: '로그인',

    // 폼
    emailLabel: '이메일',
    passwordLabel: '비밀번호',
    showPassword: '비밀번호 보기',
    hidePassword: '비밀번호 숨기기',
    passwordRequired: '비밀번호를 입력해 주세요.',
    emailRequired: '이메일을 입력해 주세요.',
    emailLength: '이메일은 254자 이하로 입력해 주세요.',
    emailFormat: '이메일 주소 형식을 확인해 주세요.',
    submitting: '로그인 중…',
    submitWait: '잠시만 기다려 주세요. 화면을 닫지 마세요.',
    lockWarning: '비밀번호를 여러 번 틀리면 잠시 로그인이 제한돼요. (5회 연속 실패 시 10분)',
    forgotLink: '비밀번호를 잊으셨나요?',

    // 세션이 끝나 돌아온 경우
    expiredTitle: '로그인 시간이 지났어요.',
    expiredBody: '보안을 위해 한동안 활동이 없으면 로그아웃돼요. 다시 로그인하면 보던 화면으로 돌아가요.',

    // 시도 제한
    lockedTitle: '로그인이 잠시 제한됐어요.',
    lockedBody: '비밀번호를 여러 번 틀려서 계정을 지키기 위해 잠갔어요.',
    lockedUntil: '다시 시도할 수 있을 때까지',
    remainingTime: '남은 시간 {time}',
    lockedButton: '잠시 후 다시 시도할 수 있어요',
};
