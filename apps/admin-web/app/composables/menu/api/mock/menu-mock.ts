// MOCK: 메뉴 관리 API(`/api/admin/menu-sets…`, `/api/admin/menu/target-options`, `/api/admin/admin-menu`)의 메모리 목업 — `useMenuApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `menu/api/mock/` 과 함께 지운다. 응답 모양·오류 코드는 `useMenuApi.ts` 머리말(후보 계약)을 따른다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   menu-fail       메뉴 그룹 목록·관리자 메뉴 조회가 `500 INTERNAL` 로 실패(오류 상태와 다시 시도)
//   menu-empty      처음 불러올 때 모든 메뉴 그룹의 트리가 비어 있다(빈 상태)
//   menu-conflict   트리 저장이 `409 MENU_VERSION_CONFLICT` 로 실패(다른 사람이 먼저 저장 — [다시 불러오기])
//   menu-reject     트리 저장이 `400 VALIDATION` + 첫 노드의 `fields["nodes[0].label"]`(관리자 메뉴는 `modes.normal[0].label`)로 거절(줄에 서버 문구)
//   menu-readonly   저장 권한이 없는 운영자 흉내 — 응답의 `editable: false`, 저장하면 `403 PERMISSION_DENIED`
//   sales-off       공동구매 판매 스위치가 꺼져 있다(`GROUP_BUYS` 계열 항목 상태 `SALES_OFF`)
//
// ── 흉내 ─
//   서버 규칙: 화면과 같은 검사(`UtilMenu.validate` · `UtilAdminMenuConfig.validate`)를 다시 해 틀린 칸을 `fields["nodes[i].children[j].<칸>"]` 로 모아 400 VALIDATION,
//   한 그룹 안에서 자동 추가 폴더는 하나(여럿이면 400), SIDE 가 아닌 그룹의 모바일 탭·자동 추가는 꺼서 저장, 저장마다 `version` 을 올린다.
//   메뉴 그룹: key 형식·길이(400) · 중복 409 `MENU_SET_KEY_DUPLICATE` · 기본 그룹 삭제 409 `MENU_SET_SYSTEM` · 없는 그룹 404 `MENU_SET_NOT_FOUND`.
//   권한: 조회 `layout.read`, 공개 메뉴 저장 `layout.settings`, 관리자 메뉴 저장 `admin-menu.manage` — 없으면 403 `PERMISSION_DENIED`.
//   게시판·등급은 이 목업 안의 고정 목록이다(실제 서버는 게시판 표·등급 설정을 읽는다). 바꾼 것은 메모리에만 남아 새로고침하면 처음으로 돌아간다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    AdminMenuConfigNodeDto,
    AdminMenuConfigResponse,
    AdminMenuConfigSaveRequest,
    MenuBoardOption,
    MenuGradeOption,
    MenuItemDto,
    MenuFolderDto,
    MenuNodeDto,
    MenuSet,
    MenuSetCreateRequest,
    MenuSetUpdateRequest,
    MenuTargetOptions,
    MenuTreeResponse,
    MenuTreeSaveRequest,
} from '~/composables/menu/api/useMenuApi.types';
import type { AdminMenuMode } from '~/utils/util-admin-menu';
import UtilAdminMenuConfig from '~/utils/util-admin-menu-config';
import type { MenuProblemCode } from '~/utils/util-menu';
import UtilMenu from '~/utils/util-menu';
import UtilMenuTree from '~/utils/util-menu-tree';

type Can = (code: string) => boolean;

