/**
 * 프로필 설정 화면(`/settings/profile`)의 문구 — 닉네임·한 줄 소개. 닉네임 칸 이름·글자 수 안내는 가입 화면과 같은 `signup.nickname*`, 닉네임 규칙 오류는 `auth.nickname*`.
 * 키는 `profile.무엇` 두 마디. `en/profile.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '프로필',
    seoDescription: '{site} 프로필의 닉네임과 한 줄 소개를 고쳐요.',
    loading: '프로필을 불러오는 중',
    loadFailed: '프로필을 불러오지 못했어요',

    // 닉네임 변경 주기
    nicknameInterval: '닉네임을 바꾸면 {days}일 동안 다시 바꿀 수 없어요.',
    nicknameLocked: '닉네임은 {date}부터 다시 바꿀 수 있어요.',

    // 한 줄 소개
    bioLabel: '한 줄 소개',
    bioHint: '{max}자까지',
    bioHelp: '내 정보와 프로필에 보여요.',
    bioTooLong: '한 줄 소개는 {max}자까지 쓸 수 있어요.',
};
