// MOCK: 등급 API(`GET·PUT /api/admin/grades`, `POST …/preview`)의 메모리 목업 — `useGradeApi.ts` 가 이 파일의 함수를 부른다. 서버가 붙으면 `grade/api/mock/` 와 함께 지운다.
// 등급 설정·역할 관리·회원 조회가 이 파일의 **같은 목록**을 읽는다(저장하면 세 화면이 함께 바뀐다). 역할 목업(`role-mock.ts`)의 등급 역할과 회원 목업(`member-mock.ts`)의 등급 이름·서열은
// 이 파일의 `mockGradeSnapshot()` 에서 만든다(정적 시드가 아니다). 새로고침하면 처음 값으로 돌아간다.
//
// ── 시드(목업 값 — community `grade_rules` 의 조건 시드, 뱃지는 임의로 정했다) ─
//   새싹(SEEDLING) 0일·0개 gray `pi pi-circle` · 일반(REGULAR) 7일·10개 blue `pi pi-star` · 단골(LOYAL) 30일·50개 green `pi pi-heart` · 베테랑(VETERAN) 90일·200개 amber `pi pi-crown`
//   회원 수는 고정 예시(새싹 4,120 · 일반 12,830 · 단골 2,310 · 베테랑 540). 새 등급은 서버가 `GRADE_5`, `GRADE_6` … 으로 키를 발급하고 회원은 0명이다.
//   수동 상향 회원 옵션 `CONTINUE_AUTO`(계속 자동 승급 — 기본값은 가정)
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   grade-error           목록을 읽는 요청이 `500 INTERNAL` 로 실패(오류 상태와 다시 시도)
//   grade-save-error      저장 요청이 `500 INTERNAL` 로 실패(화면 위쪽 오류 문구)
//   grade-invalid         저장 요청이 늘 `400 VALIDATION` — 단골의 이름·일수·글·댓글·뱃지 칸과 옵션에 서버 문구를 붙여 돌려준다(칸 오류 확인용)
//   grade-preview-error   영향 인원 미리보기가 `500 INTERNAL` 로 실패(화면은 "영향 인원을 확인하지 못했어요. 그래도 저장할까요?" 확인으로 대신한다)
//
// ── 정책 (사용자 확정 2026-10-21, 아래 "가정"은 확정되지 않은 해석) ─
//   · 등급은 순서 있는 목록(4단계 고정 아님)이고 이 설정이 원천이다. 등급 역할은 여기서 자동으로 만들어진다. 첫 등급(새싹)은 기본 등급이라 삭제·이동·조건 편집이 안 된다. 등급 수는 최소 2 · 최대 10단계(확정).
//   · 등급을 지우면 그 등급의 회원은 **바로 아래 등급으로 이동**한다(확정). 가정: 삭제와 이동은 한 트랜잭션, 이동한 회원도 자동 승급 판정 대상에 계속 포함, 이동 이력은 사유 `등급 삭제`로 남김(이력 화면은 후속).
//     이 목업은 지운 등급의 `memberCount` 를 바로 아래(저장된 순서에서 남아 있는 가장 가까운 아래) 등급에 더하고, 회원 목업은 `mockGradeResolve()` 로 지운 등급의 회원을 그 등급으로 본다.
//   · 판정 시점: 글·댓글 수처럼 사건으로 판정 가능한 조건은 **즉시**, 가입 일수처럼 시간이 지나야 충족되는 조건은 **배치**. 올리기만 한다(자동으로 내리지 않는다).
//   · 소급: 기준을 낮추면 이미 새 조건을 채운 회원도 승급한다. 가정: 활동 조건의 소급은 저장 직후 일괄, 일수 조건의 소급은 다음 배치.
//   · `manualPolicy`: 운영자가 **수동으로 올린** 회원의 이후 동작 옵션. 값은 전역 하나지만 **수동으로 올린 회원에게만** 적용되고, 그 밖의 회원은 이 값과 상관없이 늘 조건을 채우면 올라간다.
//     `CONTINUE_AUTO` 수동으로 올린 회원도 이후 더 높은 등급의 조건을 채우면 자동으로 올린다 / `STOP_AUTO` 수동으로 올린 회원은 이후 어떤 자동 승급 조치도 하지 않는다.
//   · 옵션 변경도 소급 영향이 있다: `STOP_AUTO` → `CONTINUE_AUTO` 로 바꾸면 이미 조건을 채운 수동 상향 회원이 새로 승급될 수 있다.
//
// ── 서버 규칙 흉내 ─
//   권한: 읽기는 `grade.read` · `member.read` · `role.read` 중 하나(회원 조회·역할 조회 화면도 등급 목록을 읽는다), 저장·미리보기는 `grade.manage` 가 없으면 `403 PERMISSION_DENIED`(로그인한 목업 계정 기준 — `admin-auth-mock.ts`).
//   저장·미리보기: 등급 2~10개(`409 GRADE_LIMIT`), 이름 필수·최대 8자(`fields.grades[i].label`)·고유(`409 GRADE_NAME_DUPLICATED`), 값은 정수·범위(`fields.grades[i].minDays`), 뱃지 색·아이콘은 허용 목록 안(`badgeColor`·`badgeIcon`),
//   옵션은 두 값 중 하나(`fields.manualPolicy`), 첫 등급은 기본 등급이 맨 앞에 그대로(`fields.grades[0].gradeKey`), 위 등급일수록 같거나 커야 한다(`409 GRADE_RULES_NOT_MONOTONIC`).
//   삭제(저장 때 요청에서 빠진 기존 등급): 첫 등급을 지우려 하면 `409 GRADE_FIRST_PROTECTED`. 소속 회원이 있어도 막지 않고 바로 아래 등급으로 옮긴다. 하나라도 틀리면 아무것도 저장하지 않는다.
//   미리보기의 `willMove`: 지우는 등급마다 `{ fromGradeKey, fromLabel, toGradeKey, toLabel, count }`(회원이 0명이어도 한 줄).
//   미리보기 인원(소급으로 지금 승급될 회원 수): 이미 있는 등급은 지금 저장된 값보다 **낮아진 정도**에 비례해 그럴듯한 숫자를 만든다(일수 1일당 ≈4명, 글·댓글 1개당 ≈3명, 서열이 높을수록 가중치가 작다, 최대 900명).
//   그중 20% 는 **수동으로 올린 회원**으로 보고(시드에 수동 상향 회원이 있다고 가정), 새 옵션이 `STOP_AUTO` 면 그 몫을 뺀다. 옵션을 `STOP_AUTO` → `CONTINUE_AUTO` 로 바꾸면 조건을 낮추지 않아도
//   이미 조건을 채운 수동 상향 회원(시드: 등급마다 1~3명)이 더해진다. **새 등급**은 바로 아래 등급 회원 수의 12% 가 그 등급이 되는 것으로 센다. 아무 일도 없으면 0.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import { mockCurrentAdmin } from '~/composables/auth/api/mock/admin-auth-mock';
import type { Grade, GradeKey, GradeManualPolicy, GradeMoveItem, GradePreviewItem, GradePreviewRequest, GradePreviewResponse, GradeRequest, GradesResponse, GradesSaveRequest } from '~/composables/grade/api/useGradeApi.types';
import UtilGrade from '~/utils/util-grade';

