import { ApiError } from '~/composables/common/useApiClient';
import type {
    BoardChipPosition,
    BoardColor,
    BoardDefaults,
    BoardDefaultsField,
    BoardDetail,
    BoardDisplayColor,
    BoardKind,
    BoardLayout,
    BoardListItem,
    BoardPolicy,
    BoardPolicyField,
    BoardPostEditor,
    BoardPrefix,
    BoardPrefixSaveItem,
    BoardPrefixStyle,
    BoardSaveRequest,
    BoardSecretMode,
    BoardAuthorMode,
    BoardWriteLock,
} from '~/composables/board/api/useBoardApi.types';
import { BOARD_MIN_GUEST, BOARD_POLICY_FIELDS } from '~/composables/board/api/useBoardApi.types';
import UtilColor from '~/utils/util-color';

/**
 * 칸 하나를 그리는 방식 — 숫자 글자 입력 · 켜고 끄기 · 고르기 · 등급 고르기(글쓰기·댓글)
 *  · 짧은 이름(`label` — 글자 `min`~`max`, 앞뒤 공백을 떼고 비면 null. 서버 `BoardFields.Kind.LABEL`)
 *  · 여러 개 고르기(`multi` — `choices` 안에서 겹치지 않게, 빈 목록 허용, 늘 `choices` 차례로. 서버 `BoardFields.Kind.MULTI`)
 *  · 색(`color` — `#rrggbb`. 기본 설정 화면이 따로 그린다 — `BoardHighlightColorField`)
 */
export type BoardFieldKind = 'number' | 'bool' | 'choice' | 'writeMin' | 'commentMin' | 'grade' | 'label' | 'multi' | 'color';
/** 숫자 칸의 단위 문구 키(`board.unit.*`) */
export type BoardUnit = 'seconds' | 'count' | 'days' | 'minutes' | 'hours' | 'people' | 'times' | 'likes' | 'chars';
/** 칸을 고치는 데 필요한 권한 */
export type BoardFieldPermission = 'board.settings' | 'board.staff.limit';

/** 칸 정의 — 기본 설정·게시판 설정이 같은 정의로 칸을 그리고 검사한다 */
export interface BoardFieldSpec {
    /** 칸 이름 — 문구 키 `board.field.<칸>` · `board.fieldHelp.<칸>` */
    field: string;
    kind: BoardFieldKind | 'dateDisplay';
    min?: number;
    max?: number;
    unit?: BoardUnit;
    /** 고르기 칸의 값들 — 문구 키는 `board.choice.<칸>.<값>` */
    choices?: readonly (string | number)[];
    /** 게시판별 덮어쓰기 가능 */
    overridable: boolean;
    permission: BoardFieldPermission;
}

/** 최소 자격 칸 검사 결과 */
export type BoardLevelProblem = 'GUEST_NOT_ALLOWED' | 'DEAL_FIRST_GRADE';
/** 숫자 칸 검사 결과 */
export type BoardNumProblem = 'REQUIRED' | 'NOT_INTEGER' | 'OUT_OF_RANGE';
/** 이름 칸 검사 결과 — 글자 수 초과 · 줄바꿈 등 제어 문자 */
export type BoardLabelProblem = 'LABEL_LENGTH' | 'LABEL_CHARS';
/** 정책 칸 하나의 화면 검사 결과(숫자 칸 + 이름 칸) — 문구는 `UtilBoardMessages.numProblem` */
export type BoardFieldProblem = BoardNumProblem | BoardLabelProblem;
/** 게시판 만들기·기본 정보·말머리 검사 결과 — 문구는 `UtilBoardMessages.problem` */
export type BoardTextProblem = 'COLOR_FORMAT' | 'NAME_REQUIRED' | 'NAME_LENGTH' | 'NAME_DUPLICATE' | 'SLUG_REQUIRED' | 'SLUG_FORMAT' | 'SLUG_LENGTH' | 'SLUG_RESERVED' | 'SLUG_DUPLICATE' | 'DESC_LENGTH' | 'NAME_EN_LENGTH' | 'TEXT_LENGTH' | 'TEMPLATE_LENGTH' | 'PREFIX_NAME_REQUIRED' | 'PREFIX_NAME_LENGTH' | 'PREFIX_NAME_DUPLICATE';

/** 숫자 칸들 — 화면에서는 글자로 들고 있다(`InputText` + `inputmode=numeric`, grades.vue 와 같은 이유) */
export const BOARD_NUM_FIELDS = [
    'postGapSeconds',
    'postDailyLimit',
    'commentGapSeconds',
    'commentDailyLimit',
    'listSize',
    'pinnedMax',
    'attachmentRetentionDays',
    'commentProtectMin',
    'bestCommentMinLikes',
    'managerMax',
    'staffMax',
    'staffPostDailyLimit',
    'staffCommentDailyLimit',
    'staffSanctionDailyLimit',
    'listLikeHighlight',
    'listViewHighlight',
] as const satisfies readonly BoardDefaultsField[];
export type BoardNumField = (typeof BOARD_NUM_FIELDS)[number];

/** 기본 설정 입력 — 숫자 칸만 글자 */
export type BoardDefaultsForm = Omit<BoardDefaults, BoardNumField> & Record<BoardNumField, string>;
/** 게시판별 정책 입력 — `null` = 기본값 따름, 숫자 칸은 글자 */
export type BoardPolicyForm = { [K in BoardPolicyField]: K extends BoardNumField ? string | null : BoardDefaults[K] | null };
/** 칸 하나의 입력값 — 여러 개 고르기 칸은 글자 배열 */
export type BoardFieldValue = string | number | boolean | string[] | null;

/** 고유 말머리 입력 행 — `key` 는 화면 안 식별자(새 행은 `new-N`) */
export interface BoardPrefixForm {
    key: string;
    id: string | null;
    name: string;
    staffOnly: boolean;
    adultOnly: boolean;
    /** 표시색 — `null` 자동 · 13색 이름 · `#RRGGBB`(직접 고르기, 입력 중이면 틀린 글자일 수 있다 — `prefixProblems` 가 막는다) */
    color: BoardDisplayColor | null;
    active: boolean;
    usageCount: number;
}

/** 공통 말머리 입력 — 켜고 끄기만 */
export interface BoardCommonPrefixForm {
    id: string;
    common: NonNullable<BoardPrefix['common']>;
    staffOnly: boolean;
    active: boolean;
    usageCount: number;
}

/** 게시판 설정 화면의 입력(기본 정보 + 정책 + 말머리) */
export interface BoardDetailForm {
    name: string;
    nameEn: string;
    description: string;
    layout: BoardLayout;
    viewToggle: boolean;
    /** 말머리·상태 칩 위치 — `null` = 사이트 기본 따르기(기본 설정 `listChipPosition`) */
    chipPosition: BoardChipPosition | null;
    /** 말머리 모양 — `null` = 사이트 기본 따르기(기본 설정 `listPrefixStyle`) */
    prefixStyle: BoardPrefixStyle | null;
    /** 표시색 — `null` 자동 · 13색 이름 · `#RRGGBB`(직접 고르기, 입력 중이면 틀린 글자일 수 있다 — `colorProblem` 이 막는다) */
    color: BoardDisplayColor | null;
    allowAdult: boolean;
    investmentNotice: boolean;
    writeLock: BoardWriteLock | null;
    introText: string;
    writeGuideText: string;
    /** 글 양식 — 새 글 본문에 미리 채우는 평문 */
    writeTemplate: string;
    /** 가젯 노출 금지로 고른 가젯 키 — 게시판마다의 값(기본값 따름 없음) */
    gadgetExclusions: string[];
    /** 「이 게시판에 광고 숨김」 — 게시판마다의 값(기본값 따름 없음) */
    adsHidden: boolean;
    /** 비밀글 방식 — 게시판마다의 값(기본값 따름 없음 · `diffCount` 에 세지 않는다) */
    secretMode: BoardSecretMode;
    /** 처리 상태 쓰기 — 게시판마다의 값(기본값 따름 없음 · `diffCount` 에 세지 않는다) */
    statusEnabled: boolean;
    /** 작성자 표시 — 게시판마다의 값(기본값 따름 없음 · `diffCount` 에 세지 않는다) */
    authorMode: BoardAuthorMode;
    policy: BoardPolicyForm;
    commonPrefixes: BoardCommonPrefixForm[];
    prefixes: BoardPrefixForm[];
}

/** 게시판 설정 화면의 저장 구획(바뀐 칸 세기 단위) */
export type BoardDetailSection = 'basic' | 'write' | 'prefix' | 'guide' | 'exposure' | 'protect' | 'reaction' | 'staff';

