/**
 * 게시판 API 가 주고받는 모양 — 메뉴·셸이 쓰는 게시판 요약(`BoardView`)과 공개 게시판 화면이 쓰는 게시판 하나(`BoardDetail`)·공개 표시 기본값(`BoardDisplaySettings`).
 * 글·댓글의 모양은 `usePostApi.types.ts`·`useCommentApi.types.ts` 에 있다. `BoardView.postKinds`(글 유형)는 community 의 칸이라 뺐다.
 * 정책 값은 관리자 앱의 게시판 설정(`apps/admin-web/app/composables/board/api/useBoardApi.types.ts` — `BoardDefaults`·`BoardSecuritySettings`)의 **유효값**(게시판 값 ?? 기본값)을 공개 화면에 필요한 만큼만 받는다. 서버 `server/api/.../board/dto/response` 와 같다(2026-10-22 연결).
 * ⚠ ID 는 모두 문자열이다(Snowflake 64비트). 숫자로 바꾸지 않는다.
 */
import type { AffiliateMode, DealRules } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { BoardPendingWarning, BoardStaffTools, BoardWriteRestriction } from '~/composables/board/api/useBoardStaffApi.types';

export interface PrefixView {
    id: string;
    name: string;
    /** 운영진·운영자만 고를 수 있다(공지) */
    staffOnly: boolean;
    /** 공통 말머리(질문·후기·잡담·공지) */
    common: boolean;
    /** 운영자가 고른 표시색의 프리셋 이름 또는 null(자동). 서버가 아직 안 주면(옛 응답) `undefined` — 자동으로 그린다. 운영진 전용 말머리는 값과 무관하게 회색 */
    color?: string | null;
    /** 성인 전용 — 고르면 서버가 글을 19금으로 저장한다(글쓰기 화면은 19금 체크를 켜고 잠근다, adult-content-contract). 옛 응답은 `undefined` = false */
    adultOnly?: boolean;
}

