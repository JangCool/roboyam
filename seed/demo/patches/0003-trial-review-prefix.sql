-- 데모 팩 패치 0003 — 체험단 사용기 글의 「체험단」 말머리 · 체험단 선결제(V244) 맞추기
-- (docs/features/trial-group-contract.md, 2026-11-17)
--
-- 변수: :'demo_now' · :demo_id_base — 이 패치는 새 행 ID 를 만들지 않는다.
-- 원고(seed/demo/)에는 체험단 자리가 없다(체험단·체험단 글은 패치 0002 가 넣는다) — 처음부터 다시 만들 때(seed-demo) 이 패치 내용은 빠진다(docs/seed-demo.md 「패치」).
--
-- 고치는 것
--  1) 체험단 사용기 글(posts.trial_participant_id 가 있는 사용기 게시판 글, 0002 의 3개)의 말머리 = 사용기 게시판(slug review)의 공통 말머리 TRIAL「체험단」.
--     0002 를 돌린 팩(V243)의 설치 프리셋에는 TRIAL 말머리가 없어 말머리 없이 들어갔다. 지금 프리셋(8135430f)에는 있다 — 서버도 체험단 글을 쓸 때 이 말머리를 붙인다
--     (TrialPostLinker). 말머리는 구조(설치 프리셋)라 팩에 담기지 않고, 글은 그 ID 를 가리킨다 — ID 를 적지 않고 게시판 slug·common_key 로 찾는다.
--     글의 updated_at 은 그대로(운영자가 고친 것이 아니라 처음부터 그 말머리였던 것으로).
--  2) 체험단 선결제(V244): 팩의 모집 3개는 payment_timing 칸이 없어 복원 때 기본값 PREPAID 를 받는다 — 0원이라 결제 없이 공개되는 선결제 모집으로 그대로 맞다.
--     다만 모집 중 모집(뽑기 전)은 금액이 비어(amount NULL) 결제 상태가 「견적 요청 전」(AWAITING_QUOTE_REQUEST — V244 전 초안의 상태)으로 보인다.
--     V244 뒤 선결제 모집은 저장 때 모집 인원으로 금액을 굳힌다(TrialPayments.syncPrepaid — 0원 → 0원은 처리 기록 없이 신청 건 줄만) — 같은 모양으로:
--     금액 0 · 신청 건의 상품 줄 수량 = 모집 인원(인원당). 뽑은 모집 둘은 이미 금액 0 이라 그대로.
--  3) 모집 ↔ 광고 신청 건 이력(trial_biz_applications, V244): V244 가 기존 모집을 옮겨 적었지만 팩은 그 뒤에 복원되어 이력 줄이 없다 — 모집마다 지금 결제 건 한 줄(시각 = 모집 만든 때).

DO $$
DECLARE
    v_board  bigint;
    v_prefix bigint;
    v_posts  integer;
BEGIN
    SELECT id INTO v_board FROM boards WHERE slug = 'review';
    IF v_board IS NULL THEN
        RAISE EXCEPTION '사용기 게시판(slug review)이 없다';
    END IF;
    SELECT id INTO v_prefix FROM board_prefixes WHERE board_id = v_board AND common_key = 'TRIAL' AND active ORDER BY sort_order, id LIMIT 1;
    IF v_prefix IS NULL THEN
        RAISE EXCEPTION '사용기 게시판에 체험단 말머리(common_key TRIAL)가 없다 — 프리셋에 TRIAL 말머리가 든 api jar 로 빌드 서버를 띄울 것';
    END IF;

    -- 1) 체험단 사용기 글의 말머리
    UPDATE posts SET prefix_id = v_prefix
     WHERE board_id = v_board AND trial_participant_id IS NOT NULL AND prefix_id IS DISTINCT FROM v_prefix;
    GET DIAGNOSTICS v_posts = ROW_COUNT;
    IF v_posts = 0 THEN
        RAISE EXCEPTION '말머리를 바꿀 체험단 사용기 글이 없다(패치 0002 가 넣은 글)';
    END IF;
    RAISE NOTICE '체험단 사용기 글 % 개 → 말머리 %', v_posts, v_prefix;
END $$;

-- 2) 모집 중(뽑기 전) 0원 선결제 모집의 금액 · 신청 건 상품 줄
UPDATE biz_application_items i
   SET units = greatest(1, t.recruit_count), supply = t.unit_price * greatest(1, t.recruit_count)
  FROM trials t
 WHERE i.application_id = t.biz_application_id AND i.product = 'TRIAL'
   AND t.payment_timing = 'PREPAID' AND t.amount IS NULL AND t.unit_price = 0 AND t.shipping_fee_per_person = 0
   AND t.price_unit = 'PER_PERSON';

UPDATE trials SET amount = 0
 WHERE payment_timing = 'PREPAID' AND amount IS NULL AND unit_price = 0 AND shipping_fee_per_person = 0;

-- 3) 모집 ↔ 광고 신청 건 이력
INSERT INTO trial_biz_applications (biz_application_id, trial_id, created_at)
SELECT t.biz_application_id, t.id, t.created_at
  FROM trials t
 WHERE NOT EXISTS (SELECT 1 FROM trial_biz_applications h WHERE h.biz_application_id = t.biz_application_id);