/** 서버가 거절한 칸 — 칸 이름(정책은 `policy.` 를 뗀 이름, 말머리는 `prefixes[i].name`) → 문구, 칸에 붙지 않는 것은 `form` */
export interface BoardFieldErrors {
    fields: Record<string, string>;
    form: string;
}

/** 목록 사용 여부 거르기 */
export type BoardActiveFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

const SPECS: Record<BoardDefaultsField, BoardFieldSpec> = {
    dateDisplay: { field: 'dateDisplay', kind: 'dateDisplay', choices: ['STANDARD', 'RELATIVE'], overridable: false, permission: 'board.settings' },
    // 게시판별 값(`BoardDetail.chipPosition`)은 정책 칸이 아니라 게시판 기본 정보 칸이라 「게시판별 덮어쓰기」로 세지 않는다(서버 `BoardFields.DEFAULTS` — post-chip-position-contract)
    listChipPosition: { field: 'listChipPosition', kind: 'choice', choices: ['ABOVE', 'BEFORE', 'BELOW'], overridable: false, permission: 'board.settings' },
    // 말머리 모양(mobile-nav-prefix-style-contract) — 칩 위치와 같은 틀: 게시판별 값(`BoardDetail.prefixStyle`)은 게시판 기본 정보 칸이라 「게시판별 덮어쓰기」로 세지 않는다
    listPrefixStyle: { field: 'listPrefixStyle', kind: 'choice', choices: ['TEXT', 'MUTED', 'COLOR'], overridable: false, permission: 'board.settings' },
    // 글 줄 숫자 강조(사용자 2026-11-08 — 사이트 전체, 게시판별 덮어쓰기 없음). 0 = 강조 안 함. 범위는 서버 `BoardFields.LIKE_HIGHLIGHT_MAX`·`VIEW_HIGHLIGHT_MAX`(V170 CHECK)와 같다
    listLikeHighlight: { field: 'listLikeHighlight', kind: 'number', min: 0, max: 100000, unit: 'likes', overridable: false, permission: 'board.settings' },
    listViewHighlight: { field: 'listViewHighlight', kind: 'number', min: 0, max: 10000000, unit: 'times', overridable: false, permission: 'board.settings' },
    listHighlightColor: { field: 'listHighlightColor', kind: 'color', overridable: false, permission: 'board.settings' },
    writeMin: { field: 'writeMin', kind: 'writeMin', overridable: true, permission: 'board.settings' },
    commentMin: { field: 'commentMin', kind: 'commentMin', overridable: true, permission: 'board.settings' },
    postEditor: { field: 'postEditor', kind: 'choice', choices: ['NONE', 'SIMPLE', 'ADVANCED'], overridable: true, permission: 'board.settings' },
    imageUploadAllowed: { field: 'imageUploadAllowed', kind: 'bool', overridable: true, permission: 'board.settings' },
    commentEditor: { field: 'commentEditor', kind: 'choice', choices: ['NONE', 'SIMPLE'], overridable: true, permission: 'board.settings' },
    postGapSeconds: { field: 'postGapSeconds', kind: 'number', min: 0, max: 3600, unit: 'seconds', overridable: false, permission: 'board.settings' },
    postDailyLimit: { field: 'postDailyLimit', kind: 'number', min: 1, max: 1000, unit: 'count', overridable: false, permission: 'board.settings' },
    commentGapSeconds: { field: 'commentGapSeconds', kind: 'number', min: 0, max: 3600, unit: 'seconds', overridable: false, permission: 'board.settings' },
    commentDailyLimit: { field: 'commentDailyLimit', kind: 'number', min: 1, max: 1000, unit: 'count', overridable: false, permission: 'board.settings' },
    listSize: { field: 'listSize', kind: 'number', min: 10, max: 100, unit: 'count', overridable: true, permission: 'board.settings' },
    pinnedMax: { field: 'pinnedMax', kind: 'number', min: 0, max: 10, unit: 'count', overridable: true, permission: 'board.settings' },
    indexable: { field: 'indexable', kind: 'bool', overridable: true, permission: 'board.settings' },
    searchable: { field: 'searchable', kind: 'bool', overridable: true, permission: 'board.settings' },
    inHome: { field: 'inHome', kind: 'bool', overridable: true, permission: 'board.settings' },
    attachmentPurgeEnabled: { field: 'attachmentPurgeEnabled', kind: 'bool', overridable: false, permission: 'board.settings' },
    attachmentRetentionDays: { field: 'attachmentRetentionDays', kind: 'number', min: 1, max: 3650, unit: 'days', overridable: false, permission: 'board.settings' },
    pointMode: { field: 'pointMode', kind: 'choice', choices: ['ON', 'OFF'], overridable: true, permission: 'board.settings' },
    pointMinLengthApplied: { field: 'pointMinLengthApplied', kind: 'bool', overridable: true, permission: 'board.settings' },
    commentProtectMin: { field: 'commentProtectMin', kind: 'number', min: 0, max: 100, unit: 'count', overridable: true, permission: 'board.settings' },
    commentProtectEdit: { field: 'commentProtectEdit', kind: 'bool', overridable: true, permission: 'board.settings' },
    commentProtectDelete: { field: 'commentProtectDelete', kind: 'choice', choices: ['BLOCK', 'DELAY', 'ALLOW'], overridable: true, permission: 'board.settings' },
    commentProtectDelayMinutes: { field: 'commentProtectDelayMinutes', kind: 'choice', choices: [5, 10, 30], unit: 'minutes', overridable: true, permission: 'board.settings' },
    commentGuestDelayMinutes: { field: 'commentGuestDelayMinutes', kind: 'choice', choices: [0, 10, 30, 60, 180], unit: 'minutes', overridable: true, permission: 'board.settings' },
    staffHiddenNotice: { field: 'staffHiddenNotice', kind: 'bool', overridable: true, permission: 'board.settings' },
    commentDislikeEnabled: { field: 'commentDislikeEnabled', kind: 'bool', overridable: true, permission: 'board.settings' },
    commentLikeLabel: { field: 'commentLikeLabel', kind: 'label', min: 1, max: 10, overridable: true, permission: 'board.settings' },
    commentDislikeLabel: { field: 'commentDislikeLabel', kind: 'label', min: 1, max: 10, overridable: true, permission: 'board.settings' },
    pinRequestReviewers: { field: 'pinRequestReviewers', kind: 'multi', choices: ['POST_AUTHOR', 'BOARD_MANAGER', 'OPERATOR'], overridable: true, permission: 'board.settings' },
    debateEnabled: { field: 'debateEnabled', kind: 'bool', overridable: true, permission: 'board.settings' },
    pollEnabled: { field: 'pollEnabled', kind: 'bool', overridable: true, permission: 'board.settings' },
    bestCommentMinLikes: { field: 'bestCommentMinLikes', kind: 'number', min: 1, max: 100, unit: 'likes', overridable: false, permission: 'board.settings' },
    managerMax: { field: 'managerMax', kind: 'number', min: 1, max: 10, unit: 'people', overridable: true, permission: 'board.staff.limit' },
    staffMax: { field: 'staffMax', kind: 'number', min: 1, max: 10, unit: 'people', overridable: false, permission: 'board.staff.limit' },
    staffPostDailyLimit: { field: 'staffPostDailyLimit', kind: 'number', min: 1, max: 50, unit: 'times', overridable: true, permission: 'board.staff.limit' },
    staffCommentDailyLimit: { field: 'staffCommentDailyLimit', kind: 'number', min: 1, max: 100, unit: 'times', overridable: true, permission: 'board.staff.limit' },
    staffSanctionDailyLimit: { field: 'staffSanctionDailyLimit', kind: 'number', min: 1, max: 20, unit: 'times', overridable: true, permission: 'board.staff.limit' },
};

/** 하위 화면 칸 한 줄 — [이름, 종류, (숫자) 최소, 최대, 단위 | (고르기) 값들] */
type TabSpecRow = readonly [string, 'bool' | 'grade'] | readonly [string, 'number', number, number, BoardUnit] | readonly [string, 'choice', readonly string[]];

/**
 * 기본 설정 하위 화면(신고 정책·보안·알림·개인정보 보유기간)의 칸 정의 — 일반 화면의 `SPECS` 와 같은 모양이고 같은 부품(`BoardSettingField`)이 그린다. 모두 `board.settings` 로 고친다.
 * 범위·기본값의 근거는 각 화면 머리 주석(그누보드7 `config/settings/defaults.json`·`config/board.php` → roboyam 권장안).
 */