export interface BoardView {
    id: string;
    slug: string;
    name: string;
    /** 선택 영문명 — 언어가 `en` 이고 비어 있지 않으면 `name` 대신 보인다(`UtilI18n.localizedName`). 서버가 아직 안 주거나 비었으면 null/없음 */
    nameEn?: string | null;
    description: string | null;
    /** 산업·주식 — 목록 머리와 글마다 투자 고지를 붙인다 */
    investmentNotice: boolean;
    /** 운영진·운영자만 글을 쓸 수 있는 게시판(공지) — `writeMin === 'STAFF'` 에서 나온 파생값이다 */
    staffOnlyWrite: boolean;
    /**
     * 글쓰기 최소 자격(게시판 설정 유효값, `docs/features/board-contract.md`) — `GUEST` 비회원 포함 모두 · 등급 키(등급 목록의 `gradeKey`, 그 등급 이상) · `STAFF` 운영진·그 게시판 매니저/스텝만.
     * 서버가 아직 안 주면(옛 응답) `undefined`
     */
    writeMin?: BoardAccess;
    /** 댓글 최소 자격 — `OFF` 이면 이 게시판은 새 댓글을 받지 않는다(유효값). 서버가 아직 안 주면(옛 응답) `undefined` */
    commentMin?: BoardCommentAccess;
    /** 읽기 전용 잠금 — `POST` 새 글만 막음 · `POST_COMMENT` 새 글·새 댓글 막음 · `null` 잠금 없음. 글은 그대로 읽을 수 있다. 서버가 아직 안 주면(옛 응답) `undefined` */
    writeLock?: BoardWriteLock | null;
    /**
     * 글쓰기·수정 에디터에서 새 이미지를 올릴 수 있는가(유효값) — 고급 에디터(`postEditor === 'ADVANCED'`)에서만 뜻이 있다. 거짓이면 이미지 단추·올리기 띠·붙여넣기·끌어놓기를 막는다.
     * 서버가 아직 안 주면(옛 응답) `undefined` — 그때는 **허용 안 함**으로 본다(`UtilBoard.imageUploadOn`, 공개 게시판 계약 「에디터 화면」)
     */
    imageUploadAllowed?: boolean;
    /** 글 에디터 수준(유효값) — `NONE` 평문 Textarea · `SIMPLE` 간단 · `ADVANCED` 고급. 서버가 아직 안 주면(옛 응답) `undefined` — `NONE` 으로 본다 */
    postEditor?: PostEditorLevel;
    /** 댓글 에디터 수준(유효값) — `NONE` 평문 · `SIMPLE` 간단. 서버가 아직 안 주면(옛 응답) `undefined` — `NONE` 으로 본다 */
    commentEditor?: CommentEditorLevel;
    /** 목록 모양 — `LIST` 글 행 · `CARD` 썸네일 카드(핫딜 같은 카드) · `GALLERY` 썸네일 격자(사진·영상). slug 로 짐작하지 않는다 */
    layout: BoardLayout;
    /**
     * 핫딜 게시판인가(핫딜 단계) — true 면 글쓰기에 딜 정보가 필수이고 목록은 딜 카드로 그린다. slug 로 짐작하지 않는다.
     * 서버가 아직 안 주면(옛 응답) `undefined` — 그때는 일반 게시판이다
     */
    dealEnabled?: boolean;
    /**
     * 게시판 종류(관리자 `BoardKind` 와 같은 값 — 핫딜 계약 `docs/features/hotdeal-contract.md`) — `DEAL` 이면 목록은 가격 줄 목록형, 글쓰기에 딜 칸, 글 보기에 딜 카드.
     * 서버가 아직 안 주면(옛 응답) `undefined` — 일반 게시판으로 본다(판정은 `UtilHotdeal.isDealBoard` 한 곳)
     */
    kind?: BoardKind;
    /**
     * 직접홍보 게시판인가(`docs/api-promo.md`) — true 면 사업자만 글을 쓰고 모든 글이 광고다(목록 머리 안내문·글 행 [광고]·추천 단추 없음). slug 로 짐작하지 않는다.
     * 서버가 아직 안 주면(옛 응답) `undefined` — 그때는 일반 게시판이다
     */
    promo?: boolean;
    /**
     * 보기 전환 버튼을 보일지(4차 계약 A절) — true 면 목록 위에 [게시판형 | 갤러리형] 단추가 보이고 읽는 사람의 선택을 이 브라우저에 게시판별로 기억한다.
     * `layout` 이 `LIST`·`GALLERY` 일 때만 켤 수 있다. 서버가 아직 안 주면(옛 응답) `undefined` — 그때는 단추가 없고 `layout` 대로만 보인다
     */
    viewToggle?: boolean;
    /** 이 게시판은 19금 글을 허용하는가(게시판 설정의 마스터 스위치) — true 면 머리에 성인 확인 안내가 붙는다. 서버가 아직 안 주면(옛 응답) `undefined` */
    allowAdult?: boolean;
    /** 사용 중인 말머리만, 정렬 순서대로 */
    prefixes: PrefixView[];
    /** 운영자가 고른 게시판 표시색의 프리셋 이름 또는 null(자동 — 화면이 순서로 정한다). 서버가 아직 안 주면(옛 응답) `undefined` */
    color?: string | null;
    /**
     * 비밀글(게시판마다의 값 — 기본 설정에서 물려받지 않는다) — `OFF` 없음 · `OPTIONAL` 글쓴이가 고른다 · `ALWAYS` 모든 글 비밀. 설정을 바꿔도 이미 쓴 글의 비밀 여부는 그대로다.
     * `GET /api/boards`·`/api/boards/{slug}` 둘 다 준다
     */
    secretMode?: BoardSecretMode;
    /** 처리 상태 쓰기(게시판마다의 값, 기본 꺼짐) — 켜면 목록 줄·글 보기에 처리 상태 딱지, 목록 위 상태 거르기, 운영진의 상태 바꾸기 */
    statusEnabled?: boolean;
    /**
     * 글 목록 줄의 말머리·상태 칩 위치(post-chip-position-contract) — 서버가 게시판 값 ?? 사이트 기본을 푼 **실제 값**.
     * 서버가 아직 안 주면(옛 응답) `undefined` — 제목 위(`ABOVE`)로 본다(`UtilBoard.chipPosition`)
     */
    chipPosition?: PostChipPosition;
    /**
     * 글 목록 줄·글 보기 머리의 말머리 모양(mobile-nav-prefix-style-contract 1절) — 서버가 게시판 값 ?? 사이트 기본을 푼 **실제 값**.
     * 서버가 아직 안 주면(옛 응답) `undefined` — 글자(`TEXT`)로 본다(`UtilBoard.prefixStyle`)
     */
    prefixStyle?: PostPrefixStyle;
    /**
     * 작성자 표시(anonymous-board-contract) — `NICKNAME` 닉네임(기본) · `ANONYMOUS` 글·댓글을 「익명」으로 올린다(글쓰기·댓글 입력 위 안내).
     * 익명 여부는 쓸 때 서버가 이 값으로 정해 글·댓글에 저장한다 — 보이는 이름은 늘 각 작성자 칸(`author.kind`)을 따른다. 옛 응답(칸 없음)은 `NICKNAME`
     */
    authorMode?: BoardAuthorMode;
}

