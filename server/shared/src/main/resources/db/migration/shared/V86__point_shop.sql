-- 커머스 1단계 1-2 포인트 상점 — docs/features/commerce-stage1-contract.md 「B. 포인트 상점」·「1-2 화면 목업」·「1-2 서버 계획」, docs/domain/commerce.md 7절.
-- ⚠ 합칠 때 번호 재확인(가번호 V86) — develop 에 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다(계약 「마이그레이션 계획」).
--
-- 무엇이 생기나
--   · 품목(point_shop_items): 닉네임 변경권(소모형)·닉네임 색·프로필 테두리(영구·기간제)·프로필 아바타(영구) — 종류별 칸 조합은 CHECK. 코드·종류·아바타 ID 는 만든 뒤 바꾸지 않는다(트리거).
--   · 구매(point_shop_purchases): 산 때의 이름·코드·종류·가격·기간 사본 + 상태(완료·환불) — 사본 칸은 고치지 않는다(트리거). 환불은 COMPLETED → REFUNDED 한 번만.
--   · 보유(user_items): 구매 하나 = 줄 하나. 만료는 저장하지 않고 expires_at 으로 계산(결정 6). first_used_at(처음 장착·사용·프로필로 고른 시각 — 환불 판정)은 지우지 않는다(트리거).
--     영구 아이템은 (회원, 품목) 에 회수되지 않은 줄 하나만(부분 유일 색인 — 동시에 두 번 사도 한 줄).
--   · 장착(user_equipped): (회원, 자리) 하나에 보유 줄 하나. 표시 때 표 하나만 붙이려고 품목 번호·소멸일 사본을 둔다(결정 8). 기간이 지나면 읽을 때 해제로 본다.
--   · 설정(point_shop_settings, 1행): 셀프 환불 기한 refund_days(7, 1~30) — 이미 산 구매도 지금 값으로 판정한다(화면 확정 「환불 기한 바꾸기」).
--   · 시드: 닉네임 변경권 품목 하나(가격 = 지금 point_settings.nickname_ticket_price — 그 뒤 칸을 지운다, 결정 2) · 유료 아바타 306종(결정 14).
--   · 0-5 이름 바꾸기(결정 「이름」): 요청 제한 SHOP_PURCHASE → POINT_SHOP_PURCHASE, 권한 shop.* → pointshop.*(역할에 준 것도 옮긴다).
-- 새 표는 users 에 외래키를 걸지 않는다(회원 행은 지우지 않고 익명화 — 결정 5, commerce.md 17-2).
-- 메뉴 내장 화면(MY_SHOP)의 주소는 DB 에 없다(menu_nodes 는 target_key 만 — 주소는 코드 MenuBuiltin) — 마이그레이션이 바꿀 것이 없다.

-- ── 설정(1행) ────────────────────────────────────────────────────────────────
CREATE TABLE point_shop_settings (
    id          integer     PRIMARY KEY,
    refund_days integer     NOT NULL DEFAULT 7,
    updated_at  timestamptz,
    updated_by  bigint      REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_point_shop_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_point_shop_settings_refund_days CHECK (refund_days BETWEEN 1 AND 30)
);
INSERT INTO point_shop_settings (id) VALUES (1);

