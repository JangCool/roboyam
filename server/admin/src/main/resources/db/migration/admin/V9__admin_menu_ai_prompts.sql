-- 관리자 메뉴 「AI 회원」 묶음에 「프롬프트」(화면 키 aiMemberPrompts · /ai-members/prompts — docs/features/ai-members-contract.md 「뉴스 글·프롬프트 관리 (2026-11-11)」,
-- 원천은 AdminScreenRegistry)를 넣는다. ⚠ 가번호 V9 — 합치기 직전에 admin 스트림의 마지막 번호를 다시 본다.
--
-- 저장한 적 없는 설치(admin_menu_state.updated_at IS NULL)는 서버가 레지스트리 기본 구성을 주므로 아무 일도 하지 않는다.
-- 메뉴 구성을 저장한 설치: 저장 구성 안 「AI 회원 설정」(aiMemberSettings) 항목 바로 뒤(같은 부모)에 넣고 뒤 형제의 차례를 하나씩 민다.
-- 그 항목이 없으면 「AI 회원」 묶음(SECTION, label_key menu.aiMember) 맨 끝, 묶음도 없으면(운영자가 뺐다) 넣지 않는다.
-- 이미 그 화면 키가 저장 구성에 있으면 넣지 않는다. 구조가 바뀌므로 구성 판(version)을 올린다(열려 있던 편집 창은 409).
-- ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — V8 과 같은 마이그레이션용 노드) + 순번.
DO $$
DECLARE
    v_id      bigint := ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22) | (1023::bigint << 12);
    v_parent  bigint;
    v_after   integer;
    v_new_tab boolean := true;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM admin_menu_state WHERE id = 1 AND updated_at IS NOT NULL) THEN
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM admin_menu_nodes WHERE target_type = 'ADMIN_SCREEN' AND target_key = 'aiMemberPrompts') THEN
        RETURN;
    END IF;

    SELECT parent_id, sort_order, new_tab INTO v_parent, v_after, v_new_tab
      FROM admin_menu_nodes
     WHERE mode = 'normal' AND kind = 'SCREEN' AND target_type = 'ADMIN_SCREEN' AND target_key = 'aiMemberSettings' AND parent_id IS NOT NULL
     ORDER BY sort_order LIMIT 1;

    IF v_parent IS NULL THEN
        SELECT id INTO v_parent FROM admin_menu_nodes
         WHERE mode = 'normal' AND parent_id IS NULL AND kind = 'SECTION' AND label_key = 'menu.aiMember'
         ORDER BY sort_order LIMIT 1;
        IF v_parent IS NULL THEN
            RETURN;
        END IF;
        SELECT coalesce(max(sort_order), -1) INTO v_after FROM admin_menu_nodes WHERE parent_id = v_parent;
        v_new_tab := true;
    END IF;

    UPDATE admin_menu_nodes SET sort_order = sort_order + 1 WHERE parent_id = v_parent AND sort_order > v_after;

    INSERT INTO admin_menu_nodes (id, mode, parent_id, kind, sort_order, icon, visible, target_type, target_key, new_tab)
    VALUES (v_id, 'normal', v_parent, 'SCREEN', v_after + 1, 'pi pi-pen-to-square', true, 'ADMIN_SCREEN', 'aiMemberPrompts', coalesce(v_new_tab, true));

    UPDATE admin_menu_state SET version = version + 1 WHERE id = 1;
END $$;
