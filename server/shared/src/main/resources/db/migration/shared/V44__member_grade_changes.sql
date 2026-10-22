-- 회원 등급 변경 이력 — docs/features/roles-contract.md 「회원 상세 활동·등급 이력(서버) 구현 결과」
--
-- 회원의 등급이 바뀔 때마다 한 줄을 남긴다(관리자 회원 상세의 「등급 변경 이력」). 기록하는 곳:
--   · 자동 승급(kind=AUTO): Java GradePromotionService(promoteAll·promoteOnEvent) 와 DB 배치 batch_grade_promotion(R__batch_15) — 승급과 같은 문장·같은 트랜잭션에서.
--   · 등급 삭제로 인한 일괄 이동(kind=ADMIN, reason_code=GRADE_REMOVED, 저장한 운영자): GradeService.save.
--   · 운영자의 수동 지정/자동 승급 재개(kind=ADMIN/RESUME): 수동 변경 기능(grade.override)이 생길 때 같은 표에 쓴다(아직 그 기능이 없다).
-- 이미 있던 과거의 승급은 이력이 없다(만들어 내지 않는다).
--
-- · id: 이 표만 DB 가 번호를 매긴다(IDENTITY). DB 배치(SQL)가 한 문장으로 수천 줄을 넣어 앱의 Snowflake 발급기를 쓸 수 없고, 이력은 한 회원 안에서 정렬·표시용 번호일 뿐이다.
-- · 등급 키는 외래키가 아니라 값으로 보존한다: 등급이 지워져도 이력이 지워지거나 막히지 않는다. 등급 키는 다시 쓰지 않으므로(grade_settings.next_serial, 시드 키 고정) 옛 키가 다른 등급을
--   가리킬 일이 없다. 화면은 목록에 없는 키를 중립 뱃지 + 키 글자로 그린다.
-- · actor_operator_id: 처리한 운영자(자동 변경이면 NULL). 외래키를 두지 않는다 — 운영자가 지워져도 「누가 했는지는 모르지만 사람이 했다」는 사실이 남아 자동(NULL)과 구분된다.
--   조회가 운영자 표와 맞춰 보고 없으면 「삭제된 운영자」로 보인다.
-- · reason: 운영자가 입력한 사유(수동 변경). reason_code: 시스템이 정한 사유 코드(예: GRADE_REMOVED) — 조회가 요청 언어로 풀어 보인다. 둘 다 이메일 같은 개인정보를 담지 않는다.
-- · 회원 행이 지워지면 함께 지워진다(회원 탈퇴는 users 행을 남기므로 이력도 남는다).
-- · 크기: 자동 승급은 올리기만 하므로 회원당 (등급 수 - 1)줄 이하 + 운영자의 수동 변경 몇 줄이다. 조회는 최근 50줄만 읽는다.
CREATE TABLE member_grade_changes (
    id                bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    from_grade_key    varchar(19),                                   -- 이전 등급 키. 처음 부여면 NULL
    to_grade_key      varchar(19)  NOT NULL,
    kind              varchar(8)   NOT NULL,                         -- AUTO 자동 승급 · ADMIN 운영자(수동 변경·등급 설정 변경) · RESUME 자동 승급 재개
    reason            varchar(200),
    reason_code       varchar(30),
    actor_operator_id bigint,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_member_grade_changes_kind CHECK (kind IN ('AUTO', 'ADMIN', 'RESUME')),
    CONSTRAINT ck_member_grade_changes_auto_actor CHECK (kind <> 'AUTO' OR actor_operator_id IS NULL)
);
CREATE INDEX ix_member_grade_changes_user ON member_grade_changes (user_id, created_at DESC, id DESC);