-- ── 품목 ─────────────────────────────────────────────────────────────────────
-- kind: NICKNAME_CHANGE_TICKET(소모형만) · NICKNAME_COLOR·PROFILE_BORDER(영구·기간제) · PROFILE_AVATAR(영구만, 재고·판매 기간 없음 — 관리자는 만들거나 지우지 못한다).
-- color 는 닉네임 색만(#RRGGBB 원색 — 대비 보정은 그릴 때), border_style 은 테두리만(팔레트 키 — 화면 UtilPointShop.BORDERS 와 같은 목록), avatar_id 는 아바타만(카탈로그 ID, code = 'avatar-' || avatar_id).
-- stock: null = 무제한(구매 때 조건부 UPDATE 로 1 씩 뺀다). sale_start_at·sale_end_at: null = 그쪽 제한 없음.
CREATE TABLE point_shop_items (
    id             bigint       PRIMARY KEY,
    code           varchar(40)  NOT NULL,
    kind           varchar(30)  NOT NULL,
    usage_type     varchar(12)  NOT NULL,
    name           varchar(40)  NOT NULL,
    name_en        varchar(40),
    description    varchar(500) NOT NULL,
    description_en varchar(500),
    color          varchar(7),
    border_style   varchar(12),
    avatar_id      varchar(60),
    price_point    integer      NOT NULL,
    duration_days  integer,
    stock          integer,
    sale_start_at  timestamptz,
    sale_end_at    timestamptz,
    active         boolean      NOT NULL DEFAULT true,
    sort_order     integer      NOT NULL DEFAULT 0,
    created_by     bigint       REFERENCES operators (id) ON DELETE SET NULL,
    updated_by     bigint       REFERENCES operators (id) ON DELETE SET NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_point_shop_items_code CHECK (code ~ '^[a-z0-9-]{3,40}$'),
    CONSTRAINT ck_point_shop_items_kind CHECK (kind IN ('NICKNAME_CHANGE_TICKET', 'NICKNAME_COLOR', 'PROFILE_BORDER', 'PROFILE_AVATAR')),
    CONSTRAINT ck_point_shop_items_usage CHECK (usage_type IN ('PERMANENT', 'CONSUMABLE', 'PERIOD')),
    CONSTRAINT ck_point_shop_items_kind_usage CHECK (
        (kind = 'NICKNAME_CHANGE_TICKET' AND usage_type = 'CONSUMABLE')
        OR (kind IN ('NICKNAME_COLOR', 'PROFILE_BORDER') AND usage_type IN ('PERMANENT', 'PERIOD'))
        OR (kind = 'PROFILE_AVATAR' AND usage_type = 'PERMANENT')),
    -- 종류별 칸 조합: 색은 닉네임 색에만·반드시, 테두리는 테두리에만·반드시, 아바타 ID 는 아바타에만·반드시
    CONSTRAINT ck_point_shop_items_kind_fields CHECK (
        (kind = 'NICKNAME_COLOR') = (color IS NOT NULL)
        AND (kind = 'PROFILE_BORDER') = (border_style IS NOT NULL)
        AND (kind = 'PROFILE_AVATAR') = (avatar_id IS NOT NULL)),
    CONSTRAINT ck_point_shop_items_color CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_point_shop_items_border CHECK (border_style IS NULL OR border_style IN ('GOLD', 'SILVER', 'ROSE', 'OCEAN', 'FOREST', 'NIGHT', 'RAINBOW')),
    CONSTRAINT ck_point_shop_items_avatar CHECK (avatar_id IS NULL OR (avatar_id ~ '^[a-z0-9-]{1,33}$' AND code = 'avatar-' || avatar_id)),
    CONSTRAINT ck_point_shop_items_avatar_plain CHECK (kind <> 'PROFILE_AVATAR' OR (stock IS NULL AND sale_start_at IS NULL AND sale_end_at IS NULL)),
    CONSTRAINT ck_point_shop_items_price CHECK (price_point BETWEEN 1 AND 100000),
    CONSTRAINT ck_point_shop_items_duration CHECK ((usage_type = 'PERIOD') = (duration_days IS NOT NULL) AND (duration_days IS NULL OR duration_days BETWEEN 1 AND 365)),
    CONSTRAINT ck_point_shop_items_stock CHECK (stock IS NULL OR stock >= 0),
    CONSTRAINT ck_point_shop_items_sale_period CHECK (sale_start_at IS NULL OR sale_end_at IS NULL OR sale_end_at > sale_start_at),
    CONSTRAINT ck_point_shop_items_sort CHECK (sort_order >= 0)
);
CREATE UNIQUE INDEX ux_point_shop_items_code ON point_shop_items (code);
CREATE UNIQUE INDEX ux_point_shop_items_avatar ON point_shop_items (avatar_id) WHERE avatar_id IS NOT NULL;
-- 목록(종류 거르기 · 순서 → ID)
CREATE INDEX ix_point_shop_items_kind ON point_shop_items (kind, sort_order, id);

-- 코드·종류·아바타 ID 는 만든 뒤 바꾸지 않는다(구매 사본·카탈로그와 이어진 값). 아바타 품목은 지우지 못한다(카탈로그의 유료 아바타마다 하나 — 결정 14).
CREATE FUNCTION point_shop_items_identity_fixed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.kind = 'PROFILE_AVATAR' THEN
            RAISE EXCEPTION 'point_shop_items avatar items cannot be deleted';
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.code IS DISTINCT FROM OLD.code OR NEW.kind IS DISTINCT FROM OLD.kind OR NEW.avatar_id IS DISTINCT FROM OLD.avatar_id THEN
        RAISE EXCEPTION 'point_shop_items code, kind and avatar_id are fixed';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tr_point_shop_items_identity_fixed BEFORE UPDATE OR DELETE ON point_shop_items FOR EACH ROW EXECUTE FUNCTION point_shop_items_identity_fixed();

-- ── 구매 ─────────────────────────────────────────────────────────────────────
-- 품목 칸은 산 때의 사본(운영자가 가격·이름을 바꿔도 그대로 — 7-1). 가격은 서버가 품목에서 읽은 값이다. use_ledger_id = 포인트 사용 줄(원장 ref = POINT_SHOP_PURCHASE + 이 ID).
-- 환불: refund_ledger_id = 사용 취소 줄(POINT_SHOP_REFUND). 예외 환불(운영자 pointshop.refund)이면 처리 운영자·사유가 함께 있다(셀프 환불은 둘 다 없다).
-- refunded_by_operator_id 는 운영자 외래키를 걸지 않는다 — 돈 기록이라 운영자 계정이 지워져도 누가 했는지 번호를 남긴다(이름은 감사 기록에).
CREATE TABLE point_shop_purchases (
    id                      bigint       PRIMARY KEY,
    user_id                 bigint       NOT NULL,
    item_id                 bigint       NOT NULL REFERENCES point_shop_items (id),
    item_code               varchar(40)  NOT NULL,
    item_name               varchar(40)  NOT NULL,
    item_kind               varchar(30)  NOT NULL,
    usage_type              varchar(12)  NOT NULL,
    price_point             integer      NOT NULL,
    duration_days           integer,
    status                  varchar(10)  NOT NULL DEFAULT 'COMPLETED',
    refund_notice_agreed_at timestamptz  NOT NULL,
    use_ledger_id           bigint       NOT NULL,
    refund_ledger_id        bigint,
    refunded_at             timestamptz,
    refunded_by_operator_id bigint,
    refund_reason           varchar(200),
    created_at              timestamptz  NOT NULL,
    CONSTRAINT ck_point_shop_purchases_status CHECK (status IN ('COMPLETED', 'REFUNDED')),
    CONSTRAINT ck_point_shop_purchases_refunded CHECK ((status = 'REFUNDED') = (refunded_at IS NOT NULL) AND (status = 'REFUNDED') = (refund_ledger_id IS NOT NULL)),
    CONSTRAINT ck_point_shop_purchases_exception CHECK ((refunded_by_operator_id IS NULL) = (refund_reason IS NULL) AND (refund_reason IS NULL OR status = 'REFUNDED')),
    CONSTRAINT ck_point_shop_purchases_price CHECK (price_point BETWEEN 1 AND 100000),
    CONSTRAINT ck_point_shop_purchases_duration CHECK ((usage_type = 'PERIOD') = (duration_days IS NOT NULL))
);
CREATE INDEX ix_point_shop_purchases_user ON point_shop_purchases (user_id, id DESC);
CREATE INDEX ix_point_shop_purchases_item ON point_shop_purchases (item_id);
CREATE UNIQUE INDEX ux_point_shop_purchases_use_ledger ON point_shop_purchases (use_ledger_id);

-- 사본·정체 칸은 고치지 않는다. 상태는 COMPLETED → REFUNDED 한 번만(환불 칸은 그때 한 번 채운다).
CREATE FUNCTION point_shop_purchases_snapshot_fixed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.user_id IS DISTINCT FROM OLD.user_id OR NEW.item_id IS DISTINCT FROM OLD.item_id OR NEW.item_code IS DISTINCT FROM OLD.item_code
       OR NEW.item_name IS DISTINCT FROM OLD.item_name OR NEW.item_kind IS DISTINCT FROM OLD.item_kind OR NEW.usage_type IS DISTINCT FROM OLD.usage_type
       OR NEW.price_point IS DISTINCT FROM OLD.price_point OR NEW.duration_days IS DISTINCT FROM OLD.duration_days
       OR NEW.refund_notice_agreed_at IS DISTINCT FROM OLD.refund_notice_agreed_at OR NEW.use_ledger_id IS DISTINCT FROM OLD.use_ledger_id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'point_shop_purchases snapshot is fixed';
    END IF;
    IF OLD.status = 'REFUNDED' AND (NEW.status IS DISTINCT FROM OLD.status OR NEW.refund_ledger_id IS DISTINCT FROM OLD.refund_ledger_id
       OR NEW.refunded_at IS DISTINCT FROM OLD.refunded_at OR NEW.refunded_by_operator_id IS DISTINCT FROM OLD.refunded_by_operator_id
       OR NEW.refund_reason IS DISTINCT FROM OLD.refund_reason) THEN
        RAISE EXCEPTION 'point_shop_purchases refund is final';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tr_point_shop_purchases_snapshot_fixed BEFORE UPDATE ON point_shop_purchases FOR EACH ROW EXECUTE FUNCTION point_shop_purchases_snapshot_fixed();

-- ── 보유 ─────────────────────────────────────────────────────────────────────
-- status: OWNED(가짐) · USED(소모형을 씀) · REVOKED(환불·예외 환불로 회수). 만료는 저장하지 않는다(expires_at ≤ 지금이면 만료 — 결정 6).
-- starts_at: 기간제를 기간 중에 또 사면 앞 줄의 끝(7-1), 그 밖은 산 시각. expires_at: 기간제만.
-- first_used_at: 처음 장착(색·테두리)·사용(변경권)·프로필로 고른(아바타) 시각 — 있으면 셀프 환불 불가, 지우지 않는다. used_at: 소모형을 쓴 시각.
CREATE TABLE user_items (
    id            bigint      PRIMARY KEY,
    user_id       bigint      NOT NULL,
    item_id       bigint      NOT NULL REFERENCES point_shop_items (id),
    purchase_id   bigint      NOT NULL REFERENCES point_shop_purchases (id),
    usage_type    varchar(12) NOT NULL,
    status        varchar(10) NOT NULL DEFAULT 'OWNED',
    starts_at     timestamptz NOT NULL,
    expires_at    timestamptz,
    first_used_at timestamptz,
    used_at       timestamptz,
    revoked_at    timestamptz,
    created_at    timestamptz NOT NULL,
    CONSTRAINT ck_user_items_status CHECK (status IN ('OWNED', 'USED', 'REVOKED')),
    CONSTRAINT ck_user_items_usage CHECK (usage_type IN ('PERMANENT', 'CONSUMABLE', 'PERIOD')),
    CONSTRAINT ck_user_items_period CHECK ((usage_type = 'PERIOD') = (expires_at IS NOT NULL) AND (expires_at IS NULL OR expires_at > starts_at)),
    -- 씀(USED)이면 쓴 시각이 있고, 가짐(OWNED)이면 없다(회수된 줄은 쓴 뒤 회수됐을 수 있다). 쓴 시각은 소모형에만, 처음 사용 시각과 함께
    CONSTRAINT ck_user_items_used CHECK ((status <> 'USED' OR used_at IS NOT NULL) AND (status <> 'OWNED' OR used_at IS NULL)
                                         AND (used_at IS NULL OR (usage_type = 'CONSUMABLE' AND first_used_at IS NOT NULL))),
    CONSTRAINT ck_user_items_revoked CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_user_items_purchase ON user_items (purchase_id);
-- 영구 아이템은 회원·품목마다 회수되지 않은 줄 하나만 — 동시에 두 번 사도 한 줄(서비스가 먼저 막고 이 색인이 끝까지 막는다)
CREATE UNIQUE INDEX ux_user_items_permanent ON user_items (user_id, item_id) WHERE usage_type = 'PERMANENT' AND status <> 'REVOKED';
CREATE INDEX ix_user_items_user ON user_items (user_id, item_id, id);

-- 정체·기간 칸은 고치지 않는다. first_used_at 은 한 번 채우면 그대로(지우지 않는다). 상태는 OWNED → USED·REVOKED, USED → REVOKED 만.
CREATE FUNCTION user_items_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.user_id IS DISTINCT FROM OLD.user_id OR NEW.item_id IS DISTINCT FROM OLD.item_id OR NEW.purchase_id IS DISTINCT FROM OLD.purchase_id
       OR NEW.usage_type IS DISTINCT FROM OLD.usage_type OR NEW.starts_at IS DISTINCT FROM OLD.starts_at OR NEW.expires_at IS DISTINCT FROM OLD.expires_at
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'user_items identity is fixed';
    END IF;
    IF OLD.first_used_at IS NOT NULL AND NEW.first_used_at IS DISTINCT FROM OLD.first_used_at THEN
        RAISE EXCEPTION 'user_items first_used_at is kept';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status AND NOT ((OLD.status = 'OWNED' AND NEW.status IN ('USED', 'REVOKED')) OR (OLD.status = 'USED' AND NEW.status = 'REVOKED')) THEN
        RAISE EXCEPTION 'user_items status cannot go from % to %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tr_user_items_guard BEFORE UPDATE ON user_items FOR EACH ROW EXECUTE FUNCTION user_items_guard();

-- ── 장착 ─────────────────────────────────────────────────────────────────────
-- 자리마다 하나(PK). item_id·expires_at 은 그 보유 줄의 사본(결정 8 — 글·댓글 작성자 표시가 이 표 + 품목 하나만 붙인다). expires_at 이 지나면 읽을 때 해제로 본다.
CREATE TABLE user_equipped (
    user_id      bigint      NOT NULL,
    slot         varchar(20) NOT NULL,
    user_item_id bigint      NOT NULL REFERENCES user_items (id) ON DELETE CASCADE,
    item_id      bigint      NOT NULL REFERENCES point_shop_items (id),
    expires_at   timestamptz,
    equipped_at  timestamptz NOT NULL,
    PRIMARY KEY (user_id, slot),
    CONSTRAINT ck_user_equipped_slot CHECK (slot IN ('NICKNAME_COLOR', 'PROFILE_BORDER'))
);
CREATE UNIQUE INDEX ux_user_equipped_user_item ON user_equipped (user_item_id);

-- ── 시드 ─────────────────────────────────────────────────────────────────────
-- ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — V30·V53·V54 와 같은 마이그레이션용 노드) + 순번: 변경권 0, 아바타 1..306(카탈로그 순서).
-- 아바타 306종은 avatars/manifest.json(community 와 같은 328종)에서 무료 카테고리(basic·hobby)를 뺀 것 — 스크립트로 만든 값이고, 시험이 「카탈로그의 유료 ID 집합 = 아바타 품목」을 지킨다.
DO $$
DECLARE
    v_base  bigint := ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22) | (1023::bigint << 12);
    v_price integer;
