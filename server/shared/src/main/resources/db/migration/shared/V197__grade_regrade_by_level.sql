-- 등급을 레벨에 맞춰 한 번 다시 매기기 — docs/features/grade-level-contract.md 2절 「기존 회원 다시 매기기」(사용자 결정 2026-11-12)
--
-- V196 은 등급 목록을 11단계로 바꾸면서 회원의 등급 키를 그대로 두고 「올리기만」 소급 승급을 했다. 그래서 옛 4단계에서 높았던 회원(예: 단골 → 이름만 바뀐 고수, 최소 Lv.50)이
-- 원장에서 다시 센 레벨(예: Lv.5)과 맞지 않게 높은 등급에 남았다. 이 파일은 **이번 한 번만** 올리기와 내리기를 모두 해서 회원 등급을 새 기준(최소 레벨 AND 가입 후 일수)에 맞춘다.
-- 이 뒤로는 지금 규칙 그대로 올리기만 한다(자동 강등 없음).
--
-- 규칙:
--   · 대상: 탈퇴 처리되지 않은 회원(deleted_at IS NULL) 전부 — 정지 회원도, 운영자가 수동으로 정한 등급(profiles.grade_manual)도 포함한다(사용자 결정 2026-11-12 —
--     데모·설치 데이터가 등급을 수동 표시로 넣어 두어 빼면 대부분 그대로 남는다). 다시 매긴 회원은 수동 표시를 끈다(이제 레벨이 등급을 정한다 — 이후 운영자가 다시 수동 지정할 수 있다).
--   · 새 등급 = 최소 레벨(min_level) ≤ 회원 레벨 AND 가입 후 일수 ≥ min_days AND 활동 조건(min_activity)이 0 인 등급 중 서열이 가장 높은 것. 맞는 등급이 없으면 첫 등급(서열 1 — 조건 0).
--   · 지금 등급과 다를 때만 바꾸고, 바꾼 회원마다 등급 변경 이력 한 줄(kind=AUTO · 처리자 없음 · reason_code=LEVEL_REGRADE — 관리자 회원 상세가 요청 언어로 풀어 보인다).
--   · 등급 역할은 등급 키로 파생되므로 따로 고칠 것이 없다.
WITH target AS (
    SELECT p.user_id, p.grade_key AS from_key,
           coalesce((SELECT g.grade_key
                       FROM grades g
                      WHERE g.min_level <= p.level
                        AND g.min_activity = 0
                        AND u.created_at <= now() - make_interval(days => g.min_days)
                      ORDER BY g.level DESC
                      LIMIT 1),
                    (SELECT g.grade_key FROM grades g ORDER BY g.level LIMIT 1)) AS to_key
      FROM profiles p
      JOIN users u ON u.id = p.user_id
     WHERE u.deleted_at IS NULL
), moved AS (
    UPDATE profiles p
       SET grade_key = t.to_key, grade_manual = false, updated_at = now()
      FROM target t
     WHERE p.user_id = t.user_id
       AND p.grade_key = t.from_key
       AND t.to_key <> t.from_key
 RETURNING p.user_id, t.from_key, t.to_key
)
INSERT INTO member_grade_changes (user_id, from_grade_key, to_grade_key, kind, reason_code, created_at)
SELECT user_id, from_key, to_key, 'AUTO', 'LEVEL_REGRADE', now() FROM moved;