const TAB_SPEC_ROWS: readonly TabSpecRow[] = [
    // 신고 정책
    ['reportEnabled', 'bool'],
    ['autoHideThreshold', 'number', 0, 100, 'count'],
    ['autoHideTarget', 'choice', ['POST', 'COMMENT', 'BOTH']],
    ['autoHideMinGrade', 'grade'],
    ['autoHideExemptStaffPosts', 'bool'],
    ['rightsTempMeasureDays', 'number', 1, 30, 'days'],
    ['dailyReportLimit', 'number', 0, 100, 'count'],
    ['reportCooldownSeconds', 'number', 0, 3600, 'seconds'],
    ['rejectionLimitCount', 'number', 0, 50, 'times'],
    ['rejectionLimitDays', 'number', 1, 365, 'days'],
    ['rejectionBlockDays', 'number', 1, 90, 'days'],
    ['notifyOperatorScope', 'choice', ['PER_CASE', 'PER_REPORT']],
    ['notifyAuthorOnAction', 'bool'],
    ['notifyReporterOnResult', 'bool'],
    // 보안
    ['viewCountDedupHours', 'number', 1, 168, 'hours'],
    ['blockedWordsEnabled', 'bool'],
    ['blockedWordsAction', 'choice', ['BLOCK', 'MASK']],
    ['newMemberLinkLimit', 'number', 0, 20, 'count'],
    ['newMemberImageLimit', 'number', 0, 20, 'count'],
    ['duplicatePostWindowMinutes', 'number', 0, 1440, 'minutes'],
    ['externalLinkNofollow', 'bool'],
    ['externalLinkWarn', 'bool'],
    ['guestCaptchaRequired', 'bool'],
    ['guestNameMin', 'number', 1, 20, 'chars'],
    ['guestNameMax', 'number', 1, 20, 'chars'],
    ['guestPasswordMin', 'number', 4, 20, 'chars'],
    ['guestLinkLimit', 'number', 0, 20, 'count'],
    ['guestImageUploadAllowed', 'bool'],
    // 알림
    ['groupWindowMinutes', 'number', 0, 60, 'minutes'],
    ['emailQuietHoursEnabled', 'bool'],
    ['memberNotificationRetentionDays', 'number', 7, 365, 'days'],
    // 개인정보 보유기간
    ['retentionEnabled', 'bool'],
    ['ipRetentionDays', 'number', 90, 3650, 'days'],
    ['guestRetentionDays', 'number', 0, 3650, 'days'],
    ['guestMode', 'choice', ['ANONYMIZE', 'DELETE']],
    ['deletedContentRetentionDays', 'number', 1, 365, 'days'],
    ['reportRecordRetentionDays', 'number', 90, 1825, 'days'],
    ['staffActionRetentionDays', 'number', 90, 1825, 'days'],
];

const TAB_SPECS: Record<string, BoardFieldSpec> = Object.fromEntries(
    TAB_SPEC_ROWS.map((row): [string, BoardFieldSpec] => {
        const base: BoardFieldSpec = { field: row[0], kind: row[1], overridable: false, permission: 'board.settings' };
        if (row[1] === 'number') return [row[0], { ...base, min: row[2], max: row[3], unit: row[4] }];
        if (row[1] === 'choice') return [row[0], { ...base, choices: row[2] }];
        return [row[0], base];
    }),
);

/** 기본 설정 하위 화면의 입력 — 숫자 칸만 글자(`InputText` + `inputmode=numeric`), 나머지(목록·표·시각 글자)는 그대로 */
export type BoardTabForm<T> = { [K in keyof T]: T[K] extends number ? string : T[K] };

/** 기본 설정 화면의 구획 — 구획 주소(`anchor`) → 칸들. 「고정 규칙」 구획은 칸이 없어 여기 없다 */
const DEFAULT_SECTIONS = {
    'defaults-display': ['dateDisplay', 'listChipPosition', 'listPrefixStyle', 'listLikeHighlight', 'listViewHighlight', 'listHighlightColor'],
    'defaults-write': ['writeMin', 'commentMin', 'postEditor', 'imageUploadAllowed', 'commentEditor'],
    'defaults-rate': ['postGapSeconds', 'postDailyLimit', 'commentGapSeconds', 'commentDailyLimit'],
    // 「홈·인기·최신 포함」(inHome)은 게시판 설정의 「가젯 노출 금지」가 대신해 화면에서 다루지 않는다(서버 칸은 남아 있고 저장 요청은 받은 값을 그대로 돌려보낸다)
    'defaults-list': ['listSize', 'pinnedMax', 'indexable', 'searchable'],
    'defaults-attachment': ['attachmentPurgeEnabled', 'attachmentRetentionDays'],
    'defaults-protect': ['pointMode', 'pointMinLengthApplied', 'commentProtectMin', 'commentProtectEdit', 'commentProtectDelete', 'commentProtectDelayMinutes'],
    'defaults-guest': ['commentGuestDelayMinutes', 'staffHiddenNotice'],
    'defaults-reaction': ['debateEnabled', 'pollEnabled', 'commentDislikeEnabled', 'commentLikeLabel', 'commentDislikeLabel', 'pinRequestReviewers'],
    'defaults-best': ['bestCommentMinLikes'],
    'defaults-staff': ['managerMax', 'staffMax', 'staffPostDailyLimit', 'staffCommentDailyLimit', 'staffSanctionDailyLimit'],
} as const satisfies Record<string, readonly BoardDefaultsField[]>;
export type BoardDefaultsSection = keyof typeof DEFAULT_SECTIONS;

/** 표시색 → PrimeVue 색 팔레트 이름(갈색은 PrimeVue 에 없어 stone 으로 그린다) */
const COLOR_PALETTE: Record<BoardColor, string> = {
    BLUE: 'blue',
    TEAL: 'teal',
    AMBER: 'amber',
    PURPLE: 'purple',
    GREEN: 'green',
    ORANGE: 'orange',
    SLATE: 'slate',
    RED: 'red',
    PINK: 'pink',
    INDIGO: 'indigo',
    SKY: 'sky',
    BROWN: 'stone',
    EMERALD: 'emerald',
};
/**
 * 라이트 화면 견본의 팔레트 단계 — 기본 500. 분홍은 500(#ec4899)이 공개 화면 값(`UtilColor.PRESETS` PINK 라이트 #db2777)보다 옅어 600(#db2777)으로 맞춘다(사용자 2026-11-11).
 */
const COLOR_LIGHT_STEP: Partial<Record<BoardColor, number>> = {
    PINK: 600,
};

/** slug 규칙 — 영소문자로 시작, 영소문자·숫자·`-` (서버 `BoardCreateRequest.slug` 와 같다) */
const SLUG_PATTERN = /^[a-z][a-z0-9-]*$/;
/** 정수 글자 */
const INTEGER = /^-?\d+$/;

/**
 * 게시판 관리(목록·기본 설정·게시판 설정)의 순수 규칙 — 칸 정의·범위 검사·입력 ↔ 요청 변환·바뀐 칸 세기·목록 거르기·순서 이동.
 * 서버 규칙 이름(후보): 범위는 `board_defaults`·`boards` 정책 열 CHECK(community `api-board-settings.md` 2·3절)와 `BoardDefaultsSaveRequest`·`BoardSaveRequest` 검사,
 * 이름·slug 는 `BoardCreateRequest`, 말머리는 `BoardPrefixSaveRequest`(서버 `server/admin` `BoardService`·shared `BoardFields`). 서버가 같은 규칙으로 다시 본다.
 */
export default class UtilBoard {
    static readonly NAME_MAX = 40;
    static readonly NAME_EN_MAX = 40;
    static readonly DESC_MAX = 200;
    static readonly SLUG_MIN = 2;
    static readonly SLUG_MAX = 40;
    /** slug 로 쓸 수 없는 말 — 관리자 화면 주소(`/boards/defaults/…`)·API 경로와 겹친다 */
    static readonly SLUG_RESERVED: readonly string[] = ['defaults', 'order', 'new', 'writable'];
    /** 안내문의 보이는 글자 수 상한 */
    static readonly TEXT_MAX = 500;
    /** 글 양식의 보이는 글자 수 상한(서버 `BoardFields.TEMPLATE_MAX`) */
    static readonly TEMPLATE_MAX = 2000;
    static readonly PREFIX_NAME_MAX = 20;
    /** 사용 중인 고유 말머리 최대 수 */
    static readonly PREFIX_ACTIVE_MAX = 15;
    /** 한 회원이 맡을 수 있는 게시판 수(매니저·스텝 합산) */
    static readonly MEMBER_BOARD_MAX = 5;
    /** 금지어 최대 개수 · 단어당 최대 글자 */
    static readonly BLOCKED_WORDS_MAX = 500;
    static readonly BLOCKED_WORD_LENGTH_MAX = 30;

