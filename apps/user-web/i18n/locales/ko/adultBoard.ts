/**
 * 19금 게시판 접근 설정·핀번호 문구 — 본인인증 화면(`/settings/identity`)의 「19금 게시판」 구역(`AdultBoardSettings`)·핀 입력 화면(`AdultBoardPinGate`·`AdultBoardPinDialog`).
 * 계약: `docs/features/adult-board-pin-contract.md`. 키는 `adultBoard.무엇` 두 마디. `en/adultBoard.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '19금 게시판',
    loading: '19금 게시판 설정을 불러오는 중',
    loadFailed: '19금 게시판 설정을 불러오지 못했어요',

    // 본인인증 성인이 아닐 때(인증 전 · 만 19세 미만) — 구역에 이 한 줄만
    notAdultText: '성인 전용 게시판은 본인인증으로 만 19세 이상이 확인된 회원만 접근을 켤 수 있어요.',

    // 접근 스위치
    accessLabel: '19금 게시판 접근',
    accessOn: '켜져 있어요',
    accessOff: '꺼져 있어요',
    offNote: '꺼져 있으면 성인 전용 게시판이 메뉴에 없고 들어갈 수 없어요. 켜려면 계정 비밀번호와 핀번호 6자리가 필요해요.',
    offNoteSocial: '꺼져 있으면 성인 전용 게시판이 메뉴에 없고 들어갈 수 없어요. 켜려면 최근 10분 안에 로그인한 상태에서 핀번호 6자리를 정해요.',
    onNote: '성인 전용 게시판이 메뉴에 보여요. 들어갈 때는 핀번호를 넣어야 해요.',
    onDone: '19금 게시판 접근을 켰어요',
    offConfirmTitle: '19금 게시판 접근을 끌까요?',
    offConfirmText: '성인 전용 게시판이 메뉴에서 빠지고 들어갈 수 없어요. 다시 켤 때는 계정 비밀번호와 핀번호를 새로 정해요.',
    offConfirmButton: '끄기',
    offDone: '19금 게시판 접근을 껐어요',

    // 핀번호
    pinTitle: '핀번호',
    pinLabel: '핀번호',
    pinSetAt: '{date}에 정했어요',
    pinSet: '정해져 있어요',
    pinChange: '핀번호 바꾸기',
    pinHelp: '핀번호를 잊었으면 「핀번호 바꾸기」에서 계정 비밀번호로 새로 정할 수 있어요.',
    pinHelpSocial: '핀번호를 잊었으면 「핀번호 바꾸기」에서 새로 정할 수 있어요. 최근 10분 안에 로그인한 상태여야 해요.',
    pinChanged: '핀번호를 바꿨어요',

    // 핀번호 다시 묻기
    relockTitle: '핀번호 다시 묻기',
    relockIdle: '마지막으로 본 뒤 1시간이 지나면',
    relockIdleHint: '성인 전용 게시판을 보는 동안에는 묻지 않아요.',
    relockFixed: '핀번호를 넣은 뒤 1시간이 지나면',
    relockFixedHint: '보는 중이어도 1시간마다 다시 물어요.',
    relockSaved: '핀번호 다시 묻기를 바꿨어요',

    // 지금 잠그기
    lockTitle: '지금 잠그기',
    lockOpen: '이 기기에서 열려 있어요',
    lockOpenIdle: '보지 않으면 약 {minutes}분 뒤에 다시 물어요.',
    lockOpenFixed: '약 {minutes}분 뒤에 다시 물어요.',
    lockClosed: '이 기기에서 잠겨 있어요',
    lockClosedText: '성인 전용 게시판에 들어갈 때 핀번호를 물어요.',
    lockNow: '잠그기',
    lockDone: '잠갔어요. 다시 들어갈 때 핀번호를 물어요',
    pinBlocked: '핀번호를 여러 번 틀려 약 {minutes}분 동안 넣을 수 없어요. 「핀번호 바꾸기」로 새로 정하면 바로 풀려요.',

    // 켜기 · 핀번호 바꾸기 대화상자
    dialogEnableTitle: '19금 게시판 접근 켜기',
    dialogEnableText: '계정 비밀번호를 확인하고, 성인 전용 게시판에 들어갈 때 넣을 핀번호를 정해요.',
    dialogEnableTextSocial: '성인 전용 게시판에 들어갈 때 넣을 핀번호를 정해요.',
    dialogEnableSubmit: '켜기',
    dialogChangeTitle: '핀번호 바꾸기',
    dialogChangeText: '계정 비밀번호를 확인하고 핀번호를 새로 정해요. 지금 핀번호를 몰라도 돼요.',
    dialogChangeTextSocial: '핀번호를 새로 정해요. 지금 핀번호를 몰라도 돼요.',
    dialogChangeSubmit: '바꾸기',
    passwordLabel: '계정 비밀번호',
    passwordRequired: '계정 비밀번호를 입력해 주세요.',
    noPasswordNote: '비밀번호가 없는 계정은 최근 10분 안에 로그인했는지로 확인해요.',
    reauthNeeded: '로그인한 지 10분이 넘었어요. 다시 로그인한 뒤 해 주세요.',
    reauthLogin: '다시 로그인',
    pinNewLabel: '새 핀번호',
    pinRule: '숫자 6자리',
    pinConfirmLabel: '핀번호 한 번 더',
    pinInvalid: '핀번호는 숫자 6자리로 넣어 주세요.',
    pinMismatch: '두 핀번호가 달라요.',

    // 핀 입력 화면
    gateTitle: '핀번호를 넣어 주세요',
    gateText: '성인 전용 게시판이에요. 설정에서 정한 핀번호 6자리를 넣으면 볼 수 있어요.',
    gateChecking: '확인하는 중이에요',
    gateWrong: '핀번호가 달라요. {n}번 더 틀리면 10분 동안 잠겨요',
    gateWrongPlain: '핀번호가 달라요.',
    gateLocked: '핀번호를 여러 번 틀려 잠겼어요. {time} 뒤에 다시 넣을 수 있어요',
    gateForgot: '핀번호를 잊었어요',
    gateExpired: '핀번호를 확인한 지 시간이 지났어요. 다시 넣으면 하던 일을 이어서 할 수 있어요.',
};