const BOARDS: MenuBoardOption[] = [
    { id: '7311000000000200001', slug: 'notice', name: '공지사항', nameEn: 'Notice', active: true },
    { id: '7311000000000200002', slug: 'free', name: '자유게시판', nameEn: 'Free board', active: true },
    { id: '7311000000000200003', slug: 'humor', name: '유머', nameEn: 'Humor', active: true },
    { id: '7311000000000200004', slug: 'qna', name: '질문답변', nameEn: 'Q&A', active: true },
    { id: '7311000000000200005', slug: 'photo', name: '사진', nameEn: null, active: true },
    { id: '7311000000000200006', slug: 'game', name: '게임', nameEn: 'Games', active: false },
    { id: '7311000000000200007', slug: 'hotdeal', name: '핫딜', nameEn: 'Hot deals', active: true },
    { id: '7311000000000200008', slug: 'market', name: '중고장터', nameEn: 'Market', active: true },
    { id: '7311000000000200009', slug: 'staff', name: '매니저·스텝 게시판', nameEn: 'Staff board', active: true },
];

const GRADES: MenuGradeOption[] = [
    { gradeKey: 'GRADE_1', label: '새싹', level: 1 },
    { gradeKey: 'GRADE_2', label: '일반', level: 2 },
    { gradeKey: 'GRADE_3', label: '우수', level: 3 },
    { gradeKey: 'GRADE_4', label: '최우수', level: 4 },
];

/** 서버가 주는 한국어 문구 — 화면 번역 키로 옮기지 않는다(서버 몫, `i18n.md` 5장) */
const MESSAGE: Partial<Record<MenuProblemCode | 'ICON_FORMAT', string>> = {
    LABEL_REQUIRED: '이름을 입력해 주세요.',
    LABEL_LENGTH: '이름은 20자 이하로 적어 주세요.',
    LABEL_CHARS: '이름에 <, >, 줄바꿈은 쓸 수 없어요.',
    NAME_EN_LENGTH: '영문명은 40자 이하로 적어 주세요.',
    TARGET_REQUIRED: '연결할 대상을 골라 주세요.',
    TARGET_DUPLICATE: '이 메뉴 그룹에 이미 있는 대상이에요.',
    LOGIN_AUDIENCE: '로그인한 사람에게만 보이게 할 수 있어요.',
    GRADE_REQUIRED: '등급을 골라 주세요.',
    MOBILE_TAB_SIDE: '모바일 탭은 좌측 사이드 메뉴 그룹에서만 쓸 수 있어요.',
    ICON_FORMAT: '아이콘은 pi pi-이름 꼴로 적어 주세요.',
};
const PATH_MESSAGE = '사이트 안 주소가 올바르지 않아요.';
const HREF_MESSAGE = '외부 링크 주소가 올바르지 않아요. https:// 주소나 /로 시작하는 주소만 쓸 수 있어요.';
const TREE_MESSAGE: Record<string, string> = {
    NODES_LIMIT: `한 메뉴 그룹에는 노드를 ${UtilMenu.LIMITS.nodes}개까지 둘 수 있어요.`,
    TABS_LIMIT: `모바일 탭은 ${UtilMenu.LIMITS.mobileTabs}개까지예요.`,
    AUTO_ADD_MULTI: '새 게시판 자동 추가 폴더는 한 개만 둘 수 있어요.',
    FOLDER_DEPTH: `폴더는 ${UtilMenu.LIMITS.folderDepth}단계까지 둘 수 있어요.`,
    ITEM_DEPTH: `항목은 ${UtilMenu.LIMITS.itemDepth}단계까지 둘 수 있어요.`,
};

// ── 시드 ─

let idSeq = 300000;
/** Snowflake 모양 문자열 ID(숫자로 다루면 자릿수가 깨진다) */
const nextId = () => `73110000000${String(idSeq++).padStart(8, '0')}`;

function folder(label: string, children: MenuNodeDto[], extra: Partial<MenuFolderDto> = {}): MenuFolderDto {
    return { id: nextId(), kind: 'FOLDER', label, nameEn: null, visible: true, audience: 'ALL', minGrade: null, collapsible: false, collapsed: false, autoAdd: false, children, ...extra };
}

