-- DB 배치 `ad-advertiser-contact-purge` — 보관한 직접 등록 광고주(ad_advertisers, V115·V131)의 연락처 파기. docs/features/ad-advertiser-contract.md 규칙 9.
-- 번호: shared R__batch_34 다음.
-- 규칙: 보관했고(archived_at) 아직 파기하지 않은(contact_purged_at IS NULL) 직접 등록 광고주(business_profile_id IS NULL) 중
--       「마지막 광고 끝」(그 광고주 신청 건의 가장 늦은 end_at — 상태 무관) 또는 건이 없으면 보관 시각 + retention_years 년(기본 5 — 계약서 사본 5년 보존과 맞춤, 5~10)이
--       지난 줄의 담당자·연락처·이메일 암호문과 가린 값을 비우고 contact_purged_at 을 찍는다. 이름·메모·열람 기록·처리 기록은 남는다(평문 없음).
--       보관을 푼 광고주는 대상이 아니다(UPDATE 가 조건을 다시 본다). 파기한 광고주를 보관 풀어도 연락처는 비어 있다.
-- 기본 실행: 매일 04:10 (한국시간 — batch_daily_cron 이 환산). 설정값(batch_params): retention_years(5) · chunk_size(1000) · max_per_run(100000).
-- 결과: 처리 건수 = 파기한 광고주 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지의 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_ad_advertiser_contact_purge();  (「지금 실행」은 CALL batch_ad_advertiser_contact_purge('MANUAL', 운영자ID, NULL))

CREATE OR REPLACE PROCEDURE batch_ad_advertiser_contact_purge(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'ad-advertiser-contact-purge';
    v_years  bigint := batch_param(c_job, 'retention_years', 5, 5, 10);
    v_chunk  bigint := batch_param(c_job, 'chunk_size', 1000, 100, 100000);
    v_max    bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_keep   interval;
    v_last   bigint := 0;
    v_ids    bigint[];
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

    v_keep := make_interval(years => v_years::int);
    LOOP
        BEGIN
            -- 다음 덩어리 — 보관·미파기 줄을 id 차례로 훑는다(키셋)
            SELECT array_agg(id ORDER BY id) INTO v_ids
              FROM (SELECT a.id FROM ad_advertisers a
                     WHERE a.archived_at IS NOT NULL AND a.contact_purged_at IS NULL AND a.business_profile_id IS NULL AND a.id > v_last
                     ORDER BY a.id LIMIT least(v_chunk, v_max - v_total)) s;
            EXIT WHEN v_ids IS NULL;
            v_last := v_ids[cardinality(v_ids)];
            UPDATE ad_advertisers a
               SET contact_name_enc = NULL, contact_phone_enc = NULL, email_enc = NULL,
                   contact_name_masked = NULL, contact_phone_masked = NULL, email_masked = NULL,
                   contact_purged_at = now(), updated_at = now()
             WHERE a.id = ANY (v_ids)
               AND a.archived_at IS NOT NULL AND a.contact_purged_at IS NULL AND a.business_profile_id IS NULL
               AND coalesce((SELECT max(b.end_at) FROM biz_applications b WHERE b.advertiser_id = a.id), a.archived_at) + v_keep <= now();
            GET DIAGNOSTICS v_n = ROW_COUNT;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_total := v_total + v_n;
        COMMIT;
        EXIT WHEN v_total >= v_max;
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.adAdvertiserContactPurge' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_years::text) END,
        'batch.result.adAdvertiserContactPurge.none', batch_args(v_years::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('ad-advertiser-contact-purge', 'retention_years', 5);
SELECT batch_param_seed('ad-advertiser-contact-purge', 'chunk_size', 1000);
SELECT batch_param_seed('ad-advertiser-contact-purge', 'max_per_run', 100000);
SELECT batch_define('ad-advertiser-contact-purge', 'batch_ad_advertiser_contact_purge', 4, 10);