/** community V33 의 CHECK 와 같은 범위 */
const MAX_DAYS = 3650;
const MAX_ACTIVITY = 100_000;

const POLICIES: readonly GradeManualPolicy[] = ['CONTINUE_AUTO', 'STOP_AUTO'];

/** 저장된 값 — 모듈에 둔다(역할·회원 목업과 화면이 같은 값을 읽는다) */
let grades: Grade[] = [
    { gradeKey: 'SEEDLING', level: 1, label: '새싹', minDays: 0, minActivity: 0, badgeColor: 'gray', badgeIcon: 'pi pi-circle', memberCount: 4120 },
    { gradeKey: 'REGULAR', level: 2, label: '일반', minDays: 7, minActivity: 10, badgeColor: 'blue', badgeIcon: 'pi pi-star', memberCount: 12830 },
    { gradeKey: 'LOYAL', level: 3, label: '단골', minDays: 30, minActivity: 50, badgeColor: 'green', badgeIcon: 'pi pi-heart', memberCount: 2310 },
    { gradeKey: 'VETERAN', level: 4, label: '베테랑', minDays: 90, minActivity: 200, badgeColor: 'amber', badgeIcon: 'pi pi-crown', memberCount: 540 },
];
let manualPolicy: GradeManualPolicy = 'CONTINUE_AUTO';
/** 지운 등급 → 회원이 옮겨 간 등급(지운 뒤 회원 목업이 그 등급의 회원을 어느 등급으로 볼지) */
const moved = new Map<GradeKey, GradeKey>();
/** 서버가 발급하는 새 등급 키의 다음 번호 */
let nextSerial = 5;

