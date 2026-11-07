/**
 * 프로필 아바타 문구 — 프로필 설정의 「프로필 아바타」 구획·포인트 상점 아바타 탭·내 아이템의 아바타 구획. 키는 `avatar.무엇`(카테고리 이름만 `avatar.category.<카테고리 ID>`).
 * 아바타 이름은 카탈로그 한글 이름(서버가 준다 — 여기 없다). 카테고리 이름은 카탈로그 이름의 「(남/여)」 꼬리표를 뺀 것. `en/avatar.ts` 와 같은 키, 같은 차례.
 */
export default {
    sectionTitle: '프로필 아바타',
    sectionHint: '고르면 바로 저장돼요 — 아래 닉네임·소개 저장과 따로예요.',
    change: '바꾸기',
    loading: '아바타를 불러오는 중',
    loadFailed: '아바타 목록을 불러오지 못했어요',
    categoriesLabel: '아바타 종류',
    moreInShop: '더 많은 아바타는 포인트 상점에서',
    uploadOwn: '직접 만든 그림 올리기',
    saved: '프로필 아바타를 바꿨어요',
    inUse: '사용 중',
    refundableCaption: '환불 가능',
    notForSale: '판매 중단',
    notForSaleText: '지금은 팔지 않는 아바타예요.',
    shopPickHint: '아바타를 누르면 내 프로필에 쓴 모습을 크게 볼 수 있어요.',
    previewNote: '아바타는 내 프로필, 글 보기와 글 목록의 작성자 자리에 보여요. 장착한 테두리는 그 둘레에 둘러요.',
    useNow: '바로 프로필로 쓰기',
    useInProfile: '프로필 설정에서 고르기',
    useInProfileShort: '프로필로 쓰기',
    useConfirmTitle: '이 아바타를 프로필로 쓸까요?',
    useConfirmText: '프로필로 쓰면 이 아바타는 환불할 수 없어요. 다른 아바타로 바꿔도 마찬가지예요.',

    // 움직이는 아바타 — 움직이는 닉네임 이미지와 하나만(나중에 고른 쪽이 남는다, `useAvatarChoice`)
    motionBadge: '움직임',
    motionAria: '움직이는 아바타 {name}',
    motionConflictTitle: '움직이는 아바타로 바꿀까요?',
    motionConflictText: '움직이는 아바타와 움직이는 닉네임 이미지는 하나만 쓸 수 있어요. 바꾸면 지금 닉네임 이미지는 쉬고 글자 닉네임이 보여요. 이미지는 지우지 않아서 닉네임 이미지 화면에서 다시 쓸 수 있고, 심사도 다시 받지 않아요.',
    motionConflictConfirm: '움직이는 아바타 쓰기',
    motionShopNote: '글 보기·댓글·프로필에서는 움직이고, 글 목록에서는 멈춘 그림으로 보여요. 움직이는 닉네임 이미지와는 하나만 쓸 수 있어요(나중에 고른 쪽이 남아요).',

    // 카테고리 이름(`UtilAvatar.categoryKey`)
    category: {
        basic: '기본형',
        hobby: '취미·캐릭터',
        job: '직업',
        sports: '운동',
        military: '군·경 특수직',
        transport: '항공·해양',
        animal: '동물',
        office: '사무직',
        factory: '공장·현장직',
        care: '의료·공공',
        service: '교육·서비스',
        'arts-driver': '예술·운전',
        sports2: '운동 2',
        traditional: '전통',
        motion: '움직이는 아바타',
        upload: '내가 올린 아바타',
    },
};
