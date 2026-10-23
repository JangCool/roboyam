/**
 * 서식 에디터(`RichEditor`)·글 보기 손질(`UtilRichBody`)의 문구 — 에디터 안의 단추 이름·안내·올리기 상태만 안다.
 * 칸 이름·저장 안내처럼 **화면마다 다른 문구는 쓰는 앱의 문구 파일**이 갖는다. **서버 `message` 가 있으면 그것을 먼저 쓴다.** `en/richEditor.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 도구 모음
    toolbar: '글 서식',
    body: '본문',
    heading: '제목',
    paragraph: '본문',
    heading2: '제목 2',
    heading3: '제목 3',
    textColor: '글자색',
    backgroundColor: '배경색',
    colorDefault: '기본 색',
    backgroundNone: '배경 없음',
    color: {
        red: '빨강',
        orange: '주황',
        yellow: '노랑',
        green: '초록',
        blue: '파랑',
        purple: '보라',
        gray: '회색',
        lime: '연두',
        sky: '하늘',
        pink: '분홍',
    },
    bold: '굵게',
    italic: '기울임',
    underline: '밑줄',
    strike: '취소선',
    bulletList: '글머리 목록',
    orderedList: '번호 목록',
    blockquote: '인용',
    code: '코드',
    codeInline: '인라인 코드',
    codeBlock: '코드 블록',
    link: '링크',
    horizontalRule: '구분선',
    image: '이미지 넣기 (JPG·PNG·GIF·WebP)',
    youtube: '유튜브 영상 넣기',

    // 본문 속 이미지·유튜브
    imageAlt: '본문 이미지',
    imageRemove: '이미지 지우기',
    imageBroken: '이미지를 불러오지 못했어요',
    imageBrokenHint: '네트워크가 불안정하거나 이미지가 지워졌을 수 있어요.',
    youtubeLabel: '유튜브 영상',
    youtubeRemove: '유튜브 영상 지우기',
    youtubePlay: '유튜브 영상 재생',
    youtubeFrame: '유튜브 영상',
    imageNotAllowed: '여기에서는 이미지를 올릴 수 없어요',
    mediaCount: '이미지 {images}장 · 유튜브 {videos}개',

    // 링크 넣기
    linkTitle: '링크 넣기',
    linkDescription: 'http:// 또는 https:// 주소를 입력해 주세요. 글자를 골라 두었다면 그 글자에 링크가 걸려요.',
    linkUrl: '링크 주소',
    linkRemove: '링크 해제',
    linkInvalid: 'http:// 또는 https:// 로 시작하는 올바른 주소를 입력해 주세요.',

    // 유튜브 넣기
    youtubeTitle: '유튜브 영상 넣기',
    youtubeUrl: '유튜브 주소',
    youtubeHelp: '유튜브 주소만 넣을 수 있어요. 영상 파일은 직접 올릴 수 없어요.',
    youtubePreview: '영상 썸네일 미리보기',
    youtubeInsert: '본문에 넣기',
    youtubeRequired: '유튜브 영상 주소를 넣어 주세요.',
    youtubeInvalid: '유튜브 영상 주소가 아니에요. youtu.be/… 또는 youtube.com/watch?v=… 주소를 넣어 주세요.',

    // 이미지 올리기 띠
    dropZoneLabel: '이미지 올리기. 파일을 끌어다 놓거나 눌러서 고르세요.',
    dropZoneDrop: '여기에 놓으면 올라가요',
    dropZoneText: '이미지를 끌어다 놓거나 눌러서 올리세요',
    dropZoneFormats: 'JPG·PNG·GIF·WebP',
    dropZoneCollapse: '이미지 올리기 영역 접기',
    dropZoneExpand: '이미지 올리기 영역 펼치기',

    // 올리기 목록
    uploadListLabel: '이미지 올리기 현황',
    uploadFailedTitle: '올리지 못한 이미지가 있어요',
    uploadFailedText: '실패한 파일을 지우거나 다시 시도해야 저장할 수 있어요.',
    uploadingCount: '이미지 올리는 중 · {n}장',
    uploadingStay: '올리는 동안 나가지 마세요. 나가면 올리던 이미지가 취소돼요.',
    uploadingItem: '{name} 올리는 중',
    retry: '다시 시도',
    retryItem: '{name} 다시 시도',
    removeUpload: '삭제',
    removeItem: '{name} 삭제',
    cancelUpload: '취소',
    cancelItem: '{name} 올리기 취소',
    uploadQueued: '대기 중 · {size}',
    uploadProcessing: '서버에서 처리하는 중 · {size}',
    uploadProgress: '{percent}% · {size}',

    // 올리기 실패(서버 문구가 없을 때만)
    uploadUnsupported: '형식 오류. JPG·PNG·GIF·WebP 만 올릴 수 있어요.',
    uploadEmpty: '비어 있는 파일이에요. 다른 이미지를 골라 주세요.',
    uploadTooLarge: '용량이 너무 커요. 더 작은 이미지를 골라 주세요.',
    uploadNetwork: '네트워크 문제로 올리지 못했어요. 잠시 뒤 다시 시도해 주세요.',
    uploadFailedAnnounce: '이미지를 올리지 못했어요. {name}. {message}',
    uploadedAnnounce: '이미지를 넣었어요. {name}',
};
