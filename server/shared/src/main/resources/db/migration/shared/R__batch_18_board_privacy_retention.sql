-- DB 배치 `board-privacy-retention` — 게시판 개인정보 보유기간 적용. docs/features/board-contract.md 「배치」, 관리자 화면 /boards/defaults/privacy
-- 설정은 batch_params 가 아니라 board_privacy_settings(행 하나, 관리자 화면이 저장)에서 읽는다. 전체 스위치(retention_enabled)가 꺼져 있으면 아무것도 하지 않는다(SKIP).
-- 축(axis)별 규칙 — 대상 판정은 이 파일 한 곳에만 있다(관리자 서버의 대상 수·대상 보기·「지금 실행」도 아래 함수를 부른다: BoardPrivacyGateway):
--   ip             작성한 지 ip_retention_days 일이 지난 글·댓글의 작성 IP 를 지운다(회원·비회원 모두).
--   guest          작성한 지 guest_retention_days 일이 지난 비회원 글·댓글(0 이면 하지 않는다).
--                  ANONYMIZE: 이름 ***·비밀번호 해시·IP 를 지우고 글은 남긴다. DELETE: 위에 더해 삭제 표시 + 제목·본문 파기, 비회원 글이면 그 글의 댓글·답글도 삭제·파기.
--   deletedContent 삭제한 지 deleted_content_retention_days 일이 지난 글·댓글의 원문(제목·본문)을 파기한다(purged_at).
--   reportRecord · staffAction  신고 기록·스텝 처리 기록 표가 아직 없다 — 대상 0(표가 생기면 이 파일에 더한다).
-- 기본 실행: 매일 03:30 KST. 결과: 처리 건수 = 바꾼 글·댓글 행 수(축 합계), 0 이면 SKIP. 덩어리(기본 5000)마다 COMMIT, 한 번 실행 상한(기본 100000).
-- 호출: CALL batch_board_privacy_retention();  (「지금 실행」(배치 화면)은 CALL batch_board_privacy_retention('MANUAL', 운영자ID, NULL))
-- 같은 배치 잠금(pg_advisory 7340002 + 키)을 board_privacy_process 도 잡는다 — 배치가 도는 동안 관리자 화면의 축별 「지금 실행」은 -1(실행 중)을 받는다.

-- 축의 기준 시각 — 이보다 앞선 것이 대상. NULL = 그 축은 대상이 없다(끈 축·모르는 축·표가 없는 축)
CREATE OR REPLACE FUNCTION board_privacy_cutoff(p_axis text) RETURNS timestamptz
LANGUAGE sql STABLE AS $fn$
    SELECT CASE p_axis
               WHEN 'ip' THEN now() - make_interval(days => s.ip_retention_days)
               WHEN 'guest' THEN CASE WHEN s.guest_retention_days > 0 THEN now() - make_interval(days => s.guest_retention_days) END
               WHEN 'deletedContent' THEN now() - make_interval(days => s.deleted_content_retention_days)
           END
      FROM board_privacy_settings s
     WHERE s.id = 1
$fn$;

-- 축의 대상 수(글 + 댓글)
CREATE OR REPLACE FUNCTION board_privacy_count(p_axis text) RETURNS bigint
LANGUAGE plpgsql STABLE AS $fn$
DECLARE
    v_cutoff timestamptz := board_privacy_cutoff(p_axis);
    v_posts  bigint := 0;
    v_cmts   bigint := 0;
BEGIN
    IF v_cutoff IS NULL THEN
        RETURN 0;
    END IF;
    IF p_axis = 'ip' THEN
        SELECT count(*) INTO v_posts FROM posts WHERE author_ip IS NOT NULL AND created_at < v_cutoff;
        SELECT count(*) INTO v_cmts FROM comments WHERE author_ip IS NOT NULL AND created_at < v_cutoff;
    ELSIF p_axis = 'guest' THEN
        SELECT count(*) INTO v_posts FROM posts WHERE author_id IS NULL AND guest_processed_at IS NULL AND created_at < v_cutoff;
        SELECT count(*) INTO v_cmts FROM comments WHERE author_id IS NULL AND guest_processed_at IS NULL AND created_at < v_cutoff;
    ELSIF p_axis = 'deletedContent' THEN
        SELECT count(*) INTO v_posts FROM posts WHERE deleted_at IS NOT NULL AND purged_at IS NULL AND deleted_at < v_cutoff;
        SELECT count(*) INTO v_cmts FROM comments WHERE deleted_at IS NOT NULL AND purged_at IS NULL AND deleted_at < v_cutoff;
    END IF;
    RETURN v_posts + v_cmts;
END
$fn$;

-- 축의 대상 앞의 몇 줄(오래된 것부터) — 글은 제목, 댓글은 본문 앞 40자. 비회원 이름·IP 는 싣지 않는다
CREATE OR REPLACE FUNCTION board_privacy_targets(p_axis text, p_limit int)
    RETURNS TABLE (target_id text, title text, board_name text, created_at timestamptz)
