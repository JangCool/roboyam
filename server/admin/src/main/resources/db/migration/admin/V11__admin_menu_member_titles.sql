-- 관리자 메뉴 「회원」 묶음에 「회원 칭호」(화면 키 memberTitles, /members/titles, title.read — docs/features/member-title-contract.md 「화면」, 원천은 AdminScreenRegistry)를 넣는다.
-- 자리는 「등급」(grades) 바로 아래.
--
-- 저장한 적 없는 설치(admin_menu_state.updated_at IS NULL)는 서버가 레지스트리 기본 구성을 주므로 아무 일도 하지 않는다.
-- 메뉴 구성을 저장한 설치: 저장 구성의 「등급」 화면 바로 뒤(같은 부모·같은 모드)에 넣고 뒤 형제의 차례를 하나씩 민다. 「등급」이 저장 구성에 없으면(운영자가 뺐다) 넣지 않는다.
-- 이미 memberTitles 가 저장 구성에 있으면 넣지 않는다. 구조가 바뀌므로 구성 판(version)을 올린다(열려 있던 편집 창은 409).
-- ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — 마이그레이션용 노드). 아이콘은 admin-web util-admin-menu 와 같은 pi pi-star.
DO $$
DECLARE
    v_id     bigint := ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22) | (1023::bigint << 12);
    v_mode   varchar(10);
    v_parent bigint;
    v_order  integer;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM admin_menu_state WHERE id = 1 AND updated_at IS NOT NULL) THEN
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM admin_menu_nodes WHERE target_type = 'ADMIN_SCREEN' AND target_key = 'memberTitles') THEN
        RETURN;
    END IF;

    SELECT mode, parent_id, sort_order INTO v_mode, v_parent, v_order
      FROM admin_menu_nodes
     WHERE kind = 'SCREEN' AND target_type = 'ADMIN_SCREEN' AND target_key = 'grades'
     ORDER BY mode, sort_order LIMIT 1;
    IF v_mode IS NULL THEN
        RETURN;
    END IF;

    UPDATE admin_menu_nodes SET sort_order = sort_order + 1
     WHERE mode = v_mode AND parent_id IS NOT DISTINCT FROM v_parent AND sort_order > v_order;

    INSERT INTO admin_menu_nodes (id, mode, parent_id, kind, sort_order, icon, visible, target_type, target_key, new_tab)
    VALUES (v_id, v_mode, v_parent, 'SCREEN', v_order + 1, 'pi pi-star', true, 'ADMIN_SCREEN', 'memberTitles', true);

    UPDATE admin_menu_state SET version = version + 1 WHERE id = 1;
END $$;
