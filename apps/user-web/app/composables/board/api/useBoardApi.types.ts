/**
 * 게시판 API 가 주고받는 모양 — 메뉴·셸이 쓰는 게시판 요약(`BoardView`)과 공개 게시판 화면이 쓰는 게시판 하나(`BoardDetail`)·공개 표시 기본값(`BoardDisplaySettings`).
 * 글·댓글의 모양은 `usePostApi.types.ts`·`useCommentApi.types.ts` 에 있다. `BoardView.postKinds`(글 유형)는 community 의 칸이라 뺐다.
 * 정책 값은 관리자 앱의 게시판 설정(`apps/admin-web/app/composables/board/api/useBoardApi.types.ts` — `BoardDefaults`·`BoardSecuritySettings`)의 **유효값**(게시판 값 ?? 기본값)을 공개 화면에 필요한 만큼만 받는다. 서버 `server/api/.../board/dto/response` 와 같다(2026-10-22 연결).
 * ⚠ ID 는 모두 문자열이다(Snowflake 64비트). 숫자로 바꾸지 않는다.
 */

export interface PrefixView {
    id: string;
    name: string;
    /** 운영진·운영자만 고를 수 있다(공지) */
    staffOnly: boolean;
    /** 공통 말머리(질문·후기·잡담·공지) */
    common: boolean;
    /** 운영자가 고른 표시색의 프리셋 이름 또는 null(자동). 서버가 아직 안 주면(옛 응답) `undefined` — 자동으로 그린다. 운영진 전용 말머리는 값과 무관하게 회색 */
    color?: string | null;
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
     * 글쓰기·수정 에디터에서 새 이미지를 올릴 수 있는가(유효값, `docs/api-board-settings.md` 16절, ADM-043) — 거짓이면 이미지 단추·올리기 띠·붙여넣기·끌어놓기를 막는다.
     * 서버가 아직 안 주면(옛 응답) `undefined` — 그때는 허용으로 본다(`UtilMedia.imageUploadAllowed`)
     */
    imageUploadAllowed?: boolean;
    /** 목록 모양 — `LIST` 글 행 · `CARD` 썸네일 카드(핫딜 같은 카드) · `GALLERY` 썸네일 격자(사진·영상). slug 로 짐작하지 않는다 */
    layout: BoardLayout;
    /**
     * 핫딜 게시판인가(핫딜 단계) — true 면 글쓰기에 딜 정보가 필수이고 목록은 딜 카드로 그린다. slug 로 짐작하지 않는다.
     * 서버가 아직 안 주면(옛 응답) `undefined` — 그때는 일반 게시판이다
     */
    dealEnabled?: boolean;
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
}

/**
 * 게시판 목록 모양 — `LIST` 목록형(제목이 한 줄씩) · `CARD` 카드형(제목·요약 카드, 핫딜처럼) · `GALLERY` 갤러리형(사진·영상 썸네일 격자, 홈의 갤러리 가젯에 쓸 수 있다).
 * 모르는 값은 목록형으로 그린다(서버만 먼저 새 모양을 배포해도 화면이 안 깨진다)
 */
export type BoardLayout = 'LIST' | 'CARD' | 'GALLERY';

/**
 * 글쓰기를 막는 까닭 (게시판 정보 응답의 `writeBlockedReason`). `BUSINESS_ACCOUNT_RESTRICTED` 는 사업자 전용 계정(H-2) — 일반 게시판 글쓰기는 서버가 `403` 으로 막는다.
 * `BUSINESS_NOT_APPROVED`(H-4)는 사업자 전용 계정이 핫딜 게시판에 쓰는데 승인된 사업자가 아니다(승인 대기·반려·정지) — 사업자 센터로 안내한다.
 * `PROMO_*` 는 직접홍보 게시판(`docs/api-promo.md` 4절)의 글쓰기 자격 — `PROMO_BUSINESS_ONLY` 승인된 사업자만 쓴다 · `PROMO_PERIOD_REQUIRED` 진행 중인 승인된 홍보가 없다 · `PROMO_DAILY_LIMIT` 오늘 쓸 수 있는 글을 다 썼다.
 * 서버가 게시판 응답에 어떤 값으로 주는지는 계약에 없어(`write-status` 의 `reason` 이 더 자세하다) 화면이 `UtilPromo.normalizeBlocked` 로 이 셋으로 모은다
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
    | 'PROMO_DAILY_LIMIT';

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
/** 댓글 많은 글(보호 중) 삭제 방식 — 막기 · 예약 삭제 · 바로 삭제 */
export type BoardProtectDelete = 'BLOCK' | 'DELAY' | 'ALLOW';

/**
 * 공개 표시 기본값 — `GET /api/site/info` 응답의 `boardDisplay`(서버 `BoardDisplayView`). 로그인 없이 받는다.
 * `dateDisplay` 는 관리자 기본 설정의 값, `newPostHours` 는 「새 글」 표시 기준(시간) — 관리자 설정에 없는 값이라 서버 상수로 가정(24).
 */
export interface BoardDisplaySettings {
    dateDisplay: BoardDateDisplay;
    newPostHours: number;
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
    imageUploadAllowed: boolean;
    /** 게시판 안내문 — 평문(줄바꿈 유지), null = 없음 */
    introText: string | null;
    /** 글쓰기 안내문 — 평문, null = 없음 */
    writeGuideText: string | null;
    /** 신고 기능 사용(관리자 신고 정책 `reportEnabled`) */
    reportEnabled: boolean;
    policy: BoardPublicPolicy;
    guestRules: BoardGuestRules;
    newMemberRules: BoardNewMemberRules;
    viewer: BoardViewer;
}