function item(targetType: string, extra: Partial<MenuItemDto> = {}): MenuItemDto {
    return { id: nextId(), kind: 'ITEM', label: null, nameEn: null, visible: true, audience: 'ALL', minGrade: null, targetType, targetId: null, targetKey: null, settings: null, mobileTab: false, ...extra };
}

const board = (slug: string, extra: Partial<MenuItemDto> = {}) => item('BOARD', { targetId: BOARDS.find((b) => b.slug === slug)!.id, ...extra });
const builtin = (key: string, extra: Partial<MenuItemDto> = {}) => item('BUILTIN', { targetKey: key, ...extra });

function seedTrees(): Record<string, MenuNodeDto[]> {
    return {
        main: [
            builtin('HOME', { mobileTab: true }),
            builtin('POPULAR', { mobileTab: true }),
            builtin('LATEST'),
            folder(
                '커뮤니티',
                [
                    board('free', { mobileTab: true }),
                    board('humor'),
                    board('qna', { label: '묻고 답하기', nameEn: 'Ask' }),
                    folder('취미', [board('photo'), board('game')], { collapsible: true, collapsed: true }),
                ],
                { nameEn: 'Community', collapsible: true, autoAdd: true },
            ),
            folder('핫딜·공구', [board('hotdeal', { mobileTab: true }), builtin('GROUP_BUYS')], { nameEn: 'Deals' }),
            folder('소모임', [builtin('GROUPS_ALL'), builtin('GROUPS_MINE', { audience: 'MEMBER' }), builtin('GROUPS_NEW', { audience: 'GRADE_MIN', minGrade: 'GRADE_2' })], { collapsible: true }),
            folder('보관함', [board('market')], { visible: false }),
            folder('매니저·스텝 공간', [board('staff')], { audience: 'STAFF' }),
            item('URL', { label: '공식 블로그', nameEn: 'Blog', settings: { href: 'https://blog.roboyam.com', newTab: true } }),
            item('POLL', { label: '이번 주 투표', targetId: '42', settings: { pollId: 42, showResult: true } }),
        ],
        mypage: [
            builtin('MY_HOME', { audience: 'MEMBER' }),
            builtin('MY_GRADE', { audience: 'MEMBER' }),
            builtin('MY_POINTS', { audience: 'MEMBER' }),
            folder('활동', [builtin('MESSAGES', { audience: 'MEMBER' }), builtin('NOTIFICATIONS', { audience: 'MEMBER' }), builtin('MY_SCHEDULED', { audience: 'MEMBER' })], { nameEn: 'Activity' }),
            folder(
                '설정',
                [
                    builtin('SETTINGS_PROFILE', { audience: 'MEMBER' }),
                    builtin('SETTINGS_ACCOUNT', { audience: 'MEMBER' }),
                    builtin('SETTINGS_VIEW', { audience: 'MEMBER' }),
                    builtin('SETTINGS_BLOCKS', { audience: 'MEMBER' }),
                    builtin('NOTIFICATION_SETTINGS', { audience: 'MEMBER' }),
                ],
                { nameEn: 'Settings', collapsible: true },
            ),
        ],
        'group-buy': [
            builtin('GROUP_BUYS', { label: '진행 중인 공구', nameEn: 'Ongoing deals' }),
            builtin('GROUP_BUY_ORDERS', { audience: 'MEMBER' }),
            item('PATH', { label: '공구 이용 안내', settings: { path: '/boards/notice', newTab: false } }),
        ],
    };
}

interface Store {
    sets: MenuSet[];
    trees: Record<string, MenuNodeDto[]>;
    versions: Record<string, number>;
    admin: Record<AdminMenuMode, AdminMenuConfigNodeDto[]>;
    adminVersion: number;
}

let store: Store | null = null;