/** 게시판 작성자 표시 — 닉네임 · 익명 */
export type BoardAuthorMode = 'NICKNAME' | 'ANONYMOUS';

/**
 * 글 목록 줄의 말머리·상태 칩 위치 — `ABOVE` 제목 위 칩 줄(기본) · `BEFORE` 제목 앞(한 줄 안) · `BELOW` 닉네임(메타) 줄 아래 칩 줄.
 * 여러 줄로 그리는 목록만 따른다 — PC 표형 한 줄·갤러리 카드 이름 줄·고정 공지 한 줄은 늘 `BEFORE`
 */
export type PostChipPosition = 'ABOVE' | 'BEFORE' | 'BELOW';

/**
 * 말머리 모양 — `TEXT` 칩 없이 12px 글자(지정 색은 그 색 글자, 「자동」·운영진 전용은 회색, 여럿이면 `사용기 | 기타`) · `MUTED` 회색 칩(지정 색 무시) · `COLOR` 색상 칩(지금 모양).
 * 말머리와 섞인 목록의 게시판 이름에만 쓴다 — 처리 상태·19·투표·광고·소모임 공개 라벨·「어제/지난주」는 모양과 상관없이 그대로(mobile-nav-prefix-style-contract 1절)
 */
export type PostPrefixStyle = 'TEXT' | 'MUTED' | 'COLOR';

/** 비밀글 방식(게시판 설정 `secretMode`) — 없음 · 글쓴이가 고름 · 모든 글 비밀 */
export type BoardSecretMode = 'OFF' | 'OPTIONAL' | 'ALWAYS';

/** 게시판 종류 — 일반 · 핫딜 · 직접홍보 · 소모임(관리자 `apps/admin-web/.../useBoardApi.types.ts` 의 `BoardKind` 와 같다) */
export type BoardKind = 'GENERAL' | 'DEAL' | 'PROMO' | 'GROUP';

/** 글 에디터 수준(관리자 게시판 설정 `postEditor`) — 평문 · 간단 · 고급 */
export type PostEditorLevel = 'NONE' | 'SIMPLE' | 'ADVANCED';
/** 댓글 에디터 수준(관리자 게시판 설정 `commentEditor`) — 평문 · 간단 */
export type CommentEditorLevel = 'NONE' | 'SIMPLE';

/**
 * 게시판 목록 모양 — `LIST` 목록형(제목이 한 줄씩) · `CARD` 카드형(제목·요약 카드, 핫딜처럼) · `GALLERY` 갤러리형(사진·영상 썸네일 격자, 홈의 갤러리 가젯에 쓸 수 있다).
 * 모르는 값은 목록형으로 그린다(서버만 먼저 새 모양을 배포해도 화면이 안 깨진다)
 */
export type BoardLayout = 'LIST' | 'CARD' | 'GALLERY';

/**
 * 글쓰기를 막는 까닭 (게시판 정보 응답의 `writeBlockedReason`). `BUSINESS_ACCOUNT_RESTRICTED` 는 사업자 전용 계정(H-2) — 일반 게시판 글쓰기는 서버가 `403` 으로 막는다.
 * `BUSINESS_NOT_APPROVED`(H-4)는 사업자 전용 계정이 핫딜 게시판에 쓰는데 승인된 사업자가 아니다(승인 대기·반려·정지) — 사업자 센터로 안내한다.
 * `PROMO_*` 는 직접홍보 게시판(`docs/api-promo.md` 4절)의 글쓰기 자격 — `PROMO_BUSINESS_ONLY` 승인된 사업자만 쓴다 · `PROMO_PERIOD_REQUIRED` 진행 중인 승인된 홍보가 없다 · `PROMO_DAILY_LIMIT` 오늘 쓸 수 있는 글을 다 썼다.
 * 서버가 게시판 응답에 어떤 값으로 주는지는 계약에 없어(`write-status` 의 `reason` 이 더 자세하다) 화면이 `UtilPromo.normalizeBlocked` 로 이 셋으로 모은다.
 * `GROUP_*` 는 소모임 게시판 정보(`GET /api/groups/{id}/board` — 서버 `GroupPostService.board`)의 `writeBlockedReason` 에만 온다 — `GROUP_NOT_MEMBER` 멤버가 아니다 ·
 * `GROUP_DISSOLVED` 해산한 소모임(새 글만 막는다 — 공개로 남은 글의 댓글은 열려 있어 `commentBlockedReason` 에는 오지 않는다)
 */
