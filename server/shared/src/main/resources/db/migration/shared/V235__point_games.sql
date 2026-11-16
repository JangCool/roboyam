-- 포인트 게임(룰렛 · 로또 번호 뽑기 — docs/features/point-games-contract.md, 서버 구현 2026-11-16). 커머스 범위(docs/domain/commerce.md 5·7-1 「랜덤박스는 팔지 않는다」의 예외 — 판매가 아니고 적립 포인트·비환금 보상만).
--
--   · point_game_roulette_spins  룰렛 한 번(회원 · 맞힌 상품 키 · 쓴 적립 포인트 · 받은 적립 포인트 · 시각). 원장 사용 줄(ROULETTE_SPIN)·당첨 줄(ROULETTE_WIN)의 대상이 이 ID 다.
--                                하루 횟수(한국 시간 0시부터)는 회원 잔액 행 잠금 아래에서 이 표를 센다(같은 회원의 동시 요청이 줄을 선다).
--   · point_game_lotto_draws     뽑은 번호 한 세트(회원 · 작은 수부터 1~45 숫자 6개 · 방법 AUTO|SEMI_AUTO · 한 번에 뽑은 묶음 ID · 시각). 원장 사용 줄(LOTTO_DRAW)의 대상이 묶음 ID 다.
--                                한 회원 50세트까지(서비스가 잔액 행 잠금 아래에서 센다). 회원이 지우면 행을 지운다(쓴 포인트는 돌려주지 않는다).
--   · point_game_settings        설정(1행) — 당첨 확인 링크(복권 사업자 이름 1~30자 · https:// 주소 300자 이하, 기본 동행복권 https://dhlottery.co.kr).
-- 새 표는 users 에 외래 키를 걸지 않는다(V142·V163 과 같다 — 회원 행은 지우지 않고 익명화). 운영자는 ON DELETE SET NULL.
--
-- 넓히는 것(V163 이 마지막으로 다시 만든 CHECK 들의 합집합 — ⚠ 다른 세션이 같은 CHECK 를 다시 만들면 나중에 적용되는 쪽이 모든 값을 담아야 한다):
--   · 원장 사유 ck_point_ledger_reason = V163 목록 ∪ ROULETTE_SPIN · LOTTO_DRAW(USE — 적립 묶음에서만 뺀다) · ROULETTE_WIN(EARN — 새 무상 묶음)
--   · 사유·종류 짝 ck_point_ledger_reason_kind = V163 ∪ USE 의 ROULETTE_SPIN · LOTTO_DRAW(ELSE 갈래에서도 뺀다). ROULETTE_WIN 은 ELSE(EARN) 갈래
--   · 원장 대상 ck_point_ledger_ref · 묶음 출처 ck_point_lots_source_ref = V163 목록 ∪ ROULETTE_SPIN · LOTTO_DRAW
-- 아이로빗 홈: 오른쪽 「광고」(SQUARE_BANNER) 바로 아래에 「포인트 게임」 가젯(POINT_GAMES) — 코드 기본 배치 HomeLayoutDefaults.irobit() 와 같다(아래 DO 블록).

-- ── 룰렛 ──────────────────────────────────────────────────────────────────
CREATE TABLE point_game_roulette_spins (
    id         bigint      PRIMARY KEY,
    user_id    bigint      NOT NULL,
    prize_key  varchar(16) NOT NULL,
    cost       integer     NOT NULL,
    won        integer     NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_point_game_roulette_spins_amounts CHECK (cost > 0 AND won >= 0)
);
CREATE INDEX ix_point_game_roulette_spins_user ON point_game_roulette_spins (user_id, created_at);

-- ── 로또 번호 ─────────────────────────────────────────────────────────────
CREATE TABLE point_game_lotto_draws (
    id         bigint      PRIMARY KEY,
    user_id    bigint      NOT NULL,
    batch_id   bigint      NOT NULL,
    mode       varchar(16) NOT NULL,
    n1         smallint    NOT NULL,
    n2         smallint    NOT NULL,
    n3         smallint    NOT NULL,
    n4         smallint    NOT NULL,
    n5         smallint    NOT NULL,
    n6         smallint    NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_point_game_lotto_draws_mode CHECK (mode IN ('AUTO', 'SEMI_AUTO')),
    CONSTRAINT ck_point_game_lotto_draws_numbers CHECK (n1 >= 1 AND n1 < n2 AND n2 < n3 AND n3 < n4 AND n4 < n5 AND n5 < n6 AND n6 <= 45)
);
CREATE INDEX ix_point_game_lotto_draws_user ON point_game_lotto_draws (user_id, created_at DESC, id DESC);

-- ── 설정(1행) ─────────────────────────────────────────────────────────────
CREATE TABLE point_game_settings (
    id                    integer      PRIMARY KEY,
    lottery_provider_name varchar(30)  NOT NULL DEFAULT '동행복권',
    lottery_check_url     varchar(300) NOT NULL DEFAULT 'https://dhlottery.co.kr',
    updated_at            timestamptz,
    updated_by            bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_point_game_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_point_game_settings_check_url CHECK (lottery_check_url LIKE 'https://%')
);
INSERT INTO point_game_settings (id) VALUES (1);

-- ── 원장 사유 · 대상 ──────────────────────────────────────────────────────
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_reason;
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_reason CHECK (reason IN (
    'ATTENDANCE', 'ATTENDANCE_STREAK', 'POST', 'COMMENT', 'POPULAR', 'POST_REVOKE', 'COMMENT_REVOKE', 'POST_RESTORE', 'TICKET_PURCHASE', 'ADMIN_ADJUST',
    'DEAL_END_REQUEST', 'LIKE_BONUS', 'LIKE_BONUS_REVOKE', 'SIGNUP',
    'CHARGE', 'CHARGE_BONUS', 'CHARGE_BONUS_REVOKE', 'CHARGE_REFUND', 'CHARGE_REFUND_RESTORE', 'CHARGE_BONUS_RESTORE',
    'POINT_SHOP_PURCHASE', 'POINT_SHOP_REFUND', 'GB_POINT_USE', 'GB_POINT_RESTORE',
    'REVOKE_DEBT_OFFSET', 'EXPIRE', 'WITHDRAW_FORFEIT',
    'EMOTICON_CREATOR_REWARD',
    'EVENT_REWARD', 'EVENT_REWARD_REVOKE',
    'ROULETTE_SPIN', 'LOTTO_DRAW', 'ROULETTE_WIN'));
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_reason_kind;
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_reason_kind CHECK (CASE kind
    WHEN 'USE' THEN reason IN ('TICKET_PURCHASE', 'POINT_SHOP_PURCHASE', 'GB_POINT_USE', 'ROULETTE_SPIN', 'LOTTO_DRAW')
    WHEN 'CANCEL_USE' THEN reason IN ('POINT_SHOP_REFUND', 'GB_POINT_RESTORE')
    WHEN 'EXPIRE' THEN reason IN ('EXPIRE', 'WITHDRAW_FORFEIT')
    WHEN 'REFUND' THEN reason IN ('CHARGE_REFUND')
    WHEN 'REVOKE' THEN reason IN ('POST_REVOKE', 'COMMENT_REVOKE', 'LIKE_BONUS_REVOKE', 'CHARGE_BONUS_REVOKE', 'ADMIN_ADJUST', 'REVOKE_DEBT_OFFSET', 'EVENT_REWARD_REVOKE')
    ELSE reason NOT IN ('TICKET_PURCHASE', 'POINT_SHOP_PURCHASE', 'GB_POINT_USE', 'POINT_SHOP_REFUND', 'GB_POINT_RESTORE', 'EXPIRE', 'WITHDRAW_FORFEIT',
                        'CHARGE_REFUND', 'POST_REVOKE', 'COMMENT_REVOKE', 'LIKE_BONUS_REVOKE', 'CHARGE_BONUS_REVOKE', 'REVOKE_DEBT_OFFSET', 'EVENT_REWARD_REVOKE',
                        'ROULETTE_SPIN', 'LOTTO_DRAW')
END);
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_ref;
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_ref CHECK (ref_type IS NULL OR ref_type IN ('POST', 'COMMENT', 'POINT_SHOP_PURCHASE', 'POINT_CHARGE',
    'GB_PARTICIPATION', 'LOT', 'LEDGER', 'USER', 'ATTENDANCE', 'DEAL_END_REQUEST', 'EMOTICON_ACQUIRE', 'REWARD_EVENT_RESULT', 'ROULETTE_SPIN', 'LOTTO_DRAW'));
ALTER TABLE point_lots DROP CONSTRAINT ck_point_lots_source_ref;
ALTER TABLE point_lots ADD CONSTRAINT ck_point_lots_source_ref CHECK (source_ref_type IS NULL OR source_ref_type IN ('POST', 'COMMENT', 'POINT_SHOP_PURCHASE',
    'POINT_CHARGE', 'GB_PARTICIPATION', 'LOT', 'LEDGER', 'USER', 'ATTENDANCE', 'DEAL_END_REQUEST', 'EMOTICON_ACQUIRE', 'REWARD_EVENT_RESULT', 'ROULETTE_SPIN',
    'LOTTO_DRAW'));

-- ── 아이로빗 홈: 「포인트 게임」 가젯을 오른쪽 「광고」 바로 아래에 ───────────────
-- 대상: 설치가 끝난 DB 의 아이로빗 홈 배치(home_layouts — 운영자가 저장했거나 설치 프리셋이 넣은 것)에 오른쪽 열(x ≥ 18)의 정사각 배너 광고(AD · SQUARE_BANNER)가 있고
--   포인트 게임 가젯이 아직 없을 때만. 그 광고 아래 끝에 같은 x·폭 · 높이 6 으로 넣고, 그 아래에서 같은 열을 쓰는 가젯은 6 만큼 내린 뒤 관리자 저장과 같은 「위로 당겨 정리」
--   (읽는 차례 (y, x, ID) — 열이 겹치는 앞 가젯 아래 끝으로). 모바일 순서(mobile_order)가 저장돼 있으면 그 광고 바로 뒤에 넣는다(광고가 목록에 없으면 맨 뒤, NULL 이면 그대로 — PC 배치에서 계산).
--   배치를 저장한 적 없는 DB 는 코드 기본 배치(HomeLayoutDefaults.irobit())가 이미 같은 자리에 둔다. 새 설치는 설치 프리셋(irobit.structure.json)이 같은 자리에 둔다.
-- 서버는 이 마이그레이션 뒤에 뜨므로 배치·가젯 캐시는 비어 있다.
DO $v234$
DECLARE
    v_items  jsonb;
    v_order  jsonb;
    v_new    jsonb := '[]'::jsonb;
    v_out    jsonb := '[]'::jsonb;
    v_ord    jsonb := '[]'::jsonb;
    v_it     jsonb;
    v_e      jsonb;
    v_y      int;
    v_ad     text;
    v_ad_x   int;
    v_ad_w   int;
    v_ad_end int;
    v_h      int := 6;
    v_placed boolean := false;
    v_id     bigint;
    v_seq    bigint := 0;
    v_base   bigint := ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12);
    v_style  jsonb := '{"icon": "pi-gift", "empty": {"keep": true, "text": "", "message": true}, "iconBg": "SOFT", "cardLook": "DEFAULT", "iconShow": true, "subtitle": "", "cardColor": null, "iconColor": null, "titleSize": "M", "iconSource": "PRIME", "iconTabler": null, "iconUpload": null, "mobileFold": "NONE", "titleColor": null, "bottomSpace": "NORMAL", "headerStyle": "PLAIN"}'::jsonb;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM installation i WHERE i.id = 1 AND i.status = 'COMPLETED' AND i.board_preset_applied_at IS NOT NULL) THEN
        RETURN;
    END IF;
    SELECT l.items, l.mobile_order INTO v_items, v_order FROM home_layouts l WHERE l.theme = 'irobit';
    IF NOT FOUND OR jsonb_typeof(v_items) <> 'array' THEN
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId' WHERE g.type = 'POINT_GAMES') THEN
        RETURN;
    END IF;

    SELECT it ->> 'gadgetId', (it ->> 'x')::int, (it ->> 'w')::int, (it ->> 'y')::int + (it ->> 'h')::int INTO v_ad, v_ad_x, v_ad_w, v_ad_end
      FROM jsonb_array_elements(v_items) it JOIN home_gadgets g ON g.id::text = it ->> 'gadgetId'
     WHERE g.theme = 'irobit' AND g.page_id IS NULL AND NOT g.board_layout AND g.type = 'AD' AND g.config ->> 'adType' = 'SQUARE_BANNER'
       AND (it ->> 'x')::int >= 18
     ORDER BY (it ->> 'y')::int, (it ->> 'x')::int
     LIMIT 1;
    IF v_ad IS NULL THEN
        RETURN;
    END IF;

    LOOP
        v_seq := v_seq + 1;
        v_id := v_base | v_seq;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM home_gadgets WHERE id = v_id);
    END LOOP;
    INSERT INTO home_gadgets (id, theme, type, title, desktop, mobile, style, config, more, created_at)
    VALUES (v_id, 'irobit', 'POINT_GAMES', '포인트 게임', true, true, v_style, '{}'::jsonb, NULL, now());

    -- 광고 아래에서 같은 열을 쓰는 가젯은 새 가젯 높이만큼 내린다(차례 그대로)
    FOR v_it IN SELECT value FROM jsonb_array_elements(v_items)
    LOOP
        IF (v_it ->> 'y')::int >= v_ad_end AND (v_it ->> 'x')::int < v_ad_x + v_ad_w AND v_ad_x < (v_it ->> 'x')::int + (v_it ->> 'w')::int THEN
            v_it := jsonb_set(v_it, '{y}', to_jsonb((v_it ->> 'y')::int + v_h));
        END IF;
        v_new := v_new || jsonb_build_array(v_it);
    END LOOP;
    v_new := v_new || jsonb_build_array(jsonb_build_object('gadgetId', v_id::text, 'x', v_ad_x, 'y', v_ad_end, 'w', v_ad_w, 'h', v_h));

    -- 위로 당겨 정리
    FOR v_it IN SELECT value FROM jsonb_array_elements(v_new)
                 ORDER BY (value ->> 'y')::int, (value ->> 'x')::int, value ->> 'gadgetId'
    LOOP
        SELECT coalesce(max((p ->> 'y')::int + (p ->> 'h')::int), 0) INTO v_y
          FROM jsonb_array_elements(v_out) p
         WHERE (p ->> 'x')::int < (v_it ->> 'x')::int + (v_it ->> 'w')::int
           AND (v_it ->> 'x')::int < (p ->> 'x')::int + (p ->> 'w')::int;
        v_out := v_out || jsonb_build_array(jsonb_set(v_it, '{y}', to_jsonb(v_y)));
    END LOOP;

    -- 모바일 순서 — 광고 바로 뒤(광고가 없으면 맨 뒤), NULL 이면 그대로
    IF jsonb_typeof(v_order) = 'array' THEN
        FOR v_e IN SELECT t.value FROM jsonb_array_elements(v_order) WITH ORDINALITY AS t(value, ord) ORDER BY t.ord
        LOOP
            v_ord := v_ord || jsonb_build_array(v_e);
            IF v_e #>> '{}' = v_ad THEN
                v_ord := v_ord || jsonb_build_array(v_id::text);
                v_placed := true;
            END IF;
        END LOOP;
        IF NOT v_placed THEN
            v_ord := v_ord || jsonb_build_array(v_id::text);
        END IF;
        UPDATE home_layouts SET items = v_out, mobile_order = v_ord, updated_at = now() WHERE theme = 'irobit';
    ELSE
        UPDATE home_layouts SET items = v_out, updated_at = now() WHERE theme = 'irobit';
    END IF;
END
$v234$;