function db(): Store {
    if (store) return store;
    const empty = mockFlags().has('menu-empty');
    const trees = empty ? { main: [], mypage: [], 'group-buy': [] } : seedTrees();
    const at = '2026-10-20T03:10:00Z';
    const set = (key: string, name: string, nameEn: string, placement: MenuSet['placement'], description: string): MenuSet => ({
        key,
        name,
        nameEn,
        placement,
        enabled: true,
        description,
        system: true,
        nodeCount: UtilMenuTree.count(UtilMenu.toEdit(trees[key] ?? [])),
        updatedAt: at,
    });
    store = {
        sets: [
            set('main', '좌측 사이드 메뉴', 'Side menu', 'SIDE', '공개 사이트의 기본 내비게이션이에요.'),
            set('mypage', '마이페이지 메뉴', 'My page menu', 'MYPAGE', '마이페이지 왼쪽 메뉴예요.'),
            set('group-buy', '공동구매 메뉴', 'Group buy menu', 'GROUP_BUY', '공동구매 영역의 사이드 메뉴예요.'),
        ],
        trees,
        versions: { main: 1, mypage: 1, 'group-buy': 1 },
        admin: UtilAdminMenuConfig.seed(),
        adminVersion: 1,
    };
    return store;
}

const denied = (): ApiError => new ApiError({ status: 403, code: 'PERMISSION_DENIED', message: '이 작업을 할 권한이 없어요.' });
const validation = (fields: Record<string, string>): ApiError => new ApiError({ status: 400, code: 'VALIDATION', message: '입력값을 확인해 주세요.', fields });
const notFound = (): ApiError => new ApiError({ status: 404, code: 'MENU_SET_NOT_FOUND', message: '메뉴 그룹을 찾을 수 없어요.' });
const conflict = (): ApiError => new ApiError({ status: 409, code: 'MENU_VERSION_CONFLICT', message: '다른 사람이 메뉴를 바꿨어요. 새로 불러온 뒤 다시 바꿔 주세요.' });

function findSet(key: string): MenuSet {
    const found = db().sets.find((s) => s.key === key);
    if (!found) throw notFound();
    return found;
}

function treeResponse(key: string): MenuTreeResponse {
    const s = db();
    return { set: { ...findSet(key) }, nodes: UtilMenuTree.clone(s.trees[key] ?? []), version: `v${s.versions[key] ?? 1}`, editable: !mockFlags().has('menu-readonly') };
}

/** 같은 규칙으로 다시 보고 틀린 칸을 `fields` 로 모은다 */
function problemFields(index: Map<string, string>, rows: Record<string, Record<string, string>>): Record<string, string> {
    const byKey = new Map<string, string>();
    for (const [path, key] of index) byKey.set(key, path);
    const fields: Record<string, string> = {};
    const fieldName: Record<string, string> = { target: 'targetId', path: 'settings.path', href: 'settings.href' };
    for (const [key, problems] of Object.entries(rows)) {
        const path = byKey.get(key);
        if (!path) continue;
        for (const [field, code] of Object.entries(problems)) {
            const message = code.startsWith('PATH_') ? PATH_MESSAGE : code.startsWith('HREF_') ? HREF_MESSAGE : (MESSAGE[code as MenuProblemCode] ?? '값을 확인해 주세요.');
            fields[`${path}.${fieldName[field] ?? field}`] = message;
        }
    }
    return fields;
}

// ── 메뉴 그룹 ─

export async function mockMenuSets(can: Can): Promise<MenuSet[]> {
    await mockDelay();
    if (mockFlags().has('menu-fail')) throw MOCK_ERRORS.internal();
    if (!can('layout.read')) throw denied();
    return db().sets.map((s) => ({ ...s }));
}

