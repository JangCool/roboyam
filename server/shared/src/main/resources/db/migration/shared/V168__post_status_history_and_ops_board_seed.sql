-- 운영 게시판(V166) 미확정 처리 — docs/features/ops-boards-contract.md 「V168」.
--
-- ① 처리 상태 이력 post_process_status_history — 상태가 실제로 바뀔 때마다 한 줄(처음 접수 RECEIVED 는 줄이 없고, 같은 값으로 바꾸면 남기지 않는다).
--    · board_id 는 바꾼 때 글이 있던 게시판(관리자가 글을 옮겨도 그대로), from/to 는 PostProcessStatus 이름, note 는 바꾼 사람이 적은 메모(1~200자, 없으면 NULL).
--    · actor_kind: OPERATOR(관리자 앱 운영자 — changed_by_operator_id, 또는 공개 사이트의 운영자 회원 계정 — changed_by_user_id) · BOARD_STAFF(그 게시판 매니저·스텝)
--      · GROUP_STAFF(그 소모임의 소모임장·매니저). 공개·관리자 둘 다 이 표 하나를 쓴다.
--    · 글 표는 id 월 범위 파티션이라 외래 키를 걸지 않는다(V166 process_status_by 와 같다). 정리: 게시판 삭제(BoardContentQueries#deleteContent)가 그 글의 줄을 지우고,
--      글 원문 파기(R__batch_18 board_privacy_process — 지운 글 원문·비회원 글 삭제)가 메모를 비운다. 탈퇴는 글이 남으므로 줄도 남는다(이름은 읽을 때 회원 닉네임 — 탈퇴하면 없다).
--    · 운영자 FK 는 ON DELETE SET NULL(menu_sets·boards 의 created_by 와 같다). 회원은 FK 를 걸지 않는다(posts.process_status_by 와 같다).
--
-- ② 운영 게시판 시드 — 「버그 신고」 bugs · 「기능 요청」 requests(일반·목록형) + 공개 메뉴 그룹 footer(「푸터 고객 지원」, CUSTOM) 와 두 게시판 항목.
--    값의 정본은 Java OpsBoardPreset(shared com.community.board.service) — 새 설치는 프리셋 적용(BoardPresetApplier → OpsBoardSeeder)이 같은 값으로 만든다.
--    두 경로의 결과가 같은지는 시험 OpsBoardSeedTest 가 본다(어느 쪽을 고치면 다른 쪽도 고친다).
--    · 이미 설치된 DB 에서만(installation.status = COMPLETED 이고 board_preset_applied_at 이 있는 곳) — 빈 DB 에 게시판을 넣으면 설치 프리셋·기동 때 한 번 적용
--      (BoardPresetApplier#applyOnceIfMissing — 게시판이 0개일 때만 만든다)이 「게시판이 있음」으로 보고 기본 게시판을 건너뛰기 때문이다.
--    · 주소(slug)나 이름(대소문자 무시 — ux_boards_name)이 이미 있는 게시판은 넣지 않는다. 메뉴 그룹 footer 가 이미 있으면 그룹·항목 모두 넣지 않는다.
--      그래서 두 번 돌아도(시험이 같은 블록을 다시 부른다) 그대로이고, 이미 손으로 만든 로컬 DB 는 아무것도 바뀌지 않는다.
--    · 메인 메뉴(main)에는 넣지 않는다(들어가는 길은 푸터뿐 — 계약). 글쓰기·댓글 최소 등급은 첫 등급(서열이 가장 낮은 등급 — 기본 설치의 「새싹」)이다.
--    · ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — 마이그레이션용 노드, V43·V122 와 같은 방식) + 순번.
--    · 만든 이(created_by)는 설치 때의 루트 운영자(없어졌으면 NULL).

-- ── ① 처리 상태 이력 ─────────────────────────────────────────────────────────
CREATE TABLE post_process_status_history (
    id                     bigint       PRIMARY KEY,
    post_id                bigint       NOT NULL,
    board_id               bigint       NOT NULL,
    from_status            varchar(16)  NOT NULL,
    to_status              varchar(16)  NOT NULL,
    note                   varchar(200),
    actor_kind             varchar(12)  NOT NULL,
    changed_by_user_id     bigint,
    changed_by_operator_id bigint       REFERENCES operators (id) ON DELETE SET NULL,
    changed_at             timestamptz  NOT NULL,
    CONSTRAINT ck_post_process_status_history_from CHECK (from_status IN ('RECEIVED', 'CHECKING', 'IN_PROGRESS', 'DONE', 'ON_HOLD', 'REJECTED', 'DUPLICATE')),
    CONSTRAINT ck_post_process_status_history_to CHECK (to_status IN ('RECEIVED', 'CHECKING', 'IN_PROGRESS', 'DONE', 'ON_HOLD', 'REJECTED', 'DUPLICATE')),
    CONSTRAINT ck_post_process_status_history_actor CHECK (actor_kind IN ('OPERATOR', 'BOARD_STAFF', 'GROUP_STAFF')),
    CONSTRAINT ck_post_process_status_history_note CHECK (note IS NULL OR (char_length(note) BETWEEN 1 AND 200 AND note = btrim(note)))
);
-- 글의 이력(최신순 50줄 · 마지막 메모) — 게시판 삭제·파기 배치도 post_id 로 찾는다
CREATE INDEX ix_post_process_status_history_post ON post_process_status_history (post_id, id DESC);

-- ── ② 운영 게시판 시드(이미 설치된 DB 에서만) ─────────────────────────────────
-- seed:begin (OpsBoardSeedTest 가 이 블록을 다시 불러 멱등을 본다 — 블록 밖으로 문장을 옮기지 않는다)
DO $seed$
DECLARE
    v_root     bigint;
    v_grade    varchar(19);
    v_base     bigint := ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12);
    v_seq      int := 0;
    v_order    int;
    v_board    bigint;
    v_spec     record;
    v_prefix   record;
    v_node     int := 0;
    v_now      timestamptz := now();
