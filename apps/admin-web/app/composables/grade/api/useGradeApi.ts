import type { GradePreviewRequest, GradePreviewResponse, GradesResponse, GradesSaveRequest } from '~/composables/grade/api/useGradeApi.types';

/**
 * 등급 API — 서버 주소 문자열(`/api/admin/grades…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 등급 설정 화면, 역할 관리(등급 역할의 승급 조건 요약), 회원 조회(필터 옵션·등급 뱃지)가 **같은 조회**를 쓴다(화면들이 어긋나지 않게 — `useGradeAppearance` 가 한 번 읽어 공유한다).
 *
 * 계약(서버 `GradeController`, `docs/features/roles-contract.md` 2단계): 오류는 모두 `ApiError`(useApiClient)로 던져진다. 필요한 권한은 `util-permission-catalog.ts` 의 코드.
 *   - `GET  /api/admin/grades` → 200 `{ grades: [{ gradeKey, level, label, minDays, minActivity, badgeColor, badgeIcon, memberCount }], manualPolicy }`
 *       (`grade.read` · `member.read` · `role.read` 중 하나 — 회원 조회·역할 관리 화면도 같은 목록을 읽는다. 서열 순서)
 *   - `PUT  /api/admin/grades` `{ grades: [{ gradeKey?, label, minDays?, minActivity?, badgeColor, badgeIcon }], manualPolicy }` → 200 `GradesResponse`  (`grade.manage`)
 *       **전체 저장**: 목록 순서가 곧 서열이고, 순서·추가(`gradeKey` 없음 — 서버가 `GRADE_<번호>` 로 발급)·삭제(요청에 빠진 기존 등급 — 그 등급의 회원은 바로 아래 등급으로 이동)·수정을 한 번에 보내면 서버가 diff 한다. 하나라도 틀리면 아무것도 저장하지 않는다.
 *       저장한 직후 서버가 새 기준으로 소급 승급을 한다(활동 조건·새 등급·옵션 변경은 즉시, 가입 일수 조건은 다음 배치). 낙관적 충돌 방지(버전)는 없다 — 나중에 저장한 쪽이 이긴다.
 *       새 등급은 저장 전에 `gradeKey` 가 없다 — 저장 응답으로 목록을 다시 채운다.
 *   - `POST /api/admin/grades/preview` 같은 요청 → 200 `{ items: [{ gradeKey|null, label, count }], total, willMove: [{ fromGradeKey, fromLabel, toGradeKey|null, toLabel, count }] }`
 *       (`grade.manage`. 저장하지 않고, 이 기준으로 저장하면 소급으로 승급될 회원 수(`items`·`total` — 새 등급이면 그 등급이 될 회원 수)와 지우는 등급에서 아래 등급으로 이동할 회원 수(`willMove`)를 센다. 저장 전 확인 대화상자가 쓴다)
 * 오류 코드: `VALIDATION` 400(`fields` 키는 `grades[i].label` · `grades[i].minDays` · `grades[i].minActivity` · `grades[i].badgeColor` · `grades[i].badgeIcon` · `grades[i].gradeKey` · `manualPolicy` · `grades`, i 는 요청 배열의 순서) ·
 *   409 인 `GRADE_RULES_NOT_MONOTONIC`(위 등급일수록 같거나 커야 하는데 거꾸로 — 삭제·이동·추가 뒤 **남은 목록** 기준) · `GRADE_NAME_DUPLICATED`(이름 중복) · `GRADE_LIMIT`(등급 수가 최소 2·최대 10단계 밖) · `GRADE_FIRST_PROTECTED`(첫 등급(기본 등급)을 지우려 함)는
 *   **`fields` 없이 `code`+`message` 만** 온다 — 화면은 서버 `message` 를 그대로 저장 줄에 보인다(새 코드가 생겨도 문구 분기를 두지 않는다). `PERMISSION_DENIED` 403.
 *
 * 정책(사용자 확정 2026-10-21):
 *   · 등급은 순서 있는 목록이고 등급 설정이 원천이다. 역할 관리의 등급 역할은 이 목록에서 자동으로 생기고(`ROLE_GRADE_<gradeKey>`, 내장 취급 — 이름·승급 조건은 읽기 전용, 권한만 편집) 등급을 지우면 사라진다.
 *   · 등급 수는 **최소 2 · 최대 10단계**이고, 첫 등급(기본 등급)은 지우거나 옮길 수 없다. 등급을 지우면 그 등급의 **회원은 바로 아래 등급으로 이동**한다.
 *   · 판정 시점: **사건으로 판정 가능한 조건(글·댓글 수)은 즉시** 승급하고, **시간이 지나야 채워지는 조건(가입 일수)은 배치**로 판정한다. 올리기만 하고 자동으로 내리지 않는다.
 *   · 수동 상향 회원(`manualPolicy`): 운영자가 등급을 **수동으로 올린** 회원의 이후 동작 — `CONTINUE_AUTO` 계속 자동 승급(기본값) · `STOP_AUTO` 자동 승급 중지. 수동으로 올린 회원에게만 적용된다.
 *
 * ⚠ 서버가 아직 못 하는 것(2단계 결과): 글·댓글 표가 없어 활동 수는 늘 0 으로 본다(활동 조건이 있는 등급은 그 표가 생기기 전까지 도달할 수 없다). 일수 조건 배치 스케줄러·글/댓글 작성 시 즉시 판정 호출 지점은 아직 없다.
 * ⚠ 미정: 배치 주기·시각, 소급 승급·삭제 이동 회원에게 알림·이력을 남길지, 활동 수의 정의, 뱃지 색·아이콘 목록의 범위, 공개 사이트가 같은 설정(이름·뱃지)을 읽는 방법, 영어 이름(`labelEn`).
 */
const PATH = {
    grades: '/api/admin/grades',
    preview: () => `${PATH.grades}/preview`,
};

export function useGradeApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 등급 목록(서열 순서)과 수동 상향 회원 옵션 */
        load: (): Promise<GradesResponse> => client.get<GradesResponse>(PATH.grades),

        /** 등급 목록과 옵션을 통째로 저장한다 — 저장된 모양을 돌려준다 */
        save: (body: GradesSaveRequest): Promise<GradesResponse> => client.put<GradesResponse>(PATH.grades, body),

        /** 이 기준으로 저장하면 지금 일어나는 일 — 소급 승급 인원과 삭제로 이동할 회원 수(저장하지 않는다) */
        preview: (body: GradePreviewRequest): Promise<GradePreviewResponse> => client.post<GradePreviewResponse>(PATH.preview(), body),
    };
}
