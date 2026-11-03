-- DB 배치 `member-withdrawal-cleanup` — 탈퇴 회원 개인정보 뒷정리. docs/plans/member-withdrawal/00-analysis.md 4절(글 author_ip · 사업자 파기), docs/features/member-withdrawal-contract.md.
-- 번호: shared R__batch_35 다음.
-- 규칙(위에서 아래로):
--   ① 탈퇴 회원(users.status = 'WITHDRAWN')의 글·댓글 접속 IP — 작성한 지 ip_days(기본 90, 통비법 접속기록 3개월)가 지난 것의 author_ip 를 NULL 로.
--      (탈퇴 완료 때 이미 90일 지난 것은 그 자리에서 지웠고, 안 지난 것을 여기서 90일째에 지운다. 탈퇴하지 않은 회원의 IP 는 기존 개인정보 배치의 몫.)
--   ② 탈퇴(WITHDRAWN)·반려(REJECTED)된 사업자 신원 — 그 상태가 된 지 business_days(기본 90)가 지난 줄의 암호문(사업자번호·해시·대표자·연락처·이메일·주소)과 등록증 사본 내용을 비우고
--      erased_at 을 찍는다. 상호·끝 4자리·상태·시각·심사 기록은 남는다(신청 건 스냅샷 5년). 「그 상태가 된 시각」 = 탈퇴는 withdrawn_at, 반려는 reviewed_at(없으면 updated_at).
-- 기본 실행: 매일 04:30 (한국시간 — batch_daily_cron 이 환산). 설정값(batch_params): ip_days(90) · business_days(90) · chunk_size(1000) · max_per_run(100000).
-- 결과: 처리 건수 = 바꾼 행 수 합(글·댓글·사업자·등록증), 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지의 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_member_withdrawal_cleanup();  (「지금 실행」은 CALL batch_member_withdrawal_cleanup('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_member_withdrawal_cleanup(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job      CONSTANT text := 'member-withdrawal-cleanup';
    v_ip_days  bigint := batch_param(c_job, 'ip_days', 90, 30, 3650);
    v_biz_days bigint := batch_param(c_job, 'business_days', 90, 30, 3650);
    v_chunk    bigint := batch_param(c_job, 'chunk_size', 1000, 100, 100000);
    v_max      bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_now      timestamptz;
    v_ip_cut   timestamptz;
    v_biz_cut  timestamptz;
    v_ids      bigint[];
    v_n        bigint;
    v_round    bigint;
    v_total    bigint := 0;
    v_err      text;
    v_state    text;
    v_msg      text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    v_now := now();
    v_ip_cut := v_now - make_interval(days => v_ip_days::int);
    v_biz_cut := v_now - make_interval(days => v_biz_days::int);
    LOOP
        BEGIN
            v_round := 0;

            -- ① 탈퇴 회원 글 IP
            UPDATE posts SET author_ip = NULL
             WHERE id IN (SELECT p.id FROM posts p JOIN users u ON u.id = p.author_id
                           WHERE u.status = 'WITHDRAWN' AND p.author_ip IS NOT NULL AND p.created_at <= v_ip_cut
                           ORDER BY p.id LIMIT greatest(least(v_chunk, v_max - v_total - v_round), 0));
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_round := v_round + v_n;

            -- ① 탈퇴 회원 댓글 IP
            UPDATE comments SET author_ip = NULL
             WHERE id IN (SELECT c.id FROM comments c JOIN users u ON u.id = c.author_id
                           WHERE u.status = 'WITHDRAWN' AND c.author_ip IS NOT NULL AND c.created_at <= v_ip_cut
                           ORDER BY c.id LIMIT greatest(least(v_chunk, v_max - v_total - v_round), 0));
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_round := v_round + v_n;

            -- ② 탈퇴·반려 사업자 신원 — 암호문·해시 파기(상호·끝 4자리는 남긴다)
            SELECT array_agg(id ORDER BY id) INTO v_ids
              FROM (SELECT b.id FROM business_profiles b
                     WHERE b.erased_at IS NULL AND b.status IN ('REJECTED', 'WITHDRAWN')
                       AND coalesce(CASE WHEN b.status = 'WITHDRAWN' THEN b.withdrawn_at ELSE b.reviewed_at END, b.updated_at) <= v_biz_cut
                     ORDER BY b.id LIMIT greatest(least(v_chunk, v_max - v_total - v_round), 0)) s;
            IF v_ids IS NOT NULL THEN
                UPDATE business_profiles b
                   SET biz_no_enc = NULL, biz_no_hash = NULL, owner_name_enc = NULL, phone_enc = NULL, email_enc = NULL, postcode = NULL, address1_enc = NULL, address2_enc = NULL,
                       erased_at = v_now, updated_at = v_now
                 WHERE b.id = ANY (v_ids) AND b.erased_at IS NULL AND b.status IN ('REJECTED', 'WITHDRAWN');
                GET DIAGNOSTICS v_n = ROW_COUNT;
                v_round := v_round + v_n;

                -- 그 신원의 등록증 사본 내용
                UPDATE business_documents d
                   SET content = NULL, erased_at = v_now
                 WHERE d.profile_id = ANY (v_ids) AND d.erased_at IS NULL;
                GET DIAGNOSTICS v_n = ROW_COUNT;
                v_round := v_round + v_n;
            END IF;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_total := v_total + v_round;
        COMMIT;
        EXIT WHEN v_round = 0 OR v_total >= v_max;
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.memberWithdrawalCleanup' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_ip_days::text, v_biz_days::text) END,
        'batch.result.memberWithdrawalCleanup.none', batch_args(v_ip_days::text, v_biz_days::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('member-withdrawal-cleanup', 'ip_days', 90);
SELECT batch_param_seed('member-withdrawal-cleanup', 'business_days', 90);
SELECT batch_param_seed('member-withdrawal-cleanup', 'chunk_size', 1000);
SELECT batch_param_seed('member-withdrawal-cleanup', 'max_per_run', 100000);
SELECT batch_define('member-withdrawal-cleanup', 'batch_member_withdrawal_cleanup', 4, 30);