/** 미리보기 인원을 만드는 흉내 — 낮아진 정도에 비례 */
const PER_DAY = 4;
const PER_ACTIVITY = 3;
const MAX_PER_GRADE = 900;
/** 낮아진 몫 중 수동으로 올린 회원의 비율 */
const MANUAL_SHARE = 0.2;
/** 새 등급이 되는 회원 비율(바로 아래 등급 회원 수 대비) */
const NEW_GRADE_SHARE = 0.12;
/** 이미 조건을 채웠지만 옵션이 `STOP_AUTO` 라 멈춰 있는 수동 상향 회원 수(시드) — 옵션을 `CONTINUE_AUTO` 로 풀면 승급된다 */
const MANUAL_STUCK: Record<string, number> = { REGULAR: 3, LOYAL: 2, VETERAN: 1 };

function snapshot(): GradesResponse {
    return { grades: grades.map((grade) => ({ ...grade })), manualPolicy };
}

/** 지금 등급 목록(서열 순서) — 역할·회원 목업이 등급 역할·이름·서열을 만들 때 쓴다. 지연·권한·스위치 없이 바로 준다 */
export function mockGradeSnapshot(): Grade[] {
    return grades.map((grade) => ({ ...grade }));
}

/** 지운 등급의 키를 지금 있는 등급 키로 바꾼다 — 회원 목업이 지운 등급의 회원을 그 회원이 옮겨 간 등급으로 보려고 쓴다. 지우지 않은 키는 그대로 */
export function mockGradeResolve(key: GradeKey): GradeKey {
    let current = key;
    for (let guard = 0; guard < 20 && moved.has(current); guard++) current = moved.get(current)!;
    return current;
}

function denied(): ApiError {
    return new ApiError({ status: 403, code: 'PERMISSION_DENIED', message: '이 작업을 할 권한이 없어요.' });
}

function can(code: string): boolean {
    const me = mockCurrentAdmin();
    return me !== null && (me.isSuper || me.permissions.includes(code));
}

function invalid(fields: Record<string, string>): ApiError {
    return new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 다시 확인해 주세요.', fields });
}

