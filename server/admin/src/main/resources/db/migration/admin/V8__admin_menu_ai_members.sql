-- 관리자 메뉴에 「AI 회원」 묶음(docs/features/ai-members-contract.md — 화면 키 aiMembers · aiMemberDrafts · aiMemberSettings · aiMemberLogs, 원천은 AdminScreenRegistry)을 넣는다.
--
-- 저장한 적 없는 설치(admin_menu_state.updated_at IS NULL — 행 없음)는 서버가 레지스트리 기본 구성을 주므로 아무 일도 하지 않는다.
-- 메뉴 구성을 저장한 설치는 관리자 셸이 저장 구성을 그리므로(2026-11-07 부터) 새 묶음이 저절로 보이지 않는다 — 그래서 일반(normal) 모드에
-- 묶음(SECTION, label_key menu.aiMember) + 화면 넷을 넣는다. 자리는 「소모임」 묶음(label_key menu.group) 바로 뒤, 그 묶음이 없으면 맨 끝. 뒤 묶음의 차례는 하나씩 민다.
-- 이미 그 화면 키가 하나라도 저장 구성에 있으면(운영자가 메뉴 설정에서 먼저 넣었다) 넣지 않는다. 구조가 바뀌므로 구성 판(version)을 올린다(열려 있던 편집 창은 409).
-- ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — shared 스트림 마이그레이션과 같은 마이그레이션용 노드) + 순번.
DO $$
DECLARE
    v_base    bigint := ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22) | (1023::bigint << 12);
    v_after   integer;
    v_section bigint;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM admin_menu_state WHERE id = 1 AND updated_at IS NOT NULL) THEN
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM admin_menu_nodes
                WHERE target_type = 'ADMIN_SCREEN' AND target_key IN ('aiMembers', 'aiMemberDrafts', 'aiMemberSettings', 'aiMemberLogs')) THEN
        RETURN;
    END IF;

    SELECT sort_order INTO v_after FROM admin_menu_nodes
     WHERE mode = 'normal' AND parent_id IS NULL AND kind = 'SECTION' AND label_key = 'menu.group'
     ORDER BY sort_order LIMIT 1;
    IF v_after IS NULL THEN
        SELECT coalesce(max(sort_order), -1) INTO v_after FROM admin_menu_nodes WHERE mode = 'normal' AND parent_id IS NULL;
    END IF;

    UPDATE admin_menu_nodes SET sort_order = sort_order + 1
     WHERE mode = 'normal' AND parent_id IS NULL AND sort_order > v_after;

    v_section := v_base;
    INSERT INTO admin_menu_nodes (id, mode, parent_id, kind, sort_order, label_key, icon, visible)
    VALUES (v_section, 'normal', NULL, 'SECTION', v_after + 1, 'menu.aiMember', 'pi pi-microchip-ai', true);

    INSERT INTO admin_menu_nodes (id, mode, parent_id, kind, sort_order, icon, visible, target_type, target_key, new_tab)
    VALUES (v_base + 1, 'normal', v_section, 'SCREEN', 0, 'pi pi-users', true, 'ADMIN_SCREEN', 'aiMembers', true),
           (v_base + 2, 'normal', v_section, 'SCREEN', 1, 'pi pi-inbox', true, 'ADMIN_SCREEN', 'aiMemberDrafts', true),
           (v_base + 3, 'normal', v_section, 'SCREEN', 2, 'pi pi-cog', true, 'ADMIN_SCREEN', 'aiMemberSettings', true),
           (v_base + 4, 'normal', v_section, 'SCREEN', 3, 'pi pi-history', true, 'ADMIN_SCREEN', 'aiMemberLogs', true);

    UPDATE admin_menu_state SET version = version + 1 WHERE id = 1;
END $$;
