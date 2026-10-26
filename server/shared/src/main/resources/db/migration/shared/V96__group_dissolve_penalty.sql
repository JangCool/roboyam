-- 소모임 해산 규칙 변경(docs/features/group-contract.md 「해산 때 글·패널티」 — 2026-10-26 사용자 결정).
--
-- · 해산 패널티: 모임장이 스스로 해산(POST /api/groups/{id}/dissolve)하면 그 모임장은 group_settings.dissolve_penalty_days 동안 새 소모임을 개설 신청할 수 없다.
--   개설(opened_at)부터 해산까지 1년(달력, KST) 이상 유지된 소모임은 패널티가 없다(앱이 판정). 관리자 비활성화·운영자 처리로 없어진 소모임은 패널티가 아니다.
-- · 기록은 해산한 소모임 행에 둔다 — groups.dissolve_penalty_until = 해산 시각 + 패널티 일수(대상이 아니면 NULL). 해산한 사람 = 그 행의 leader_id(해산 뒤 바뀌지 않는다).
--   판정: 「leader_id = 나 AND dissolve_penalty_until > now」 인 행이 있으면 패널티 중(가장 늦은 끝 시각까지).
-- · 설정 칸: group_settings.dissolve_penalty_days(개설 규칙 구획, 0~365, NULL = 서버 설정 파일 값 — 기본 30, 0 = 끔). 이미 해산된 소모임의 끝 시각은 설정을 바꿔도 그대로다.
-- · 해산하면 소모임 글을 공개 여부와 상관없이 모두 지운다(앱 GroupPostQueries#deleteOnDissolve). 이 규칙 전에 해산돼 공개로 남아 있던 글도 같은 뜻으로 지운다
--   (소모임 기능은 아직 배포 전이라 개발 DB 의 남은 행만 해당한다). 지운 글은 보유기간까지 원문이 남는다(일반 글 삭제와 같다 — 투표·공개 요청 행은 건드리지 않는다).

-- ── 설정 칸 ─────────────────────────────────────────────────────────────────
ALTER TABLE group_settings ADD COLUMN dissolve_penalty_days smallint;
ALTER TABLE group_settings ADD CONSTRAINT ck_group_settings_dissolve_penalty
    CHECK (dissolve_penalty_days IS NULL OR dissolve_penalty_days BETWEEN 0 AND 365);

-- ── 패널티 기록 ─────────────────────────────────────────────────────────────
ALTER TABLE groups ADD COLUMN dissolve_penalty_until timestamptz;
CREATE INDEX ix_groups_dissolve_penalty ON groups (leader_id, dissolve_penalty_until DESC) WHERE dissolve_penalty_until IS NOT NULL;

-- ── 이전 규칙으로 남아 있던 해산 소모임의 글 ───────────────────────────────────
UPDATE posts SET deleted_at = now(), pinned_at = NULL, updated_at = now()
WHERE deleted_at IS NULL AND group_id IN (SELECT id FROM groups WHERE status = 'DELETED');
