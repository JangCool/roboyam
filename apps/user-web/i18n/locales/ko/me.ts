/**
 * 내 정보 화면(`/me`)의 문구 — 프로필 요약·바로가기. 포인트 요약 문구는 `point.*`, 알림은 `notification.*`, 설정 화면 이름은 `settings.*`.
 * 키는 `me.무엇` 두 마디. `en/me.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '내 정보',
    seoDescription: '{site} 내 정보예요.',

    // 프로필 요약
    profileTitle: '프로필 요약',
    noEmail: '이메일 없음',
    joined: '{date} 가입',
    editProfile: '프로필 편집',
    profileLoading: '소개를 불러오는 중',
    profileLoadFailed: '소개를 불러오지 못했어요.',
    bioEmpty: '아직 한 줄 소개가 없어요.',

    // 바로가기
    shortcuts: '바로가기',
    entryProfile: '내 프로필 보기',
    entryGrade: '등급 안내',
};