    static readonly LAYOUTS: readonly BoardLayout[] = ['LIST', 'CARD', 'GALLERY'];
    static readonly KINDS: readonly BoardKind[] = ['GENERAL', 'DEAL', 'PROMO', 'GROUP'];
    static readonly COLORS: readonly BoardColor[] = ['BLUE', 'TEAL', 'AMBER', 'PURPLE', 'GREEN', 'ORANGE', 'SLATE', 'RED', 'PINK', 'INDIGO', 'SKY', 'BROWN', 'EMERALD'];
    static readonly WRITE_LOCKS: readonly (BoardWriteLock | null)[] = [null, 'POST', 'POST_COMMENT'];
    /** 비밀글 방식 선택지 — 끔 · 글쓴이가 고름 · 모든 글 비밀 */
    static readonly SECRET_MODES: readonly BoardSecretMode[] = ['OFF', 'OPTIONAL', 'ALWAYS'];
    /** 작성자 표시 선택지 — 닉네임(기본) · 익명 */
    static readonly AUTHOR_MODES: readonly BoardAuthorMode[] = ['NICKNAME', 'ANONYMOUS'];
    /** 글 목록 줄의 말머리·상태 칩 위치 선택지 — 제목 위(기본) · 제목 앞 · 닉네임 아래(서버 `BoardFields.CHIP_POSITIONS` 차례) */
    static readonly CHIP_POSITIONS: readonly BoardChipPosition[] = ['ABOVE', 'BEFORE', 'BELOW'];
    /** 말머리 모양 선택지 — 글자(기본) · 회색 칩 · 색상 칩(서버 `BoardFields` 차례) */
    static readonly PREFIX_STYLES: readonly BoardPrefixStyle[] = ['TEXT', 'MUTED', 'COLOR'];
    /** 말머리 모양의 전체 기본값(서버 V195 기본값과 같다) — 옛 서버 응답에 칸이 없으면 이 값으로 본다 */
    static readonly PREFIX_STYLE_DEFAULT: BoardPrefixStyle = 'TEXT';

    /** 말머리 모양 값 — 선택지 밖이면(옛 응답·소문자 등) 전체 기본값 */
    static prefixStyle(value: unknown): BoardPrefixStyle {
        const upper = typeof value === 'string' ? value.toUpperCase() : '';
        return (UtilBoard.PREFIX_STYLES as readonly string[]).includes(upper) ? (upper as BoardPrefixStyle) : UtilBoard.PREFIX_STYLE_DEFAULT;
    }
    /** 글 줄 숫자 강조 색의 기본값 — 빨강(서버 기본값과 같다) */
    static readonly HIGHLIGHT_COLOR = '#e5484d';
    /** 강조 색 추천 칩 — 빨강(기본) · 주황 · 분홍 · 보라 · 파랑 · 초록. 공개 화면이 바탕에 맞게 명도만 보정한다 */
    static readonly HIGHLIGHT_COLORS: readonly { key: string; hex: string }[] = [
        { key: 'red', hex: '#e5484d' },
        { key: 'orange', hex: '#f76b15' },
        { key: 'pink', hex: '#d6409f' },
        { key: 'purple', hex: '#8e4ec6' },
        { key: 'blue', hex: '#0090ff' },
        { key: 'green', hex: '#30a46c' },
    ];

    /**
     * 강조 색 값 — `#rrggbb` 면 소문자로, 아니면 기본 빨강.
     * @param {unknown} value - 입력·서버 값.
     * @returns {string} 저장할 `#rrggbb`.
     */
    static highlightColor(value: unknown): string {
        return typeof value === 'string' && /^#[0-9a-fA-F]{6}$/.test(value) ? value.toLowerCase() : UtilBoard.HIGHLIGHT_COLOR;
    }
    static readonly SPECS: Readonly<Record<BoardDefaultsField, BoardFieldSpec>> = SPECS;
    static readonly DEFAULT_SECTIONS: Readonly<Record<BoardDefaultsSection, readonly BoardDefaultsField[]>> = DEFAULT_SECTIONS;
    static readonly POLICY_FIELDS: readonly BoardPolicyField[] = BOARD_POLICY_FIELDS;

    /**
     * 칸 정의 — 일반 화면(`SPECS`)과 하위 화면(신고·보안·알림·개인정보)을 함께 찾는다. 모르는 칸은 켜고 끄기로 본다.
     * @param {string} field - 칸 이름.
     * @returns {BoardFieldSpec} 칸 정의.
     */
    static spec(field: string): BoardFieldSpec {
        return (SPECS as Record<string, BoardFieldSpec>)[field] ?? TAB_SPECS[field] ?? { field, kind: 'bool', overridable: false, permission: 'board.settings' };
    }

    /** 숫자 글자 입력 칸인가(일반·하위 화면 모두) */
    static isNumber(field: string): boolean {
        return UtilBoard.spec(field).kind === 'number';
    }

    // ── 기본 설정 하위 화면(자기 요청·자기 저장) ─

    /** 서버 값 → 입력(숫자 칸은 글자, 목록·표는 복사) */
    static tabFormOf<T extends object>(values: T): BoardTabForm<T> {
        const out: Record<string, unknown> = {};
        for (const [key, value] of Object.entries(values)) out[key] = UtilBoard.isNumber(key) ? String(value) : structuredClone(value);
        return out as BoardTabForm<T>;
    }

    /** 하위 화면 입력의 숫자 칸 문제들 */
    static tabProblems(form: object): Record<string, BoardNumProblem> {
        const out: Record<string, BoardNumProblem> = {};
        for (const [key, value] of Object.entries(form)) {
            if (!UtilBoard.isNumber(key)) continue;
            const problem = UtilBoard.numProblem(key, String(value));
            if (problem) out[key] = problem;
        }
        return out;
    }

    /** 입력 → 요청(숫자 칸이 틀렸으면 null) */
    static tabValuesOf<T extends object>(form: BoardTabForm<T>): T | null {
        const out: Record<string, unknown> = {};
        for (const [key, value] of Object.entries(form)) {
            if (!UtilBoard.isNumber(key)) {
                out[key] = JSON.parse(JSON.stringify(value)) as unknown; // 화면의 반응형 값에서 떼어 낸다(structuredClone 은 Proxy 를 못 복사한다)
                continue;
            }
            if (UtilBoard.numProblem(key, String(value))) return null;
            out[key] = UtilBoard.parse(String(value));
        }
        return out as T;
    }

    /** 저장된 값과 다른 칸들(숫자 칸은 앞뒤 공백 무시, 목록·표는 내용 비교) */
    static tabChanged<T extends object>(form: BoardTabForm<T>, saved: T): string[] {
        return Object.keys(saved).filter((key) => {
            const a = (form as Record<string, unknown>)[key];
            const b = (saved as Record<string, unknown>)[key];
            return UtilBoard.isNumber(key) ? String(a).trim() !== String(b) : JSON.stringify(a) !== JSON.stringify(b);
        });
    }

    /**
     * 금지어 글자(줄마다 하나) → 목록. 앞뒤 공백을 떼고 빈 줄·중복(대소문자 무시)을 뺀다.
     * @param {string} text - 줄바꿈으로 나눈 글자.
     * @returns {string[]} 단어 목록(처음 나온 차례).
     */
    static wordsOf(text: string): string[] {
        const seen = new Set<string>();
        const out: string[] = [];
        for (const raw of text.split(/\r?\n/)) {
            const word = raw.trim();
            if (!word || seen.has(word.toLowerCase())) continue;
            seen.add(word.toLowerCase());
            out.push(word);
        }
        return out;
    }

    /** 금지어 목록의 문제 — 개수·길이 */
    static wordsProblem(words: readonly string[]): 'WORDS_COUNT' | 'WORD_LENGTH' | null {
        if (words.length > UtilBoard.BLOCKED_WORDS_MAX) return 'WORDS_COUNT';
        if (words.some((w) => [...w].length > UtilBoard.BLOCKED_WORD_LENGTH_MAX)) return 'WORD_LENGTH';
        return null;
    }

    /** `HH:mm` 시각 글자인가 */
    static isClock(text: string): boolean {
        return /^([01]\d|2[0-3]):[0-5]\d$/.test(text.trim());
    }

    /** 숫자 칸인가 */
    static isNum(field: BoardDefaultsField): field is BoardNumField {
        return (BOARD_NUM_FIELDS as readonly string[]).includes(field);
    }

    /**
     * 글자를 정수로 읽는다.
     * @param {string} text - 입력 글자.
     * @returns {number | null} 정수가 아니면 null.
     */
    static parse(text: string): number | null {
        const trimmed = text.trim();
        return INTEGER.test(trimmed) ? Number(trimmed) : null;
    }