export type WriteBlockedReason =
    | 'LOGIN_REQUIRED'
    | 'EMAIL_NOT_VERIFIED'
    | 'ACCOUNT_RESTRICTED'
    | 'STAFF_ONLY'
    | 'WRITE_LOCKED'
    | 'GRADE_TOO_LOW'
    | 'BOARD_RESTRICTED'
    | 'COMMENTS_OFF'
    | 'BUSINESS_ACCOUNT_RESTRICTED'
    | 'BUSINESS_NOT_APPROVED'
    | 'PROMO_BUSINESS_ONLY'
    | 'PROMO_PERIOD_REQUIRED'
    | 'PROMO_DAILY_LIMIT'
    | 'GROUP_NOT_MEMBER'
    | 'GROUP_DISSOLVED'
    /** 이 게시판에서 쓰기 제한 중(게시판 제재 — 끝 시각·사유는 `viewer.writeRestriction`). 다른 까닭이 없을 때 서버가 이 값을 준다(board-sanction-contract). 쓰기 API 거절은 `403 BOARD_WRITE_RESTRICTED` + `until`·`boardSanctionId`·`reasonCode` */
    | 'BOARD_WRITE_RESTRICTED';

/** 최소 자격의 「비회원 포함 모두」 값 — 게시판별로만 고른다(관리자 `BOARD_MIN_GUEST` 와 같은 값) */
export const BOARD_MIN_GUEST = 'GUEST';
/** 최소 자격의 「운영진만」 값 — 운영진과 그 게시판의 매니저·스텝만 */
export const BOARD_MIN_STAFF = 'STAFF';
/** 댓글 최소 자격의 「끔」 값 — 새 댓글을 받지 않는다(기존 댓글은 보인다) */
export const BOARD_MIN_OFF = 'OFF';

/**
 * 글쓰기·댓글 최소 자격(게시판 설정) — `GUEST` · 등급 키 · `STAFF`. 등급은 운영자가 정하는 목록이라 고정 enum 이 아니다(글자).
 * 화면은 값을 비교하지 않는다 — 판정(`canWrite`·`writeBlockedReason`)과 필요한 등급 이름(`writeMinGradeLabel`)은 서버가 준다.
 */
export type BoardAccess = typeof BOARD_MIN_GUEST | string | typeof BOARD_MIN_STAFF;
/** 댓글 최소 자격 — 글쓰기 자격 + `OFF` */
export type BoardCommentAccess = typeof BOARD_MIN_OFF | BoardAccess;
/** 읽기 전용 잠금의 범위 — 새 글만 · 새 글과 새 댓글 */
export type BoardWriteLock = 'POST' | 'POST_COMMENT';

/** 날짜 표시 방식 — 표준형(최근은 N분/N시간 전, 올해는 월-일, 오래된 글은 연-월-일) · 유동형(모두 상대 시간). 관리자 기본 설정 `dateDisplay` */
export type BoardDateDisplay = 'STANDARD' | 'RELATIVE';
/**
 * 비회원에게 가려진 댓글(`DELAYED`) 자리의 안내 방식 — 관리자 게시판 기본 설정 `commentDelayNotice`(사이트 전체, board-public-contract 결정 2 정정 2026-11-12).
 * `TIME`(기본) 「N시간 후에 볼 수 있어요 · 지금 보려면 로그인하세요」(남은 시간 = 댓글의 `visibleAt` − 지금) · `LOGIN` 「로그인하면 바로 볼 수 있어요」(시간을 알리지 않는다).
 */
export type CommentDelayNotice = 'TIME' | 'LOGIN';
/** 댓글 많은 글(보호 중) 삭제 방식 — 막기 · 예약 삭제 · 바로 삭제 */
export type BoardProtectDelete = 'BLOCK' | 'DELAY' | 'ALLOW';

/**
 * 공개 표시 기본값 — `GET /api/site/info` 응답의 `boardDisplay`(서버 `BoardDisplayView`). 로그인 없이 받는다.
 * `dateDisplay` 는 관리자 기본 설정의 값, `newPostHours` 는 「새 글」 표시 기준(시간) — 관리자 설정에 없는 값이라 서버 상수로 가정(24).
 */