BEGIN
    SELECT nickname_ticket_price INTO v_price FROM point_settings WHERE id = 1;
    INSERT INTO point_shop_items (id, code, kind, usage_type, name, name_en, description, description_en, price_point, sort_order)
    VALUES (v_base, 'nickname-change', 'NICKNAME_CHANGE_TICKET', 'CONSUMABLE', '닉네임 변경권', 'Nickname change ticket',
            '가입 뒤 1번의 무료 변경을 쓴 다음, 닉네임을 한 번 더 바꿀 수 있어요.', 'Change your nickname once more after using your one free change.',
            coalesce(v_price, 1000), 0);

    INSERT INTO point_shop_items (id, code, kind, usage_type, name, description, description_en, avatar_id, price_point, sort_order)
    SELECT v_base + a.seq, 'avatar-' || a.avatar_id, 'PROFILE_AVATAR', 'PERMANENT', a.name, '프로필 동그라미에 이 아바타를 보여요.', 'Shows this avatar in your profile circle.',
           a.avatar_id, 1000, a.seq
      FROM (VALUES
        (1, 'job-doctor-m', '의사 남'),
        (2, 'job-doctor-f', '의사 여'),
        (3, 'job-nurse-m', '간호사 남'),
        (4, 'job-nurse-f', '간호사 여'),
        (5, 'job-firefighter-m', '소방관 남'),
        (6, 'job-firefighter-f', '소방관 여'),
        (7, 'job-police-m', '경찰 남'),
        (8, 'job-police-f', '경찰 여'),
        (9, 'job-construction-m', '건설 남'),
        (10, 'job-construction-f', '건설 여'),
        (11, 'job-excavator-m', '포크레인 남'),
        (12, 'job-excavator-f', '포크레인 여'),
        (13, 'job-mixer-m', '레미콘 남'),
        (14, 'job-mixer-f', '레미콘 여'),
        (15, 'job-student-m', '학생 남'),
        (16, 'job-student-f', '학생 여'),
        (17, 'job-teacher-m', '선생님 남'),
        (18, 'job-teacher-f', '선생님 여'),
        (19, 'job-kindergarten-m', '유치원 교사 남'),
        (20, 'job-kindergarten-f', '유치원 교사 여'),
        (21, 'job-chef-m', '요리사 남'),
        (22, 'job-chef-f', '요리사 여'),
        (23, 'job-developer-m', '개발자 남'),
        (24, 'job-developer-f', '개발자 여'),
        (25, 'job-farmer-m', '농부 남'),
        (26, 'job-farmer-f', '농부 여'),
        (27, 'job-judge-m', '판사 남'),
        (28, 'job-judge-f', '판사 여'),
        (29, 'job-delivery-m', '배달 남'),
        (30, 'job-delivery-f', '배달 여'),
        (31, 'job-hairdresser-m', '미용사 남'),
        (32, 'job-hairdresser-f', '미용사 여'),
        (33, 'job-banker-m', '은행원 남'),
        (34, 'job-banker-f', '은행원 여'),
        (35, 'job-middle-aged-m', '중년 남'),
        (36, 'job-middle-aged-f', '중년 여'),
        (37, 'job-grandpa', '할아버지'),
        (38, 'job-grandma', '할머니'),
        (39, 'sports-basketball-m', '농구 남'),
        (40, 'sports-basketball-f', '농구 여'),
        (41, 'sports-soccer-m', '축구 남'),
        (42, 'sports-soccer-f', '축구 여'),
        (43, 'sports-baseball-m', '야구 남'),
        (44, 'sports-baseball-f', '야구 여'),
        (45, 'sports-judo-m', '유도 남'),
        (46, 'sports-judo-f', '유도 여'),
        (47, 'sports-taekwondo-m', '태권도 남'),
        (48, 'sports-taekwondo-f', '태권도 여'),
        (49, 'sports-swimming-m', '수영 남'),
        (50, 'sports-swimming-f', '수영 여'),
        (51, 'military-army-m', '육군 남'),
        (52, 'military-army-f', '육군 여'),
        (53, 'military-navy-m', '해군 남'),
        (54, 'military-navy-f', '해군 여'),
        (55, 'military-airforce-m', '공군 남'),
        (56, 'military-airforce-f', '공군 여'),
        (57, 'military-marine-m', '해병대 남'),
        (58, 'military-marine-f', '해병대 여'),
        (59, 'military-sf-m', '특전사 남'),
        (60, 'military-sf-f', '특전사 여'),
        (61, 'military-commando-m', '특공대 남'),
        (62, 'military-commando-f', '특공대 여'),
        (63, 'military-soldier-m', '군인 남'),
        (64, 'military-soldier-f', '군인 여'),
        (65, 'military-medic-officer-m', '군의관 남'),
        (66, 'military-medic-officer-f', '군의관 여'),
        (67, 'military-medic-m', '위생병 남'),
        (68, 'military-medic-f', '위생병 여'),
        (69, 'military-cook-m', '취사병 남'),
        (70, 'military-cook-f', '취사병 여'),
        (71, 'military-engineer-m', '공병 남'),
        (72, 'military-engineer-f', '공병 여'),
        (73, 'military-police-swat-m', '경찰특공대 남'),
        (74, 'military-police-swat-f', '경찰특공대 여'),
        (75, 'military-musa-m', '무사 남'),
        (76, 'military-musa-f', '무사 여'),
        (77, 'transport-heli-pilot-m', '헬기조종사 남'),
        (78, 'transport-heli-pilot-f', '헬기조종사 여'),
        (79, 'transport-captain-m', '기장 남'),
        (80, 'transport-captain-f', '기장 여'),
        (81, 'transport-flight-attendant-m', '스튜어디스 남'),
        (82, 'transport-flight-attendant-f', '스튜어디스 여'),
        (83, 'transport-ship-captain-m', '선장 남'),
        (84, 'transport-ship-captain-f', '선장 여'),
        (85, 'transport-mate-m', '항해사 남'),
        (86, 'transport-mate-f', '항해사 여'),
        (87, 'transport-sailor-m', '선원 남'),
        (88, 'transport-sailor-f', '선원 여'),
        (89, 'animal-cat-m', '고양이 수컷'),
        (90, 'animal-cat-f', '고양이 암컷'),
        (91, 'animal-dog-m', '강아지 수컷'),
        (92, 'animal-dog-f', '강아지 암컷'),
        (93, 'animal-rabbit-m', '토끼 수컷'),
        (94, 'animal-rabbit-f', '토끼 암컷'),
        (95, 'animal-bear-m', '곰 수컷'),
        (96, 'animal-bear-f', '곰 암컷'),
        (97, 'animal-lion-m', '사자 수컷'),
        (98, 'animal-lion-f', '사자 암컷'),
        (99, 'animal-tiger-m', '호랑이 수컷'),
        (100, 'animal-tiger-f', '호랑이 암컷'),
        (101, 'animal-otter-m', '수달 수컷'),
        (102, 'animal-otter-f', '수달 암컷'),
        (103, 'animal-seal-m', '물개 수컷'),
        (104, 'animal-seal-f', '물개 암컷'),
        (105, 'animal-chicken-m', '닭 수컷'),
        (106, 'animal-chicken-f', '닭 암컷'),
        (107, 'office-employee-m', '회사원 남'),
        (108, 'office-employee-f', '회사원 여'),
        (109, 'office-team-lead-m', '팀장 남'),
        (110, 'office-team-lead-f', '팀장 여'),
        (111, 'office-executive-m', '임원 남'),
        (112, 'office-executive-f', '임원 여'),
        (113, 'office-secretary-m', '비서 남'),
        (114, 'office-secretary-f', '비서 여'),
        (115, 'office-hr-m', '인사담당 남'),
        (116, 'office-hr-f', '인사담당 여'),
        (117, 'office-accountant-m', '회계사 남'),
        (118, 'office-accountant-f', '회계사 여'),
        (119, 'office-tax-accountant-m', '세무사 남'),
        (120, 'office-tax-accountant-f', '세무사 여'),
        (121, 'office-lawyer-m', '변호사 남'),
        (122, 'office-lawyer-f', '변호사 여'),
        (123, 'office-civil-servant-m', '공무원 남'),
        (124, 'office-civil-servant-f', '공무원 여'),
        (125, 'office-insurance-m', '보험설계사 남'),
        (126, 'office-insurance-f', '보험설계사 여'),
        (127, 'office-realtor-m', '부동산중개인 남'),
        (128, 'office-realtor-f', '부동산중개인 여'),
        (129, 'office-trader-m', '증권트레이더 남'),
        (130, 'office-trader-f', '증권트레이더 여'),
        (131, 'office-sales-m', '영업사원 남'),
        (132, 'office-sales-f', '영업사원 여'),
        (133, 'office-marketer-m', '마케터 남'),
        (134, 'office-marketer-f', '마케터 여'),
        (135, 'office-planner-m', '기획자 남'),
        (136, 'office-planner-f', '기획자 여'),
        (137, 'office-consultant-m', '컨설턴트 남'),
        (138, 'office-consultant-f', '컨설턴트 여'),
        (139, 'office-designer-m', '디자이너 남'),
        (140, 'office-designer-f', '디자이너 여'),
        (141, 'office-data-analyst-m', '데이터분석가 남'),
        (142, 'office-data-analyst-f', '데이터분석가 여'),
        (143, 'office-security-expert-m', '보안전문가 남'),
        (144, 'office-security-expert-f', '보안전문가 여'),
        (145, 'office-server-engineer-m', '서버엔지니어 남'),
        (146, 'office-server-engineer-f', '서버엔지니어 여'),
        (147, 'office-call-center-m', '콜센터상담원 남'),
        (148, 'office-call-center-f', '콜센터상담원 여'),
        (149, 'office-reception-m', '안내데스크 남'),
        (150, 'office-reception-f', '안내데스크 여'),
        (151, 'office-reporter-m', '기자 남'),
        (152, 'office-reporter-f', '기자 여'),
        (153, 'office-intern-m', '인턴 남'),
        (154, 'office-intern-f', '인턴 여'),
        (155, 'factory-production-m', '생산직 남'),
        (156, 'factory-production-f', '생산직 여'),
        (157, 'factory-assembler-m', '조립공 남'),
        (158, 'factory-assembler-f', '조립공 여'),
        (159, 'factory-qc-m', '품질검사원 남'),
        (160, 'factory-qc-f', '품질검사원 여'),
        (161, 'factory-packer-m', '포장작업자 남'),
        (162, 'factory-packer-f', '포장작업자 여'),
        (163, 'factory-foreman-m', '현장반장 남'),
        (164, 'factory-foreman-f', '현장반장 여'),
        (165, 'factory-welder-m', '용접공 남'),
        (166, 'factory-welder-f', '용접공 여'),
        (167, 'factory-cnc-m', 'CNC기술자 남'),
        (168, 'factory-cnc-f', 'CNC기술자 여'),
        (169, 'factory-painter-m', '도장공 남'),
        (170, 'factory-painter-f', '도장공 여'),
        (171, 'factory-electrician-m', '전기기사 남'),
        (172, 'factory-electrician-f', '전기기사 여'),
        (173, 'factory-plumber-m', '배관공 남'),
        (174, 'factory-plumber-f', '배관공 여'),
        (175, 'factory-maintenance-m', '설비보전 남'),
        (176, 'factory-maintenance-f', '설비보전 여'),
        (177, 'factory-forklift-m', '지게차기사 남'),
        (178, 'factory-forklift-f', '지게차기사 여'),
        (179, 'factory-crane-m', '크레인기사 남'),
        (180, 'factory-crane-f', '크레인기사 여'),
        (181, 'factory-logistics-m', '물류센터 남'),
        (182, 'factory-logistics-f', '물류센터 여'),
        (183, 'factory-truck-driver-m', '화물트럭기사 남'),
        (184, 'factory-truck-driver-f', '화물트럭기사 여'),
        (185, 'factory-cleanroom-m', '반도체클린룸 남'),
        (186, 'factory-cleanroom-f', '반도체클린룸 여'),
        (187, 'factory-food-factory-m', '식품공장 남'),
        (188, 'factory-food-factory-f', '식품공장 여'),
        (189, 'factory-chemical-m', '화학공정 남'),
        (190, 'factory-chemical-f', '화학공정 여'),
        (191, 'factory-auto-mechanic-m', '자동차정비사 남'),
        (192, 'factory-auto-mechanic-f', '자동차정비사 여'),
        (193, 'factory-robot-engineer-m', '로봇엔지니어 남'),
        (194, 'factory-robot-engineer-f', '로봇엔지니어 여'),
        (195, 'factory-safety-manager-m', '안전관리자 남'),
        (196, 'factory-safety-manager-f', '안전관리자 여'),
        (197, 'factory-rebar-m', '철근공 남'),
        (198, 'factory-rebar-f', '철근공 여'),
        (199, 'factory-miner-m', '광부 남'),
        (200, 'factory-miner-f', '광부 여'),
        (201, 'factory-loader-m', '상하차작업자 남'),
        (202, 'factory-loader-f', '상하차작업자 여'),
        (203, 'care-pharmacist-m', '약사 남'),
        (204, 'care-pharmacist-f', '약사 여'),
        (205, 'care-dentist-m', '치과의사 남'),
        (206, 'care-dentist-f', '치과의사 여'),
        (207, 'care-vet-m', '수의사 남'),
        (208, 'care-vet-f', '수의사 여'),
        (209, 'care-paramedic-m', '응급구조사 남'),
        (210, 'care-paramedic-f', '응급구조사 여'),
        (211, 'care-social-worker-m', '사회복지사 남'),
        (212, 'care-social-worker-f', '사회복지사 여'),
        (213, 'care-physio-m', '물리치료사 남'),
        (214, 'care-physio-f', '물리치료사 여'),
        (215, 'care-kor-doctor-m', '한의사 남'),
        (216, 'care-kor-doctor-f', '한의사 여'),
        (217, 'care-prison-guard-m', '교도관 남'),
        (218, 'care-prison-guard-f', '교도관 여'),
        (219, 'care-bodyguard-m', '경호원 남'),
        (220, 'care-bodyguard-f', '경호원 여'),
        (221, 'care-postman-m', '집배원 남'),
        (222, 'care-postman-f', '집배원 여'),
        (223, 'care-sanitation-m', '환경미화원 남'),
        (224, 'care-sanitation-f', '환경미화원 여'),
        (225, 'care-coast-guard-m', '해양경찰 남'),
        (226, 'care-coast-guard-f', '해양경찰 여'),
        (227, 'service-professor-m', '교수 남'),
        (228, 'service-professor-f', '교수 여'),
        (229, 'service-scientist-m', '과학자 남'),
        (230, 'service-scientist-f', '과학자 여'),
        (231, 'service-librarian-m', '사서 남'),
        (232, 'service-librarian-f', '사서 여'),
        (233, 'service-tutor-m', '학원강사 남'),
        (234, 'service-tutor-f', '학원강사 여'),
        (235, 'service-barista-m', '바리스타 남'),
        (236, 'service-barista-f', '바리스타 여'),
        (237, 'service-waiter-m', '웨이터 남'),
        (238, 'service-waiter-f', '웨이터 여'),
        (239, 'service-hotelier-m', '호텔리어 남'),
        (240, 'service-hotelier-f', '호텔리어 여'),
        (241, 'service-convenience-m', '편의점직원 남'),
        (242, 'service-convenience-f', '편의점직원 여'),
        (243, 'service-butcher-m', '정육점주인 남'),
        (244, 'service-butcher-f', '정육점주인 여'),
        (245, 'service-fisher-m', '어부 남'),
        (246, 'service-fisher-f', '어부 여'),
        (247, 'service-baker-m', '제빵사 남'),
        (248, 'service-baker-f', '제빵사 여'),
        (249, 'service-cashier-m', '마트캐셔 남'),
        (250, 'service-cashier-f', '마트캐셔 여'),
        (251, 'arts-driver-photographer-m', '사진작가 남'),
        (252, 'arts-driver-photographer-f', '사진작가 여'),
        (253, 'arts-driver-announcer-m', '아나운서 남'),
        (254, 'arts-driver-announcer-f', '아나운서 여'),
        (255, 'arts-driver-actor-m', '배우 남'),
        (256, 'arts-driver-actor-f', '배우 여'),
        (257, 'arts-driver-singer-m', '가수 남'),
        (258, 'arts-driver-singer-f', '가수 여'),
        (259, 'arts-driver-cartoonist-m', '만화가 남'),
        (260, 'arts-driver-cartoonist-f', '만화가 여'),
        (261, 'arts-driver-writer-m', '작가 남'),
        (262, 'arts-driver-writer-f', '작가 여'),
        (263, 'arts-driver-film-director-m', '영화감독 남'),
        (264, 'arts-driver-film-director-f', '영화감독 여'),
        (265, 'arts-driver-model-m', '모델 남'),
        (266, 'arts-driver-model-f', '모델 여'),
        (267, 'arts-driver-taxi-m', '택시기사 남'),
        (268, 'arts-driver-taxi-f', '택시기사 여'),
        (269, 'arts-driver-bus-m', '버스기사 남'),
        (270, 'arts-driver-bus-f', '버스기사 여'),
        (271, 'arts-driver-train-driver-m', '기관사 남'),
        (272, 'arts-driver-train-driver-f', '기관사 여'),
        (273, 'arts-driver-courier-m', '택배기사 남'),
        (274, 'arts-driver-courier-f', '택배기사 여'),
        (275, 'sports2-golf-m', '골프 남'),
        (276, 'sports2-golf-f', '골프 여'),
        (277, 'sports2-badminton-m', '배드민턴 남'),
        (278, 'sports2-badminton-f', '배드민턴 여'),
        (279, 'sports2-table-tennis-m', '탁구 남'),
        (280, 'sports2-table-tennis-f', '탁구 여'),
        (281, 'sports2-boxing-m', '복싱 남'),
        (282, 'sports2-boxing-f', '복싱 여'),
        (283, 'sports2-wrestling-m', '레슬링 남'),
        (284, 'sports2-wrestling-f', '레슬링 여'),
        (285, 'sports2-archery-m', '양궁 남'),
        (286, 'sports2-archery-f', '양궁 여'),
        (287, 'sports2-ski-m', '스키 남'),
        (288, 'sports2-ski-f', '스키 여'),
        (289, 'sports2-tennis-m', '테니스 남'),
        (290, 'sports2-tennis-f', '테니스 여'),
        (291, 'sports2-trainer-m', '헬스트레이너 남'),
        (292, 'sports2-trainer-f', '헬스트레이너 여'),
        (293, 'sports2-yoga-m', '요가 남'),
        (294, 'sports2-yoga-f', '요가 여'),
        (295, 'sports2-marathon-m', '마라톤 남'),
        (296, 'sports2-marathon-f', '마라톤 여'),
        (297, 'sports2-equestrian-m', '승마 남'),
        (298, 'sports2-equestrian-f', '승마 여'),
        (299, 'traditional-seonbi-m', '선비 남'),
        (300, 'traditional-seonbi-f', '선비 여'),
        (301, 'traditional-haenyeo-m', '해녀 남'),
        (302, 'traditional-haenyeo-f', '해녀 여'),
        (303, 'traditional-monk-m', '스님 남'),
        (304, 'traditional-monk-f', '스님 여'),
        (305, 'traditional-potter-m', '도공 남'),
        (306, 'traditional-potter-f', '도공 여')
      ) AS a (seq, avatar_id, name);
