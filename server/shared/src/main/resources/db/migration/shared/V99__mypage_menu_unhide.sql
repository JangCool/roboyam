-- 마이페이지 메뉴 정리 — docs/features/install-contract.md 「프리셋 구조 (2026-10-27)」 3·4 (모든 설치 — 새 DB 와 이미 설치된 곳 모두)
--
-- 1) V49 가 「화면 없음」으로 숨긴 mypage 그룹 항목 중 지금 user-web 에 화면이 있는 것을 다시 보이게 한다:
--    MY_HOME · MY_GRADE · MY_POINTS · MESSAGES · NOTIFICATIONS · SETTINGS_PROFILE · SETTINGS_ACCOUNT · SETTINGS_VIEW · SETTINGS_BLOCKS · NOTIFICATION_SETTINGS.
--    예약 글(MY_SCHEDULED — /scheduled 화면 없음)과 group-buy 그룹은 숨긴 채 둔다. 숨겨진 것만 바꾼다(운영자가 다시 숨긴 것도 켜지지만, 이 목록은 V49 가 숨긴 것과 같다).
-- 2) 포인트 상점(BUILTIN MY_SHOP, 보이는 대상 MEMBER — 로그인 필요 화면)을 내 포인트(MY_POINTS) 바로 다음 차례에 넣는다.
--    같은 부모의 뒤 형제 순서를 1 씩 밀고 그 자리에 넣는다. MY_POINTS 가 없으면(운영자가 지웠으면) 최상위 끝에 넣는다.
--    이미 MY_SHOP 항목이 있거나 그룹 노드가 한도(80)면 넣지 않는다. 노드 ID 는 V43 과 같은 Snowflake 모양(EPOCH 2026-01-01 UTC, 마이그레이션 전용 서버 번호 1023).
-- 3) 바뀐 것이 있으면 mypage 그룹의 트리 버전을 1 올린다(관리자 저장의 MENU_VERSION_CONFLICT 판정 — 열려 있던 편집 화면이 옛 트리로 덮어쓰지 않게).
-- 참고: user-web 은 아직 mypage 메뉴 그룹을 읽지 않는다(헤더 드롭다운·서랍·/me 의 고정 링크). 관리자 메뉴 화면과 이후 연결을 위한 정리다.

DO $$
DECLARE
    v_changed boolean := false;
    v_rows    int;
    v_points  record;
    v_id      bigint;
    v_order   int;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM menu_sets WHERE set_key = 'mypage') THEN
        RETURN;
    END IF;

    -- 1) 숨김 풀기
    UPDATE menu_nodes
       SET visible = true
     WHERE set_key = 'mypage'
       AND kind = 'ITEM'
       AND target_type = 'BUILTIN'
       AND target_key IN ('MY_HOME', 'MY_GRADE', 'MY_POINTS', 'MESSAGES', 'NOTIFICATIONS',
                          'SETTINGS_PROFILE', 'SETTINGS_ACCOUNT', 'SETTINGS_VIEW', 'SETTINGS_BLOCKS', 'NOTIFICATION_SETTINGS')
       AND NOT visible;
    GET DIAGNOSTICS v_rows = ROW_COUNT;
    v_changed := v_rows > 0;

    -- 2) 포인트 상점
    IF NOT EXISTS (SELECT 1 FROM menu_nodes WHERE set_key = 'mypage' AND target_type = 'BUILTIN' AND target_key = 'MY_SHOP')
       AND (SELECT count(*) FROM menu_nodes WHERE set_key = 'mypage') < 80 THEN
        v_id := ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22) | (1023::bigint << 12);
        WHILE EXISTS (SELECT 1 FROM menu_nodes WHERE id = v_id) LOOP
            v_id := v_id + 1; -- 같은 밀리초에 다른 마이그레이션이 같은 서버 번호로 만든 노드와 겹치지 않게
        END LOOP;

        SELECT id, parent_id, sort_order INTO v_points
          FROM menu_nodes
         WHERE set_key = 'mypage' AND kind = 'ITEM' AND target_type = 'BUILTIN' AND target_key = 'MY_POINTS'
         ORDER BY id
         LIMIT 1;

        IF FOUND THEN
            UPDATE menu_nodes
               SET sort_order = sort_order + 1
             WHERE set_key = 'mypage'
               AND parent_id IS NOT DISTINCT FROM v_points.parent_id
               AND sort_order > v_points.sort_order;
            INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, audience, target_type, target_key)
            VALUES (v_id, 'mypage', v_points.parent_id, 'ITEM', v_points.sort_order + 1, 'MEMBER', 'BUILTIN', 'MY_SHOP');
        ELSE
            SELECT coalesce(max(sort_order) + 1, 0) INTO v_order FROM menu_nodes WHERE set_key = 'mypage' AND parent_id IS NULL;
            INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, audience, target_type, target_key)
            VALUES (v_id, 'mypage', NULL, 'ITEM', v_order, 'MEMBER', 'BUILTIN', 'MY_SHOP');
        END IF;
        v_changed := true;
    END IF;

    -- 3) 버전
    IF v_changed THEN
        UPDATE menu_sets SET version = version + 1, updated_at = now() WHERE set_key = 'mypage';
    END IF;
END
$$;