function setFields(body: MenuSetUpdateRequest): Record<string, string> {
    const fields: Record<string, string> = {};
    const name = body.name.trim();
    if (!name) fields.name = '이름을 입력해 주세요.';
    else if ([...name].length > UtilMenu.SET_LIMITS.nameMax) fields.name = `이름은 ${UtilMenu.SET_LIMITS.nameMax}자 이하로 적어 주세요.`;
    if ((body.nameEn ?? '').length > UtilMenu.SET_LIMITS.nameEnMax) fields.nameEn = `영문명은 ${UtilMenu.SET_LIMITS.nameEnMax}자 이하로 적어 주세요.`;
    if ((body.description ?? '').length > UtilMenu.SET_LIMITS.descMax) fields.description = `설명은 ${UtilMenu.SET_LIMITS.descMax}자 이하로 적어 주세요.`;
    if (!UtilMenu.PLACEMENTS.includes(body.placement)) fields.placement = '위치를 골라 주세요.';
    return fields;
}

export async function mockMenuSetCreate(can: Can, body: MenuSetCreateRequest): Promise<MenuSet> {
    await mockDelay();
    if (!can('layout.settings') || mockFlags().has('menu-readonly')) throw denied();
    const s = db();
    const fields = setFields(body);
    const keyProblem = UtilMenu.setKeyProblem(body.key, []);
    if (keyProblem) fields.key = '코드는 영문 소문자로 시작하고 영문 소문자·숫자·하이픈(-)으로 2~30자예요.';
    if (Object.keys(fields).length) throw validation(fields);
    if (s.sets.some((set) => set.key === body.key.trim())) {
        throw new ApiError({ status: 409, code: 'MENU_SET_KEY_DUPLICATE', message: '이미 있는 코드예요.', fields: { key: '이미 있는 코드예요.' } });
    }
    const created: MenuSet = {
        key: body.key.trim(),
        name: body.name.trim(),
        nameEn: body.nameEn?.trim() || null,
        placement: body.placement,
        enabled: body.enabled,
        description: body.description?.trim() || null,
        system: false,
        nodeCount: 0,
        updatedAt: new Date().toISOString(),
    };
    s.sets.push(created);
    s.trees[created.key] = [];
    s.versions[created.key] = 1;
    return { ...created };
}

export async function mockMenuSetUpdate(can: Can, key: string, body: MenuSetUpdateRequest): Promise<MenuSet> {
    await mockDelay();
    if (!can('layout.settings') || mockFlags().has('menu-readonly')) throw denied();
    const target = findSet(key);
    const fields = setFields(body);
    if (Object.keys(fields).length) throw validation(fields);
    Object.assign(target, {
        name: body.name.trim(),
        nameEn: body.nameEn?.trim() || null,
        placement: target.system ? target.placement : body.placement,
        enabled: body.enabled,
        description: body.description?.trim() || null,
        updatedAt: new Date().toISOString(),
    });
    return { ...target };
}

export async function mockMenuSetDelete(can: Can, key: string): Promise<void> {
    await mockDelay();
    if (!can('layout.settings') || mockFlags().has('menu-readonly')) throw denied();
    const s = db();
    const target = findSet(key);
    if (target.system) throw new ApiError({ status: 409, code: 'MENU_SET_SYSTEM', message: '기본 메뉴 그룹은 지울 수 없어요.' });
    s.sets = s.sets.filter((set) => set.key !== key);
    delete s.trees[key];
    delete s.versions[key];
}

// ── 트리 ─

export async function mockMenuTree(can: Can, key: string): Promise<MenuTreeResponse> {
    await mockDelay();
    if (!can('layout.read')) throw denied();
    return treeResponse(key);
}

