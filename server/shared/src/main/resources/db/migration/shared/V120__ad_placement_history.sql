-- 광고 노출 자리 설정 변경 이력 — 사용자 지시 2026-11-01 「변경 이력 관리해.」(메일 서버 설정 이력 V54 와 같은 짜임의 별도 표)
--
-- ad_placement_settings(V118 · V119) 를 관리자 화면에서 저장할 때마다(값이 실제로 바뀐 때만) 한 줄. 저장과 같은 트랜잭션.
--   * action: CREATE(행을 처음 만듦 — 그 전까지는 서버 기본값으로 돌았다) · UPDATE(고침). 설정 지우기는 없다.
--   * source: ADMIN(관리자 화면 /ads/placements) 하나뿐이다(설치 마법사는 이 설정을 넣지 않는다).
--   * changes = [{field, before, after}] — field 차례 postInline.enabled · postInline.adType · postInline.position · wings.left · wings.right ·
--       wings.screens · boardList.enabled · boardList.after · adult.enabled. 값은 모두 글자다: 켜기 'true'/'false', 유형·위치는 이름, 화면은
--       HOME·PAGE·BOARD·POST 차례 쉼표 이음(빈 목록은 ''), 몇 번째 뒤는 숫자 글자. 만들기는 before 가 null 이고 아홉 칸을 모두 담는다(메일 이력과 같다).
--   * operator_id: 바꾼 운영자(operators.id). 운영자가 지워져도 이력은 남도록 FK 를 걸지 않는다(audit_logs.actor_id · V53 · V54 와 같다).
--   * 보관: 무기한(값이 실제로 바뀐 저장만 남고 행이 작다 — 메일 이력과 같다).
CREATE TABLE ad_placement_history (
    id          bigint      PRIMARY KEY,                     -- Snowflake(앱 발급)
    action      varchar(10) NOT NULL,
    source      varchar(10) NOT NULL,
    operator_id bigint,
    changed_at  timestamptz NOT NULL,
    changes     jsonb       NOT NULL,
    CONSTRAINT ck_ad_placement_history_action CHECK (action IN ('CREATE', 'UPDATE')),
    CONSTRAINT ck_ad_placement_history_source CHECK (source IN ('ADMIN')),
    CONSTRAINT ck_ad_placement_history_changes CHECK (jsonb_typeof(changes) = 'array' AND jsonb_array_length(changes) > 0)
);

CREATE INDEX ix_ad_placement_history_changed ON ad_placement_history (changed_at DESC, id DESC);

-- 이미 저장한 DB: 지금 값을 ADMIN · CREATE 첫 줄로 남긴다(V54 와 같은 방식). 시각·운영자는 그 행을 마지막으로 저장한 때·사람.
-- ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — V30 · V53 · V54 와 같은 마이그레이션용 노드), 순번 0.
INSERT INTO ad_placement_history (id, action, source, operator_id, changed_at, changes)
SELECT ((greatest(0, (extract(epoch FROM s.updated_at) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12),
       'CREATE', 'ADMIN', s.updated_by, s.updated_at,
       jsonb_build_array(
               jsonb_build_object('field', 'postInline.enabled', 'before', NULL, 'after', s.post_inline_enabled::text),
               jsonb_build_object('field', 'postInline.adType', 'before', NULL, 'after', s.post_inline_type),
               jsonb_build_object('field', 'postInline.position', 'before', NULL, 'after', s.post_inline_position),
               jsonb_build_object('field', 'wings.left', 'before', NULL, 'after', s.wing_left::text),
               jsonb_build_object('field', 'wings.right', 'before', NULL, 'after', s.wing_right::text),
               jsonb_build_object('field', 'wings.screens', 'before', NULL, 'after',
                                  coalesce((SELECT string_agg(x.screen, ',' ORDER BY array_position(ARRAY['HOME', 'PAGE', 'BOARD', 'POST']::text[], x.screen))
                                            FROM unnest(s.wing_screens) AS x(screen) WHERE x.screen <> ''), '')),
               jsonb_build_object('field', 'boardList.enabled', 'before', NULL, 'after', s.board_list_enabled::text),
               jsonb_build_object('field', 'boardList.after', 'before', NULL, 'after', s.board_list_after::text),
               jsonb_build_object('field', 'adult.enabled', 'before', NULL, 'after', s.adult_enabled::text))
FROM ad_placement_settings s
WHERE s.id = 1;