export interface BoardDisplaySettings {
    dateDisplay: BoardDateDisplay;
    newPostHours: number;
    /** 섞인 목록(최신글·인기글·검색·팁 등)의 말머리·상태 칩 위치 — 관리자 게시판 기본 설정 `listChipPosition`. 옛 응답에 없으면 걸러 낼 때 `ABOVE`(`UtilSiteInfo.cleanBoardDisplay`) */
    listChipPosition: PostChipPosition;
    /** 섞인 목록의 말머리·게시판 이름 모양 — 관리자 게시판 기본 설정 `listPrefixStyle`. 옛 응답에 없으면 걸러 낼 때 `TEXT`(`UtilSiteInfo.cleanBoardDisplay`) */
    listPrefixStyle: PostPrefixStyle;
    /** 글 줄 추천 수 강조 기준(관리자 기본 설정 `listLikeHighlight`) — 이 수 이상이면 숫자를 강조 색·굵게. 0 = 강조 안 함. 옛 응답에 없으면 10 */
    likeHighlight: number;
    /** 글 줄 조회 수 강조 기준(`listViewHighlight`) — 0 = 강조 안 함. 옛 응답에 없으면 1000 */
    viewHighlight: number;
    /** 강조 색 원색 `#rrggbb`(`listHighlightColor`) — 그릴 때 라이트·다크 바탕에서 읽히게 명도만 보정한다(`UtilColor.cssPair`). 옛 응답·틀린 값이면 `#e5484d` */
    highlightColor: string;
    /** 비회원에게 가려진 댓글 자리의 안내 방식(관리자 기본 설정 `commentDelayNotice`) — 옛 응답·틀린 값이면 `TIME`(`UtilSiteInfo.cleanBoardDisplay`) */
    commentDelayNotice: CommentDelayNotice;
}

/** 공개 화면이 쓰는 정책 유효값(게시판 값 ?? 기본값) — 관리자 `BoardDefaults` 의 같은 이름 칸 */
export interface BoardPublicPolicy {
    /** 목록 한 쪽 글 수 10~100 */
    listSize: number;
    /** 고정 공지 최대 0~5 */
    pinnedMax: number;
    /** 댓글 많은 글 보호 기준(보이는 댓글 수) 0 = 끔 */
    commentProtectMin: number;
    /** 보호 중인 글의 수정도 막는다 */
    commentProtectEdit: boolean;
    commentProtectDelete: BoardProtectDelete;
    /** 예약 삭제까지의 시간(분) */
    commentProtectDelayMinutes: number;
    /** 비회원에게 댓글을 늦게 보이는 시간(분) 0 = 바로 */
    commentGuestDelayMinutes: number;
    /** 베스트 댓글 최소 추천 수 */
    bestCommentMinLikes: number;
}

/** 비회원 글·댓글 규칙 — 관리자 보안 설정(`BoardSecuritySettings.guest*`)의 공개해도 되는 값 */
export interface BoardGuestRules {
    /** 자동입력 방지를 거쳐야 한다 */
    captchaRequired: boolean;
    nameMin: number;
    nameMax: number;
    /** 수정·삭제 비밀번호 최소 길이 */
    passwordMin: number;
    /** 비밀번호 최대 길이 — 관리자 설정에 없는 값이라 서버 상수로 가정(20) */
    passwordMax: number;
    /** 글·댓글당 외부 링크 수, 0 = 링크 금지 */
    linkLimit: number;
    /** 비회원 이미지 올리기 */
    imageUploadAllowed: boolean;
}

/** 새 회원(첫 등급) 규칙 — 관리자 보안 설정 `newMemberLinkLimit`·`newMemberImageLimit`. `applies` 는 지금 사용자가 첫 등급인가 */
export interface BoardNewMemberRules {
    applies: boolean;
    linkLimit: number;
    imageLimit: number;
}