LANGUAGE plpgsql STABLE AS $fn$
#variable_conflict use_column
DECLARE
    v_cutoff timestamptz := board_privacy_cutoff(p_axis);
BEGIN
    IF v_cutoff IS NULL OR p_axis NOT IN ('ip', 'guest', 'deletedContent') THEN
        RETURN;
    END IF;
    RETURN QUERY
    SELECT t.target_id, t.title, b.name::text, t.created_at
      FROM (
            (SELECT 'post-' || p.id AS target_id, p.title::text AS title, p.board_id, p.created_at
               FROM posts p
              WHERE (p_axis = 'ip' AND p.author_ip IS NOT NULL AND p.created_at < v_cutoff)
                 OR (p_axis = 'guest' AND p.author_id IS NULL AND p.guest_processed_at IS NULL AND p.created_at < v_cutoff)
                 OR (p_axis = 'deletedContent' AND p.deleted_at IS NOT NULL AND p.purged_at IS NULL AND p.deleted_at < v_cutoff)
              ORDER BY p.created_at LIMIT p_limit)
            UNION ALL
            (SELECT 'comment-' || c.id, left(c.content, 40), c.board_id, c.created_at
               FROM comments c
              WHERE (p_axis = 'ip' AND c.author_ip IS NOT NULL AND c.created_at < v_cutoff)
                 OR (p_axis = 'guest' AND c.author_id IS NULL AND c.guest_processed_at IS NULL AND c.created_at < v_cutoff)
                 OR (p_axis = 'deletedContent' AND c.deleted_at IS NOT NULL AND c.purged_at IS NULL AND c.deleted_at < v_cutoff)
              ORDER BY c.created_at LIMIT p_limit)
           ) t
      LEFT JOIN boards b ON b.id = t.board_id
     ORDER BY t.created_at
     LIMIT p_limit;
END
$fn$;

-- 축 하나를 덩어리 하나(최대 p_limit 행)만큼 처리한다. 돌려주는 값 = 바꾼 행 수(글 + 댓글), 배치가 도는 중이면 -1.
-- 전체 스위치는 부르는 쪽이 본다(배치 프로시저·관리자 서버).
CREATE OR REPLACE FUNCTION board_privacy_process(p_axis text, p_limit int) RETURNS bigint
LANGUAGE plpgsql AS $fn$
DECLARE
    v_cutoff timestamptz := board_privacy_cutoff(p_axis);
    v_mode   text;
    v_ids    bigint[];
    v_n      bigint;
    v_total  bigint := 0;
