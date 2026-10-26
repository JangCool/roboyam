-- DB 배치 `point-invariant-check` — 포인트 불변식 검사(읽기만). docs/domain/commerce.md 5-5·15-5, docs/features/commerce-stage1-contract.md 「A」 불변식.
-- 규칙(둘 다 맞아야 OK):
--   1) 회원별·종류별로 원장 몫 합 = 잔액 = 남은 묶음 합 — 무상: sum(point_ledger.free_delta) = point_balances.free_balance = sum(point_lots.remaining_amount WHERE kind='FREE'),
--      유상(paid_delta · paid_balance · PAID 묶음)도 같다. 잔액 행·원장·묶음 중 어디에라도 있는 회원을 모두 본다(없는 쪽은 0). 원장은 전환(V85) 이전 줄까지 모두 더한다 —
--      전환 묶음(MIGRATION)은 원장 줄 없이 그때 잔액만큼 만들었으므로 원장 합 = 잔액이 그대로 성립한다.
--   2) 묶음 추적 시작(point_settings.lot_tracking_started_at) 뒤 원장 줄마다 point_ledger_lots 반영 합 = 그 줄의 몫 — 무상 묶음 반영 합 = free_delta, 유상 묶음 반영 합 = paid_delta
--      (둘의 합 = delta 는 원장 CHECK). 전환 이전 줄은 묶음 반영이 없으므로 보지 않는다(5-5 「전환 이전 줄 제외」).
-- 쓰지 않는다 — 고치지 않고 알리기만 한다. 어긋난 회원·줄이 있으면 FAIL(사유 = 건수 + 앞 sample_size 개의 회원 ID·원장 줄 ID, 개인정보 없음) → 기존 배치 실패 알림(BATCH_RUN_FAILED)이 운영자에게 알린다.
-- 대량: 규칙마다 집계 SQL 한 문장(한 스냅숏 — 같은 트랜잭션에서 잔액·원장·묶음을 함께 바꾸는 PointService 가 반쯤 끝난 모습을 보지 않는다). 원장·묶음·반영 표를 한 번씩 훑는
--   해시 집계라 잠금은 읽기(ACCESS SHARE)뿐이고, 메모리가 모자라면 PostgreSQL 이 디스크로 넘긴다. 하루 한 번 새벽이라 덩어리로 나누지 않는다
--   (회원 범위로 나누면 덩어리 사이에 생긴 줄 때문에 오히려 판정이 흔들린다). 수천만 줄이 되어 시간이 문제면 그때 회원 ID 범위 덩어리를 검토한다.
-- 주의: 원장 보관 기간 파기·탈퇴 회원 원장 정리(commerce.md 17-3)가 생기면 규칙 1 의 원장 합이 잔액과 달라진다 — 그 기능을 만들 때 이 검사도 함께 고친다(파기한 몫을 더하는 등).
-- 기본 실행: 매일 05:30 (한국시간 — 소멸 배치 00:10 뒤, 다른 정리 배치 03:00~04:40 뒤). batch_daily_cron 이 cron.timezone 에 맞춰 환산해 등록한다.
-- 설정값(batch_params): sample_size(오류 사유에 싣는 ID 개수, 기본 5, 1~20).
-- 결과: 처리 건수 = 검사한 회원 수(0 이면 SKIP — 검사할 것이 없음). 집계가 실패하면 그 사유(민감값을 가려)로 FAIL.
-- 호출: CALL batch_point_invariant_check();  (「지금 실행」은 CALL batch_point_invariant_check('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_point_invariant_check(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job          CONSTANT text := 'point-invariant-check';
    v_sample       bigint := batch_param(c_job, 'sample_size', 5, 1, 20);
    v_members      bigint := 0;
    v_bad_members  bigint := 0;
    v_member_ids   text;
    v_lines        bigint := 0;
    v_bad_lines    bigint := 0;
    v_line_ids     text;
    v_err          text;
    v_state        text;
    v_msg          text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    BEGIN
        -- 1) 회원별·종류별: 원장 몫 합 = 잔액 = 남은 묶음 합
        WITH led AS (
                 SELECT user_id, sum(free_delta) AS f, sum(paid_delta) AS p FROM point_ledger GROUP BY user_id),
             lot AS (
                 SELECT user_id, coalesce(sum(remaining_amount) FILTER (WHERE kind = 'FREE'), 0) AS f,
                        coalesce(sum(remaining_amount) FILTER (WHERE kind = 'PAID'), 0) AS p
                   FROM point_lots WHERE remaining_amount > 0 GROUP BY user_id),
             bal AS (
                 SELECT user_id, free_balance AS f, paid_balance AS p FROM point_balances),
             ids AS (
                 SELECT user_id FROM bal UNION SELECT user_id FROM led UNION SELECT user_id FROM lot),
             cmp AS (
                 SELECT i.user_id,
                        coalesce(b.f, 0) = coalesce(l.f, 0) AND coalesce(b.f, 0) = coalesce(t.f, 0)
                        AND coalesce(b.p, 0) = coalesce(l.p, 0) AND coalesce(b.p, 0) = coalesce(t.p, 0) AS ok
                   FROM ids i
                   LEFT JOIN bal b ON b.user_id = i.user_id
                   LEFT JOIN led l ON l.user_id = i.user_id
                   LEFT JOIN lot t ON t.user_id = i.user_id)
        SELECT count(*), count(*) FILTER (WHERE NOT ok),
               (SELECT string_agg(s.user_id::text, ', ' ORDER BY s.user_id) FROM (SELECT user_id FROM cmp WHERE NOT ok ORDER BY user_id LIMIT v_sample) s)
          INTO v_members, v_bad_members, v_member_ids
          FROM cmp;

        -- 2) 묶음 추적 뒤 원장 줄마다: 무상 묶음 반영 합 = free_delta, 유상 묶음 반영 합 = paid_delta
        WITH lines AS (
                 SELECT l.id, l.free_delta, l.paid_delta
                   FROM point_ledger l
                  WHERE l.created_at >= (SELECT s.lot_tracking_started_at FROM point_settings s WHERE s.id = 1)),
             parts AS (
                 SELECT x.ledger_id, coalesce(sum(x.amount) FILTER (WHERE t.kind = 'FREE'), 0) AS f,
                        coalesce(sum(x.amount) FILTER (WHERE t.kind = 'PAID'), 0) AS p
                   FROM point_ledger_lots x
                   JOIN point_lots t ON t.id = x.lot_id
                  WHERE x.ledger_id IN (SELECT id FROM lines)
                  GROUP BY x.ledger_id),
             cmp AS (
                 SELECT ln.id, coalesce(r.f, 0) = ln.free_delta AND coalesce(r.p, 0) = ln.paid_delta AS ok
                   FROM lines ln LEFT JOIN parts r ON r.ledger_id = ln.id)
        SELECT count(*), count(*) FILTER (WHERE NOT ok),
               (SELECT string_agg(s.id::text, ', ' ORDER BY s.id) FROM (SELECT id FROM cmp WHERE NOT ok ORDER BY id LIMIT v_sample) s)
          INTO v_lines, v_bad_lines, v_line_ids
          FROM cmp;
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
        v_err := batch_error_text(v_state, v_msg);
    END;

    IF v_err IS NULL AND (v_bad_members > 0 OR v_bad_lines > 0) THEN
        -- 회원 ID·원장 줄 ID 만 싣는다(금액·이메일 없음) — 자세한 것은 관리자 원장·회원 상세에서 본다
        v_err := format('포인트 불변식 불일치 — 회원 %s명(앞 ID: %s) · 원장 줄 %s줄(앞 ID: %s)',
                        v_bad_members, coalesce(v_member_ids, '-'), v_bad_lines, coalesce(v_line_ids, '-'));
    END IF;

    PERFORM batch_run_close(p_run_id, c_job, v_members, v_err,
        'batch.result.pointInvariantCheck', batch_args(v_members::text, v_lines::text),
        'batch.result.pointInvariantCheck.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_param_seed('point-invariant-check', 'sample_size', 5);
SELECT batch_define('point-invariant-check', 'batch_point_invariant_check', 5, 30);
