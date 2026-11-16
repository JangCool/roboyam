/**
 * 포인트 게임 관리자 설정(포인트 상점 설정 화면의 「포인트 게임」 구획) — 로또 번호 뽑기의 「당첨 번호 확인」 링크.
 * 키는 `pointGame.무엇` 두 마디. `en/pointGame.ts` 와 같은 키, 같은 차례.
 */
export default {
    section: '포인트 게임',
    lead: '회원 화면 「로또 번호 뽑기」의 당첨 번호 확인 링크예요. 복권 사업자가 바뀌면 여기서 이름과 주소를 바꿔요.',
    loadFailed: '포인트 게임 설정을 불러오지 못했어요.',
    providerName: '복권 사업자 이름',
    providerNameHint: '1~{max}자',
    providerNameHelp: '회원 화면 링크 글자에 들어가요.',
    checkUrl: '당첨 확인 링크',
    checkUrlHint: 'https:// 주소',
    checkUrlHelp: '회원이 누르면 새 탭으로 열려요.',
    providerNameRequired: '복권 사업자 이름을 적어 주세요.',
    providerNameTooLong: '{max}자까지 적을 수 있어요.',
    checkUrlRequired: '당첨 확인 링크를 적어 주세요.',
    checkUrlInvalid: '주소 모양이 맞지 않아요. 예: https://example.com',
    checkUrlNotHttps: 'https:// 로 시작하는 주소만 쓸 수 있어요.',
    checkUrlTooLong: '{max}자까지 적을 수 있어요.',
    preview: '회원 화면',
    previewLabel: '당첨 번호 확인 · {provider}',
    openLink: '새 탭에서 열어 보기',
    defaultValue: '처음 값 {provider} · {url}',
    useDefault: '처음 값으로',
    mockNote: '아직 서버에 연결하지 않은 미리보기예요 — 저장해도 이 탭에서만 바뀌고 회원 화면에는 닿지 않아요.',
    noteNoWinning: '사이트는 당첨 번호를 만들거나 회원이 뽑은 번호와 맞춰 보지 않아요. 링크 하나만 보여 줘요.',
    noteNoOfficial: '회원 화면에는 사업자의 로고를 쓰지 않고, 공식 서비스처럼 보이는 말도 쓰지 않아요.',
    noteFixedRules: '룰렛(1회 50P · 하루 5회 · 확률표)과 로또 번호 값(1세트 20P · 5세트 80P)은 아직 고정이에요.',
    saveFailed: '포인트 게임 설정을 저장하지 못했어요',
    lastSaved: '마지막 저장 {at} · {by}',
    neverSaved: '아직 바꾼 적이 없어요(처음 값).',
};
