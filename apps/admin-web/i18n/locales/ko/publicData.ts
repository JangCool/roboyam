/**
 * 공공데이터포털 설정 화면 문구(`/settings/public-data`) — 화면 `pages/settings/public-data.vue`. 메뉴 이름은 `menu.publicData`.
 * `en/publicData.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '공공데이터포털',
    subtitle: '공공데이터포털(data.go.kr) 서비스 키예요. 국세청 사업자 상태조회·진위확인이 이 키 하나를 같이 써요. 저장한 값은 다시 보이지 않아요.',
    readOnly: '보기만 할 수 있어요. 키를 바꾸거나 지우려면 비밀 설정 권한이 필요해요.',
    loadFailed: '공공데이터포털 설정을 불러오지 못했어요',
    cryptoMissingTitle: '서버에 암호화 키가 없어요',
    cryptoMissingText: '키가 없으면 값을 저장할 수 없어요(지우기는 돼요). 서버 설정(ROBOYAM_CRYPTO_KEY)을 먼저 확인해 주세요.',

    usageTitle: '사용처',
    usageNts: '국세청 사업자 조회',
    usageNtsHelp: '사업자 신청 때 등록번호·상호·대표자명·개업일 진위확인, 심사 때 계속·휴업·폐업 상태조회에 써요.',
    provider: {
        ODCLOUD: '공공데이터포털',
        FAKE: '가짜(개발용)',
    },
    providerFake: '지금은 가짜 구현이라 사업자 신청·심사 조회에는 키가 쓰이지 않아요(local·시험 전용, dev·prod 에서는 서버가 뜨지 않아요). 아래 [연결 시험]은 실제 국세청으로 보내요.',
    noKeyTitle: '국세청 조회를 할 수 없어요',
    noKeyText: '서비스 키가 없어 모든 조회가 「조회 불가」가 돼요. 운영자는 사유를 적어야 승인할 수 있어요.',
    providerHelp: '어떤 구현을 쓸지는 서버 배포 설정(ROBOYAM_NTS_PROVIDER)으로 정해지고, 이 화면에서는 바꿀 수 없어요.',

    keyTitle: '서비스 키',
    keyHelp: 'data.go.kr 마이페이지 › 개발계정 › 「국세청_사업자등록정보 진위확인 및 상태조회 서비스」의 「일반 인증키(Decoding)」를 넣어요.',
    source: {
        SCREEN: '화면에서 설정',
        ENV: '환경 변수 사용',
        NONE: '설정 필요',
    },
    field: '일반 인증키',
    configured: '설정됨',
    notConfigured: '설정 안 됨',
    updatedAt: '{at} 변경',
    updatedBy: '{at} · {name}',
    envNotice: '서버 배포 환경 변수(ROBOYAM_NTS_SERVICE_KEY)의 값을 쓰고 있어요. 여기서 저장하면 화면 값이 먼저 쓰이고, 지우면 다시 환경 변수로 돌아가요.',
    value: '인증키',
    newValue: '새 인증키',
    valueHelp: '영문·숫자만, 공백 없이',
    keepHint: '바꾸지 않으려면 비워 두세요.',
    noSpaces: '공백 없이 넣어 주세요.',
    changes: '저장하지 않은 입력이 있어요',
    noChanges: '바뀐 것이 없어요',
    saved: '서비스 키를 저장했어요',
    remove: '지우기',
    removeTitle: '서비스 키를 지울까요?',
    removeMessage: '화면에서 저장한 키를 지워요. 환경 변수가 있으면 그 값으로, 없으면 국세청 조회가 「조회 불가」가 돼요.',
    removed: '서비스 키를 지웠어요',

    testTitle: '연결 시험',
    testHelp: '저장된 키로 국세청 상태조회를 실제로 한 번 불러 봐요. 가짜 구현일 때도 실제 국세청으로 보내요.',
    testRun: '연결 시험',
    testSaveFirst: '입력한 키를 먼저 저장해 주세요.',
    testResult: {
        OK: '국세청 응답을 받았어요. 키가 정상이에요.',
        KEY_INVALID: '키가 맞지 않아요. 일반 인증키(Decoding)를 다시 확인해 주세요.',
        NO_KEY: '저장된 키가 없어요.',
        TIMEOUT: '국세청이 응답하지 않아요. 잠시 뒤 다시 시험해 주세요.',
        ERROR: '조회하지 못했어요. 잠시 뒤 다시 시험해 주세요.',
    },
    testedAt: '{at} 시험',
    testedBy: '{at} · {name} 시험',

    leaveTitle: '저장하지 않고 나갈까요?',
    leaveMessage: '입력한 키는 저장되지 않아요.',
    leaveConfirm: '나가기',
};
