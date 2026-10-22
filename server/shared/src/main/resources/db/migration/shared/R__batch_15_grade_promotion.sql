-- DB 배치 `grade-promotion` — 등급 일수 승급. 가입 후 일수가 지나 새로 승급 조건을 채운 회원의 등급을 올린다.
-- (예전에는 Java 배치(GradePromotionBatchJob → GradePromotionService.promoteByElapsedTime)였다. DB 배치로 옮겼다 — 같은 키를 이어 쓰므로 쌓인 로그와 화면의 이름이 그대로고,
--  옛 batch_jobs 행은 기동 때 BatchCatalogSynchronizer 가 지운다.)
--
-- ★ 이 판정은 Java 에도 있다 — 둘은 항상 같은 결과를 내야 한다. 어느 한쪽을 고치면 다른 쪽도 같이 고친다:
--     · Java : server/shared/.../grade/service/GradePromotionService.java (promoteAll — 등급 저장 직후 소급 승급 / promoteOnEvent — 글·댓글 작성 때 즉시 승급)
--              와 server/shared/.../grade/repository/master/GradeRepositoryQDSL.java (promotable 조건)
--     · SQL  : 이 파일
--   Java 쪽 promoteAll 은 「등급 저장 직후」에 일수 조건을 저장 전 값 이상으로 올려 부르므로(기준을 낮춘 몫은 다음 배치로 미룬다) 일수 조건을 낮춘 몫은 이 배치만 올린다.
--
-- 규칙 (Java 와 같다):
--   · 올리기만 한다(자동으로 내리지 않는다). 지금 등급보다 서열이 높은 등급 중 조건 두 개 — 가입 후 일수(grades.min_days)와 글+댓글 수(grades.min_activity) — 를 모두(AND) 채운
--     가장 높은 등급으로 한 번에 올린다. 일수 = 가입 시각(users.created_at) <= 지금 - min_days 일.
--   · 대상은 정상(ACTIVE) 회원이고 탈퇴 처리(deleted_at)되지 않은 회원만. 정지·탈퇴 회원은 자동 승급이 멈춘다.
--   · 운영자가 수동으로 올린 회원(profiles.grade_manual)은 등급 옵션(grade_settings.manual_policy)이 CONTINUE_AUTO 일 때만 포함한다. 옵션 행이 없으면 포함한다.
--   · 활동 수: 승급 판정은 모든 회원의 활동 수를 0 으로 본다 — 활동 조건이 0 인 등급(조건이 일수뿐인 등급)만 올라갈 수 있다. 글·댓글 표(V42)는 있지만 잇지 않았다(의도 —
--     매일 전체 회원을 훑는 배치에서 회원마다 글 월 조각 + 댓글 64조각의 색인을 찌르는 집계가 너무 무겁다. 작성 쪽 카운터가 생기면 잇는다. roles-contract.md 「회원 상세 활동·등급 이력(서버) 구현 결과」).
--     (Java GradePromotionService.KNOWN_ACTIVITY 와 같은 가정. 잇게 되면 아래 c_known_activity 자리와 Java 의 activityReachable·QDSL 도 함께 고친다.)
--   · 이력: 올린 회원마다 member_grade_changes(V44)에 kind=AUTO 한 줄을 승급과 같은 문장에서 남긴다(Java GradeRepositoryQDSL#promote·promoteOnEvent 와 같다).
--   · 등급 저장과의 충돌 방지: 덩어리를 시작할 때마다 grade_settings 행을 FOR UPDATE 로 잠근다. 등급 저장(GradeService.save)도 같은 행을 잠그고 시작하므로, 저장이 진행 중이면 끝나기를
--     기다린 뒤 **저장이 끝난 등급 목록**으로 판정한다(잠그지 않으면 지워지는 등급을 참조하다 외래키 오류가 날 수 있다). 잠금은 덩어리의 COMMIT 으로 풀린다 — 덩어리마다 새로 잠그고 목록을 다시 읽는다.
--     30초 안에 잠금을 못 얻으면 그 실행은 FAIL 로 끝난다(다음 실행이 이어서 한다).
-- 기본 실행: 매일 04:30 (한국시간으로 적고 pg_cron 의 cron.timezone 에 맞춰 환산해 등록한다 — batch_daily_cron). 정책 문서 미확정 11 의 가정(새벽 시각).
-- 설정값(batch_params): chunk_size(한 번에 올리는 덩어리, 기본 5000) · max_per_run(한 번 실행에서 올리는 상한, 기본 1000000 — 상한에 닿으면 나머지는 다음 실행이 이어서 올린다).
-- 결과: 처리 건수 = 승급한 회원 수, 0 이면 SKIP. 덩어리마다 COMMIT. 실패하면 그때까지 올린 건수와 사유(민감값을 가려)를 남기고 FAIL.
-- 호출: CALL batch_grade_promotion();  (pg_cron 이 이 명령으로 부른다. 앱의 「지금 실행」은 CALL batch_grade_promotion('MANUAL', 운영자ID, NULL))
-- 이름 규칙: 프로시저 이름 = 'batch_' + 배치 키의 하이픈을 밑줄로 바꾼 것.

CREATE OR REPLACE PROCEDURE batch_grade_promotion(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)
LANGUAGE plpgsql AS $proc$
DECLARE
    c_job            CONSTANT text := 'grade-promotion';
    c_known_activity CONSTANT int  := 0;  -- 글·댓글 표가 없는 동안 모든 회원이 가진 활동 수 (Java GradePromotionService.KNOWN_ACTIVITY 와 같다)
    v_chunk          bigint := batch_param(c_job, 'chunk_size', 5000, 100, 100000);
    v_max            bigint := batch_param(c_job, 'max_per_run', 1000000, 1000, 100000000);
    v_now            timestamptz;
    v_include_manual boolean;
    v_picked         bigint;
    v_n              bigint;
    v_last           bigint := 0;
    v_total          bigint := 0;
    v_err            text;
    v_state          text;
    v_msg            text;
BEGIN
    p_run_id := batch_run_begin(c_job, p_trigger, p_operator);
    IF p_run_id IS NULL THEN
        RETURN; -- 이미 돌고 있다
    END IF;
    COMMIT; -- 시작 줄을 바로 보이게(실행 중 표시)

    v_now := clock_timestamp(); -- 한 실행의 판정 기준 시각 (Java 의 now)
    LOOP
        BEGIN
            PERFORM set_config('lock_timeout', '30s', true); -- 이 덩어리(트랜잭션) 동안만
            -- 등급 저장이 진행 중이면 끝나기를 기다린다 (Java: settings.findWithLockById). 옵션도 이 잠금 뒤에 읽는다
            SELECT manual_policy = 'CONTINUE_AUTO' INTO v_include_manual FROM grade_settings WHERE id = 1 FOR UPDATE;
            IF NOT FOUND THEN
                v_include_manual := true;
            END IF;

            -- 이번 덩어리: 아직 보지 않은(user_id > v_last) 승급 대상을 user_id 순서로 v_chunk 명까지 고른다.
            -- to_key = 지금 등급보다 서열이 높고 두 조건을 모두 채운 등급 중 가장 높은 것 (Java: 높은 등급부터 처리해 한 번에 올라간다).
            -- 올린 뒤에는 지금 등급 서열이 올라가 같은 회원이 다시 대상이 되지 않는다. 고른 뒤 다른 곳이 등급을 바꾼 회원은 건너뛴다(from_key 확인).
            WITH picked AS (
                SELECT p.user_id, p.grade_key AS from_key, t.to_key
                  FROM profiles p
                  JOIN users u    ON u.id = p.user_id
                  JOIN grades cur ON cur.grade_key = p.grade_key
                 CROSS JOIN LATERAL (
                       SELECT g.grade_key AS to_key
                         FROM grades g
                        WHERE g.level > cur.level
                          AND g.min_activity <= c_known_activity
                          AND u.created_at <= v_now - make_interval(days => g.min_days)
                        ORDER BY g.level DESC
                        LIMIT 1) t
                 WHERE p.user_id > v_last
                   AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
                   AND (v_include_manual OR NOT p.grade_manual)
                 ORDER BY p.user_id
                 LIMIT least(v_chunk, v_max - v_total)
            ), moved AS (
                UPDATE profiles p
                   SET grade_key = picked.to_key, updated_at = v_now
                  FROM picked
                 WHERE p.user_id = picked.user_id AND p.grade_key = picked.from_key
             RETURNING p.user_id, picked.from_key, picked.to_key
            ), history AS (
                -- 올린 회원마다 등급 변경 이력 한 줄(kind=AUTO) — 승급과 같은 문장이라 같은 트랜잭션(덩어리)에서 함께 커밋된다.
                -- (Java: GradeRepositoryQDSL#promote 가 같은 일을 한다.) 데이터 변경 CTE 는 아래 SELECT 가 참조하지 않아도 끝까지 실행된다.
                INSERT INTO member_grade_changes (user_id, from_grade_key, to_grade_key, kind, created_at)
                SELECT user_id, from_key, to_key, 'AUTO', v_now FROM moved
                RETURNING 1
            )
            SELECT (SELECT count(*) FROM picked), (SELECT count(*) FROM moved), coalesce((SELECT max(user_id) FROM picked), v_last)
              INTO v_picked, v_n, v_last;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_msg = MESSAGE_TEXT;
            v_err := batch_error_text(v_state, v_msg);
            EXIT;
        END;
        v_total := v_total + v_n;
        COMMIT;
        EXIT WHEN v_picked = 0 OR v_total >= v_max;
    END LOOP;

    PERFORM batch_run_close(p_run_id, c_job, v_total, v_err,
        CASE WHEN v_total >= v_max THEN 'batch.result.gradePromotion.capped' ELSE 'batch.result.gradePromotion' END,
        CASE WHEN v_total >= v_max THEN batch_args(v_total::text, v_max::text) ELSE batch_args(v_total::text) END,
        'batch.result.gradePromotion.none', NULL);
    COMMIT;
END
$proc$;

SELECT batch_param_seed('grade-promotion', 'chunk_size', 5000);
SELECT batch_param_seed('grade-promotion', 'max_per_run', 1000000);
SELECT batch_define('grade-promotion', 'batch_grade_promotion', 4, 30);
