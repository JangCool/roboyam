import { ApiError } from '~/composables/common/useApiClient';
import type {
    BoardColor,
    BoardDefaults,
    BoardDefaultsField,
    BoardDetail,
    BoardKind,
    BoardLayout,
    BoardListItem,
    BoardPolicy,
    BoardPolicyField,
    BoardPostEditor,
    BoardPrefix,
    BoardPrefixSaveItem,
    BoardSaveRequest,
    BoardWriteLock,
} from '~/composables/board/api/useBoardApi.types';
import { BOARD_MIN_GUEST, BOARD_POLICY_FIELDS } from '~/composables/board/api/useBoardApi.types';

/** 칸 하나를 그리는 방식 — 숫자 글자 입력 · 켜고 끄기 · 고르기 · 등급 고르기(글쓰기·댓글) */
export type BoardFieldKind = 'number' | 'bool' | 'choice' | 'writeMin' | 'commentMin' | 'grade';
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
/** 게시판 만들기·기본 정보·말머리 검사 결과 — 문구는 `UtilBoardMessages.problem` */
export type BoardTextProblem = 'NAME_REQUIRED' | 'NAME_LENGTH' | 'NAME_DUPLICATE' | 'SLUG_REQUIRED' | 'SLUG_FORMAT' | 'SLUG_LENGTH' | 'SLUG_RESERVED' | 'SLUG_DUPLICATE' | 'DESC_LENGTH' | 'NAME_EN_LENGTH' | 'TEXT_LENGTH' | 'PREFIX_NAME_REQUIRED' | 'PREFIX_NAME_LENGTH' | 'PREFIX_NAME_DUPLICATE';

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
] as const satisfies readonly BoardDefaultsField[];
export type BoardNumField = (typeof BOARD_NUM_FIELDS)[number];

/** 기본 설정 입력 — 숫자 칸만 글자 */
export type BoardDefaultsForm = Omit<BoardDefaults, BoardNumField> & Record<BoardNumField, string>;
/** 게시판별 정책 입력 — `null` = 기본값 따름, 숫자 칸은 글자 */
export type BoardPolicyForm = { [K in BoardPolicyField]: K extends BoardNumField ? string | null : BoardDefaults[K] | null };
/** 칸 하나의 입력값 */
export type BoardFieldValue = string | number | boolean | null;

/** 고유 말머리 입력 행 — `key` 는 화면 안 식별자(새 행은 `new-N`) */
export interface BoardPrefixForm {
    key: string;
    id: string | null;
    name: string;
    staffOnly: boolean;
    color: BoardColor | null;
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
    color: BoardColor | null;
    allowAdult: boolean;
    investmentNotice: boolean;
    writeLock: BoardWriteLock | null;
    introText: string;
    writeGuideText: string;
    /** 가젯 노출 금지로 고른 가젯 키 — 게시판마다의 값(기본값 따름 없음) */
    gadgetExclusions: string[];
    policy: BoardPolicyForm;
    commonPrefixes: BoardCommonPrefixForm[];
    prefixes: BoardPrefixForm[];
}

/** 서버가 거절한 칸 — 칸 이름(정책은 `policy.` 를 뗀 이름, 말머리는 `prefixes[i].name`) → 문구, 칸에 붙지 않는 것은 `form` */
export interface BoardFieldErrors {
    fields: Record<string, string>;
    form: string;
}

/** 목록 사용 여부 거르기 */
export type BoardActiveFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

