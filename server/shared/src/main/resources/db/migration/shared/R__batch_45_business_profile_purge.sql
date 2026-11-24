-- DB 배치 `business-profile-purge` — 반려·탈퇴한 사업자 신청(사업자 신원) 기록을 지우거나 익명화한다(2026-11-24 사용자 결정 — 개인정보처리방침 2절 「사업자 회원 정보」,
-- 같은 날 메인 세션 결정: 지울 수 없는 줄은 익명화). 번호: shared R__batch_44 다음. 90일 뒤 민감 칸만 지우는 것은 R__batch_36 ② 그대로이고,
-- 이 배치는 그 뒤 남는 상호·등록번호 끝 4자리·개업일·국세청 조회 결과·심사 기록까지 없앤다.
-- 대상: 반려(REJECTED)·탈퇴(WITHDRAWN)인 신원 중 「그 상태가 된 시각」(탈퇴 = withdrawn_at, 반려 = reviewed_at, 없으면 updated_at — R__batch_36 과 같다) + retention_years(기본 5)가 지난 것.
--   ① 신청 건(biz_applications)이 가리키지 않으면 — 줄을 지운다. 함께 지우는 것: 등록증 사본 줄(business_documents — DB 안 암호문이라 저장소 파일은 없다) · 국세청 조회 스냅샷 ·
--      처리 기록 · 열람 기록(사업자 화면 · 광고주 화면의 사업자 열람) · 서비스 이용 권한 · 등록증 종이 접수 기록(biz_doc_intakes CERTIFICATE).
--      연결된 광고주 줄(ad_advertisers)은 지우지 않고 연결을 풀고 이름을 「삭제된 사업자 <광고주 번호>」로 바꾼다(사업자 광고주의 이름은 상호다 — 유일 색인이라 번호를 붙인다).
--   ② 신청 건이 가리키면(외래 키 + ck_ba_source_business 로 지울 수 없다) — 그 건들이 모두 끝났고(심사·결제 대기 아님 · 열린 환불 없음) 「게재가 끝난 날」(R__batch_46 과 같은 셈 —
--      게재 끝 · 닫힌 시각 · 환불 · 입금 확인 중 가장 늦은 것) + 같은 기간이 지났을 때 익명화한다: 상호 → 「삭제된 사업자」, 등록번호 끝 4자리·개업일·국세청 예외 승인 사유 → NULL,
--      반려 사유 글 → 「삭제된 기록」(반려면 NOT NULL), 남은 암호문 → NULL(erased_at), anonymized_at 을 찍는다(V265). 신청 건의 상호 스냅샷(상호·끝 4자리·광고주 이름)과
--      사업자 핫딜 글의 상호 표시(post_deals.business_name)도 같은 값으로 바꾼다. 광고 문구·이미지(소재)와 핫딜 글 본문은 사업자가 공개로 낸 광고물이라 그대로 둔다.
--      딸린 기록은 ①과 같이 지우되 서비스 이용 권한 줄(상태만 — 개인정보 없음, 신청 건 화면이 읽는다)은 남긴다. 연결된 광고주는 연결을 두고 이름만 바꾼다.
--      기간이 안 된 신청 건이 있으면 다음 실행으로 미룬다.
-- 기본 실행: 매일 05:00 (한국시간 — batch_daily_cron 이 환산). 설정값(batch_params): retention_years(5, 5~10) · chunk_size(500) · max_per_run(100000).
-- 결과: 처리 건수 = 지운 신원 + 익명화한 신원, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지의 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_business_profile_purge();  (「지금 실행」은 CALL batch_business_profile_purge('MANUAL', 운영자ID, NULL))
-- 시험: shared BizRecordPurgeBatchesTest.