    /**
     * 숫자 칸 하나의 문제.
     * @param {BoardDefaultsField} field - 칸.
     * @param {string} text - 입력 글자.
     * @returns {BoardNumProblem | null} 문제가 없으면 null.
     */
    static numProblem(field: string, text: string): BoardNumProblem | null {
        const spec = UtilBoard.spec(field);
        if (!text.trim()) return 'REQUIRED';
        const value = UtilBoard.parse(text);
        if (value === null) return 'NOT_INTEGER';
        if ((spec.min !== undefined && value < spec.min) || (spec.max !== undefined && value > spec.max)) return 'OUT_OF_RANGE';
        return null;
    }

    // ── 이름 칸 · 여러 개 고르기 칸 ─

    /** 짧은 이름 칸인가(댓글 추천·비추천 이름) */
    static isLabel(field: string): boolean {
        return UtilBoard.spec(field).kind === 'label';
    }

    /** 여러 개 고르기 칸인가(고정 요청 확인자) */
    static isMulti(field: string): boolean {
        return UtilBoard.spec(field).kind === 'multi';
    }

    /** 이름 입력 → 저장 값 — 앞뒤 공백을 떼고 비면 null(서버 `BoardDefaultsValues.label` 과 같다) */
    static labelValue(value: unknown): string | null {
        return typeof value === 'string' ? value.trim() || null : null;
    }

    /**
     * 이름 칸의 문제 — 비면 문제 없음(null 저장). 글자 수는 앞뒤 공백을 뗀 유니코드 한 글자씩(서버와 같다).
     * @param {string} field - 칸.
     * @param {unknown} value - 입력.
     * @returns {BoardLabelProblem | null} 문제가 없으면 null.
     */
    static labelProblem(field: string, value: unknown): BoardLabelProblem | null {
        const text = UtilBoard.labelValue(value);
        if (text === null) return null;
        // eslint-disable-next-line no-control-regex -- 서버가 거절하는 제어 문자(줄바꿈·탭 등)를 미리 알린다
        if (/[\u0000-\u001f\u007f-\u009f]/.test(text)) return 'LABEL_CHARS';
        return [...text].length > (UtilBoard.spec(field).max ?? Number.MAX_SAFE_INTEGER) ? 'LABEL_LENGTH' : null;
    }

    /** 여러 개 고르기 값 → 칸 정의의 차례로(목록 밖 값은 뺀다 — 서버가 저장 차례로 정리한 값과 비교하려고). 배열이 아니면 null */
    static multiValue(field: string, value: unknown): string[] | null {
        if (!Array.isArray(value)) return null;
        return (UtilBoard.spec(field).choices ?? []).filter((choice): choice is string => typeof choice === 'string' && value.includes(choice));
    }

    /** 두 값이 같은가 — 숫자 칸은 앞뒤 공백 무시, 이름 칸은 정리한 값, 여러 개 고르기는 차례와 무관하게 */
    static sameValue(field: string, a: unknown, b: unknown): boolean {
        if (UtilBoard.isNumber(field)) return String(a ?? '').trim() === String(b ?? '');
        if (UtilBoard.isLabel(field)) return UtilBoard.labelValue(a) === UtilBoard.labelValue(b);
        if (UtilBoard.isMulti(field)) return JSON.stringify(UtilBoard.multiValue(field, a)) === JSON.stringify(UtilBoard.multiValue(field, b));
        return a === b;
    }

    // ── 기본 설정 ─

    /** 서버 값 → 입력(목록 칸은 복사) */
    static defaultsFormOf(defaults: BoardDefaults): BoardDefaultsForm {
        const form = { ...defaults } as unknown as BoardDefaultsForm;
        for (const field of BOARD_NUM_FIELDS) form[field] = String(defaults[field]);
        form.pinRequestReviewers = [...(defaults.pinRequestReviewers ?? [])];
        // 숫자 강조 칸을 아직 주지 않는 서버(새 칸을 서버보다 먼저 배포한 경우)면 기본값(추천 10 · 조회 1000 · 빨강)으로 채운다
        form.listLikeHighlight = String(defaults.listLikeHighlight ?? 10);
        form.listViewHighlight = String(defaults.listViewHighlight ?? 1000);
        form.listHighlightColor = UtilBoard.highlightColor(defaults.listHighlightColor);
        // 말머리 모양을 아직 주지 않는 서버면 전체 기본값(글자)
        form.listPrefixStyle = UtilBoard.prefixStyle(defaults.listPrefixStyle);
        return form;
    }

    /** 기본 설정 입력의 칸 문제들(숫자 칸 · 이름 칸) */
    static defaultsProblems(form: BoardDefaultsForm): Partial<Record<BoardDefaultsField, BoardFieldProblem>> {
        const out: Partial<Record<BoardDefaultsField, BoardFieldProblem>> = {};
        for (const field of BOARD_NUM_FIELDS) {
            const problem = UtilBoard.numProblem(field, form[field]);
            if (problem) out[field] = problem;
        }
        for (const field of Object.keys(SPECS) as BoardDefaultsField[]) {
            if (!UtilBoard.isLabel(field)) continue;
            const problem = UtilBoard.labelProblem(field, form[field]);
            if (problem) out[field] = problem;
        }
        return out;
    }

    /**
     * 입력 → 요청. 칸이 하나라도 맞지 않으면 null. 이름 칸은 앞뒤 공백을 떼고 비면 null, 여러 개 고르기는 칸 정의 차례로.
     * @param {BoardDefaultsForm} form - 입력.
     * @returns {BoardDefaults | null} 요청 본문.
     */
    static toDefaults(form: BoardDefaultsForm): BoardDefaults | null {
        if (Object.keys(UtilBoard.defaultsProblems(form)).length) return null;
        const out = { ...form } as unknown as BoardDefaults;
        for (const field of BOARD_NUM_FIELDS) out[field] = UtilBoard.parse(form[field]) as number;
        out.commentLikeLabel = UtilBoard.labelValue(form.commentLikeLabel);
        out.commentDislikeLabel = UtilBoard.labelValue(form.commentDislikeLabel);
        out.pinRequestReviewers = (UtilBoard.multiValue('pinRequestReviewers', form.pinRequestReviewers) ?? []) as BoardDefaults['pinRequestReviewers'];
        out.listHighlightColor = UtilBoard.highlightColor(form.listHighlightColor);
        out.listPrefixStyle = UtilBoard.prefixStyle(form.listPrefixStyle);
        return out;
    }

    /** 저장된 값과 다른 기본 설정 칸들 — 옛 서버가 주지 않은 말머리 모양은 전체 기본값과 견준다(채워 넣은 기본값을 「바뀜」으로 세지 않게) */
    static defaultsChanged(form: BoardDefaultsForm, saved: BoardDefaults): BoardDefaultsField[] {
        const base: BoardDefaults = { ...saved, listPrefixStyle: UtilBoard.prefixStyle(saved.listPrefixStyle) };
        return (Object.keys(SPECS) as BoardDefaultsField[]).filter((field) => !UtilBoard.sameValue(field, form[field], base[field]));
    }

    // ── 게시판별 정책 ─

    /** 서버 정책 → 입력 */
    static policyFormOf(policy: BoardPolicy): BoardPolicyForm {
        const form = { ...policy } as unknown as Record<BoardPolicyField, BoardFieldValue>;
        for (const field of BOARD_POLICY_FIELDS) {
            // 서버가 아직 주지 않는 칸(새 칸을 서버보다 먼저 배포한 경우 — 에디터 칸 등)은 「기본값 따름」(null)으로 본다
            const value = policy[field] ?? null;
            form[field] = UtilBoard.isNum(field) && value !== null ? String(value) : Array.isArray(value) ? [...value] : value;
        }
        return form as BoardPolicyForm;
    }

    /** 정책 입력의 칸 문제들 — 숫자 칸 · 이름 칸(기본값 따름 칸은 보지 않는다) */
    static policyProblems(policy: BoardPolicyForm): Partial<Record<BoardPolicyField, BoardFieldProblem>> {
        const out: Partial<Record<BoardPolicyField, BoardFieldProblem>> = {};
        for (const field of BOARD_POLICY_FIELDS) {
            const value = policy[field] as BoardFieldValue;
            if (value === null) continue;
            const problem = UtilBoard.isNum(field) ? UtilBoard.numProblem(field, String(value)) : UtilBoard.isLabel(field) ? UtilBoard.labelProblem(field, value) : null;
            if (problem) out[field] = problem;
        }
        return out;
    }