const SPECS: Record<BoardDefaultsField, BoardFieldSpec> = {
    dateDisplay: { field: 'dateDisplay', kind: 'dateDisplay', choices: ['STANDARD', 'RELATIVE'], overridable: false, permission: 'board.settings' },
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
    pinnedMax: { field: 'pinnedMax', kind: 'number', min: 0, max: 5, unit: 'count', overridable: true, permission: 'board.settings' },
    indexable: { field: 'indexable', kind: 'bool', overridable: true, permission: 'board.settings' },
    searchable: { field: 'searchable', kind: 'bool', overridable: true, permission: 'board.settings' },
    inHome: { field: 'inHome', kind: 'bool', overridable: true, permission: 'board.settings' },
    attachmentPurgeEnabled: { field: 'attachmentPurgeEnabled', kind: 'bool', overridable: false, permission: 'board.settings' },
    attachmentRetentionDays: { field: 'attachmentRetentionDays', kind: 'number', min: 1, max: 3650, unit: 'days', overridable: false, permission: 'board.settings' },
    pointMode: { field: 'pointMode', kind: 'choice', choices: ['ON', 'OFF'], overridable: true, permission: 'board.settings' },
    commentProtectMin: { field: 'commentProtectMin', kind: 'number', min: 0, max: 100, unit: 'count', overridable: true, permission: 'board.settings' },
    commentProtectEdit: { field: 'commentProtectEdit', kind: 'bool', overridable: true, permission: 'board.settings' },
    commentProtectDelete: { field: 'commentProtectDelete', kind: 'choice', choices: ['BLOCK', 'DELAY', 'ALLOW'], overridable: true, permission: 'board.settings' },
    commentProtectDelayMinutes: { field: 'commentProtectDelayMinutes', kind: 'choice', choices: [5, 10, 30], unit: 'minutes', overridable: true, permission: 'board.settings' },
    commentGuestDelayMinutes: { field: 'commentGuestDelayMinutes', kind: 'choice', choices: [0, 10, 30, 60, 180], unit: 'minutes', overridable: true, permission: 'board.settings' },
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
    'defaults-display': ['dateDisplay'],
    'defaults-write': ['writeMin', 'commentMin', 'postEditor', 'imageUploadAllowed', 'commentEditor'],
    'defaults-rate': ['postGapSeconds', 'postDailyLimit', 'commentGapSeconds', 'commentDailyLimit'],
    // 「홈·인기·최신 포함」(inHome)은 게시판 설정의 「가젯 노출 금지」가 대신해 화면에서 다루지 않는다(서버 칸은 남아 있고 저장 요청은 받은 값을 그대로 돌려보낸다)
    'defaults-list': ['listSize', 'pinnedMax', 'indexable', 'searchable'],
    'defaults-attachment': ['attachmentPurgeEnabled', 'attachmentRetentionDays'],
    'defaults-protect': ['pointMode', 'commentProtectMin', 'commentProtectEdit', 'commentProtectDelete', 'commentProtectDelayMinutes'],
    'defaults-guest': ['commentGuestDelayMinutes'],
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
    static readonly PREFIX_NAME_MAX = 20;
    /** 사용 중인 고유 말머리 최대 수 */
    static readonly PREFIX_ACTIVE_MAX = 6;
    /** 한 회원이 맡을 수 있는 게시판 수(매니저·스텝 합산) */
    static readonly MEMBER_BOARD_MAX = 5;
    /** 금지어 최대 개수 · 단어당 최대 글자 */
    static readonly BLOCKED_WORDS_MAX = 500;
    static readonly BLOCKED_WORD_LENGTH_MAX = 30;

    static readonly LAYOUTS: readonly BoardLayout[] = ['LIST', 'CARD', 'GALLERY'];
    static readonly KINDS: readonly BoardKind[] = ['GENERAL', 'DEAL', 'PROMO', 'GROUP'];
    static readonly COLORS: readonly BoardColor[] = ['BLUE', 'TEAL', 'AMBER', 'PURPLE', 'GREEN', 'ORANGE', 'SLATE', 'RED', 'PINK', 'INDIGO', 'SKY', 'BROWN'];
    static readonly WRITE_LOCKS: readonly (BoardWriteLock | null)[] = [null, 'POST', 'POST_COMMENT'];
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

    // ── 기본 설정 ─

    /** 서버 값 → 입력 */
    static defaultsFormOf(defaults: BoardDefaults): BoardDefaultsForm {
        const form = { ...defaults } as unknown as BoardDefaultsForm;
        for (const field of BOARD_NUM_FIELDS) form[field] = String(defaults[field]);
        return form;
    }

    /** 기본 설정 입력의 숫자 칸 문제들 */
    static defaultsProblems(form: BoardDefaultsForm): Partial<Record<BoardDefaultsField, BoardNumProblem>> {
        const out: Partial<Record<BoardDefaultsField, BoardNumProblem>> = {};
        for (const field of BOARD_NUM_FIELDS) {
            const problem = UtilBoard.numProblem(field, form[field]);
            if (problem) out[field] = problem;
        }
        return out;
    }

    /**
     * 입력 → 요청. 숫자 칸이 하나라도 맞지 않으면 null.
     * @param {BoardDefaultsForm} form - 입력.
     * @returns {BoardDefaults | null} 요청 본문.
     */
    static toDefaults(form: BoardDefaultsForm): BoardDefaults | null {
        const out = { ...form } as unknown as BoardDefaults;
        for (const field of BOARD_NUM_FIELDS) {
            if (UtilBoard.numProblem(field, form[field])) return null;
            out[field] = UtilBoard.parse(form[field]) as number;
        }
        return out;
    }

    /** 저장된 값과 다른 기본 설정 칸들 */
    static defaultsChanged(form: BoardDefaultsForm, saved: BoardDefaults): BoardDefaultsField[] {
        return (Object.keys(SPECS) as BoardDefaultsField[]).filter((field) => {
            const value = form[field];
            return UtilBoard.isNum(field) ? String(value).trim() !== String(saved[field]) : value !== saved[field];
        });
    }

    // ── 게시판별 정책 ─

    /** 서버 정책 → 입력 */
    static policyFormOf(policy: BoardPolicy): BoardPolicyForm {
        const form = { ...policy } as unknown as Record<BoardPolicyField, BoardFieldValue>;
        for (const field of BOARD_POLICY_FIELDS) {
            // 서버가 아직 주지 않는 칸(새 칸을 서버보다 먼저 배포한 경우 — 에디터 칸 등)은 「기본값 따름」(null)으로 본다
            const value = policy[field] ?? null;
            form[field] = UtilBoard.isNum(field) && value !== null ? String(value) : value;
        }
        return form as BoardPolicyForm;
    }

    /** 정책 입력의 숫자 칸 문제들(기본값 따름 칸은 보지 않는다) */
    static policyProblems(policy: BoardPolicyForm): Partial<Record<BoardPolicyField, BoardNumProblem>> {
        const out: Partial<Record<BoardPolicyField, BoardNumProblem>> = {};
        for (const field of BOARD_POLICY_FIELDS) {
            if (!UtilBoard.isNum(field)) continue;
            const value = policy[field] as string | null;
            if (value === null) continue;
            const problem = UtilBoard.numProblem(field, value);
            if (problem) out[field] = problem;
        }
        return out;
    }

    /** 정책 입력 → 요청(숫자 칸이 틀렸으면 null) */
    static toPolicy(policy: BoardPolicyForm): BoardPolicy | null {
        const out = { ...policy } as unknown as Record<BoardPolicyField, BoardFieldValue>;
        for (const field of BOARD_POLICY_FIELDS) {
            if (!UtilBoard.isNum(field)) continue;
            const value = policy[field] as string | null;
            if (value === null) continue;
            if (UtilBoard.numProblem(field, value)) return null;
            out[field] = UtilBoard.parse(value);
        }
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
        return BOARD_POLICY_FIELDS.filter((field) => policy[field] !== null).length + (writeLock ? 1 : 0);
    }

    /**
     * 칸의 유효값 — 게시판 값 ?? 기본값. 숫자 칸 입력이 틀렸으면 기본값으로 본다.
     * @returns {BoardDefaults[K]} 유효값.
     */
    static effective<K extends BoardPolicyField>(policy: BoardPolicyForm, defaults: BoardDefaults, field: K): BoardDefaults[K] {
        const value = policy[field] as BoardFieldValue;
        if (value === null) return defaults[field];
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
            color: detail.color,
            allowAdult: detail.allowAdult,
            investmentNotice: detail.investmentNotice,
            writeLock: detail.writeLock,
            introText: detail.introText ?? '',
            writeGuideText: detail.writeGuideText ?? '',
            // 서버가 아직 주지 않으면(새 칸을 서버보다 먼저 배포한 경우) 빈 목록
            gadgetExclusions: [...(detail.gadgetExclusions ?? [])],
            policy: UtilBoard.policyFormOf(detail.policy),
            commonPrefixes: detail.prefixes.flatMap((p) => (p.common ? [{ id: p.id, common: p.common, staffOnly: p.staffOnly, active: p.active, usageCount: p.usageCount }] : [])),
            prefixes: detail.prefixes.flatMap((p) => (p.common ? [] : [{ key: p.id, id: p.id, name: p.name, staffOnly: p.staffOnly, color: p.color, active: p.active, usageCount: p.usageCount }])),
        };
    }

    /** 입력 → 저장 요청(숫자 칸이 틀렸으면 null) */
    static toSaveRequest(form: BoardDetailForm): BoardSaveRequest | null {
        const policy = UtilBoard.toPolicy(form.policy);
        if (!policy) return null;
        return {
            name: form.name.trim(),
            nameEn: form.nameEn.trim() || null,
            description: form.description.trim(),
            layout: form.layout,
            viewToggle: form.viewToggle,
            color: form.color,
            allowAdult: form.allowAdult,
            investmentNotice: form.investmentNotice,
            writeLock: form.writeLock,
            introText: UtilBoard.normalizeText(form.introText),
            writeGuideText: UtilBoard.normalizeText(form.writeGuideText),
            policy,
            commonPrefixes: form.commonPrefixes.map((p) => ({ id: p.id, active: p.active })),
            prefixes: form.prefixes.map((p): BoardPrefixSaveItem => ({ id: p.id, name: p.name.trim(), staffOnly: p.staffOnly, color: p.color, active: p.active })),
            gadgetExclusions: [...form.gadgetExclusions],
        };
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
     * @returns {Record<'basic' | 'write' | 'prefix' | 'guide' | 'exposure' | 'protect' | 'staff', number>} 구획 → 바뀐 칸 수.
     */
    static detailChanges(form: BoardDetailForm, saved: BoardDetail): Record<'basic' | 'write' | 'prefix' | 'guide' | 'exposure' | 'protect' | 'staff', number> {
        const base = UtilBoard.detailFormOf(saved);
        const diff = (a: unknown, b: unknown) => (JSON.stringify(a) === JSON.stringify(b) ? 0 : 1);
        const policyDiff = (fields: readonly BoardPolicyField[]) => fields.reduce((sum, f) => sum + diff(UtilBoard.policyValueKey(form.policy[f]), UtilBoard.policyValueKey(base.policy[f])), 0);
        const basic = (['name', 'nameEn', 'description', 'layout', 'viewToggle', 'color', 'allowAdult', 'investmentNotice'] as const).reduce((sum, k) => sum + diff(form[k], base[k]), 0);
        const prefix = diff(form.commonPrefixes, base.commonPrefixes) + diff(form.prefixes.map(UtilBoard.prefixKey), base.prefixes.map(UtilBoard.prefixKey));
        return {
            basic,
            write: policyDiff(['writeMin', 'commentMin', 'postEditor', 'imageUploadAllowed', 'commentEditor']) + diff(form.writeLock, base.writeLock),
            prefix,
            guide: diff(form.introText, base.introText) + diff(form.writeGuideText, base.writeGuideText),
            // inHome 은 화면에서 다루지 않는다(가젯 노출 금지가 대신한다) — 가젯 선택은 순서 없이 묶음으로 1칸
            exposure: policyDiff(['listSize', 'pinnedMax', 'indexable', 'searchable']) + diff(UtilBoard.gadgetKey(form.gadgetExclusions), UtilBoard.gadgetKey(base.gadgetExclusions)),
            protect: policyDiff(['pointMode', 'commentProtectMin', 'commentProtectEdit', 'commentProtectDelete', 'commentProtectDelayMinutes', 'commentGuestDelayMinutes']),
            staff: policyDiff(['managerMax', 'staffPostDailyLimit', 'staffCommentDailyLimit', 'staffSanctionDailyLimit']),
        };
    }

    /** 가젯 선택 비교 키 — 고른 차례와 무관하게 같은 묶음이면 같다 */
    private static gadgetKey(keys: readonly string[]): string {
        return JSON.stringify([...new Set(keys)].sort());
    }

    private static policyValueKey(value: BoardFieldValue): string {
        return value === null ? 'null' : String(value).trim();
    }

    private static prefixKey(p: BoardPrefixForm): string {
        return JSON.stringify([p.id, p.name.trim(), p.staffOnly, p.color, p.active]);
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
        return { key: `new-${seq}`, id: null, name: '', staffOnly: false, color: null, active: true, usageCount: 0 };
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

    /**
     * 목록을 거른다 — 이름·영문명·slug(대소문자 무시), 사용 여부, 기본값과 다른 것만.
     * @returns {BoardListItem[]} 걸러낸 게시판(원래 순서).
     */
    static filterBoards(boards: readonly BoardListItem[], keyword: string, active: BoardActiveFilter, differingOnly: boolean): BoardListItem[] {
        const needle = keyword.trim().toLowerCase();
        return boards.filter((b) => {
            if (needle && !b.name.toLowerCase().includes(needle) && !(b.nameEn ?? '').toLowerCase().includes(needle) && !b.slug.includes(needle)) return false;
            if (active === 'ACTIVE' && !b.active) return false;
            if (active === 'INACTIVE' && b.active) return false;
            return !differingOnly || b.diffCount > 0;
        });
    }

    /** 한 게시판을 한 칸 옮긴 뒤의 전체 ID 순서 — 옮길 수 없으면 null(끝 판정은 걸러진 목록이 아니라 전체 목록 기준) */
    static orderAfterMove(boards: readonly BoardListItem[], id: string, step: -1 | 1): string[] | null {
        const at = boards.findIndex((b) => b.id === id);
        const to = at + step;
        if (at < 0 || to < 0 || to >= boards.length) return null;
        return UtilBoard.moved(boards, at, step).map((b) => b.id);
    }

    // ── 표시 ─

    /** 표시색의 CSS 색 값(라이트·다크) — 색 없음이면 null */
    static colorVar(color: BoardColor | null): string | null {
        if (!color) return null;
        const name = COLOR_PALETTE[color];
        return `light-dark(var(--p-${name}-500), var(--p-${name}-400))`;
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
