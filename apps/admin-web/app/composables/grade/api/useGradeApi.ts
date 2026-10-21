import { mockGradesLoad, mockGradesPreview, mockGradesSave } from '~/composables/grade/api/mock/grade-mock';
import type { GradePreviewRequest, GradePreviewResponse, GradesResponse, GradesSaveRequest } from '~/composables/grade/api/useGradeApi.types';

/**
 * 등급 API — 서버 주소 문자열(`/api/admin/grades…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 등급 설정 화면, 역할 관리(등급 역할의 승급 조건 요약), 회원 조회(필터 옵션·등급 뱃지)가 **같은 조회**를 쓴다(화면들이 어긋나지 않게 — `useGradeAppearance` 가 한 번 읽어 공유한다).
 *
 * 후보 계약(확정 전): 오류는 모두 `ApiError`(useApiClient)로 던져진다. 필요한 권한은 `util-permission-catalog.ts` 의 코드.
 *   - `GET  /api/admin/grades` → 200 `{ grades: [{ gradeKey, level, label, minDays, minActivity, badgeColor, badgeIcon, memberCount }], manualPolicy }`  (`grade.read`. 서열 순서)
 *   - `PUT  /api/admin/grades` `{ grades: [{ gradeKey?, label, minDays?, minActivity?, badgeColor, badgeIcon }], manualPolicy }` → 200 `GradesResponse`  (`grade.manage`)
 *       **전체 저장**: 목록 순서가 곧 서열이고, 순서·추가(`gradeKey` 없음 — 서버가 발급)·삭제(요청에 빠진 기존 등급 — 그 등급의 회원은 바로 아래 등급으로 이동)·수정을 한 번에 보내면 서버가 diff 한다. 하나라도 틀리면 아무것도 저장하지 않는다.
 *       (개별 `POST/PATCH/DELETE` 대신 전체 저장을 제안한다 — 저장 줄(한 번에 저장·이탈 가드)과 맞고, "위 등급일수록 같거나 커야 한다"는 규칙이 여러 줄에 걸쳐 있어 하나씩 저장하면 중간 상태가 규칙을 어길 수 있다)
 *   - `POST /api/admin/grades/preview` 같은 요청 → 200 `{ items: [{ gradeKey|null, label, count }], total, willMove: [{ fromGradeKey, fromLabel, toGradeKey|null, toLabel, count }] }`
 *       (`grade.manage`. 저장하지 않고, 이 기준으로 저장하면 소급으로 승급될 회원 수(`items`·`total` — 새 등급이면 그 등급이 될 회원 수)와 지우는 등급에서 아래 등급으로 이동할 회원 수(`willMove`)를 센다. 저장 전 확인 대화상자가 쓴다)
 * 오류 코드 후보: `VALIDATION` 400(`fields` 키는 `grades[i].label` · `grades[i].minDays` · `grades[i].minActivity` · `grades[i].badgeColor` · `grades[i].badgeIcon` · `grades[i].gradeKey` · `manualPolicy`, i 는 요청 배열의 순서) ·
 *   `GRADE_RULES_NOT_MONOTONIC` 409(위 등급일수록 같거나 커야 하는데 거꾸로 — 삭제·이동·추가 뒤 **남은 목록** 기준으로 판정) · `GRADE_NAME_DUPLICATED` 409(이름 중복) ·
 *   `GRADE_LIMIT` 409(등급 수가 최소 2·최대 10단계 밖) · `GRADE_FIRST_PROTECTED` 409(첫 등급(기본 등급)을 지우려 함) · `PERMISSION_DENIED` 403.
 *
 * 정책(사용자 확정 2026-10-21 — 아래 "구현 가정"은 확정되지 않은 해석):
 *   · 등급은 순서 있는 목록이고 등급 설정이 원천이다. 역할 관리의 등급 역할은 이 목록에서 자동으로 생기고(`ROLE_GRADE_<gradeKey>`, 내장 취급 — 이름·승급 조건은 읽기 전용, 권한만 편집) 등급을 지우면 사라진다.
 *   · 등급 수는 **최소 2 · 최대 10단계**(확정)이고, 첫 등급(기본 등급)은 지우거나 옮길 수 없다.
 *   · 등급을 지우면 그 등급의 **회원은 바로 아래 등급으로 이동**한다(확정). 첫 등급은 지울 수 없어 이동할 아래 등급이 늘 있다. 구현 가정: 삭제와 회원 이동은 한 트랜잭션이고,
 *     이동한 회원은 **자동 승급 판정 대상에 계속 포함**되며(이동된 등급 기준으로 다시 올라갈 수 있다), 이동 이력은 등급 변경 이력에 사유 `등급 삭제`로 남긴다(이력 화면은 후속).
 *   · 판정 시점: **사건으로 판정 가능한 조건(글·댓글 수)은 즉시** 승급하고, **시간이 지나야 채워지는 조건(가입 일수)은 배치**로 판정한다. 올리기만 하고 자동으로 내리지 않는다.
 *   · 소급: 기준을 낮추면 **이미 새 조건을 채운 회원도 승급**시킨다. 구현 가정: 활동 조건의 소급은 저장 직후 일괄, 일수 조건의 소급은 다음 배치에서 한다. 새 등급을 추가해도 이미 조건을 채운 회원이 그 등급이 된다(가정).
 *   · 수동 상향 회원(`manualPolicy`): 운영자가 등급을 **수동으로 올린** 회원의 이후 동작을 정하는 옵션이다. 값은 전역 하나지만 **수동으로 올린 회원에게만** 적용되고, 그 밖의 회원은 이 값과 상관없이 조건을 채우면 올라간다.
 *     `CONTINUE_AUTO` 계속 자동 승급(수동으로 올린 회원도 이후 더 높은 등급의 조건을 채우면 자동으로 올린다, 기본값 — 가정) · `STOP_AUTO` 자동 승급 중지(수동으로 올린 회원은 이후 어떤 자동 승급 조치도 하지 않는다).
 *     옵션을 바꾸는 것도 소급 영향이 있다: `CONTINUE_AUTO` 로 바꾸면 이미 조건을 채운 수동 상향 회원이 새로 승급될 수 있다(미리보기가 이를 포함한다).
 *     ⚠ 사용자 문장이 "수동 승급인지 / 이후 상향 조건이 되면 올릴지 / 아무 조치 안 할지" 3지 선택으로도 읽힌다 — 지금은 두 값으로 해석했다.
 *
 * ⚠ 미정(서버 정책): 승급 조건 항목(지금은 가입 후 일수 · 글·댓글 합계), 값의 범위(community V33 의 CHECK 와 같게 일수 0~3650 · 글·댓글 0~100000), 이름 길이(가정 8자)·gradeKey 발급 방식(가정 `GRADE_<순번>`),
 *   일수 조건 배치의 주기·시각, 소급 승급·삭제로 이동한 회원에게 알림·이력을 남길지와 이동 뒤 자동 승급을 다시 판정하는 시점, 옵션을 바꿀 때 이미 수동으로 올린 회원이 소급 승급되는지,
 *   활동 수의 정의(삭제·숨김 제외 등), 저장을 감사 로그에 어떻게 남길지, 등급 목록을 회원 조회·역할 조회 화면이 읽을 때의 권한(지금 목업은 `grade.read` · `member.read` · `role.read` 중 하나면 읽는다),
 *   뱃지 색·아이콘 목록의 범위, 공개 사이트가 같은 설정(이름·뱃지)을 읽는 방법.
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/grade-mock.ts` 를 부른다(스위치는 그 파일 머리말). 서버가 붙으면 `// MOCK:` 줄을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    grades: '/api/admin/grades',
    preview: () => `${PATH.grades}/preview`,
};

export function useGradeApi() {
    // REAL: const client = useApiClient();
    void PATH;

    return {
        /** 등급 목록(서열 순서)과 수동 상향 회원 옵션 */
        load: (): Promise<GradesResponse> =>
            // MOCK:
            mockGradesLoad(),
        // REAL: client.get<GradesResponse>(PATH.grades),

        /** 등급 목록과 옵션을 통째로 저장한다 — 저장된 모양을 돌려준다 */
        save: (body: GradesSaveRequest): Promise<GradesResponse> =>
            // MOCK:
            mockGradesSave(body),
        // REAL: client.put<GradesResponse>(PATH.grades, body),

        /** 이 기준으로 저장하면 지금 일어나는 일 — 소급 승급 인원과 삭제로 이동할 회원 수(저장하지 않는다) */
        preview: (body: GradePreviewRequest): Promise<GradePreviewResponse> =>
            // MOCK:
            mockGradesPreview(body),
        // REAL: client.post<GradePreviewResponse>(PATH.preview(), body),
    };
}