    /**
     * 정책 칸 하나의 저장 값 — 숫자 칸은 정수(틀렸으면 그대로 글자), 이름 칸은 앞뒤 공백을 떼고 비면 null(= 기본 설정 따름), 여러 개 고르기는 칸 정의 차례로.
     * @returns {BoardFieldValue} `null` = 기본값 따름.
     */
    static policyValue(field: BoardPolicyField, value: BoardFieldValue): BoardFieldValue {
        if (value === null) return null;
        if (UtilBoard.isNum(field)) return UtilBoard.parse(String(value)) ?? value;
        if (UtilBoard.isLabel(field)) return UtilBoard.labelValue(value);
        if (UtilBoard.isMulti(field)) return UtilBoard.multiValue(field, value);
        return value;
    }

    /** 정책 입력 → 요청(칸이 틀렸으면 null) */
    static toPolicy(policy: BoardPolicyForm): BoardPolicy | null {
        if (Object.keys(UtilBoard.policyProblems(policy)).length) return null;
        const out = { ...policy } as unknown as Record<BoardPolicyField, BoardFieldValue>;
        for (const field of BOARD_POLICY_FIELDS) out[field] = UtilBoard.policyValue(field, policy[field] as BoardFieldValue);
        return out as BoardPolicy;
    }

    /**
     * 이미지 올리기 칸이 지금 쓰이지 않는가 — 이미지는 고급 글 에디터에서만 올린다(간단 에디터는 이미지 태그를 만들지 않고, 평문은 에디터가 없다. 공개 게시판 계약 「에디터 화면」).
     * @param {BoardPostEditor | undefined} postEditor - 유효 글 에디터(서버가 아직 안 주면 undefined — 그때는 끄지 않는다).
     * @returns {boolean} 고급이 아니면 true.
     */
    static imageUploadIdle(postEditor: BoardPostEditor | undefined): boolean {
        return postEditor !== undefined && postEditor !== 'ADVANCED';
    }

    /** 기본값과 다르게 정한(기본값 따름이 아닌) 정책 칸 수 — 잠금이 걸려 있으면 1을 더한다(잠금도 기본값과 다른 설정이다) */
    static diffCount(policy: BoardPolicyForm | BoardPolicy, writeLock: BoardWriteLock | null): number {
        // 이름 칸을 비우면 저장 때 null(= 기본값 따름)이 되므로 세지 않는다
        return BOARD_POLICY_FIELDS.filter((field) => UtilBoard.policyValue(field, policy[field] as BoardFieldValue) !== null).length + (writeLock ? 1 : 0);
    }

    /**
     * 칸의 유효값 — 게시판 값 ?? 기본값. 숫자 칸 입력이 틀렸으면 기본값으로 본다.
     * @returns {BoardDefaults[K]} 유효값.
     */
    static effective<K extends BoardPolicyField>(policy: BoardPolicyForm, defaults: BoardDefaults, field: K): BoardDefaults[K] {
        const value = policy[field] as BoardFieldValue;
        if (value === null) return defaults[field];
        if (UtilBoard.isLabel(field)) return (UtilBoard.labelValue(value) ?? defaults[field]) as BoardDefaults[K];
        if (UtilBoard.isNum(field)) {
            const parsed = UtilBoard.parse(String(value));
            return (parsed ?? defaults[field]) as BoardDefaults[K];
        }
        return value as BoardDefaults[K];
    }

    // ── 게시판 기본 정보·말머리 ─

    /** 서버 값 → 게시판 설정 입력 */
    static detailFormOf(detail: BoardDetail): BoardDetailForm {
        return {
            name: detail.name,
            nameEn: detail.nameEn ?? '',
            description: detail.description,
            layout: detail.layout,
            viewToggle: detail.viewToggle,
            // 옛 서버 응답에 칸이 없으면 사이트 기본 따르기
            chipPosition: detail.chipPosition ?? null,
            // 옛 서버 응답에 칸이 없으면 사이트 기본 따르기
            prefixStyle: detail.prefixStyle ?? null,
            color: detail.color,
            allowAdult: detail.allowAdult,
            investmentNotice: detail.investmentNotice,
            writeLock: detail.writeLock,
            introText: detail.introText ?? '',
            writeGuideText: detail.writeGuideText ?? '',
            writeTemplate: detail.writeTemplate ?? '',
            // 서버가 아직 주지 않으면(새 칸을 서버보다 먼저 배포한 경우) 빈 목록
            gadgetExclusions: [...(detail.gadgetExclusions ?? [])],
            // 옛 서버 응답에 칸이 없으면 끔
            adsHidden: detail.adsHidden === true,
            // 옛 서버 응답에 칸이 없으면 끔(목업이 채운다 — `useBoardApi` MOCK)
            secretMode: detail.secretMode ?? 'OFF',
            statusEnabled: detail.statusEnabled === true,
            // 옛 서버 응답에 칸이 없으면 닉네임
            authorMode: detail.authorMode ?? 'NICKNAME',
            policy: UtilBoard.policyFormOf(detail.policy),
            commonPrefixes: detail.prefixes.flatMap((p) => (p.common ? [{ id: p.id, common: p.common, staffOnly: p.staffOnly, active: p.active, usageCount: p.usageCount }] : [])),
            prefixes: detail.prefixes.flatMap((p) => (p.common ? [] : [{ key: p.id, id: p.id, name: p.name, staffOnly: p.staffOnly, adultOnly: p.adultOnly, color: p.color, active: p.active, usageCount: p.usageCount }])),
        };
    }

    /**
     * 입력 → 저장 요청(숫자 칸이 틀렸으면 null).
     * @param {BoardDetailForm} form - 입력.
     * @param {BoardKind} [kind] - 게시판 종류 — 운영 설정(비밀글·처리 상태)을 받지 않는 종류(핫딜·직접홍보)면 두 칸을 null(그대로)로 보낸다(서버 `400 board.ops.generalOnly` 를 피한다).
     * @returns {BoardSaveRequest | null} 저장 요청.
     */
    static toSaveRequest(form: BoardDetailForm, kind?: BoardKind): BoardSaveRequest | null {
        const opsOn = kind === undefined || UtilBoard.opsAllowed(kind);
        const policy = UtilBoard.toPolicy(form.policy);
        if (!policy) return null;
        return {
            name: form.name.trim(),
            nameEn: form.nameEn.trim() || null,
            description: form.description.trim(),
            layout: form.layout,
            viewToggle: form.viewToggle,
            // 「사이트 기본 따르기」는 빈 글자로 보낸다(null 은 서버가 「그대로」로 읽는다 — writeTemplate 과 같은 규칙)
            chipPosition: form.chipPosition ?? '',
            // 말머리 모양도 같은 규칙 — 「사이트 기본 따르기」는 빈 글자
            prefixStyle: form.prefixStyle ?? '',
            color: UtilBoard.normalizeColor(form.color), // 13색 이름 또는 대문자 #RRGGBB(서버도 같은 모양으로 정리)
            allowAdult: form.allowAdult,
            investmentNotice: form.investmentNotice,
            writeLock: form.writeLock,
            introText: UtilBoard.normalizeText(form.introText),
            writeGuideText: UtilBoard.normalizeText(form.writeGuideText),
            // 비우면 빈 글자를 보내 지운다(null 은 서버가 「그대로」로 읽는다)
            writeTemplate: UtilBoard.normalizeText(form.writeTemplate) ?? '',
            policy,
            commonPrefixes: form.commonPrefixes.map((p) => ({ id: p.id, active: p.active })),
            // 말머리 색 — 13색 이름 또는 대문자 #RRGGBB(게시판 표시색과 같은 정리, 서버가 다시 정리한다)
            prefixes: form.prefixes.map((p): BoardPrefixSaveItem => ({ id: p.id, name: p.name.trim(), staffOnly: p.staffOnly, adultOnly: form.allowAdult && p.adultOnly, color: UtilBoard.normalizeColor(p.color), active: p.active })),
            gadgetExclusions: [...form.gadgetExclusions],
            adsHidden: form.adsHidden,
            secretMode: opsOn ? form.secretMode : null,
            statusEnabled: opsOn ? form.statusEnabled : null,
            // 작성자 표시는 일반 게시판만 — 그 밖 종류는 null(그대로)로 보낸다
            authorMode: kind === undefined || UtilBoard.anonymousAllowed(kind) ? form.authorMode : null,
        };
    }

    /** 이 종류의 게시판이 익명 작성자 표시를 고를 수 있는가 — 일반 게시판만(핫딜·직접홍보·소모임은 서버도 거절 — anonymous-board-contract 정책 1) */
    static anonymousAllowed(kind: BoardKind): boolean {
        return kind === 'GENERAL';
    }

