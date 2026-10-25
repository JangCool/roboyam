/**
 * 포트원 설정 화면 문구(`/settings/portone`, 커머스 0-4) — 화면(`pages/settings/portone.vue`)과 카드(`PortoneSecretCard`). 메뉴 이름은 `menu.portone`.
 * `en/portone.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '포트원',
    subtitle: '포트원 V2 의 본인인증 설정이에요. 저장한 값은 다시 보이지 않아요.',
    readOnly: '보기만 할 수 있어요. 값을 바꾸거나 지우려면 비밀 설정 권한이 필요해요.',
    loadFailed: '포트원 설정을 불러오지 못했어요',
    cryptoMissingTitle: '서버에 암호화 키가 없어요',
    cryptoMissingText: '키가 없으면 값을 저장할 수 없어요(지우기는 돼요). 서버 설정(ROBOYAM_CRYPTO_KEY)을 먼저 확인해 주세요.',

    providerTitle: '지금 쓰는 본인인증 구현',
    providerNow: '지금 구현',
    provider: {
        PORTONE: '포트원',
        FAKE: '가짜(개발용)',
    },
    providerHelp: '서버 배포 설정(roboyam.identity.provider)으로 정해지고, 이 화면에서는 바꿀 수 없어요.',
    providerFake: '가짜 구현은 local·시험 전용이에요. dev·prod 에서는 서버가 뜨지 않아요.',
    notReadyTitle: '본인인증을 쓸 수 없어요',
    notReadyText: '상점 ID · API 비밀키 · 본인인증 채널 키 세 칸을 모두 채우세요.',

    identityCard: '본인인증',
    identityCardHelp: '포트원 콘솔의 상점 ID, V2 API 비밀키, 본인인증용 채널 키(다날 또는 KCP)를 넣어요.',
    badgeReady: '모두 설정됨',
    badgeIncomplete: '설정 필요',
    field: {
        storeId: '상점 ID',
        apiSecret: 'API 비밀키(V2 API Secret)',
        identityChannelKey: '본인인증 채널 키',
    },
    fieldHelp: {
        storeId: '포트원 콘솔 › 상점 정보의 store- 로 시작하는 값',
        apiSecret: '포트원 콘솔 › 연동 정보 › V2 API Secret',
        identityChannelKey: '포트원 콘솔 › 연동 정보 › 채널 관리의 본인인증 채널 키',
    },
    configured: '설정됨',
    notConfigured: '설정 안 됨',
    updatedAt: '{at} 변경',
    updatedBy: '{at} · {name}',
    value: '값',
    newValue: '새 값',
    keepHint: '비워 두면 지금 값을 그대로 써요.',
    noSpaces: '공백 없이 입력해 주세요.',
    changes: '{count}개 칸을 바꿨어요',
    noChanges: '바꾼 칸이 없어요',
    saved: '포트원 본인인증 설정을 저장했어요',
    remove: '지우기',
    removeAria: '{name} 지우기',
    removeTitle: '{name}을(를) 지울까요?',
    removeMessage: '지우면 {name}이(가) 설정 안 됨으로 바뀌고, 포트원 본인인증을 쓸 수 없게 돼요.',
    removed: '{name}을(를) 지웠어요',
    leaveTitle: '저장하지 않고 나갈까요?',
    leaveMessage: '입력한 값이 저장되지 않아요.',
    leaveConfirm: '나가기',
    footnote: '결제(공동구매·충전) 설정은 결제 단계에서 「결제」 카드로 더해져요. 값은 서버에서 암호화해 저장하고, 바꾸거나 지우면 감사 기록에 남아요.',
};
