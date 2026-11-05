-- DB 배치 `emoticon-payout-account-cleanup` — 탈퇴한 로보티콘 작가의 정산 정보(emoticon_payout_accounts, V143 — 계좌번호·예금주·사업자 번호 암호문과 가린 값) 파기.
-- docs/features/emoticon-contract.md 「후속 결정 반영(2026-11-05)」 5.
-- 번호: shared R__batch_40 다음. ⚠ 가번호 — develop 도 R__batch_40 이 마지막이라 합칠 때 다른 세션이 41 을 먼저 썼으면 뒤로 민다(파일 이름만 바꾸면 된다 — 반복 마이그레이션).
-- 규칙: 회원이 탈퇴(users.status = 'WITHDRAWN')했고 그 작가의 정산이 모두 끝난 줄을 지운다. 「끝남」 =
--       ① 아직 정산에 들어가지 않은 판매·환불이 없다(emoticon_purchases: 결제된 판매의 settlement_month 가 비었거나 · 환불의 refund_settlement_month 가 비었으면 남은 예정 금액)
--          그리고 결제 대기(PENDING) 구매도 없다(곧 판매가 될 수 있다),
--       ② 남은 확정·보류 금액이 없다(emoticon_settlements: 보류 HELD 줄 · 지급액이 있는데 지급 완료가 아닌 확정 CONFIRMED 줄이 없다),
--       ③ 마지막으로 굳힌 달이 넘긴 금액(carried_out)이 0 이하다(최소 지급액 미만으로 넘겨 둔 돈이 남아 있으면 끝나지 않았다 — 음수(확정 뒤 환불로 뺄 돈)는 작가에게 줄 돈이 아니라 끝난 것으로 본다).
--          탈퇴한 작가는 최소 지급액이 없어(후속 결정 6 — EmoticonSettlementCalculator.Rules#forCreator) 다음 확정에서 넘긴 금액까지 지급 대상이 되고, 지급 기록 뒤 ③이 풀린다.
--          정산 정보가 없어 확정이 막힌(PAYOUT_ACCOUNT_MISSING) 작가는 지울 정산 정보도 없다.
--       그리고 「끝난 시각」 = 탈퇴 완료 · 마지막 지급 기록 · 마지막 확정 · 정산 정보를 마지막으로 바꾼 시각 중 가장 늦은 것에서 grace_days(기본 30일, 0~365)가 지나야 지운다
--       — 지급 뒤 이체 반송·정정에 쓸 여유. 정산 기록(emoticon_settlements·처리 기록·구매 기록 — 금액·상태·날짜, 개인정보 없음)은 지우지 않는다(거래 기록 보존은 commerce.md 17-3 의
--       일반 거래 기록 파기가 맡는다). 정산 정보는 개인정보(계좌·실명)라 17-3 의 「세법 보존 기준일(다음 해 6월 1일 + 5년)」을 쓰지 않는다 — 그 기준은 익명 거래 기록에만 쓴다(2026-11-04 정정).
--       분쟁 연락은 결제 이력이 있는 탈퇴 회원의 분리 보관 이메일(withdrawn_member_contacts, 마지막 거래 + 5년)이 맡는다.
--       조건은 지우는 문장 안에서 다시 보므로, 고른 뒤 새 판매·보류가 생기면 지우지 않는다.
-- 기본 실행: 매일 04:35 (한국시간 — batch_daily_cron 이 환산). 설정값(batch_params): grace_days(30) · chunk_size(500) · max_per_run(100000).
-- 결과: 처리 건수 = 지운 정산 정보 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지의 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_emoticon_payout_account_cleanup();  (「지금 실행」은 CALL batch_emoticon_payout_account_cleanup('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_emoticon_payout_account_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'emoticon-payout-account-cleanup';
    v_days   bigint := batch_param(c_job, 'grace_days', 30, 0, 365);
    v_chunk  bigint := batch_param(c_job, 'chunk_size', 500, 100, 100000);
    v_max    bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_cutoff timestamptz;
    v_n      bigint;
    v_total  bigint := 0;
    v_err    text;
    v_state  text;
    v_msg    text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    v_cutoff := now() - make_interval(days => v_days::int);
    LOOP
        BEGIN
            -- 정산 정보는 작가마다 한 줄이라 작은 표다 — 조건에 맞는 줄을 PK 로 골라 지운다(지운 줄은 다시 대상이 아니라 루프가 끝난다)
            DELETE FROM emoticon_payout_accounts
             WHERE user_id IN (
                   SELECT a.user_id
                     FROM emoticon_payout_accounts a
                     JOIN users u ON u.id = a.user_id
                    WHERE u.status = 'WITHDRAWN'
                      -- ① 남은 예정 금액·결제 대기 없음(ix_emoticon_purchases_creator_paid · 정산 달 부분 색인)
                      AND NOT EXISTS (SELECT 1 FROM emoticon_purchases p
                                       WHERE p.creator_id = a.user_id
                                         AND (p.status = 'PENDING'
                                              OR (p.paid_at IS NOT NULL AND p.settlement_month IS NULL)
                                              OR (p.refunded_at IS NOT NULL AND p.refund_settlement_month IS NULL)))
                      -- ② 남은 확정·보류 금액 없음
                      AND NOT EXISTS (SELECT 1 FROM emoticon_settlements s
                                       WHERE s.creator_id = a.user_id
                                         AND (s.status = 'HELD' OR (s.status = 'CONFIRMED' AND s.payout_amount > 0)))
                      -- ③ 마지막으로 굳힌 달이 넘긴 돈이 없음
                      AND coalesce((SELECT s.carried_out FROM emoticon_settlements s
                                     WHERE s.creator_id = a.user_id AND s.confirmed_at IS NOT NULL
                                     ORDER BY s.sales_month DESC LIMIT 1), 0) <= 0
                      -- 끝난 시각 + 여유가 지남(greatest 는 NULL 을 건너뛴다)
                      AND greatest(a.updated_at,
                                   (SELECT max(w.completed_at) FROM member_withdrawals w WHERE w.user_id = a.user_id AND w.status = 'COMPLETED'),
                                   (SELECT max(greatest(s.paid_at, s.confirmed_at)) FROM emoticon_settlements s WHERE s.creator_id = a.user_id)) <= v_cutoff
                    ORDER BY a.user_id
                    LIMIT least(v_chunk, v_max - v_total));
            GET DIAGNOSTICS v_n = ROW_COUNT;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_total := v_total + v_n;
        COMMIT;
        EXIT WHEN v_n = 0 OR v_total >= v_max;
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.emoticonPayoutAccountCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_days::text) END,
        'batch.result.emoticonPayoutAccountCleanup.none', batch_args(v_days::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('emoticon-payout-account-cleanup', 'grace_days', 30);
SELECT batch_param_seed('emoticon-payout-account-cleanup', 'chunk_size', 500);
SELECT batch_param_seed('emoticon-payout-account-cleanup', 'max_per_run', 100000);
SELECT batch_define('emoticon-payout-account-cleanup', 'batch_emoticon_payout_account_cleanup', 4, 35);
