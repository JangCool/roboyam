# 역할 · 운영자 · 등급 계약 (화면 목업 단계 — 사용자 화면 확정 전, 2026-10-21 기준)

**의도**: 운영자(관리자 앱 계정)와 그 역할·권한, 회원 역할(등급·회원 유형·게시판 단위·사용자 정의), 등급 설정을 관리자 앱에서 다룬다. 지금은 **프론트 목업만** 있고 서버 코드·마이그레이션이 없다(`server/` 에는 `V1~V6` 뿐). 화면 확정 전에는 백엔드를 시작하지 않는다.
**정책의 출처**: `docs/domain/operator-permissions.md`(확정 규칙·역할 유형 표·추가 확정). 이 문서는 화면이 정한 모양과 API 후보만 적는다.

## 화면 (admin-web, 목업)
| 경로 | 내용 |
|---|---|
| `/settings/roles/operator` | 운영자 역할. 탭 = **전체** · **종합** · 콘텐츠 · 커뮤니티 안전 · 회원 · 소모임 · 핫딜 · 공동구매 · 사업자·광고 · 시스템(영역). 영역 안은 세부 유형 소제목(24개)으로 묶는다 |
| `/settings/roles/member` | 회원 역할. 탭 = **전체** · 회원 유형 · 게시판 단위 · 사용자 정의. 어느 탭에서든 [역할 추가](사용자 정의 유형으로 생성) |
| `/settings/roles/grade` | 등급 역할(회원 역할의 한 종류). 탭 줄 없음, 등급 설정에서 자동 생성되는 파생물 → 만들기·이름 수정 불가, `/grades` 링크 |
| `/settings/operators` | 운영자 목록(탭: 승인 대기 포함 상태별 + **초대 대기**). 승인·**반려**·초대·상태·역할 부여 |
| `/grades` | 등급 설정. [저장]·[되돌리기]는 페이지 하단 떠 있는 저장 줄(`StickySaveBar`, 가이드 `forms.md` 2-4) |
| `/members` | 회원 조회(읽기 전용) — 회원 역할 표시만 |
- 메뉴(`util-admin-menu.ts`): 설정 > 운영자와 권한 > 운영자 · 역할 관리(하위: 회원 역할 · 운영자 역할 · 등급 역할). `/settings/roles` 와 모르는 종류는 `/settings/roles/operator` 로 간다. **화면 안에는 운영자/회원 전환이 없다** — 메뉴가 종류를 정한다. 탭은 `?tab=`(`all` = 전체, 기본).
- 타입·동작의 기준은 화면 코드: `app/composables/{role,operator,grade,member}/api/*.types.ts`, `app/utils/util-role.ts`, `util-permission-catalog.ts`, `util-grade.ts`, `util-operator.ts`.

## 규칙 (화면에서 정한 것)
- **운영자 역할 유형** = 카탈로그 세부 유형 24개(id `op_<유형id>`), 영역 9개 아래. 역할 하나는 세부 유형 하나에 속하고 후보 권한은 그 유형 것만. **종합 관리** 유형은 유형 제한 없이 고르며 최고 관리자만 만들고 고친다.
- **종합 탭**의 영역별 관리자(`<영역> 관리자`, 권한 = 그 영역 전부)는 목업 시드다(카탈로그에서 계산). 서버 시드로 둘지 미정.
- **회원 역할 유형 4종**: 등급(파생, `ROLE_GRADE_<key>`) · 회원 유형(제품 고정) · 게시판 단위(매니저·스텝, 제품 고정) · 사용자 정의(운영자가 추가). 새로 만들 수 있는 것은 사용자 정의뿐.
- 공통: 권한 합집합, 권한 상승 방지(자기 권한 범위 안에서만 부여), 내장 역할 삭제 불가. 권한: 조회 `role.read`·`operator.read`·`grade.read`, 변경 `*.manage`. 감추는 것은 편의이고 막는 것은 서버다.
- **운영자 계정**: 4개 상태(승인·잠금·만료·사용)와 계정 기간. 루트(최고 관리자)는 어떤 변경도 거절. 등록 두 방식 — 생성/초대(**초대 링크**, 유효 72시간, 같은 이메일의 유효 초대는 409) · 신청 후 승인/반려(반려는 승인 대기 건만, 사유 선택 200자).
- **등급**: 순서 있는 목록 2~10단계, 첫 등급은 삭제·이동 불가, 삭제하면 소속 회원은 바로 아래 등급으로 이동. 자동 승급 조건(가입 일수·글+댓글 수) — 활동 조건은 즉시, 일수 조건은 배치(주기 미확정). 수동 상향 옵션(`CONTINUE_AUTO` 기본 / 자동 승급 중지). 기준을 낮추거나 등급을 추가·삭제하면 저장 전 영향 미리보기 후 확인(소급 승급). 등급 목록+옵션+뱃지를 **PUT 한 번**으로 저장.

## API 후보 (확정 전 — 목업이 부르는 모양)
| | |
|---|---|
| `GET /api/admin/role-types?axis` | 역할 유형(`roleTypeId` `description` `axis` `area` `derived` `creatable` `roleCount`). 운영자 유형 24개를 서버가 줄지 화면 카탈로그로만 둘지 미정 |
| `GET/POST /api/admin/roles` · `PUT/DELETE …/{id}` · `PUT …/{id}/permissions` | 역할 목록·생성 `{roleTypeId, roleId, roleName, description}`·수정·삭제·권한(`{permissionCodes}`) |
| `GET/POST /api/admin/operators` · `PUT …/{id}` · `PUT …/{id}/status` · `POST …/{id}/rejection {reason}` | 운영자 목록(`tab`, `keyword`)·등록·수정·상태·반려(204) |
| `GET/POST /api/admin/operator-invitations` · `DELETE …/{id}` | 초대 목록(`invitationId` `email` `roles[]` `invitedAt` `expiresAt` `expired`)·생성·취소(204) |
| `GET/PUT /api/admin/grades` · `POST …/preview` | 등급 목록+옵션 조회·일괄 저장 · 저장 전 영향 미리보기(승급 인원·이동 회원 수) |
| `GET /api/admin/members` · `…/{id}` · 역할 선택지 | 회원 조회(읽기). 회원에게 사용자 정의 역할을 **수동 부여하는 API·화면은 아직 없다** |
- 오류 코드(목업): 403 `PERMISSION_ESCALATION`, 409 `ROLE_BUILTIN` · `ROLE_TYPE_FIXED` · `INVITATION_DUPLICATED` · `OPERATOR_NOT_PENDING`, 404 `INVITATION_NOT_FOUND`.

## 미확정 사항
1. 사용자 정의 역할을 회원에게 수동 부여하는 화면·API.
2. 반려한 신청의 보관 방식, 반려 사유를 신청자에게 알릴지, 만료된 초대의 보관·재발송, 초대 유효기간 값(72시간은 가정).
3. 등급 일수 조건 배치의 실행 주기·시각, 소급 승급 시 알림·이력, 활동 수 정의(삭제·숨김 제외 여부) — `operator-permissions.md` 미확정 11.
4. 종합 탭 영역별 관리자 역할을 서버 시드로 둘지, 권한 중 화면이 아직 없는 것(`featureReady:false`)까지 줄지.
5. 운영자 로그인 세부(잠금 해제 방식, 비밀번호 재설정), 감사 보관 기간(1년은 가정), 신청 항목·승인자 권한.
6. 서버 구조: `operators` 별도 테이블·역할/권한 3표, `users.role` 의 운영자 값 제거, 관리자 서버 인증 분리(`operator-permissions.md` "이 정책이 바꾸는 현재 코드").
