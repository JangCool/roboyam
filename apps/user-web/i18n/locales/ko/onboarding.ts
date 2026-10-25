/**
 * 온보딩(`/onboarding`, 가입 직후 첫 화면)의 문구 — 환영 + 운영 원칙 3장. 원칙 문구는 원본(community) `pages/onboarding.vue` 의 초안 그대로(오픈 전 전문 검토),
 * 예시(`*Tag`·`*Example`·`*Note`·`investNotice`)만 로보얌 주제(로봇·AI)로 바꿨다 — `member-account-contract.md` 「결정 (2026-10-24 2차)」.
 * 키는 `onboarding.무엇` 두 마디. `en/onboarding.ts` 와 같은 키, 같은 차례.
 */
export default {
    seoTitle: '환영해요',
    seoDescription: '{site} 운영 원칙을 확인하고 시작해요.',
    avatar: '봇',
    welcome: '환영해요, {name}님!',
    intro: '이곳에서 함께 이야기를 나눠요.',
    introRules: '시작하기 전에 운영 원칙 {count}가지만 확인해 주세요.',
    ruleCount: '운영 원칙 {current}/{total}',
    stepLabel: '{current} / {total} 단계',
    start: '시작하기',

    // 원칙 1 — 출처
    sourceShort: '출처를 붙여요',
    sourceTitle: '주장에는 출처를 붙여요',
    sourceBody: '뉴스·수치·인용은 출처(매체·링크)를 함께 적어 주세요. 출처 없는 주장은 신고될 수 있어요.',
    sourceTag: '논문',
    sourceExample: '로봇 팔 집기 성공률, 새 AI 모델이 20%p 올렸다는 발표 정리',
    sourceNote: '출처: 논문 제목·학회(예시) · 링크',

    // 원칙 2 — 비방 금지
    respectShort: '비방하지 않아요',
    respectTitle: '비방·인신공격은 안 돼요',
    respectBody: '사람을 향한 비방과 인신공격은 쓰지 않아요. 허위정보로 보이는 글은 신고해 주세요. 운영팀이 검토해요.',
    respectExample: '「그 로봇 회사 사람들은 다 사기꾼」 대신 「이 시연 영상은 원격 조종으로 보여요(근거: 영상 1분 20초)」처럼 써 주세요. 신고는 글·댓글의 ⋯ 메뉴에서 할 수 있어요.',

    // 원칙 3 — 투자 고지
    investShort: '투자 고지를 지켜요',
    investTitle: '산업·주식 글에는 투자 고지가 붙어요',
    investBody: '매수·매도 권유, 목표가, 수익 보장 표현은 쓸 수 없어요. 운영팀이 관련 종목을 가지고 있으면 공개해요.',
    investNotice: '투자 고지(예시) · 이 글은 로봇·AI 산업 정보를 나누려는 글이며 특정 종목의 투자 권유가 아니에요. 투자 판단과 책임은 본인에게 있어요.',
};