/** 저장·미리보기가 같이 쓰는 서버 검사 — 틀리면 던진다 */
function validate(body: GradesSaveRequest): void {
    const rows = body.grades;
    if (rows.length < UtilGrade.MIN_GRADES || rows.length > UtilGrade.MAX_GRADES) {
        throw new ApiError({ status: 409, code: 'GRADE_LIMIT', message: `등급은 ${UtilGrade.MIN_GRADES}~${UtilGrade.MAX_GRADES}단계까지 둘 수 있어요.` });
    }

    // 첫 등급(기본 등급)은 지울 수 없다
    if (!rows.some((row) => row.gradeKey === grades[0]!.gradeKey)) {
        throw new ApiError({ status: 409, code: 'GRADE_FIRST_PROTECTED', message: '첫 등급(기본 등급)은 지울 수 없어요.' });
    }

    const fields: Record<string, string> = {};
    const keys = new Set<string>();
    rows.forEach((row, i) => {
        const name = row.label?.trim() ?? '';
        if (!name) fields[`grades[${i}].label`] = '등급 이름을 입력해 주세요.';
        else if ([...name].length > UtilGrade.LABEL_MAX) fields[`grades[${i}].label`] = `등급 이름은 ${UtilGrade.LABEL_MAX}자까지예요.`;
        if (row.gradeKey !== undefined) {
            if (!grades.some((g) => g.gradeKey === row.gradeKey) || keys.has(row.gradeKey)) fields[`grades[${i}].gradeKey`] = '알 수 없거나 겹치는 등급이에요.';
            keys.add(row.gradeKey);
        }
        if (i === 0) {
            if (row.gradeKey !== grades[0]!.gradeKey) fields['grades[0].gradeKey'] = '기본 등급은 맨 앞에 그대로 있어야 해요.';
        } else {
            if (!Number.isInteger(row.minDays) || row.minDays! < 0 || row.minDays! > MAX_DAYS) fields[`grades[${i}].minDays`] = `0~${MAX_DAYS.toLocaleString('en-US')} 사이의 정수로 입력해 주세요.`;
            if (!Number.isInteger(row.minActivity) || row.minActivity! < 0 || row.minActivity! > MAX_ACTIVITY) fields[`grades[${i}].minActivity`] = `0~${MAX_ACTIVITY.toLocaleString('en-US')} 사이의 정수로 입력해 주세요.`;
        }
        if (!UtilGrade.BADGE_COLORS.some((c) => c.name === row.badgeColor)) fields[`grades[${i}].badgeColor`] = '고를 수 없는 색이에요.';
        if (row.badgeIcon !== null && !UtilGrade.BADGE_ICONS.includes(row.badgeIcon)) fields[`grades[${i}].badgeIcon`] = '고를 수 없는 아이콘이에요.';
    });
    if (!POLICIES.includes(body.manualPolicy)) fields.manualPolicy = '계속 자동 승급 또는 자동 승급 중지 중 하나를 골라 주세요.';
    if (Object.keys(fields).length) throw invalid(fields);

    const names = new Set<string>();
    for (const row of rows) {
        const name = row.label.trim().toLowerCase();
        if (names.has(name)) throw new ApiError({ status: 409, code: 'GRADE_NAME_DUPLICATED', message: `이미 있는 등급 이름이에요: ${row.label.trim()}` });
        names.add(name);
    }

    for (let i = 2; i < rows.length; i++) {
        if (rows[i]!.minDays! < rows[i - 1]!.minDays! || rows[i]!.minActivity! < rows[i - 1]!.minActivity!) {
            throw new ApiError({ status: 409, code: 'GRADE_RULES_NOT_MONOTONIC', message: '위 등급일수록 가입 후 일수와 글·댓글 합계가 같거나 커야 해요.' });
        }
    }
}

/**
 * 지우는 등급(요청에서 빠진 기존 등급)과 그 회원이 갈 곳 — 저장된 서열에서 남아 있는 가장 가까운 아래 등급. 그 등급이 이번에 이름이 바뀌었으면 바뀐 이름을 보인다.
 * 첫 등급은 지울 수 없어(검사가 먼저 막는다) 아래 등급이 늘 있다.
 */
function moveTargets(body: GradesSaveRequest): GradeMoveItem[] {
    const kept = new Map(body.grades.filter((row) => row.gradeKey !== undefined).map((row) => [row.gradeKey!, row]));
    const out: GradeMoveItem[] = [];
    for (const grade of grades) {
        if (kept.has(grade.gradeKey)) continue;
        let to: Grade | undefined;
        for (let i = grade.level - 2; i >= 0 && !to; i--) if (kept.has(grades[i]!.gradeKey)) to = grades[i];
        to ??= grades[0]!;
        out.push({ fromGradeKey: grade.gradeKey, fromLabel: grade.label, toGradeKey: to.gradeKey, toLabel: kept.get(to.gradeKey)?.label.trim() ?? to.label, count: grade.memberCount });
    }
    return out;
}

/** `GET /api/admin/grades` */
export async function mockGradesLoad(): Promise<GradesResponse> {
    await mockDelay();
    if (mockFlags().has('grade-error')) throw MOCK_ERRORS.internal();
    if (!can('grade.read') && !can('member.read') && !can('role.read')) throw denied();
    return snapshot();
}