BEGIN
    SELECT o.id INTO v_root
      FROM installation i LEFT JOIN operators o ON o.id = i.root_operator_id
     WHERE i.id = 1 AND i.status = 'COMPLETED' AND i.board_preset_applied_at IS NOT NULL;
    IF NOT FOUND THEN
        RETURN; -- 설치 전·설치 프리셋을 아직 적용하지 않은 DB — 새 설치는 프리셋 적용이 만든다
    END IF;
    SELECT g.grade_key INTO v_grade FROM grades g ORDER BY g.level LIMIT 1;

    FOR v_spec IN
        SELECT * FROM (VALUES
            (1, 'bugs', '버그 신고', '사이트를 쓰다 생긴 오류를 알려 주세요',
             E'사이트를 쓰다 이상한 점을 알려 주세요. 운영자가 확인해 처리 상태(접수 → 확인 중 → 완료)와 댓글로 알려 드려요.\n계정·결제처럼 남에게 보이면 안 되는 내용은 「비밀글」로 써 주세요(글쓴이와 운영진만 봐요). 비밀번호는 비밀글에도 적지 마세요. 로그인이 안 되면 사이트 아래 문의 이메일로 알려 주세요.',
             E'■ 문제가 생긴 화면(주소):\n■ 재현 순서\n1.\n2.\n3.\n■ 기대한 결과:\n■ 실제 결과:\n■ 기기·브라우저: (예: 갤럭시 S24 · 크롬 / 윈도우 11 · 엣지)\n■ 발생 시각:\n※ 화면 캡처를 붙여 주시면 더 빨리 찾을 수 있어요.',
             'OPTIONAL', ARRAY['화면 오류', '기능 오류', '모바일', '결제·주문', '기타']),
            (2, 'requests', '기능 요청', '있으면 좋을 기능과 고쳤으면 하는 점을 제안해 주세요',
             E'있으면 좋을 기능이나 고쳤으면 하는 점을 제안해 주세요.\n비슷한 제안이 이미 있으면 추천과 댓글로 힘을 보태 주세요. 운영자가 검토해 처리 상태(접수 → 확인 중 → 진행 중 → 완료)로 알려 드려요.',
             E'■ 제안 내용:\n■ 필요한 이유(지금 불편한 점):\n■ 생각한 방식(있으면):\n■ 참고(다른 사이트·화면 캡처):',
             'OFF', ARRAY['새 기능', '개선', '디자인', '모바일', '기타'])
        ) AS s (n, slug, name, description, intro_text, write_template, secret_mode, prefixes)
        ORDER BY n
    LOOP
        CONTINUE WHEN EXISTS (SELECT 1 FROM boards b WHERE b.slug = v_spec.slug OR lower(b.name) = lower(v_spec.name));
        SELECT coalesce(max(b.sort_order), 0) + 1 INTO v_order FROM boards b;
        v_seq := v_seq + 1;
        v_board := v_base | v_seq;
        INSERT INTO boards (id, slug, name, name_en, description, kind, layout, view_toggle, color, allow_adult, investment_notice, active, sort_order,
                            write_lock, intro_text, write_guide_text, write_template, write_min, comment_min, image_upload_allowed, indexable, searchable,
                            point_mode, debate_enabled, poll_enabled, gadget_exclusions, ads_hidden, secret_mode, status_enabled,
                            created_at, created_by, updated_at, updated_by)
        VALUES (v_board, v_spec.slug, v_spec.name, NULL, v_spec.description, 'GENERAL', 'LIST', true, NULL, false, false, true, v_order,
                NULL, v_spec.intro_text, NULL, v_spec.write_template, v_grade, v_grade, true, false, true,
                'OFF', false, false, ARRAY['POPULAR', 'LATEST']::varchar[], true, v_spec.secret_mode, true,
                v_now, v_root, v_now, v_root);
        -- 공통 말머리 4개(BoardCreator 와 같은 차례·이름 — 질문·후기·잡담·공지[운영진 전용]) 중 「공지」만 켠다
        FOR v_prefix IN
            SELECT * FROM (VALUES (1, 'QUESTION', '질문'), (2, 'REVIEW', '후기'), (3, 'CHAT', '잡담'), (4, 'NOTICE', '공지')) AS c (ord, common_key, name)
            ORDER BY ord
        LOOP
            v_seq := v_seq + 1;
            INSERT INTO board_prefixes (id, board_id, common_key, name, staff_only, color, active, sort_order, created_at, updated_at)
            VALUES (v_base | v_seq, v_board, v_prefix.common_key, v_prefix.name, v_prefix.common_key = 'NOTICE', NULL, v_prefix.common_key = 'NOTICE',
                    v_prefix.ord, v_now, v_now);
        END LOOP;
        -- 고유 말머리 5개 — 차례 10부터(BoardCreator 와 같다)
        FOR v_prefix IN SELECT p.name, p.ord FROM unnest(v_spec.prefixes) WITH ORDINALITY AS p (name, ord) ORDER BY p.ord
        LOOP
            v_seq := v_seq + 1;
            INSERT INTO board_prefixes (id, board_id, common_key, name, staff_only, color, active, sort_order, created_at, updated_at)
            VALUES (v_base | v_seq, v_board, NULL, v_prefix.name, false, NULL, true, 9 + v_prefix.ord::int, v_now, v_now);
        END LOOP;
    END LOOP;

    -- 공개 메뉴 그룹 footer — 없을 때만, 항목은 지금 있는 두 게시판(주소 차례: 버그 신고 → 기능 요청)
    IF NOT EXISTS (SELECT 1 FROM menu_sets WHERE set_key = 'footer') THEN
        INSERT INTO menu_sets (set_key, name, name_en, placement, enabled, description, is_system, version, created_at, created_by, updated_at, updated_by)
        VALUES ('footer', '푸터 고객 지원', 'Footer support', 'CUSTOM', true, '사이트 아래 「고객 지원」 링크', false, 1, v_now, v_root, v_now, v_root);
        FOR v_spec IN
            SELECT b.id FROM (VALUES (1, 'bugs'), (2, 'requests')) AS s (n, slug) JOIN boards b ON b.slug = s.slug ORDER BY s.n
        LOOP
            v_seq := v_seq + 1;
            INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, label, name_en, visible, audience, min_grade, collapsible, collapsed, auto_add,
                                    target_type, target_id, target_key, settings, mobile_tab, divider, description)
            VALUES (v_base | v_seq, 'footer', NULL, 'ITEM', v_node, NULL, NULL, true, 'ALL', NULL, false, false, false,
                    'BOARD', v_spec.id::text, NULL, NULL, false, 'NONE', NULL);
            v_node := v_node + 1;
        END LOOP;
    END IF;
END
$seed$;
-- seed:end
