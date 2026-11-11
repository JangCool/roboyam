-- 방문 통계 3단계 — docs/features/site-analytics-contract.md 「3단계」 결정 ⑥(판정 표는 날짜 파티션 + DROP — 큰 DELETE 를 두지 않는다) · ad-contract 「공개 노출」.
--
-- ad_view_marks(V118 — 공개 광고 「같은 방문자·같은 버전·같은 자리는 한국 하루 노출 1번·클릭 1번」 판정)를 한국 날짜(day) 하루 범위 파티션으로 바꾼다.
-- 노출·클릭 수는 일별 집계(ad_daily_stats)에 따로 있어 판정 표는 보관 일수(DB 배치 `ad-view-mark-cleanup` 설정 retention_days — 기본 2 = 오늘·어제, 2~30)만큼만 있으면 된다.
-- 지난 조각은 같은 배치(R__batch_33)가 통째로 DROP 한다(예전에는 날마다 지난 줄을 DELETE — 큰 규모면 vacuum 빈자리). 조각은 그 배치가 오늘·내일·모레 것을 미리 만들고,
-- 쓰는 쪽(api PublicAdStore#count)은 조각이 없다는 오류(23514)를 받으면 ad_ensure_view_mark_partitions(R__batch_33)를 그 날로 한 번 부르고 다시 쓴다.
-- 옮기기: 같은 이름·같은 칸·같은 기본 키·같은 CHECK 의 파티션 표를 새로 만들어 보관 일수 안의 줄만 옮기고 바꿔 끼운다
-- (칸이 같아 롤링 배포 중 옛 서버의 넣기 문장도 그대로 맞는다). 이 표를 가리키는 외래 키·다른 인덱스는 없다(V118 — 기본 키와 CHECK 뿐).
-- 기본 키 (day, visitor_hash, kind, application_id, version, placement) 가 파티션 열(day)을 맨 앞에 담아 ON CONFLICT DO NOTHING 이 그대로 동작한다.
-- 조각 이름: ad_view_marks_YYYYMMDD.
-- 보관 일수는 batch_params 에서 직접 읽는다(batch_param 함수는 반복 마이그레이션 R__batch_00 이라 새 설치에서는 이 파일보다 뒤에 만들어진다). 값이 없거나 2~30 밖이면 2.
ALTER TABLE ad_view_marks RENAME TO ad_view_marks_old;
ALTER TABLE ad_view_marks_old RENAME CONSTRAINT ad_view_marks_pkey TO ad_view_marks_old_pkey;

CREATE TABLE ad_view_marks (
    day             date         NOT NULL,
    visitor_hash    char(64)     NOT NULL,
    kind            char(1)      NOT NULL,
    application_id  bigint       NOT NULL,
    version         integer      NOT NULL,
    placement       varchar(20)  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (day, visitor_hash, kind, application_id, version, placement),
    CONSTRAINT ck_avm_kind CHECK (kind IN ('I', 'C'))
) PARTITION BY RANGE (day);

DO $$
DECLARE
    v_today date := (now() AT TIME ZONE 'Asia/Seoul')::date;
    v_keep  int  := coalesce((SELECT param_value FROM batch_params
                               WHERE job_key = 'ad-view-mark-cleanup' AND param_key = 'retention_days' AND param_value BETWEEN 2 AND 30), 2)::int;
    v_day   date;
BEGIN
    FOR i IN -(v_keep - 1)..2 LOOP -- 보관 일수 안의 지난날(기본 어제) · 오늘 · 내일 · 모레
        v_day := v_today + i;
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF ad_view_marks FOR VALUES FROM (%L) TO (%L)',
                       'ad_view_marks_' || to_char(v_day, 'YYYYMMDD'), v_day, v_day + 1);
    END LOOP;
    INSERT INTO ad_view_marks (day, visitor_hash, kind, application_id, version, placement, created_at)
    SELECT day, visitor_hash, kind, application_id, version, placement, created_at FROM ad_view_marks_old
     WHERE day >= v_today - (v_keep - 1) AND day <= v_today + 2
    ON CONFLICT DO NOTHING;
END
$$;

DROP TABLE ad_view_marks_old;

-- 줄 단위 DELETE 가 없어져 쓰지 않는 설정값(덩어리 크기·한 번 상한)을 지운다 — 보관 일수(retention_days)는 조각 단위로 계속 쓴다.
DELETE FROM batch_params WHERE job_key = 'ad-view-mark-cleanup' AND param_key IN ('chunk_size', 'max_per_run');