END $$;

-- ── 변경권 값의 출처를 품목 가격 하나로(결정 2) — 정책 칸을 지운다 ───────────────
-- 범위 CHECK(ck_point_settings_other)가 이 칸을 함께 묶고 있어 칸을 지우면 CHECK 전체가 사라진다 — 남은 칸으로 다시 만든다.
ALTER TABLE point_settings DROP CONSTRAINT ck_point_settings_other;
ALTER TABLE point_settings DROP COLUMN nickname_ticket_price;
ALTER TABLE point_settings ADD CONSTRAINT ck_point_settings_other CHECK (popular_reward_top_n BETWEEN 0 AND 50 AND like_bonus_threshold BETWEEN 1 AND 1000);

-- ── 0-5 이름 바꾸기(결정 「이름」 — 오픈 전이라 지금 바꾼다) ───────────────────
-- 요청 제한: 한도 행·멱등 키 행의 종류 이름. 1분 시도 수(UNLOGGED)는 지난 창이 곧 정리되므로 옛 이름 줄을 지운다.
UPDATE request_rate_limits SET action = 'POINT_SHOP_PURCHASE' WHERE action = 'SHOP_PURCHASE';
UPDATE idempotency_requests SET action = 'POINT_SHOP_PURCHASE' WHERE action = 'SHOP_PURCHASE';
DELETE FROM request_rate_counters WHERE action = 'SHOP_PURCHASE';