export async function mockMenuTreeSave(can: Can, key: string, body: MenuTreeSaveRequest): Promise<MenuTreeResponse> {
    await mockDelay();
    const flags = mockFlags();
    if (!can('layout.settings') || flags.has('menu-readonly')) throw denied();
    const s = db();
    const set = findSet(key);
    if (flags.has('menu-conflict') || body.version !== `v${s.versions[key] ?? 1}`) throw conflict();
    if (flags.has('menu-reject') && body.nodes.length) throw validation({ 'nodes[0].label': '이 이름은 쓸 수 없어요(금칙어).' });

    const edit = UtilMenu.toEdit(body.nodes);
    const problems = UtilMenu.validate(edit, set.placement);
    const fields = problemFields(UtilMenuTree.pathIndex(edit), problems.rows);
    if (problems.tree.length) fields.nodes = problems.tree.map((code) => TREE_MESSAGE[code] ?? code).join(' ');
    if (Object.keys(fields).length) throw validation(fields);

    // SIDE 가 아닌 그룹의 모바일 탭·자동 추가는 꺼서 저장한다(toDto 가 끈다). 새 노드에 ID 를 매긴다
    const assign = (nodes: MenuNodeDto[]): MenuNodeDto[] =>
        nodes.map((node) => (node.kind === 'FOLDER' ? { ...node, id: node.id ?? nextId(), children: assign(node.children) } : { ...node, id: node.id ?? nextId() }));
    s.trees[key] = assign(UtilMenu.toDto(edit, set.placement));
    s.versions[key] = (s.versions[key] ?? 1) + 1;
    set.nodeCount = UtilMenuTree.count(edit);
    set.updatedAt = new Date().toISOString();
    return treeResponse(key);
}

export async function mockMenuTargetOptions(can: Can, key: string): Promise<MenuTargetOptions> {
    await mockDelay();
    if (!can('layout.read')) throw denied();
    findSet(key);
    return { boards: BOARDS.map((b) => ({ ...b })), grades: GRADES.map((g) => ({ ...g })), salesEnabled: !mockFlags().has('sales-off') };
}

// ── 관리자 메뉴 ─

function adminResponse(): AdminMenuConfigResponse {
    const s = db();
    return { modes: UtilMenuTree.clone(s.admin), version: `a${s.adminVersion}`, editable: !mockFlags().has('menu-readonly') };
}

export async function mockAdminMenu(can: Can): Promise<AdminMenuConfigResponse> {
    await mockDelay();
    if (mockFlags().has('menu-fail')) throw MOCK_ERRORS.internal();
    if (!can('layout.read')) throw denied();
    return adminResponse();
}

export async function mockAdminMenuSave(can: Can, body: AdminMenuConfigSaveRequest): Promise<AdminMenuConfigResponse> {
    await mockDelay();
    const flags = mockFlags();
    if (!can('admin-menu.manage') || flags.has('menu-readonly')) throw denied();
    const s = db();
    if (flags.has('menu-conflict') || body.version !== `a${s.adminVersion}`) throw conflict();
    if (flags.has('menu-reject') && body.modes.normal.length) throw validation({ 'modes.normal[0].label': '이 이름은 쓸 수 없어요(금칙어).' });

    const edit = { normal: UtilAdminMenuConfig.toEdit(body.modes.normal), settings: UtilAdminMenuConfig.toEdit(body.modes.settings) };
    const problems = UtilAdminMenuConfig.validate(edit);
    const index = new Map([...UtilMenuTree.pathIndex(edit.normal, 'modes.normal'), ...UtilMenuTree.pathIndex(edit.settings, 'modes.settings')]);
    const fields = problemFields(index, problems.rows);
    for (const mode of UtilAdminMenuConfig.MODES) {
        if (UtilAdminMenuConfig.structureProblem(edit[mode])) fields[`modes.${mode}`] = '묶음은 맨 위에만, 하위 메뉴는 묶음 안이나 맨 위에만 둘 수 있어요.';
    }
    if (Object.keys(fields).length) throw validation(fields);

    const assign = (nodes: AdminMenuConfigNodeDto[]): AdminMenuConfigNodeDto[] => nodes.map((node) => ({ ...node, id: node.id ?? nextId(), children: node.children ? assign(node.children) : null }));
    s.admin = { normal: assign(UtilAdminMenuConfig.toDto(edit.normal)), settings: assign(UtilAdminMenuConfig.toDto(edit.settings)) };
    s.adminVersion += 1;
    return adminResponse();
}
