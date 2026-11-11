-- 방문 통계 3단계 — docs/features/site-analytics-contract.md 「3단계」 결정 ⑥(판정 표는 날짜 파티션 + DROP — 큰 DELETE 를 두지 않는다).
--
-- analytics_visit_marks(V1·V2 — 「그 날 이미 셌다」 표시)를 한국 날짜(day) 하루 범위 파티션으로 바꾼다. 판정은 하루 단위라 오늘·어제 조각만 있으면 되고,
-- 지난 조각은 DB 배치 `analytics-visit-mark-cleanup`(R__analytics_11)이 통째로 DROP 한다(예전에는 날마다 지난 줄을 DELETE — 큰 규모면 하루 약 10만 줄과 vacuum 빈자리).
-- 조각은 같은 배치가 어제~모레를 미리 만들고(자정에 조각이 없어 쓰기가 실패하지 않게), 쓰는 쪽(api VisitFlusher)은 조각이 없다는 오류(23514)를 받으면
-- analytics_ensure_visit_mark_partitions(R__analytics_11)를 한 번 부르고 다시 쓴다(원본 조각과 같은 방식 — 잠금 아래 한 번만).
-- 기본 키 (day, visitor_hash) 가 파티션 열을 담아 1분 몰아쓰기의 INSERT … ON CONFLICT … RETURNING (xmax = 0) 이 그대로 동작한다.
-- 옮기기: 같은 이름·같은 칸의 파티션 표를 새로 만들어 어제·오늘(그리고 이미 들어온 내일·모레) 줄만 옮기고 바꿔 끼운다 — 칸이 같아 롤링 배포 중 옛 서버의 문장도 그대로 맞는다.
-- 삭제 요청 크론(R__analytics_13)은 부모 표에 DELETE 하므로 그대로다. 조각 이름: analytics_visit_marks_YYYYMMDD.
ALTER TABLE analytics_visit_marks RENAME TO analytics_visit_marks_old;
ALTER TABLE analytics_visit_marks_old RENAME CONSTRAINT analytics_visit_marks_pkey TO analytics_visit_marks_old_pkey;

CREATE TABLE analytics_visit_marks (
    day          date        NOT NULL,                     -- 한국 날짜
    visitor_hash varchar(64) NOT NULL,
    new_visitor  boolean     NOT NULL DEFAULT false,
    PRIMARY KEY (day, visitor_hash)
) PARTITION BY RANGE (day);

DO $$
DECLARE
    v_today date := (now() AT TIME ZONE 'Asia/Seoul')::date;
    v_day   date;
BEGIN
    FOR i IN -1..2 LOOP -- 어제 · 오늘 · 내일 · 모레
        v_day := v_today + i;
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF analytics_visit_marks FOR VALUES FROM (%L) TO (%L)',
                       'analytics_visit_marks_' || to_char(v_day, 'YYYYMMDD'), v_day, v_day + 1);
    END LOOP;
    INSERT INTO analytics_visit_marks (day, visitor_hash, new_visitor)
    SELECT day, visitor_hash, new_visitor FROM analytics_visit_marks_old WHERE day >= v_today - 1 AND day <= v_today + 2
    ON CONFLICT DO NOTHING;
END
$$;

DROP TABLE analytics_visit_marks_old;