    /** 이 종류의 게시판이 운영 설정(비밀글·처리 상태)을 받는가 — 일반·소모임 게시판만(핫딜·직접홍보는 서버도 400) */
    static opsAllowed(kind: BoardKind): boolean {
        return kind === 'GENERAL' || kind === 'GROUP';
    }

    /** 안내문 — 보이는 글자가 없으면 null, 줄바꿈은 `\n` 하나로 */
    static normalizeText(text: string): string | null {
        const normalized = text.replace(/\r\n?/g, '\n');
        return normalized.trim() ? normalized : null;
    }

    /** 안내문의 보이는 글자 수(코드 포인트, 줄바꿈 1자) */
    static textLength(text: string): number {
        return [...text.replace(/\r\n?/g, '\n')].length;
    }

    /**
     * 바뀐 칸을 구획별로 센다(게시판 설정 화면).
     * @returns {Record<BoardDetailSection, number>} 구획 → 바뀐 칸 수.
     */
    static detailChanges(form: BoardDetailForm, saved: BoardDetail): Record<BoardDetailSection, number> {
        const base = UtilBoard.detailFormOf(saved);
        const diff = (a: unknown, b: unknown) => (JSON.stringify(a) === JSON.stringify(b) ? 0 : 1);
        const policyDiff = (fields: readonly BoardPolicyField[]) => fields.reduce((sum, f) => sum + diff(UtilBoard.policyValueKey(f, form.policy[f]), UtilBoard.policyValueKey(f, base.policy[f])), 0);
        const basic = (['name', 'nameEn', 'description', 'layout', 'viewToggle', 'chipPosition', 'prefixStyle', 'color', 'allowAdult', 'investmentNotice', 'authorMode'] as const).reduce((sum, k) => sum + diff(form[k], base[k]), 0);
        const prefix = diff(form.commonPrefixes, base.commonPrefixes) + diff(form.prefixes.map(UtilBoard.prefixKey), base.prefixes.map(UtilBoard.prefixKey));
        return {
            basic,
            write: policyDiff(['writeMin', 'commentMin', 'postEditor', 'imageUploadAllowed', 'commentEditor']) + diff(form.writeLock, base.writeLock) + diff(form.secretMode, base.secretMode) + diff(form.statusEnabled, base.statusEnabled),
            prefix,
            guide: diff(form.introText, base.introText) + diff(form.writeGuideText, base.writeGuideText) + diff(form.writeTemplate, base.writeTemplate),
            // inHome 은 화면에서 다루지 않는다(가젯 노출 금지가 대신한다) — 가젯 선택은 순서 없이 묶음으로 1칸
            exposure:
                policyDiff(['listSize', 'pinnedMax', 'indexable', 'searchable']) + diff(UtilBoard.gadgetKey(form.gadgetExclusions), UtilBoard.gadgetKey(base.gadgetExclusions)) + diff(form.adsHidden, base.adsHidden),
            protect: policyDiff(['pointMode', 'pointMinLengthApplied', 'commentProtectMin', 'commentProtectEdit', 'commentProtectDelete', 'commentProtectDelayMinutes', 'commentGuestDelayMinutes', 'staffHiddenNotice']),
            reaction: policyDiff(['debateEnabled', 'pollEnabled', 'commentDislikeEnabled', 'commentLikeLabel', 'commentDislikeLabel', 'pinRequestReviewers']),
            staff: policyDiff(['managerMax', 'staffPostDailyLimit', 'staffCommentDailyLimit', 'staffSanctionDailyLimit']),
        };
    }

    /** 가젯 선택 비교 키 — 고른 차례와 무관하게 같은 묶음이면 같다 */
    private static gadgetKey(keys: readonly string[]): string {
        return JSON.stringify([...new Set(keys)].sort());
    }

    /** 바뀐 칸 비교 키 — 이름 칸의 빈 글자는 「기본값 따름」(null)과 같고, 여러 개 고르기는 차례와 무관하다 */
    private static policyValueKey(field: BoardPolicyField, value: BoardFieldValue): string {
        if (value === null) return 'null';
        if (UtilBoard.isLabel(field)) return JSON.stringify(UtilBoard.labelValue(value));
        if (UtilBoard.isMulti(field)) return JSON.stringify(UtilBoard.multiValue(field, value));
        return String(value).trim();
    }

    private static prefixKey(p: BoardPrefixForm): string {
        return JSON.stringify([p.id, p.name.trim(), p.staffOnly, p.adultOnly, p.color, p.active]);
    }

    /**
     * 게시판 이름의 문제(만들기·기본 정보).
     * @param {string} name - 입력한 이름.
     * @param {readonly string[]} others - 다른 게시판 이름들(대소문자 무시 중복 검사). 모르면 빈 목록 — 서버가 본다.
     * @returns {BoardTextProblem | null} 문제가 없으면 null.
     */
    static nameProblem(name: string, others: readonly string[] = []): BoardTextProblem | null {
        const trimmed = name.trim();
        if (!trimmed) return 'NAME_REQUIRED';
        if (trimmed.length > UtilBoard.NAME_MAX) return 'NAME_LENGTH';
        if (others.some((other) => other.trim().toLowerCase() === trimmed.toLowerCase())) return 'NAME_DUPLICATE';
        return null;
    }

    static nameEnProblem(nameEn: string): BoardTextProblem | null {
        return nameEn.trim().length > UtilBoard.NAME_EN_MAX ? 'NAME_EN_LENGTH' : null;
    }

    /** slug 의 문제 — 형식·길이·예약어·중복 */
    static slugProblem(slug: string, others: readonly string[] = []): BoardTextProblem | null {
        const value = slug.trim();
        if (!value) return 'SLUG_REQUIRED';
        if (value.length < UtilBoard.SLUG_MIN || value.length > UtilBoard.SLUG_MAX) return 'SLUG_LENGTH';
        if (!SLUG_PATTERN.test(value)) return 'SLUG_FORMAT';
        if (UtilBoard.SLUG_RESERVED.includes(value)) return 'SLUG_RESERVED';
        if (others.includes(value)) return 'SLUG_DUPLICATE';
        return null;
    }

    static descProblem(description: string): BoardTextProblem | null {
        return description.trim().length > UtilBoard.DESC_MAX ? 'DESC_LENGTH' : null;
    }

    static textProblem(text: string): BoardTextProblem | null {
        return UtilBoard.textLength(text) > UtilBoard.TEXT_MAX ? 'TEXT_LENGTH' : null;
    }

    /**
     * 글 양식 문제 — 2,000자 초과
     * @param {string} text - 글 양식.
     * @returns {BoardTextProblem | null} 문제가 없으면 null.
     */
    static templateProblem(text: string): BoardTextProblem | null {
        return UtilBoard.textLength(text) > UtilBoard.TEMPLATE_MAX ? 'TEMPLATE_LENGTH' : null;
    }

    /**
     * 고유 말머리 이름들의 문제 — 비었나·길이·중복(공통 말머리 이름 포함, 대소문자 무시).
     * @param {readonly BoardPrefixForm[]} prefixes - 고유 말머리 입력.
     * @param {readonly string[]} commonNames - 공통 말머리 이름(지금 언어).
     * @returns {Record<string, BoardTextProblem>} 행 key → 문제.
     */
    static prefixProblems(prefixes: readonly BoardPrefixForm[], commonNames: readonly string[]): Record<string, BoardTextProblem> {
        const out: Record<string, BoardTextProblem> = {};
        const seen = new Map<string, number>();
        for (const name of [...commonNames, ...prefixes.map((p) => p.name)]) {
            const key = name.trim().toLowerCase();
            if (key) seen.set(key, (seen.get(key) ?? 0) + 1);
        }
        for (const p of prefixes) {
            const name = p.name.trim();
            if (!name) out[p.key] = 'PREFIX_NAME_REQUIRED';
            else if (name.length > UtilBoard.PREFIX_NAME_MAX) out[p.key] = 'PREFIX_NAME_LENGTH';
            else if ((seen.get(name.toLowerCase()) ?? 0) > 1) out[p.key] = 'PREFIX_NAME_DUPLICATE';
            else if (UtilBoard.colorProblem(p.color)) out[p.key] = 'COLOR_FORMAT'; // 직접 고르기 칸에 틀린 글자
        }
        return out;
    }

    /** 이 종류의 게시판이 「비회원 포함 모두」를 받는가 — 핫딜(일반 등급 이상 하한)·직접홍보(사업자 전용)는 받지 않는다 */
    static guestAllowed(kind: BoardKind): boolean {
        return kind === 'GENERAL' || kind === 'GROUP';
    }