/** 지금 보는 사람 기준의 판정 — 서버가 정한다(화면은 편의로 숨기거나 안내만 한다) */
export interface BoardViewer {
    /** 로그인하지 않았다 */
    guest: boolean;
    /** 이 게시판의 매니저·스텝(운영자는 공개 사이트에 로그인하지 않는다) — 운영진 전용 말머리를 고를 수 있고 잠금에서 면제된다 */
    staff: boolean;
    canWrite: boolean;
    writeBlockedReason: WriteBlockedReason | null;
    canComment: boolean;
    /** 댓글을 못 쓰는 까닭 — `COMMENTS_OFF`(댓글 받지 않음) · `LOGIN_REQUIRED` · `GRADE_TOO_LOW` · `STAFF_ONLY` · `WRITE_LOCKED` · `ACCOUNT_RESTRICTED` */
    commentBlockedReason: WriteBlockedReason | null;
    /**
     * 그 게시판에서 **제재를 줄 수 있는** 매니저·스텝 본인에게만 — 오늘 남은 제재 한도·매니저 여부(board-sanction-contract). 그 밖·소모임 게시판은 null.
     * 역할 권한 `board.sanction.issue` 가 없거나 계정이 정상·인증이 아니면 자리가 있어도 null 이다
     */
    staffTools: BoardStaffTools | null;
    /** 보는 내가 이 게시판에서 쓰기 제한 중이면 끝 시각·사유(처리자 없음), 아니면 null */
    writeRestriction: BoardWriteRestriction | null;
    /** 보는 내가 이 게시판에서 받고 아직 확인하지 않은 경고(최근 것이 위) — 게시판 머리 안내. 비로그인·소모임 게시판은 빈 목록(옛 서버는 칸 없음) */
    pendingWarnings?: BoardPendingWarning[];
}

/**
 * 게시판 하나 — `GET /api/boards/{slug}`. 게시판 요약 + 유효 정책 + 지금 보는 사람의 판정.
 * 꺼진 게시판은 `403 BOARD_INACTIVE`, 없는 게시판은 `404 BOARD_NOT_FOUND`.
 */
export interface BoardDetail extends BoardView {
    description: string | null;
    writeMin: BoardAccess;
    commentMin: BoardCommentAccess;
    /** `writeMin`·`commentMin` 이 등급 키일 때 그 등급의 이름(안내 문구용) — 아니면 null */
    writeMinGradeLabel: string | null;
    commentMinGradeLabel: string | null;
    writeLock: BoardWriteLock | null;
    viewToggle: boolean;
    allowAdult: boolean;
    /** 관리자 「이 게시판에 광고 숨김」 — 켜면 목록 한 줄·글 보기·핫딜 노출이 없다(19금 게시판은 이 칸과 상관없이 없다) */
    adsHidden: boolean;
    imageUploadAllowed: boolean;
    /** 게시판 안내문 — 평문(줄바꿈 유지), null = 없음 */
    introText: string | null;
    /** 글쓰기 안내문 — 평문, null = 없음 */
    writeGuideText: string | null;
    /** 글 양식 — 새 글 본문에 미리 채우는 평문(V153), null = 없음. 옛 서버 응답에는 칸이 없을 수 있다 */
    writeTemplate?: string | null;
    /** 신고 기능 사용(관리자 신고 정책 `reportEnabled`) */
    reportEnabled: boolean;
    /**
     * 글쓴이(회원)가 글쓰기·고치기에서 찬반 토론을 열 수 있다(유효 정책 `debateEnabled` — post-debate-contract). 옛 응답에는 없을 수 있다(없으면 끔).
     * 가정: 게시판 응답의 맨 위 칸(`allowAdult`·`reportEnabled` 와 같은 자리)
     */
    debateEnabled?: boolean;
    /**
     * 글쓴이(회원)가 글쓰기·고치기에서 투표를 붙일 수 있다(유효 정책 `pollEnabled` — post-poll-contract, 기본 끔). 옛 응답에는 없을 수 있다(없으면 끔).
     * 가정: `debateEnabled` 와 같은 자리(게시판 응답의 맨 위 칸)
     */
    pollEnabled?: boolean;
    /**
     * 핫딜 게시판(`kind DEAL`)의 지금 제휴 링크 방식(D5 — 핫딜 설정 `affiliateMode`) — 쓰기·고치기가 「제휴 링크예요」 체크를 보일지 정한다. 핫딜이 아니면 null(`MEMBER` 로 본다).
     * 판정 응답(`DealLinkCheck.affiliateMode`)이 오면 그 값이 우선(`UtilHotdeal.affiliateModeOf`)
     */
    dealAffiliateMode?: AffiliateMode | null;
    /** 핫딜 게시판이면 핫딜 설정 공개 칸(금액 상한·할인율 경고·기간 종료 일수·HOT 문턱), 아니면 null — 화면은 `UtilHotdeal.rulesOf` 로 읽는다(없으면 기본값) */
    dealRules?: DealRules | null;
    policy: BoardPublicPolicy;
    guestRules: BoardGuestRules;
    newMemberRules: BoardNewMemberRules;
    viewer: BoardViewer;
}