BEGIN
    IF NOT pg_try_advisory_xact_lock(7340002, hashtext('board-privacy-retention')) THEN
        RETURN -1;
    END IF;
    IF v_cutoff IS NULL OR p_limit < 1 THEN
        RETURN 0;
    END IF;
    SELECT guest_mode INTO v_mode FROM board_privacy_settings WHERE id = 1;

    IF p_axis = 'ip' THEN
        UPDATE posts SET author_ip = NULL, updated_at = now()
         WHERE id IN (SELECT id FROM posts WHERE author_ip IS NOT NULL AND created_at < v_cutoff ORDER BY created_at LIMIT p_limit);
        GET DIAGNOSTICS v_n = ROW_COUNT;
        v_total := v_n;
        IF v_total < p_limit THEN
            UPDATE comments SET author_ip = NULL, updated_at = now()
             WHERE (post_id, id) IN (SELECT post_id, id FROM comments WHERE author_ip IS NOT NULL AND created_at < v_cutoff
                                      ORDER BY created_at LIMIT p_limit - v_total);
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_total := v_total + v_n;
        END IF;

    ELSIF p_axis = 'guest' THEN
        SELECT array_agg(id) INTO v_ids
          FROM (SELECT id FROM posts WHERE author_id IS NULL AND guest_processed_at IS NULL AND created_at < v_cutoff ORDER BY created_at LIMIT p_limit) x;
        IF v_ids IS NOT NULL THEN
            IF v_mode = 'DELETE' THEN
                UPDATE posts SET guest_name = '***', guest_password_hash = NULL, author_ip = NULL, guest_processed_at = now(),
                                 deleted_at = coalesce(deleted_at, now()), title = '', content = '', purged_at = coalesce(purged_at, now()), updated_at = now()
                 WHERE id = ANY (v_ids);
                GET DIAGNOSTICS v_n = ROW_COUNT;
                -- 그 글의 댓글·답글도 삭제·파기한다(대상 수에는 세지 않는다)
                UPDATE comments SET deleted_at = coalesce(deleted_at, now()), content = '', purged_at = now(), updated_at = now()
                 WHERE post_id = ANY (v_ids) AND purged_at IS NULL;
            ELSE
                UPDATE posts SET guest_name = '***', guest_password_hash = NULL, author_ip = NULL, guest_processed_at = now(), updated_at = now()
                 WHERE id = ANY (v_ids);
                GET DIAGNOSTICS v_n = ROW_COUNT;
            END IF;
            v_total := v_n;
        END IF;
        IF v_total < p_limit THEN
            IF v_mode = 'DELETE' THEN
                UPDATE comments SET guest_name = '***', guest_password_hash = NULL, author_ip = NULL, guest_processed_at = now(),
                                    deleted_at = coalesce(deleted_at, now()), content = '', purged_at = coalesce(purged_at, now()), updated_at = now()
                 WHERE (post_id, id) IN (SELECT post_id, id FROM comments WHERE author_id IS NULL AND guest_processed_at IS NULL AND created_at < v_cutoff
                                          ORDER BY created_at LIMIT p_limit - v_total);
            ELSE
                UPDATE comments SET guest_name = '***', guest_password_hash = NULL, author_ip = NULL, guest_processed_at = now(), updated_at = now()
                 WHERE (post_id, id) IN (SELECT post_id, id FROM comments WHERE author_id IS NULL AND guest_processed_at IS NULL AND created_at < v_cutoff
                                          ORDER BY created_at LIMIT p_limit - v_total);
            END IF;
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_total := v_total + v_n;
        END IF;

    ELSIF p_axis = 'deletedContent' THEN
        UPDATE posts SET title = '', content = '', purged_at = now(), updated_at = now()
         WHERE id IN (SELECT id FROM posts WHERE deleted_at IS NOT NULL AND purged_at IS NULL AND deleted_at < v_cutoff ORDER BY deleted_at LIMIT p_limit);
        GET DIAGNOSTICS v_n = ROW_COUNT;
        v_total := v_n;
        IF v_total < p_limit THEN
            UPDATE comments SET content = '', purged_at = now(), updated_at = now()
             WHERE (post_id, id) IN (SELECT post_id, id FROM comments WHERE deleted_at IS NOT NULL AND purged_at IS NULL AND deleted_at < v_cutoff
                                      ORDER BY deleted_at LIMIT p_limit - v_total);
            GET DIAGNOSTICS v_n = ROW_COUNT;
            v_total := v_total + v_n;
        END IF;
    END IF;
    RETURN v_total;
END
$fn$;

CREATE OR REPLACE PROCEDURE batch_board_privacy_retention(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job     CONSTANT text := 'board-privacy-retention';
    v_chunk   bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max     bigint := batch_param(c_job, 'max_per_run', 100000, 1000, 100000000);
    v_enabled boolean;
    v_axis    text;
    v_n       bigint;
    v_req     bigint;
    v_ip     bigint := 0;
    v_guest   bigint := 0;
    v_deleted bigint := 0;
    v_total   bigint := 0;
    v_err     text;
    v_state   text;
    v_msg     text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    SELECT retention_enabled INTO v_enabled FROM board_privacy_settings WHERE id = 1;
    IF NOT coalesce(v_enabled, false) THEN
        PERFORM batch_run_close(p_run_id, c_job, 0, NULL, 'batch.result.boardPrivacyRetention', NULL, 'batch.result.boardPrivacyRetention.disabled', NULL);
        COMMIT;
        RETURN;
    END IF;

    FOREACH v_axis IN ARRAY ARRAY['ip', 'guest', 'deletedContent'] LOOP
        LOOP
            v_req := least(v_chunk, v_max - v_total);
            BEGIN
                v_n := board_privacy_process(v_axis, v_req::int);
            EXCEPTION WHEN OTHERS THEN
                GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
                v_err := batch_error_text(v_state, v_msg);
                EXIT;
            END;
            IF v_n < 0 THEN
                v_n := 0; -- 같은 세션이 잠금을 쥐고 있어 오지 않는 경우지만 안전하게
            END IF;
            v_total := v_total + v_n;
            CASE v_axis WHEN 'ip' THEN v_ip := v_ip + v_n; WHEN 'guest' THEN v_guest := v_guest + v_n; ELSE v_deleted := v_deleted + v_n; END CASE;
            COMMIT;
            EXIT WHEN v_n < v_req OR v_total >= v_max;
        END LOOP;
        EXIT WHEN v_err IS NOT NULL OR v_total >= v_max;
    END LOOP;

    UPDATE board_privacy_settings SET last_run_at = now() WHERE id = 1;
    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.boardPrivacyRetention.capped' ELSE 'batch.result.boardPrivacyRetention' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text, v_ip::text, v_guest::text, v_deleted::text) END,
        'batch.result.boardPrivacyRetention.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_param_seed('board-privacy-retention', 'chunk_size', 5000);
SELECT batch_param_seed('board-privacy-retention', 'max_per_run', 100000);
SELECT batch_define('board-privacy-retention', 'batch_board_privacy_retention', 3, 30);