-- 권한: 서버가 뜰 때 카탈로그(코드)에서 사라진 권한은 역할에서도 빠진다(PermissionCatalogSynchronizer) — 그 전에 새 이름 행을 만들고 역할에 준 것을 옮긴다.
-- 기능이 생겨 featureReady = true(서버가 뜰 때 코드 값으로 다시 맞춘다).
CREATE TEMPORARY TABLE tmp_point_shop_permission_rename (old_code varchar(60) PRIMARY KEY, new_code varchar(60) NOT NULL) ON COMMIT DROP;
INSERT INTO tmp_point_shop_permission_rename VALUES
    ('shop.read', 'pointshop.read'), ('shop.manage', 'pointshop.manage'), ('shop.refund', 'pointshop.refund'), ('shop.use', 'pointshop.use');
INSERT INTO permissions (code, axis, type_id, category, feature_ready, synced_at)
SELECT r.new_code, p.axis, p.type_id, p.category, true, now()
  FROM permissions p JOIN tmp_point_shop_permission_rename r ON r.old_code = p.code
ON CONFLICT (code) DO NOTHING;
UPDATE role_permissions rp
   SET permission_code = r.new_code
  FROM tmp_point_shop_permission_rename r
 WHERE rp.permission_code = r.old_code
   AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_code = r.new_code);
DELETE FROM role_permissions WHERE permission_code IN (SELECT old_code FROM tmp_point_shop_permission_rename);
DELETE FROM permissions WHERE code IN (SELECT old_code FROM tmp_point_shop_permission_rename);
