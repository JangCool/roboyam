/**
 * 내 짤 문구 — 고르기 창 「짤」 탭 · 짤 메뉴(담기·크게 보기·신고) · 처음 안내 창 · 올리기 창 · 내 로보티콘 「내 짤」 칸.
 * 계약 `docs/features/my-jjal-contract.md`. 키는 `jjal.무엇` 두 마디(문제는 `jjal.problem.무엇`). `en/jjal.ts` 와 같은 키, 같은 차례. 서버 오류 문구는 서버가 준다(여기 없다).
 * ⚠ 문구 안의 `@`·`|` 는 글자가 아니다(`{'@'}`·`{'|'}` 로 감싼다).
 */
export default {
    // 고르기 창 「짤」 탭
    tab: '짤',
    tabTitle: '내 짤',
    upload: '올리기',
    count: '{count} / {limit}',
    pickerNote: '나만 쓰는 보관함이에요. 상점에 나오지 않아요.',
    pickerEmpty: '아직 짤이 없어요. 움짤을 올리거나 글·댓글의 짤을 담아 보세요.',
    pickAria: '짤 넣기',
    loading: '짤을 불러오는 중',
    loadFailed: '내 짤을 불러오지 못했어요.',
    failed: '잠시 후 다시 시도해 주세요.',
    tooManyComment: '댓글에는 짤을 {max}개까지 넣을 수 있어요.',
    tooManyPost: '글에는 짤을 {max}개까지 넣을 수 있어요.',
    inBody: '넣은 짤 {count}개',
    removeFromBody: '짤 빼기',

    // 글·댓글 속 짤
    blockedLabel: '내려간 짤',
    summaryWord: '(짤)',

    // 짤 메뉴
    menuTitle: '짤',
    menuCollect: '내 짤에 담기',
    menuView: '크게 보기',
    menuReport: '짤 신고',
    viewTitle: '짤 크게 보기',
    collected: '내 짤에 담았어요 {count} / {limit}',
    alreadyIn: '이미 내 짤에 있어요',
    full: '내 짤 {limit}개가 꽉 찼어요',
    organize: '정리하기',
    loginTitle: '로그인하고 짤을 담아 보세요',
    loginText: '마음에 드는 짤을 내 짤에 담아 두고 글·댓글에 쓸 수 있어요.',
    reportTitle: '짤 신고',
    reported: '신고했어요. 운영진이 확인할게요.',

    // 처음 안내 창
    introTitle: '내 짤 안내',
    introPrivate: '나만 보는 보관함이에요. 누가 담았는지는 보이지 않아요.',
    introShare: '댓글에 붙은 짤은 누구나 담을 수 있어요. 내 짤도요.',
    introReport: '신고로 내려간 짤은 모든 곳에서 함께 내려가요.',
    introKeep: '올린 사람이 지워도 담은 짤은 남아요.',
    introCheck: '확인했어요',
    introLater: '나중에',
    introContinue: '계속하기',

    // 올리기 창
    uploadTitle: '짤 올리기',
    uploadDrop: '여기에 끌어 놓거나',
    uploadChoose: '파일 고르기',
    uploadFormats: 'GIF · PNG · JPG · WebP',
    ruleSize: '2MB 이하 · 긴 변 300px 로 줄여 저장해요',
    ruleAnimated: '움짤은 10초 · 100장까지',
    ruleLimit: '최대 {limit}개까지 담아 둘 수 있어요',
    confirmClean: '음란물·불법촬영물·혐오 짤이 아니에요',
    confirmRights: '권리 침해 신고가 오면 내려갈 수 있어요',
    preparing: '줄이는 중…',
    preparedInfo: '{width}×{height} · {size}',
    preparedAnimated: '움짤 {frames}장 · {seconds}초',
    previewAlt: '올릴 짤 미리 보기',
    changeFile: '다른 파일',
    uploadSubmit: '올리기',
    uploaded: '내 짤에 올렸어요 {count} / {limit}',
    uploadFull: '내 짤 {limit}개가 꽉 찼어요. 정리한 뒤에 올려 주세요.',

    // 줄이기 문제
    problem: {
        unsupported: 'GIF·PNG·JPG·WebP 그림만 올릴 수 있어요.',
        webpAnimated: '이 브라우저에서는 움직이는 WebP 를 바꿀 수 없어요. GIF로 올려 주세요.',
        broken: '그림을 읽지 못했어요. 다른 파일로 해 주세요.',
        tooLong: '움짤이 {seconds}초예요. {max}초 이하로 줄여 주세요.',
        tooManyFrames: '움짤이 {frames}장이에요. {max}장 이하로 줄여 주세요.',
        resultTooBig: '줄여도 {max}가 넘어요. 더 짧은 움짤로 해 주세요.',
        sourceTooBig: '원본이 너무 커요. {max} 이하 파일로 해 주세요.',
    },

    // 내 로보티콘 「내 짤」 칸
    myTitle: '내 짤',
    myEmpty: '아직 짤이 없어요',
    myEmptyText: '움짤을 올리거나, 글·댓글의 짤을 눌러 「내 짤에 담기」로 모아 보세요.',
    mySelect: '고르기',
    mySelectDone: '그만 고르기',
    mySelected: '{count}개 고름',
    myRemove: '빼기',
    myItemAria: '짤 {n}',
    mySourceUpload: '올린 짤',
    mySourceCollect: '담은 짤',
    removeConfirmTitle: '고른 짤 {count}개를 내 짤에서 뺄까요?',
    removeConfirmText: '내 보관함에서만 빠져요. 이미 쓴 글·댓글의 짤은 그대로 보여요.',
    removed: '내 짤에서 {count}개를 뺐어요.',
    blockedNote: '숨겨진 짤 {count}개 — 권리 침해 신고로 내려갔어요. 쓴 댓글에서도 「내려간 짤」로 보여요.',
    blockedCell: '내려간 짤',
    myReorder: '순서 바꾸기',
    myReorderHint: '칸을 끌어 옮기거나 앞으로·뒤로 단추로 순서를 바꿔요. 고르기 창에도 이 순서로 보여요.',
    moveEarlier: '{n}번째 짤 앞으로',
    moveLater: '{n}번째 짤 뒤로',
    movedTo: '{n}번째로 옮겼어요',
    orderSaved: '내 짤 순서를 저장했어요.',
};