/** `PUT /api/admin/grades` — 전체 저장(순서·추가·삭제·수정) */
export async function mockGradesSave(body: GradesSaveRequest): Promise<GradesResponse> {
    await mockDelay();
    const flags = mockFlags();
    if (!can('grade.manage')) throw denied();
    if (flags.has('grade-save-error')) throw MOCK_ERRORS.internal();

    if (flags.has('grade-invalid')) {
        const found = body.grades.findIndex((row) => row.gradeKey === 'LOYAL');
        const at = found < 0 ? Math.min(2, body.grades.length - 1) : found;
        const message = '서버가 받아들이지 못하는 값이에요. 다시 확인해 주세요.';
        throw invalid({ [`grades[${at}].label`]: message, [`grades[${at}].minDays`]: message, [`grades[${at}].minActivity`]: message, [`grades[${at}].badgeColor`]: message, manualPolicy: message });
    }

    validate(body);

    // 요청에 빠진 기존 등급은 지우는 것이다 — 그 등급의 회원은 바로 아래 등급으로 옮긴다(한 트랜잭션, 이동 이력은 사유 `등급 삭제` — 가정)
    const moves = moveTargets(body);
    const arrived = new Map<GradeKey, number>();
    for (const move of moves) {
        arrived.set(move.toGradeKey!, (arrived.get(move.toGradeKey!) ?? 0) + move.count);
        moved.set(move.fromGradeKey, move.toGradeKey!);
    }

    grades = body.grades.map((row: GradeRequest, i): Grade => {
        const before = row.gradeKey !== undefined ? grades.find((g) => g.gradeKey === row.gradeKey) : undefined;
        return {
            gradeKey: before?.gradeKey ?? `GRADE_${nextSerial++}`,
            level: i + 1,
            label: row.label.trim(),
            minDays: i === 0 ? 0 : row.minDays!,
            minActivity: i === 0 ? 0 : row.minActivity!,
            badgeColor: row.badgeColor,
            badgeIcon: row.badgeIcon,
            memberCount: (before?.memberCount ?? 0) + (before ? (arrived.get(before.gradeKey) ?? 0) : 0),
        };
    });
    manualPolicy = body.manualPolicy;
    return snapshot();
}

/** `POST /api/admin/grades/preview` — 저장하지 않고 소급 승급 인원과 삭제로 이동할 회원 수를 센다 */
export async function mockGradesPreview(body: GradePreviewRequest): Promise<GradePreviewResponse> {
    await mockDelay();
    if (!can('grade.manage')) throw denied();
    if (mockFlags().has('grade-preview-error')) throw MOCK_ERRORS.internal();

    validate(body);
    const items: GradePreviewItem[] = [];
    body.grades.forEach((row, i) => {
        if (i === 0) return;
        const now: Grade | undefined = row.gradeKey !== undefined ? grades.find((g) => g.gradeKey === row.gradeKey) : undefined;
        let count: number;
        if (now) {
            const weight = Math.max(0.3, 1.6 - 0.4 * (now.level - 2));
            const drop = Math.max(0, now.minDays - row.minDays!) * PER_DAY + Math.max(0, now.minActivity - row.minActivity!) * PER_ACTIVITY;
            const base = Math.min(MAX_PER_GRADE, Math.round(drop * weight));
            const manual = Math.round(base * MANUAL_SHARE);
            // 새 옵션이 STOP_AUTO 면 수동으로 올린 회원의 몫은 승급하지 않는다
            count = body.manualPolicy === 'STOP_AUTO' ? base - manual : base;
            // STOP_AUTO → CONTINUE_AUTO: 멈춰 있던 수동 상향 회원 중 이미 조건을 채운 회원이 새로 승급된다
            if (manualPolicy === 'STOP_AUTO' && body.manualPolicy === 'CONTINUE_AUTO') count += MANUAL_STUCK[now.gradeKey] ?? 1;
        } else {
            // 새 등급 — 바로 아래 등급 회원 중 일부가 이미 조건을 채웠다고 본다
            const below = body.grades[i - 1]!.gradeKey;
            const belowCount = below !== undefined ? (grades.find((g) => g.gradeKey === below)?.memberCount ?? 0) : 0;
            count = Math.round(belowCount * NEW_GRADE_SHARE);
        }
        items.push({ gradeKey: (row.gradeKey ?? null) as GradeKey | null, label: row.label.trim(), count });
    });
    return { items, total: items.reduce((sum, item) => sum + item.count, 0), willMove: moveTargets(body) };
}
