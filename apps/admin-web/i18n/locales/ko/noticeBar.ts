/**
 * 공지 띠 설정(설정 > 일반 「공지 띠」 — `components/site/SiteNoticeBarSettings.vue`) 문구.
 * 키는 `noticeBar.무엇` 두 마디. `en/noticeBar.ts` 와 같은 키, 같은 차례. `<` `>` 는 따옴표 안에 둔다.
 */
export default {
    title: '공지 띠',
    intro: '공개 사이트 모든 화면의 메뉴 줄 아래에 한 줄 공지를 보여요. 방문자는 X 를 눌러 하루·1주일·1달·영원히 닫을 수 있고, 띠 내용이 바뀌면 닫아 둔 사람에게도 다시 보여요.',
    enabledLabel: '공지 띠 보이기',
    enabledHelp: '끄면 모든 화면에서 띠가 사라져요.',
    sourceLabel: '내용',
    sourceBoard: '공지 게시판 최근 글',
    sourceCustom: '직접 입력',
    boardHelp: '「{name}」 게시판의 최근 글을 5초마다 한 줄씩 넘겨 보여요. 누르면 그 글로 가요.',
    boardMissing: '공지 게시판(주소 notice)이 없어서 띠에 보일 글이 없어요. 게시판 관리에서 만들거나 직접 입력을 고르세요.',
    countLabel: '보여 줄 글 수',
    countOption: '{count}개',
    textLabel: '공지 문구',
    textHint: '1~{max}자',
    linkLabel: '누르면 갈 곳(선택)',
    linkHelp: '사이트 안 주소(/boards/notice)나 https:// 주소. 비우면 글자만 보여요.',
    previewLabel: '지금 띠에 보이는 줄(저장된 값)',
    previewEmpty: '보일 줄이 없어서 띠가 나오지 않아요.',
    previewOff: '꺼져 있어서 띠가 나오지 않아요.',
    loadFailed: '공지 띠 설정을 불러오지 못했어요',
    saved: '공지 띠 설정을 저장했어요',

    // 화면이 미리 검사한 오류
    textRequired: '공지 문구를 입력해 주세요.',
    textLength: '공지 문구는 {max}자 이하로 입력해 주세요.',
    textChars: '공지 문구에 "<" 와 ">" 를 넣을 수 없어요.',
    linkLength: '링크는 {max}자 이하로 입력해 주세요.',
    linkShape: '/ 로 시작하는 사이트 안 주소나 http(s):// 주소를 넣어 주세요.',
};