CREATE OR REPLACE PROCEDURE batch_business_profile_purge(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job    CONSTANT text := 'business-profile-purge';
    c_name   CONSTANT text := '삭제된 사업자';
    v_years  bigint := batch_param(c_job, 'retention_years', 5, 5, 10);
    v_chunk  bigint := batch_param(c_job, 'chunk_size', 500, 100, 100000);
    v_max    bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_cut    timestamptz;
    v_last   bigint := 0;
    v_ids    bigint[];
    v_del    bigint[];
    v_anon   bigint[];
    v_n      bigint;
    v_round  bigint;
    v_total  bigint := 0;
    v_deleted bigint := 0;
    v_anonymized bigint := 0;
    v_err    text;
    v_state  text;
    v_msg    text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    v_cut := now() - make_interval(years => v_years::int);
    LOOP
        BEGIN
            -- 다음 덩어리 — 기간이 지났고 아직 익명화하지 않은 반려·탈퇴 신원(id 차례 키셋). 고른 줄은 잠근다(그 사이 다시 신청하면 상태가 바뀌어 빠진다)
            SELECT array_agg(id ORDER BY id) INTO v_ids
              FROM (SELECT b.id FROM business_profiles b
                     WHERE b.status IN ('REJECTED', 'WITHDRAWN') AND b.anonymized_at IS NULL AND b.id > v_last
                       AND coalesce(CASE WHEN b.status = 'WITHDRAWN' THEN b.withdrawn_at ELSE b.reviewed_at END, b.updated_at) <= v_cut
                     ORDER BY b.id LIMIT least(v_chunk, v_max - v_total) FOR UPDATE) s;
            EXIT WHEN v_ids IS NULL;
            v_last := v_ids[cardinality(v_ids)];
            v_round := 0;

            -- ① 신청 건이 가리키지 않는 줄 → 지운다
            SELECT array_agg(id) INTO v_del FROM unnest(v_ids) AS u(id)
             WHERE NOT EXISTS (SELECT 1 FROM biz_applications a WHERE a.business_profile_id = u.id);
            -- ② 가리키는 신청 건이 모두 끝났고 그 끝 + 기간이 지난 줄 → 익명화한다
            SELECT array_agg(id) INTO v_anon FROM unnest(v_ids) AS u(id)
             WHERE EXISTS (SELECT 1 FROM biz_applications a WHERE a.business_profile_id = u.id)
               AND NOT EXISTS (SELECT 1 FROM biz_applications a WHERE a.business_profile_id = u.id
                                AND (a.status NOT IN ('SCHEDULED', 'TAKEN_DOWN', 'REJECTED', 'CANCELED', 'EXPIRED')
                                     OR greatest(CASE WHEN a.status IN ('SCHEDULED', 'TAKEN_DOWN') THEN a.end_at ELSE coalesce(a.closed_at, a.updated_at) END,
                                                 (SELECT max(greatest(r.requested_at, r.decided_at, r.completed_at, r.transferred_at)) FROM biz_refunds r WHERE r.application_id = a.id),
                                                 (SELECT d.confirmed_at FROM biz_deposits d WHERE d.application_id = a.id)) > v_cut
                                     OR EXISTS (SELECT 1 FROM biz_refunds r WHERE r.application_id = a.id AND (r.status = 'REQUESTED' OR r.transfer_status = 'PENDING'))));

            IF v_del IS NOT NULL OR v_anon IS NOT NULL THEN
                v_ids := coalesce(v_del, '{}') || coalesce(v_anon, '{}');
                -- 딸린 기록(둘 다)
                DELETE FROM ad_advertiser_views WHERE business_profile_id = ANY (v_ids);
                DELETE FROM biz_doc_intakes
                 WHERE profile_id = ANY (v_ids)
                    OR scan_document_id IN (SELECT d.id FROM business_documents d WHERE d.profile_id = ANY (v_ids));
                DELETE FROM business_history WHERE profile_id = ANY (v_ids);
                DELETE FROM business_sensitive_views WHERE profile_id = ANY (v_ids);
                DELETE FROM business_nts_checks WHERE profile_id = ANY (v_ids);
                UPDATE business_profiles SET certificate_id = NULL WHERE id = ANY (v_ids) AND certificate_id IS NOT NULL;
                DELETE FROM business_documents WHERE profile_id = ANY (v_ids);
                -- 연결된 광고주 — 이름(= 상호)을 바꾸고, 지우는 신원이면 연결도 푼다
                UPDATE ad_advertisers SET name = c_name || ' ' || id,
                       business_profile_id = CASE WHEN business_profile_id = ANY (coalesce(v_del, '{}')) THEN NULL ELSE business_profile_id END, updated_at = now()
                 WHERE business_profile_id = ANY (v_ids);
            END IF;

            IF v_del IS NOT NULL THEN
                DELETE FROM business_services WHERE profile_id = ANY (v_del);
                DELETE FROM business_profiles WHERE id = ANY (v_del);
                GET DIAGNOSTICS v_n = ROW_COUNT;
                v_deleted := v_deleted + v_n;
                v_round := v_round + v_n;
            END IF;

            IF v_anon IS NOT NULL THEN
                -- 신청 건의 상호 스냅샷(상호·끝 4자리 · 광고주 이름 advertiser_name)과 사업자 핫딜 글의 상호 표시(post_deals.business_name) — 소재(광고 문구·이미지)·글 본문은 그대로
                UPDATE biz_applications SET company_name = c_name, biz_no_last4 = NULL,
                       advertiser_name = CASE WHEN advertiser_name IS NULL THEN NULL ELSE c_name END, updated_at = now()
                 WHERE business_profile_id = ANY (v_anon)
                   AND (company_name IS DISTINCT FROM c_name OR biz_no_last4 IS NOT NULL OR (advertiser_name IS NOT NULL AND advertiser_name <> c_name));
                UPDATE post_deals SET business_name = c_name
                 WHERE biz_application_id IN (SELECT a.id FROM biz_applications a WHERE a.business_profile_id = ANY (v_anon)) AND business_name IS DISTINCT FROM c_name;
                UPDATE business_profiles
                   SET company_name = c_name, biz_no_last4 = NULL, opened_on = NULL, nts_override_reason = NULL,
                       reject_text = CASE WHEN reject_text IS NULL THEN NULL ELSE '삭제된 기록' END, suspend_text = NULL,
                       biz_no_enc = NULL, biz_no_hash = NULL, owner_name_enc = NULL, phone_enc = NULL, email_enc = NULL, postcode = NULL, address1_enc = NULL, address2_enc = NULL,
                       erased_at = coalesce(erased_at, now()), anonymized_at = now(), updated_at = now()
                 WHERE id = ANY (v_anon);
                GET DIAGNOSTICS v_n = ROW_COUNT;
                v_anonymized := v_anonymized + v_n;
                v_round := v_round + v_n;
            END IF;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_total := v_total + v_round;
        COMMIT;
        EXIT WHEN v_total >= v_max;
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.cleanup.capped' ELSE 'batch.result.businessProfilePurge' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_deleted::text, v_years::text, v_anonymized::text) END,
        'batch.result.businessProfilePurge.none', batch_args(v_years::text));
    COMMIT;
END
$proc$;

SELECT batch_param_seed('business-profile-purge', 'retention_years', 5);
SELECT batch_param_seed('business-profile-purge', 'chunk_size', 500);
SELECT batch_param_seed('business-profile-purge', 'max_per_run', 100000);
SELECT batch_define('business-profile-purge', 'batch_business_profile_purge', 5, 0);