    /**
     * 최소 자격 칸의 문제 — 서버 `BoardService` 저장 검사(`policy.writeMin|commentMin`)와 같다.
     *  · 비회원을 받지 않는 종류(핫딜·직접홍보)에 `GUEST` 를 고른 경우 → `GUEST_NOT_ALLOWED`
     *  · 핫딜 게시판 글쓰기에 첫 등급(새싹)을 고른 경우 → `DEAL_FIRST_GRADE`(핫딜 글쓰기는 일반 등급 이상)
     * @param {string | null} firstGradeKey - 등급 목록의 첫 등급 키 — 모르면 null(그 검사는 서버에 맡긴다).
     * @returns {Partial<Record<'writeMin' | 'commentMin', BoardLevelProblem>>} 칸 → 문제.
     */
    static levelProblems(kind: BoardKind, policy: BoardPolicyForm, firstGradeKey: string | null): Partial<Record<'writeMin' | 'commentMin', BoardLevelProblem>> {
        const out: Partial<Record<'writeMin' | 'commentMin', BoardLevelProblem>> = {};
        if (!UtilBoard.guestAllowed(kind)) {
            if (policy.writeMin === BOARD_MIN_GUEST) out.writeMin = 'GUEST_NOT_ALLOWED';
            if (policy.commentMin === BOARD_MIN_GUEST) out.commentMin = 'GUEST_NOT_ALLOWED';
        }
        if (kind === 'DEAL' && firstGradeKey && policy.writeMin === firstGradeKey) out.writeMin = 'DEAL_FIRST_GRADE';
        return out;
    }

    /** 사용 중인 고유 말머리 수 */
    static activePrefixCount(prefixes: readonly BoardPrefixForm[]): number {
        return prefixes.filter((p) => p.active).length;
    }

    /** 새 고유 말머리 행 */
    static newPrefix(seq: number): BoardPrefixForm {
        return { key: `new-${seq}`, id: null, name: '', staffOnly: false, adultOnly: false, color: null, active: true, usageCount: 0 };
    }

    /** 배열에서 한 칸 옮긴 새 배열 */
    static moved<T>(list: readonly T[], index: number, step: -1 | 1): T[] {
        const to = index + step;
        if (index < 0 || to < 0 || to >= list.length) return [...list];
        const next = [...list];
        const [item] = next.splice(index, 1);
        next.splice(to, 0, item as T);
        return next;
    }

    // ── 목록 ─

    /** 소모임 게시판(종류 `GROUP`) — 메뉴·게시판 페이지가 없고, 목록에서는 맨 아래 따로 묶이며 순서를 옮기지 않는다 */
    static isGroup(board: { kind: BoardKind }): boolean {
        return board.kind === 'GROUP';
    }

    /**
     * 목록을 거른다 — 이름·영문명·slug(대소문자 무시), 사용 여부, 기본값과 다른 것만, 소모임 게시판만.
     * @returns {BoardListItem[]} 걸러낸 게시판 — 일반 게시판(원래 순서) 뒤에 소모임 게시판(원래 순서).
     */
    static filterBoards(boards: readonly BoardListItem[], keyword: string, active: BoardActiveFilter, differingOnly: boolean, groupOnly = false): BoardListItem[] {
        const needle = keyword.trim().toLowerCase();
        const matched = boards.filter((b) => {
            if (groupOnly && !UtilBoard.isGroup(b)) return false;
            if (needle && !b.name.toLowerCase().includes(needle) && !(b.nameEn ?? '').toLowerCase().includes(needle) && !b.slug.includes(needle)) return false;
            if (active === 'ACTIVE' && !b.active) return false;
            if (active === 'INACTIVE' && b.active) return false;
            return !differingOnly || b.diffCount > 0;
        });
        return [...matched.filter((b) => !UtilBoard.isGroup(b)), ...matched.filter((b) => UtilBoard.isGroup(b))];
    }

    /**
     * 일반 게시판 하나를 일반 게시판끼리 한 칸 옮긴 뒤의 전체 ID 순서 — 옮길 수 없으면 null(끝 판정은 걸러진 목록이 아니라 전체 일반 게시판 기준).
     * `PUT …/order` 는 모든 ID 를 받으므로 일반 게시판의 새 순서 뒤에 소모임 게시판을 지금 순서대로 붙인다. 소모임 게시판은 옮기지 않는다(null).
     */
    static orderAfterMove(boards: readonly BoardListItem[], id: string, step: -1 | 1): string[] | null {
        const general = boards.filter((b) => !UtilBoard.isGroup(b));
        const at = general.findIndex((b) => b.id === id);
        const to = at + step;
        if (at < 0 || to < 0 || to >= general.length) return null;
        return [...UtilBoard.moved(general, at, step).map((b) => b.id), ...boards.filter((b) => UtilBoard.isGroup(b)).map((b) => b.id)];
    }

    // ── 표시 ─

    /**
     * 표시색의 CSS 색 값(라이트·다크) — 13색은 PrimeVue 팔레트(라이트 500 · 분홍만 600, 다크 400), 직접 고른 `#RRGGBB` 는 두 바탕에서 읽히게 명도만 보정(`UtilColor.cssPair`, 공개 화면과 같은 식).
     * 색 없음·틀린 값이면 null.
     */
    static colorVar(color: BoardDisplayColor | null): string | null {
        if (!color) return null;
        if (UtilColor.isHex(color)) return UtilColor.cssPair(color);
        if (!Object.prototype.hasOwnProperty.call(COLOR_PALETTE, color)) return null;
        const name = COLOR_PALETTE[color as BoardColor];
        const light = COLOR_LIGHT_STEP[color as BoardColor] ?? 500;
        return `light-dark(var(--p-${name}-${light}), var(--p-${name}-400))`;
    }

    /**
     * 표시색 값을 읽는다 — 13색 이름(대문자)은 그대로, `#rrggbb` 는 대문자(서버 `BoardFields.normalizeColor` 와 같은 모양), 그 밖(빈 값·틀린 글자)은 null.
     * 메뉴 글자 색(`UtilMenu.normalizeTextColor`)도 같은 규칙이다.
     * @param {unknown} value - 서버 값 또는 입력 중인 글자.
     * @returns {string | null} 정리한 값 또는 null(자동·기본).
     */
    static normalizeColor(value: unknown): string | null {
        if (typeof value !== 'string') return null;
        const text = value.trim();
        if ((UtilBoard.COLORS as readonly string[]).includes(text)) return text;
        return UtilColor.isHex(text) ? text.toUpperCase() : null;
    }

    /** 표시색의 문제 — 자동(null)·13색·`#RRGGBB` 가 아니면(직접 고르기 칸에 틀린 글자) `COLOR_FORMAT` */
    static colorProblem(color: BoardDisplayColor | null): BoardTextProblem | null {
        return color !== null && UtilBoard.normalizeColor(color) === null ? 'COLOR_FORMAT' : null;
    }

    /**
     * 매니저·스텝을 지정할 수 있는 종류인가 — 일반·핫딜(핫딜 담당 = 그 게시판 매니저·스텝, hotdeal-contract). 직접홍보·소모임은 안 된다(서버 `BoardStaffService` 와 같게)
     * @param {BoardKind} kind - 게시판 종류.
     * @returns {boolean} 지정할 수 있으면 true.
     */
    static staffAssignable(kind: BoardKind): boolean {
        return kind === 'GENERAL' || kind === 'DEAL';
    }

    /** 종류 배지의 PrimeVue severity */
    static kindSeverity(kind: BoardKind): 'secondary' | 'danger' | 'warn' | 'info' {
        return kind === 'DEAL' ? 'danger' : kind === 'PROMO' ? 'warn' : kind === 'GROUP' ? 'info' : 'secondary';
    }

    /** 목록 모양 아이콘 */
    static layoutIcon(layout: BoardLayout): string {
        return layout === 'CARD' ? 'pi pi-clone' : layout === 'GALLERY' ? 'pi pi-images' : 'pi pi-list';
    }

    /**
     * 서버가 거절한 칸을 읽는다(`400 VALIDATION` · `409` 의 `fields`). 정책 칸은 `policy.` 를 뗀 이름으로 둔다.
     * @param {unknown} e - 잡은 오류.
     * @returns {BoardFieldErrors | null} 칸이 없는 오류면 null.
     */
    static fieldErrorsOf(e: unknown): BoardFieldErrors | null {
        if (!(e instanceof ApiError) || !e.fields || Object.keys(e.fields).length === 0) return null;
        const fields: Record<string, string> = {};
        for (const [name, message] of Object.entries(e.fields)) fields[name.replace(/^policy\./, '')] = message;
        return { fields, form: '' };
    }
}
