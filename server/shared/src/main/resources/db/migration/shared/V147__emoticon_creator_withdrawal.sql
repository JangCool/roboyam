-- 로보티콘 — 탈퇴 작가의 팩은 새 판매만 멈춘다(사용자 결정 2026-11-05). docs/features/emoticon-contract.md 「후속 결정 반영(2026-11-05)」 6.
--
-- 무엇이 바뀌나
--   ① 판매 중단의 종류(emoticon_packs.suspend_kind): OPERATOR(운영자 판단 — 「다시 판매」로 되살린다) · CREATOR_WITHDRAWN(작가 탈퇴 — 시스템이 멈추고 되살리지 못한다).
--      판매 중단(SUSPENDED)이면 늘 있고, 아니면 없다. 작가 탈퇴로 멈춘 팩은 작가 팩만 · 멈춘 운영자가 없다(시스템 처리).
--   ② 심사 건을 시스템이 닫은 까닭(emoticon_reviews.system_cause): CREATOR_WITHDRAWN = 탈퇴 작가의 대기 건을 탈퇴 때 반려로 닫았다(처리한 운영자 없음). null = 운영자가 처리.
--   ③ 지키기 트리거: 작가 탈퇴로 멈춘 팩은 상태·종류·상점의 판을 바꾸지 못한다(다시 판매 · 새 판 승인 · 운영자 판매 중단 덮어쓰기 금지) ·
--      탈퇴한 작가의 팩은 새로 판매 중이 되지 못한다(심사 승인의 마지막 그물). 서비스가 먼저 409 EMOTICON_PACK_CREATOR_WITHDRAWN 으로 답한다 — 이 트리거는 마지막 그물이다.
--      가격 칸은 막지 않는다(멈춘 팩은 팔리지 않아 가격이 판매를 되살리지 않는다 — 서비스는 가격 바꾸기도 409 로 막는다).
--   ④ 이미 탈퇴한 작가(운영 전 — 개발 데이터)의 판매 중·판매 중단 팩과 대기 심사 건을 같은 규칙으로 정리한다.
-- 탈퇴 처리(WithdrawalCompletionService → EmoticonCreatorWithdrawal)가 같은 트랜잭션에서 ①·②를 쓴다.

-- ── ① 판매 중단의 종류 ──────────────────────────────────────────────────────
ALTER TABLE emoticon_packs ADD COLUMN suspend_kind varchar(17);
UPDATE emoticon_packs SET suspend_kind = 'OPERATOR' WHERE status = 'SUSPENDED';
ALTER TABLE emoticon_packs ADD CONSTRAINT ck_emoticon_packs_suspend_kind CHECK ((status = 'SUSPENDED') = (suspend_kind IS NOT NULL)
    AND (suspend_kind IS NULL OR suspend_kind IN ('OPERATOR', 'CREATOR_WITHDRAWN'))
    AND (suspend_kind IS DISTINCT FROM 'CREATOR_WITHDRAWN' OR (source = 'CREATOR' AND suspended_by IS NULL)));

-- ── ② 심사 건을 시스템이 닫은 까닭 ──────────────────────────────────────────
ALTER TABLE emoticon_reviews ADD COLUMN system_cause varchar(17);
ALTER TABLE emoticon_reviews ADD CONSTRAINT ck_emoticon_reviews_system_cause CHECK (system_cause IS NULL
    OR (system_cause = 'CREATOR_WITHDRAWN' AND status = 'REJECTED' AND decided_by IS NULL));

-- ── ③ 지키기 ────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION emoticon_packs_creator_withdrawn_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.suspend_kind = 'CREATOR_WITHDRAWN'
            AND (NEW.status, NEW.suspend_kind, NEW.live_revision_id) IS DISTINCT FROM (OLD.status, OLD.suspend_kind, OLD.live_revision_id) THEN
        RAISE EXCEPTION 'emoticon pack % was stopped by creator withdrawal and cannot be put back on sale', OLD.id;
    END IF;
    IF NEW.status = 'APPROVED' AND OLD.status IS DISTINCT FROM 'APPROVED' AND NEW.creator_id IS NOT NULL
            AND EXISTS (SELECT 1 FROM users u WHERE u.id = NEW.creator_id AND u.status = 'WITHDRAWN') THEN
        RAISE EXCEPTION 'emoticon pack % of a withdrawn creator cannot go on sale', NEW.id;
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER tr_emoticon_packs_creator_withdrawn BEFORE UPDATE OF status, suspend_kind, live_revision_id ON emoticon_packs
    FOR EACH ROW EXECUTE FUNCTION emoticon_packs_creator_withdrawn_guard();

-- ── ④ 이미 탈퇴한 작가(개발 데이터) ─────────────────────────────────────────
-- 대기 심사 건 먼저(처음 신청이면 팩도 반려) — 서비스와 같은 차례(심사 건 → 팩)
WITH closed AS (
    UPDATE emoticon_reviews r
       SET status = 'REJECTED', decided_at = now(), decided_by = NULL, reason = '작가 탈퇴로 심사를 닫았어요(시스템 처리).', system_cause = 'CREATOR_WITHDRAWN'
      FROM users u
     WHERE u.id = r.creator_id AND u.status = 'WITHDRAWN' AND r.status = 'PENDING'
    RETURNING r.pack_id, r.kind
)
UPDATE emoticon_packs p SET status = 'REJECTED', updated_at = now()
  FROM closed c
 WHERE p.id = c.pack_id AND c.kind = 'FIRST' AND p.status = 'PENDING';

UPDATE emoticon_packs p
   SET status = 'SUSPENDED', suspend_kind = 'CREATOR_WITHDRAWN', suspend_reason = '작가 탈퇴로 새 판매를 멈췄어요(시스템 처리).', suspended_at = now(),
       suspended_by = NULL, updated_at = now()
  FROM users u
 WHERE u.id = p.creator_id AND u.status = 'WITHDRAWN' AND p.status IN ('APPROVED', 'SUSPENDED');
