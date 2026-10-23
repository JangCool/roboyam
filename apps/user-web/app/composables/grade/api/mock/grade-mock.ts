// MOCK: 등급 안내(`GET /api/grade-rules`)·내 진행도(`GET /api/me/grade`)의 목업 — `useGradeApi.ts` 가 부른다. 서버가 붙으면 `grade/api/mock/` 와 함께 지운다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   grade-rules-fail   조건표 읽기 실패(500)          grade-fail          내 진행도 읽기 실패(500)
//   grade-paused-sanction  정지 중이라 승급 보류       grade-paused-admin  운영자가 등급을 고정
//   grade-highest      가장 높은 등급(다음 등급 없음)
//
// ── 목업 규칙(가정 — 계약 「목업 가정」) ─
//   · 조건표는 기본 시드 등급(V9: 새싹 0·0 / 일반 7일·10개 / 단골 30일·50개 / 베테랑 90일·200개)이다 — 운영자가 바꾼 등급 설정은 반영되지 않는다.
//   · 내 등급은 로그인 정보의 등급(`/api/auth/me` 의 `grade`·`gradeLabel`). 조건표에 없는 등급이면 첫 등급으로 본다.
//   · 가입 일수 128일(프로필 목업의 가입일과 같다)·활동 수 31개는 가짜 값이다. 정지 중(로그인 정보 `restriction`)이면 `PAUSED_SANCTION`.

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { AuthUser, Restriction } from '~/composables/auth/api/useAuthApi.types';
import type { GradeRule, GradeRules, MyGrade } from '~/composables/grade/api/useGradeApi.types';

const RULES: GradeRule[] = [
    { code: 'SEEDLING', label: '새싹', minDays: 0, minActivity: 0 },
    { code: 'REGULAR', label: '일반', minDays: 7, minActivity: 10 },
    { code: 'LOYAL', label: '단골', minDays: 30, minActivity: 50 },
    { code: 'VETERAN', label: '베테랑', minDays: 90, minActivity: 200 },
];
const DAYS = 128;
const ACTIVITY = 31;

export async function mockGradeRules(): Promise<GradeRules> {
    await mockDelay();
    if (mockFlags().has('grade-rules-fail')) throw MOCK_ERRORS.internal();
    return mockClone({ rules: RULES });
}

export async function mockMyGrade(user: AuthUser | null, restriction: Restriction | null): Promise<MyGrade> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('grade-fail')) throw MOCK_ERRORS.internal();
    let at = Math.max(0, RULES.findIndex((r) => r.code === user?.grade));
    if (flags.has('grade-highest')) at = RULES.length - 1;
    const mine = RULES[at]!;
    const next = RULES[at + 1] ?? null;
    const restricted = restriction?.status === 'SUSPENDED' || restriction?.status === 'BANNED';
    const autoPromotion = flags.has('grade-paused-sanction') || restricted ? 'PAUSED_SANCTION' : flags.has('grade-paused-admin') ? 'PAUSED_ADMIN' : 'ACTIVE';
    return mockClone({
        grade: { code: mine.code, label: user?.gradeLabel || mine.label },
        autoPromotion,
        next: next
            ? {
                  code: next.code,
                  label: next.label,
                  requirements: [
                      ...(next.minDays > 0 ? [{ kind: 'DAYS', current: DAYS, required: next.minDays, met: DAYS >= next.minDays }] : []),
                      ...(next.minActivity > 0 ? [{ kind: 'ACTIVITY', current: ACTIVITY, required: next.minActivity, met: ACTIVITY >= next.minActivity }] : []),
                  ],
              }
            : null,
    });
}
