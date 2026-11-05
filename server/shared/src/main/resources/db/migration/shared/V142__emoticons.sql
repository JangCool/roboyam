-- 로보티콘 S1(무료 팩·심사·카탈로그·토큰 검사·받기 보상) — docs/features/emoticon-contract.md 「서버 구현 단계」 S1 · 「S1 구현 결과」.
-- ⚠ 합칠 때 번호 재확인(가번호 V142 — develop 의 V139 는 배송지) — develop 에 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다.
--
-- 무엇이 생기나
--   · 설정(emoticon_settings, 1행): 받기 보상(rewardPerAcquire 10P · 작가 하루 상한 500P) · 팩당 개수(8~40) · 한 사람 심사 대기 상한(3). 유료·정산 칸은 S2~S4 마이그레이션이 더한다.
--   · 그림(emoticon_images): 올린 그림 하나 = 줄 하나(작가 = uploaded_by_user, 운영자 = uploaded_by_operator, 운영 팩 시드 = 둘 다 없음). 고정 규격(200×200 · GIF 24프레임 ·
--     2MB · 대표 PNG 1MB)은 CHECK 로도 막는다. 파일은 저장소 키(storage_key — ImageStorage) 또는 앱 정적 파일 주소(static_path — 시드 그림) 중 하나. 지문 content_hash = SHA-256(hex).
--   · 팩(emoticon_packs): 출처 OFFICIAL·CREATOR, 상태 DRAFT·PENDING·APPROVED·REJECTED·SUSPENDED, 상점에 나오는 판(live_revision_id — 판매 중·판매 중단이면 있다).
--     가격 price 는 S1 에서 0 만(CHECK — S2 가 유료를 열며 넓힌다). 받은 수 acquire_count(무료 받기 처음 한 번 — 빼고 다시 받아도 그대로).
--   · 판(emoticon_pack_revisions): 신청할 때마다 새 판(version 1..) — 이름·설명·태그·대표·판매 희망·가격 변경 요청 메모. version 이 없는 줄은 아직 내지 않은 임시 판(팩마다 하나).
--     낸 판은 고치지 않는다(트리거) — 심사 건이 신청 때의 판을 가리켜 처리가 끝난 건도 비교가 보인다.
--   · 로보티콘(emoticons): 토큰 `:emo-{id}:` 의 ID — 판이 바뀌어도 같은 로보티콘은 같은 ID. 판의 차례·그림·이름표는 판 줄(emoticon_revision_items).
--   · 심사 건(emoticon_reviews): 신청 한 번 = 한 줄(FIRST 처음 · UPDATE 판매 중·판매 중단 팩의 새 판), 동의 셋 시각·문구 판, 처리(승인 가격·반려 사유). 팩마다 대기 건 하나.
--   · 받기(emoticon_acquisitions): (회원, 팩) 하나에 줄 하나 — 빼면 removed_at, 다시 받으면 지운다(줄은 남는다 → 받기 보상은 줄마다 한 번 = 같은 회원의 같은 팩은 처음 한 번만).
--   · 포인트 원장 사유 ∪ EMOTICON_CREATOR_REWARD, 대상 ∪ EMOTICON_ACQUIRE(받기 줄) · 회원 알림 종류 ∪ EMOTICON_PACK.
--   · 시드: 운영 팩 로보얌콘(남)·로보얌콘핑크(여) 32개씩 — 그림은 두 앱 public 의 /emoticons/robotikon-{m,f}/NN.gif·cover.png(지문·용량은 그 파일에서 잰 값).
-- 새 표는 users 에 외래 키를 걸지 않는다(회원 행은 지우지 않고 익명화 — V86 과 같다). 운영자는 ON DELETE SET NULL.

-- ── 설정(1행) ────────────────────────────────────────────────────────────────
CREATE TABLE emoticon_settings (
    id                 integer     PRIMARY KEY,
    reward_per_acquire integer     NOT NULL DEFAULT 10,
    reward_daily_cap   integer     NOT NULL DEFAULT 500,
    item_min           integer     NOT NULL DEFAULT 8,
    item_max           integer     NOT NULL DEFAULT 40,
    pending_limit      integer     NOT NULL DEFAULT 3,
    updated_at         timestamptz,
    updated_by         bigint      REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_emoticon_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_emoticon_settings_reward CHECK (reward_per_acquire BETWEEN 0 AND 1000),
    CONSTRAINT ck_emoticon_settings_cap CHECK (reward_daily_cap BETWEEN 1 AND 100000 AND reward_daily_cap >= reward_per_acquire),
    CONSTRAINT ck_emoticon_settings_item_min CHECK (item_min BETWEEN 1 AND 40),
    CONSTRAINT ck_emoticon_settings_item_max CHECK (item_max BETWEEN 8 AND 60 AND item_max >= item_min),
    CONSTRAINT ck_emoticon_settings_pending CHECK (pending_limit BETWEEN 1 AND 10)
);
INSERT INTO emoticon_settings (id) VALUES (1);

-- ── 그림 ─────────────────────────────────────────────────────────────────────
CREATE TABLE emoticon_images (
    id                   bigint       PRIMARY KEY,
    kind                 varchar(8)   NOT NULL,
    format               varchar(3)   NOT NULL,
    width                integer      NOT NULL,
    height               integer      NOT NULL,
    bytes                integer      NOT NULL,
    frames               integer      NOT NULL,
    loop_forever         boolean      NOT NULL,
    content_hash         varchar(64)  NOT NULL,
    storage_key          varchar(200),
    static_path          varchar(200),
    uploaded_by_user     bigint,
    uploaded_by_operator bigint       REFERENCES operators (id) ON DELETE SET NULL,
    created_at           timestamptz  NOT NULL,
    CONSTRAINT ck_emoticon_images_kind CHECK (kind IN ('EMOTICON', 'COVER')),
    CONSTRAINT ck_emoticon_images_format CHECK (format IN ('GIF', 'PNG') AND (kind <> 'COVER' OR format = 'PNG')),
    CONSTRAINT ck_emoticon_images_size CHECK (width = 200 AND height = 200),
    CONSTRAINT ck_emoticon_images_bytes CHECK (bytes > 0 AND bytes <= CASE kind WHEN 'COVER' THEN 1048576 ELSE 2097152 END),
    CONSTRAINT ck_emoticon_images_frames CHECK (frames BETWEEN 1 AND 24 AND (format = 'GIF' OR frames = 1)),
    CONSTRAINT ck_emoticon_images_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_emoticon_images_file CHECK ((storage_key IS NULL) <> (static_path IS NULL)),
    CONSTRAINT ck_emoticon_images_uploader CHECK (uploaded_by_user IS NULL OR uploaded_by_operator IS NULL)
);
CREATE INDEX ix_emoticon_images_user ON emoticon_images (uploaded_by_user, created_at) WHERE uploaded_by_user IS NOT NULL;

-- ── 팩 ───────────────────────────────────────────────────────────────────────
CREATE TABLE emoticon_packs (
    id               bigint        PRIMARY KEY,
    source           varchar(8)    NOT NULL,
    creator_id       bigint,
    status           varchar(10)   NOT NULL,
    price            integer       NOT NULL DEFAULT 0,
    live_revision_id bigint,
    acquire_count    integer       NOT NULL DEFAULT 0,
    published_at     timestamptz,
    suspend_reason   varchar(200),
    suspended_at     timestamptz,
    suspended_by     bigint        REFERENCES operators (id) ON DELETE SET NULL,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    CONSTRAINT ux_emoticon_packs_live UNIQUE (live_revision_id),
    CONSTRAINT ck_emoticon_packs_source CHECK (source IN ('OFFICIAL', 'CREATOR')),
    CONSTRAINT ck_emoticon_packs_creator CHECK ((source = 'CREATOR') = (creator_id IS NOT NULL)),
    CONSTRAINT ck_emoticon_packs_status CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    -- 운영 팩은 심사 없이 바로 판매 중(판매 중단만 있다)
    CONSTRAINT ck_emoticon_packs_official_status CHECK (source = 'CREATOR' OR status IN ('APPROVED', 'SUSPENDED')),
    -- S1 은 무료만(유료 판매는 S2 — 그때 이 CHECK 를 넓힌다). 운영 팩은 늘 무료(계약 「목업 2차」)
    CONSTRAINT ck_emoticon_packs_price CHECK (price = 0),
    -- 상점에 나온 팩(판매 중 · 판매 중단)에만 상점의 판이 있고, 처음 나온 시각이 있다
    CONSTRAINT ck_emoticon_packs_live CHECK ((status IN ('APPROVED', 'SUSPENDED')) = (live_revision_id IS NOT NULL)
        AND (live_revision_id IS NULL OR published_at IS NOT NULL)),
    CONSTRAINT ck_emoticon_packs_suspended CHECK ((status = 'SUSPENDED') = (suspend_reason IS NOT NULL) AND (suspend_reason IS NULL) = (suspended_at IS NULL)
        AND (suspend_reason IS NULL OR char_length(btrim(suspend_reason)) BETWEEN 5 AND 200)),
    CONSTRAINT ck_emoticon_packs_count CHECK (acquire_count >= 0)
);
CREATE INDEX ix_emoticon_packs_creator ON emoticon_packs (creator_id, updated_at) WHERE creator_id IS NOT NULL;
CREATE INDEX ix_emoticon_packs_shop ON emoticon_packs (status, acquire_count, id);

-- ── 판 ───────────────────────────────────────────────────────────────────────
CREATE TABLE emoticon_pack_revisions (
    id                 bigint        PRIMARY KEY,
    pack_id            bigint        NOT NULL REFERENCES emoticon_packs (id),
    version            integer,
    name               varchar(20)   NOT NULL DEFAULT '',
    description        varchar(200)  NOT NULL DEFAULT '',
    tags               text[]        NOT NULL DEFAULT '{}',
    cover_image_id     bigint        REFERENCES emoticon_images (id),
    sale_wish          varchar(4)    NOT NULL DEFAULT 'FREE',
    price_request_memo varchar(200),
    created_at         timestamptz   NOT NULL,
    submitted_at       timestamptz,
    CONSTRAINT ux_emoticon_pack_revisions_version UNIQUE (pack_id, version),
    CONSTRAINT ux_emoticon_pack_revisions_pack_id UNIQUE (pack_id, id),
    CONSTRAINT ck_emoticon_pack_revisions_version CHECK (version IS NULL OR version >= 1),
    -- 낸 판(version 있음)만 낸 시각이 있다 — 낸 판은 이름 2~20자 · 대표 이미지가 있다
    CONSTRAINT ck_emoticon_pack_revisions_sealed CHECK ((version IS NULL) = (submitted_at IS NULL)
        AND (version IS NULL OR (char_length(btrim(name)) BETWEEN 2 AND 20 AND cover_image_id IS NOT NULL))),
    CONSTRAINT ck_emoticon_pack_revisions_tags CHECK (cardinality(tags) <= 5),
    CONSTRAINT ck_emoticon_pack_revisions_wish CHECK (sale_wish IN ('FREE', 'PAID'))
);
-- 팩마다 아직 내지 않은 임시 판은 하나
CREATE UNIQUE INDEX ux_emoticon_pack_revisions_draft ON emoticon_pack_revisions (pack_id) WHERE version IS NULL;
-- 상점의 판은 그 팩의 판이다(팩 → 판, 판 → 팩이 서로 가리켜 커밋 때 본다)
ALTER TABLE emoticon_packs ADD CONSTRAINT fk_emoticon_packs_live FOREIGN KEY (id, live_revision_id)
    REFERENCES emoticon_pack_revisions (pack_id, id) DEFERRABLE INITIALLY DEFERRED;

-- ── 로보티콘(토큰 ID) · 판의 차례 ───────────────────────────────────────────
CREATE TABLE emoticons (
    id         bigint       PRIMARY KEY,
    pack_id    bigint       NOT NULL REFERENCES emoticon_packs (id),
    created_at timestamptz  NOT NULL,
    CONSTRAINT ux_emoticons_pack_id UNIQUE (pack_id, id)
);

CREATE TABLE emoticon_revision_items (
    revision_id bigint       NOT NULL,
    position    integer      NOT NULL,
    pack_id     bigint       NOT NULL,
    emoticon_id bigint       NOT NULL,
    image_id    bigint       NOT NULL REFERENCES emoticon_images (id),
    label       varchar(10)  NOT NULL DEFAULT '',
    PRIMARY KEY (revision_id, position),
    CONSTRAINT ux_emoticon_revision_items_emoticon UNIQUE (revision_id, emoticon_id),
    CONSTRAINT ux_emoticon_revision_items_image UNIQUE (revision_id, image_id),
    -- 판의 로보티콘은 그 팩의 로보티콘이다(다른 팩의 ID 를 끌어오지 못한다)
    CONSTRAINT fk_emoticon_revision_items_revision FOREIGN KEY (pack_id, revision_id) REFERENCES emoticon_pack_revisions (pack_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_emoticon_revision_items_emoticon FOREIGN KEY (pack_id, emoticon_id) REFERENCES emoticons (pack_id, id),
    CONSTRAINT ck_emoticon_revision_items_position CHECK (position BETWEEN 1 AND 60)
);
CREATE INDEX ix_emoticon_revision_items_image ON emoticon_revision_items (image_id);
CREATE INDEX ix_emoticon_revision_items_emoticon ON emoticon_revision_items (emoticon_id);

-- 낸 판은 고치지 않는다 — 판 칸·차례 줄 모두(임시 판만 고친다). 지우기도 낸 판은 막는다
CREATE OR REPLACE FUNCTION emoticon_revision_sealed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME = 'emoticon_pack_revisions' THEN
        IF OLD.version IS NOT NULL AND (TG_OP = 'DELETE' OR (NEW.pack_id, NEW.version, NEW.name, NEW.description, NEW.tags, NEW.cover_image_id, NEW.sale_wish,
                NEW.price_request_memo, NEW.created_at, NEW.submitted_at) IS DISTINCT FROM (OLD.pack_id, OLD.version, OLD.name, OLD.description, OLD.tags,
                OLD.cover_image_id, OLD.sale_wish, OLD.price_request_memo, OLD.created_at, OLD.submitted_at)) THEN
            RAISE EXCEPTION 'emoticon revision is sealed';
        END IF;
    ELSIF EXISTS (SELECT 1 FROM emoticon_pack_revisions r WHERE r.id = OLD.revision_id AND r.version IS NOT NULL) THEN
        RAISE EXCEPTION 'emoticon revision is sealed';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END
$$;
CREATE TRIGGER tr_emoticon_pack_revisions_sealed BEFORE UPDATE OR DELETE ON emoticon_pack_revisions FOR EACH ROW EXECUTE FUNCTION emoticon_revision_sealed();
CREATE TRIGGER tr_emoticon_revision_items_sealed BEFORE UPDATE OR DELETE ON emoticon_revision_items FOR EACH ROW EXECUTE FUNCTION emoticon_revision_sealed();
-- 낸 판에 차례 줄을 더하지 못한다(낸 순간의 차례가 그대로 남게) — 시드·신청은 판을 내기 전에 차례를 넣는다
CREATE OR REPLACE FUNCTION emoticon_revision_items_insert() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM emoticon_pack_revisions r WHERE r.id = NEW.revision_id AND r.version IS NOT NULL) THEN
        RAISE EXCEPTION 'emoticon revision is sealed';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER tr_emoticon_revision_items_insert BEFORE INSERT ON emoticon_revision_items FOR EACH ROW EXECUTE FUNCTION emoticon_revision_items_insert();

-- ── 심사 건 ──────────────────────────────────────────────────────────────────
CREATE TABLE emoticon_reviews (
    id                   bigint        PRIMARY KEY,
    pack_id              bigint        NOT NULL REFERENCES emoticon_packs (id),
    revision_id          bigint        NOT NULL,
    previous_revision_id bigint,
    creator_id           bigint        NOT NULL,
    kind                 varchar(6)    NOT NULL,
    status               varchar(8)    NOT NULL,
    submitted_at         timestamptz   NOT NULL,
    agree_ownership_at   timestamptz   NOT NULL,
    agree_license_at     timestamptz   NOT NULL,
    agree_policy_at      timestamptz   NOT NULL,
    consent_version      varchar(20)   NOT NULL,
    decided_at           timestamptz,
    decided_by           bigint        REFERENCES operators (id) ON DELETE SET NULL,
    reason               varchar(200),
    approved_price       integer,
    CONSTRAINT ux_emoticon_reviews_revision UNIQUE (revision_id),
    CONSTRAINT fk_emoticon_reviews_revision FOREIGN KEY (pack_id, revision_id) REFERENCES emoticon_pack_revisions (pack_id, id),
    CONSTRAINT fk_emoticon_reviews_previous FOREIGN KEY (pack_id, previous_revision_id) REFERENCES emoticon_pack_revisions (pack_id, id),
    CONSTRAINT ck_emoticon_reviews_kind CHECK (kind IN ('FIRST', 'UPDATE') AND (kind = 'UPDATE') = (previous_revision_id IS NOT NULL)),
    CONSTRAINT ck_emoticon_reviews_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_emoticon_reviews_decided CHECK ((status = 'PENDING') = (decided_at IS NULL)),
    CONSTRAINT ck_emoticon_reviews_reason CHECK ((status = 'REJECTED') = (reason IS NOT NULL) AND (reason IS NULL OR char_length(btrim(reason)) BETWEEN 5 AND 200)),
    -- 승인한 건만 가격이 있다 — S1 은 무료 승인만(S2 가 넓힌다)
    CONSTRAINT ck_emoticon_reviews_price CHECK ((status = 'APPROVED') = (approved_price IS NOT NULL) AND (approved_price IS NULL OR approved_price = 0))
);
-- 팩마다 대기 건은 하나 · 작가의 대기 건 세기(상한)
CREATE UNIQUE INDEX ux_emoticon_reviews_pending ON emoticon_reviews (pack_id) WHERE status = 'PENDING';
CREATE INDEX ix_emoticon_reviews_creator_pending ON emoticon_reviews (creator_id) WHERE status = 'PENDING';
CREATE INDEX ix_emoticon_reviews_pack ON emoticon_reviews (pack_id, submitted_at);
CREATE INDEX ix_emoticon_reviews_status ON emoticon_reviews (status, submitted_at);

-- ── 받기(가진 팩) ───────────────────────────────────────────────────────────
CREATE TABLE emoticon_acquisitions (
    id                bigint        PRIMARY KEY,
    user_id           bigint        NOT NULL,
    pack_id           bigint        NOT NULL REFERENCES emoticon_packs (id),
    kind              varchar(8)    NOT NULL DEFAULT 'FREE',
    first_acquired_at timestamptz   NOT NULL,
    acquired_at       timestamptz   NOT NULL,
    removed_at        timestamptz,
    CONSTRAINT ux_emoticon_acquisitions_member_pack UNIQUE (user_id, pack_id),
    -- S1 은 무료 받기만(유료 구매는 S3 — 그때 넓힌다)
    CONSTRAINT ck_emoticon_acquisitions_kind CHECK (kind IN ('FREE')),
    CONSTRAINT ck_emoticon_acquisitions_time CHECK (acquired_at >= first_acquired_at AND (removed_at IS NULL OR removed_at >= acquired_at))
);
CREATE INDEX ix_emoticon_acquisitions_owned ON emoticon_acquisitions (user_id, acquired_at) WHERE removed_at IS NULL;
CREATE INDEX ix_emoticon_acquisitions_pack ON emoticon_acquisitions (pack_id);

-- ── 포인트 원장 사유·대상: 무료 팩 받기 보상(작가에게 적립 — 대상 = 그 받기 줄, 원장 유일 (사유, 대상) 로 줄마다 한 번) ──
-- 사유: V85 목록 ∪ EMOTICON_CREATOR_REWARD(적립 EARN — ck_point_ledger_reason_kind 의 ELSE 갈래라 그대로 둔다).
-- 대상: V122 목록 ∪ EMOTICON_ACQUIRE. ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_reason;
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_reason CHECK (reason IN (
    'ATTENDANCE', 'ATTENDANCE_STREAK', 'POST', 'COMMENT', 'POPULAR', 'POST_REVOKE', 'COMMENT_REVOKE', 'POST_RESTORE', 'TICKET_PURCHASE', 'ADMIN_ADJUST',
    'DEAL_END_REQUEST', 'LIKE_BONUS', 'LIKE_BONUS_REVOKE', 'SIGNUP',
    'CHARGE', 'CHARGE_BONUS', 'CHARGE_BONUS_REVOKE', 'CHARGE_REFUND', 'CHARGE_REFUND_RESTORE', 'CHARGE_BONUS_RESTORE',
    'POINT_SHOP_PURCHASE', 'POINT_SHOP_REFUND', 'GB_POINT_USE', 'GB_POINT_RESTORE',
    'REVOKE_DEBT_OFFSET', 'EXPIRE', 'WITHDRAW_FORFEIT',
    'EMOTICON_CREATOR_REWARD'));
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_ref;
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_ref CHECK (ref_type IS NULL OR ref_type IN ('POST', 'COMMENT', 'POINT_SHOP_PURCHASE', 'POINT_CHARGE',
    'GB_PARTICIPATION', 'LOT', 'LEDGER', 'USER', 'ATTENDANCE', 'DEAL_END_REQUEST', 'EMOTICON_ACQUIRE'));
ALTER TABLE point_lots DROP CONSTRAINT ck_point_lots_source_ref;
ALTER TABLE point_lots ADD CONSTRAINT ck_point_lots_source_ref CHECK (source_ref_type IS NULL OR source_ref_type IN ('POST', 'COMMENT', 'POINT_SHOP_PURCHASE',
    'POINT_CHARGE', 'GB_PARTICIPATION', 'LOT', 'LEDGER', 'USER', 'ATTENDANCE', 'DEAL_END_REQUEST', 'EMOTICON_ACQUIRE'));

-- ── 회원 알림 종류: V137 목록 ∪ EMOTICON_PACK(작가 — 승인·반려·판매 중단·다시 판매, 덧붙임 kind) ──
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다).
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'DM', 'NICKNAME_IMAGE_REVIEWED', 'OPINION_PIN_REQUESTED',
                    'OPINION_PIN_REVIEWED', 'POST_REMOVED', 'COMMENT_REMOVED', 'REPORT_RESULT', 'FEATURE_RESTRICTED', 'POINTS_EXPIRING',
                    'POINT_SHOP_REFUND_APPROVED', 'POINT_SHOP_REFUND_REJECTED',
                    'GROUP_APPROVED', 'GROUP_REJECTED', 'GROUP_JOIN_REQUEST', 'GROUP_JOIN_APPROVED', 'GROUP_JOIN_REJECTED', 'GROUP_KICKED',
                    'GROUP_DISSOLVED', 'GROUP_LEADER_TRANSFERRED', 'GROUP_DEACTIVATED', 'GROUP_REACTIVATED',
                    'GROUP_POST_UNPUBLISHED',
                    'GROUP_MANAGER_APPOINTED', 'GROUP_MANAGER_PERMISSIONS_CHANGED', 'GROUP_MANAGER_REMOVED', 'GROUP_MANAGER_RESIGNED',
                    'GROUP_KICKED_BY_MANAGER',
                    'GROUP_EVENT',
                    'GROUP_PUBLISH_REQUESTED', 'GROUP_PUBLISH_APPROVED', 'GROUP_PUBLISH_REJECTED', 'GROUP_POST_PUBLISHED', 'GROUP_VISIBILITY_CHANGED',
                    'GROUP_POST_MEMBERS_ONLY', 'GROUP_POST_RELEASED',
                    'GROUP_BUY',
                    'BUSINESS',
                    'BIZ_APPLICATION',
                    'DEAL_FLAGGED', 'DEAL_AUTO_ENDED', 'DEAL_MANAGER_ENDED', 'DEAL_EXPIRED', 'DEAL_REOPENED_FIX_ENDS',
                    'SANCTION_ISSUED', 'SANCTION_LIFTED', 'APPEAL_ANSWERED',
                    'BOARD_SANCTION_ISSUED',
                    'EMOTICON_PACK'));

-- ── 시드: 운영 팩 로보얌콘(남)·로보얌콘핑크(여) ─────────────────────────────
-- ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — V86 과 같은 마이그레이션용 노드) + 순번:
-- 팩 m 1 · f 2, 판 m 3 · f 4, 그림 m 100(대표)·101..132 · f 200(대표)·201..232, 로보티콘 m 301..332 · f 401..432(토큰 ID).
-- 이름·이름표는 robotikon/meta.json(설명은 화면 이름 「로보티콘」으로), 그림 정보(용량·프레임·무한 반복·지문)는 두 앱 public 의 파일을 읽어 잰 값이다.
DO $$
DECLARE
    v_base bigint := ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22) | (1023::bigint << 12);
    v_now  timestamptz := now();
BEGIN
    CREATE TEMP TABLE seed_robotikon (pack text, no integer, kind text, format text, bytes integer, frames integer, loop_forever boolean, hash text, path text,
                                      label text) ON COMMIT DROP;
    INSERT INTO seed_robotikon VALUES
        ('m', 1, 'EMOTICON', 'GIF', 69271, 24, true, '9477ab01b51db2fae593fc2afc7460d24a08c832f7cb9c840fadebcbecd09341', '/emoticons/robotikon-m/01.gif', '기쁨'),
        ('m', 2, 'EMOTICON', 'GIF', 104529, 24, true, '4cd3bf8b43dca36ba5f0ea1f2ed0df6ba9aed8e704cd0cdacd8702e1827b79b8', '/emoticons/robotikon-m/02.gif', '슬픔'),
        ('m', 3, 'EMOTICON', 'GIF', 76511, 24, true, '52ff91ddc7ff111eaf8fe114bc58bbb0b1b27cbd1656ed6abe22b718781d2525', '/emoticons/robotikon-m/03.gif', '우울'),
        ('m', 4, 'EMOTICON', 'GIF', 111264, 24, true, 'd5ae55e5933aee5447dee0532c9367d16f88591aa3fcc8328e94a3440a4ad4b5', '/emoticons/robotikon-m/04.gif', '분노'),
        ('m', 5, 'EMOTICON', 'GIF', 95232, 24, true, '8f776aad2d8986d84d621ee0793abd7119f043469cc420f4d8ca5527833a5b2c', '/emoticons/robotikon-m/05.gif', '짜증'),
        ('m', 6, 'EMOTICON', 'GIF', 89183, 24, true, '8ea3d2a7e9701461eac6efe63264bb1eb1660f80d47121ed2194ce88dcfe7b4f', '/emoticons/robotikon-m/06.gif', '헉'),
        ('m', 7, 'EMOTICON', 'GIF', 105231, 24, true, 'c1743009f67acb8212e2b855fbce28972248182ab8d79d402b0aacfdd50f2dcd', '/emoticons/robotikon-m/07.gif', '무서'),
        ('m', 8, 'EMOTICON', 'GIF', 91680, 24, true, '4918985a5859498d5fae9f9636b177692f91b840fbaaadb0986a04ffd84e82ba', '/emoticons/robotikon-m/08.gif', '윽'),
        ('m', 9, 'EMOTICON', 'GIF', 114034, 24, true, '72194c6f62ff91aea94cb9e60acb1b91fcf962f8c2bcf5a455e174c055ba8f34', '/emoticons/robotikon-m/09.gif', '당황'),
        ('m', 10, 'EMOTICON', 'GIF', 106910, 24, true, '2cf402cb4bb94526d7d088f5203f93c7667589ed73c205510f3f051a296b1474', '/emoticons/robotikon-m/10.gif', '부끄'),
        ('m', 11, 'EMOTICON', 'GIF', 71415, 24, true, 'f4d065cc0558c466fc3a272e74e8ba70c635bf14acad8c6a0c80dfbf9fbaa8f2', '/emoticons/robotikon-m/11.gif', '사랑'),
        ('m', 12, 'EMOTICON', 'GIF', 104835, 24, true, 'd29b1fc23fa4b47445cc93be2cc7fbbab93617c0d74ca0321dc96feaf0143094', '/emoticons/robotikon-m/12.gif', '두근'),
        ('m', 13, 'EMOTICON', 'GIF', 86489, 24, true, 'b19f78126e53a7757ba789a4a7df44b250ed84d0bbd194bd6b4aa6e96281f000', '/emoticons/robotikon-m/13.gif', '신남'),
        ('m', 14, 'EMOTICON', 'GIF', 67118, 24, true, '53304d03a4485ecf11dea391a70afdb241b37abf4b86c10fd47b9c5da5c7aeb1', '/emoticons/robotikon-m/14.gif', '평온'),
        ('m', 15, 'EMOTICON', 'GIF', 88912, 24, true, 'ef8d0df1cbef4cd1eff622708ede3f6433aab047f6abb7b4d90791e4fb6946b0', '/emoticons/robotikon-m/15.gif', '심심'),
        ('m', 16, 'EMOTICON', 'GIF', 71310, 24, true, 'ebe3dc5597d645137b093dc395dfb8543dddc47e6f1a21af2337770aba5128f7', '/emoticons/robotikon-m/16.gif', '졸려'),
        ('m', 17, 'EMOTICON', 'GIF', 102043, 24, true, '99bd0aede0251dc33a3e644f59ab7da5ad572bedbe278767295b07044bf6988a', '/emoticons/robotikon-m/17.gif', '안녕'),
        ('m', 18, 'EMOTICON', 'GIF', 93482, 24, true, '3c0fb126ddd2ab8ac45c5d335d03140f88dd983a5a723c5e128596a62c8213c8', '/emoticons/robotikon-m/18.gif', '감사'),
        ('m', 19, 'EMOTICON', 'GIF', 110299, 24, true, '753ee3db31d1be83f0242e6c4ebcdd3e15f9de5422a98c1d15ccf1807f30de87', '/emoticons/robotikon-m/19.gif', '굿'),
        ('m', 20, 'EMOTICON', 'GIF', 92169, 24, true, 'bd215cf9d619f4c47f146fb7a9df38047c9729cf2beb0eb8f0fe558991812e0e', '/emoticons/robotikon-m/20.gif', '미안'),
        ('m', 21, 'EMOTICON', 'GIF', 107806, 24, true, '99581ac5b3efd44a46203af18681e443a4de60a09c82625e5197101daf31838d', '/emoticons/robotikon-m/21.gif', '파이팅'),
        ('m', 22, 'EMOTICON', 'GIF', 100001, 24, true, '1e73dcce1673b0a8ea60052aaa781241337f0682c844916e5a9da31429f4f4cf', '/emoticons/robotikon-m/22.gif', '짝짝'),
        ('m', 23, 'EMOTICON', 'GIF', 109104, 24, true, '301da0034808ce9a58b7047d7f8c68ba230cc418d87a7ad1fd101413910c4d4d', '/emoticons/robotikon-m/23.gif', '싫어'),
        ('m', 24, 'EMOTICON', 'GIF', 107145, 24, true, 'e3b7cb44dce7cad353f1058767048b39e7cd28ff9584f64ebbd483d1020c5a09', '/emoticons/robotikon-m/24.gif', '뭐지'),
        ('m', 25, 'EMOTICON', 'GIF', 91615, 24, true, '371b7e05c4acf481221339ce462c9747b44a1a53f8f9238e5fe57594d7876ae6', '/emoticons/robotikon-m/25.gif', '로딩'),
        ('m', 26, 'EMOTICON', 'GIF', 108135, 24, true, 'e083e44a964c68c13acfb808036a64be6a64fa8eb3d6532f30ea56dedd757d77', '/emoticons/robotikon-m/26.gif', '에러'),
        ('m', 27, 'EMOTICON', 'GIF', 93889, 24, true, 'a020c239a812f19eb08404d1b605359301e586775f1e89c9d63797d368beed13', '/emoticons/robotikon-m/27.gif', '과열'),
        ('m', 28, 'EMOTICON', 'GIF', 82253, 24, true, '5bd960b140995710307cabf25d22dc76581d59f32b232f23ae3a016c5fbebdc6', '/emoticons/robotikon-m/28.gif', '방전'),
        ('m', 29, 'EMOTICON', 'GIF', 96009, 24, true, '3ec3390c20a96b22c9eeb4f21310d07652e475d878f3f34ab167276a780fe033', '/emoticons/robotikon-m/29.gif', '충전'),
        ('m', 30, 'EMOTICON', 'GIF', 89841, 24, true, 'f04a9ad870186e77dd4c48873e4e51cba9799822bda8b4768c78e697aa47b994', '/emoticons/robotikon-m/30.gif', '업뎃'),
        ('m', 31, 'EMOTICON', 'GIF', 72391, 24, true, 'c36c8061f418c5ebd1df593538b6307ddffccf60cc2310ef2426eb721d923ad6', '/emoticons/robotikon-m/31.gif', '재부팅'),
        ('m', 32, 'EMOTICON', 'GIF', 95018, 24, true, 'ad2b32e0ba63fa41613f054ec06b7d7f16826f31d6903f3b5bdd3eefea5d4427', '/emoticons/robotikon-m/32.gif', '해킹'),
        ('m', 0, 'COVER', 'PNG', 12773, 1, true, '98e910f41b0240f4e6d1ad863e82994b6b984c281ba860f5ae0e5935d4a62061', '/emoticons/robotikon-m/cover.png', NULL),
        ('f', 1, 'EMOTICON', 'GIF', 77721, 24, true, '6462d70fd7aac0a337e7d8acb2fab021ee24c311629ced7222d1b59acc9cc217', '/emoticons/robotikon-f/01.gif', '기쁨'),
        ('f', 2, 'EMOTICON', 'GIF', 112341, 24, true, '78e945593d773be76f23292cdb291320b2ccbd0873c8b2aa644d66b028c95784', '/emoticons/robotikon-f/02.gif', '슬픔'),
        ('f', 3, 'EMOTICON', 'GIF', 80250, 24, true, '05ba7a8a9f950e254b39de380d942a04b00077074d4c50b7fbd408811bdfaa1c', '/emoticons/robotikon-f/03.gif', '우울'),
        ('f', 4, 'EMOTICON', 'GIF', 118580, 24, true, '85daf37f2da57b0bb884b9731454acbe4668deb4b5cf2b8a8e209b08297471ce', '/emoticons/robotikon-f/04.gif', '분노'),
        ('f', 5, 'EMOTICON', 'GIF', 100815, 24, true, 'ca2a4906948104302d813fa5cd063c62cdeb91ffbd42c698b6788f9f4c1e48ad', '/emoticons/robotikon-f/05.gif', '짜증'),
        ('f', 6, 'EMOTICON', 'GIF', 97621, 24, true, '632ef5d6eb7c3e445a0c0e57cbc622196839c46782ea9efeb6e2d7d606233547', '/emoticons/robotikon-f/06.gif', '헉'),
        ('f', 7, 'EMOTICON', 'GIF', 111465, 24, true, '1642b3fa7d74923db6e911853bb5a4fb95aebb4833ce8ef5a667dbf9ec854073', '/emoticons/robotikon-f/07.gif', '무서'),
        ('f', 8, 'EMOTICON', 'GIF', 98500, 24, true, '0826cc2cefa26f6fb5c75962f243dd3de2407431c55efc7acc6c1673d4a6e164', '/emoticons/robotikon-f/08.gif', '윽'),
        ('f', 9, 'EMOTICON', 'GIF', 122321, 24, true, '52c3f5af135505d739e2aa466084985683f556c78b4af64dc034d0f2787d2ccf', '/emoticons/robotikon-f/09.gif', '당황'),
        ('f', 10, 'EMOTICON', 'GIF', 112458, 24, true, 'e399474b4012d9f24e00adc0a2343a2a46b2795c99745a0842bbc7266eca75ab', '/emoticons/robotikon-f/10.gif', '부끄'),
        ('f', 11, 'EMOTICON', 'GIF', 77881, 24, true, '7f2bd4a4e7ea17470677bf880c30e71892c424c94964f644f5d41ac86d817cb7', '/emoticons/robotikon-f/11.gif', '사랑'),
        ('f', 12, 'EMOTICON', 'GIF', 113299, 24, true, '5be5e809f17342ae1ce356f63b4e84d427b8815edcf9d2ee56b984593e0fc8db', '/emoticons/robotikon-f/12.gif', '두근'),
        ('f', 13, 'EMOTICON', 'GIF', 93380, 24, true, '69685d1de759c2ce3e36537ae99d335588f26aea2f1c5ca1702b7b9614de34c0', '/emoticons/robotikon-f/13.gif', '신남'),
        ('f', 14, 'EMOTICON', 'GIF', 74553, 24, true, '2c8ea43d0e2a3c43aeb2e26d941230070cdbb0c0f624bccbb9950c3dd09c43de', '/emoticons/robotikon-f/14.gif', '평온'),
        ('f', 15, 'EMOTICON', 'GIF', 95096, 24, true, '3a344de3297b16b8569c6aa87569925238f2c1297cdebb061f77b147951cd494', '/emoticons/robotikon-f/15.gif', '심심'),
        ('f', 16, 'EMOTICON', 'GIF', 78951, 24, true, '4bbb82da7ae3fbb7947a25f6543079d87d2c2093b89dd5b2c9cd7c8275c2f523', '/emoticons/robotikon-f/16.gif', '졸려'),
        ('f', 17, 'EMOTICON', 'GIF', 108857, 24, true, 'd03f5906b1d913074889154f862696b0da642803c5a2c960bf45b50b5c55a4fc', '/emoticons/robotikon-f/17.gif', '안녕'),
        ('f', 18, 'EMOTICON', 'GIF', 99478, 24, true, '9d3d036552aaab5a0c6a9f9e39e9455ba4c6b24da8a6ec4261649d7782d27487', '/emoticons/robotikon-f/18.gif', '감사'),
        ('f', 19, 'EMOTICON', 'GIF', 118809, 24, true, '8ea23ad4b540693983eab0266a72bef71f73f9e244e82821b37009f4a0fb4398', '/emoticons/robotikon-f/19.gif', '굿'),
        ('f', 20, 'EMOTICON', 'GIF', 98573, 24, true, 'e95620d52d1b724cfc5b73a0b23d4256f69d40a9f45572d16c955a7116d2b6f8', '/emoticons/robotikon-f/20.gif', '미안'),
        ('f', 21, 'EMOTICON', 'GIF', 115496, 24, true, 'b7e802a6ef93ac267e738dc4db25bdcb4ef9779a8af708848bf6352df68c6f67', '/emoticons/robotikon-f/21.gif', '파이팅'),
        ('f', 22, 'EMOTICON', 'GIF', 105997, 24, true, 'ccfb87cb1947e00730be98fc3b38ffc8c08e5fd1478c08f8f98650d7db352f9a', '/emoticons/robotikon-f/22.gif', '짝짝'),
        ('f', 23, 'EMOTICON', 'GIF', 116424, 24, true, '0840a8295ca19c168fef488e9b25fa41477aa9abba0d37061ded55bd4715ed62', '/emoticons/robotikon-f/23.gif', '싫어'),
        ('f', 24, 'EMOTICON', 'GIF', 114123, 24, true, 'eef8ae9bb6c5ff3cccad0a49cfde13c9cd30f75bb5ff6ef531a2afeb59df7edc', '/emoticons/robotikon-f/24.gif', '뭐지'),
        ('f', 25, 'EMOTICON', 'GIF', 97377, 24, true, '18a2a54c397ec728adf5e04a82b9ec0f5844c67e6d9f3702b4abc0608964988c', '/emoticons/robotikon-f/25.gif', '로딩'),
        ('f', 26, 'EMOTICON', 'GIF', 113870, 24, true, '24807bdf171c30c087597f49f7cc884627941d006282404d7fb2a6d8ef5c085b', '/emoticons/robotikon-f/26.gif', '에러'),
        ('f', 27, 'EMOTICON', 'GIF', 98729, 24, true, 'f039517586b083e0fc1b639f837382265ab4b37590cef8404c7be509f19f53a2', '/emoticons/robotikon-f/27.gif', '과열'),
        ('f', 28, 'EMOTICON', 'GIF', 87251, 24, true, 'eec69d7c76f6cddb00fee08ca8c981836c851edfa6d1c0b54a9514a9a9578091', '/emoticons/robotikon-f/28.gif', '방전'),
        ('f', 29, 'EMOTICON', 'GIF', 102323, 24, true, '5a12310440c2651b9d98394e1b972b3263fdacea045e8e430d4a5dde7ed49c6f', '/emoticons/robotikon-f/29.gif', '충전'),
        ('f', 30, 'EMOTICON', 'GIF', 93953, 24, true, '8a8f6729a6030f21ff1ef50cd856fdb8d6034ddf5ac88974ea72cd981faeb23c', '/emoticons/robotikon-f/30.gif', '업뎃'),
        ('f', 31, 'EMOTICON', 'GIF', 79591, 24, true, 'f737e7c066b520b9e68e3cf116afd14d71dc804a18f3d2335f7483dbfbda8c4f', '/emoticons/robotikon-f/31.gif', '재부팅'),
        ('f', 32, 'EMOTICON', 'GIF', 102734, 24, true, '08db261a27a6fcf16766298cf871d220d26e19c3cfa62705aa5416844ce8303b', '/emoticons/robotikon-f/32.gif', '해킹'),
        ('f', 0, 'COVER', 'PNG', 13381, 1, true, '0b69df50e50c7d5750aeb17f542fa97f3a2b43b61b3795499d9353951f5b984d', '/emoticons/robotikon-f/cover.png', NULL);

    INSERT INTO emoticon_images (id, kind, format, width, height, bytes, frames, loop_forever, content_hash, static_path, created_at)
    SELECT v_base + CASE pack WHEN 'm' THEN 100 ELSE 200 END + no, kind, format, 200, 200, bytes, frames, loop_forever, hash, path, v_now FROM seed_robotikon;

    -- 팩은 상점의 판을 먼저 가리키고(커밋 때 확인하는 외래 키), 판·차례를 넣은 뒤 판을 낸다(낸 판에는 차례를 더하지 못한다)
    INSERT INTO emoticon_packs (id, source, status, price, live_revision_id, acquire_count, published_at, created_at, updated_at) VALUES
        (v_base + 1, 'OFFICIAL', 'APPROVED', 0, v_base + 3, 0, v_now, v_now, v_now),
        (v_base + 2, 'OFFICIAL', 'APPROVED', 0, v_base + 4, 0, v_now, v_now, v_now);
    INSERT INTO emoticon_pack_revisions (id, pack_id, name, description, tags, cover_image_id, created_at) VALUES
        (v_base + 3, v_base + 1, '로보얌콘', '로보얌의 파란 로봇이 기쁨, 슬픔부터 로딩, 에러, 방전까지 감정과 로봇다운 순간을 표현하는 움직이는 로보티콘입니다.',
         ARRAY['로봇', '로보얌', '기계', '리액션', '귀여움'], v_base + 100, v_now),
        (v_base + 4, v_base + 2, '로보얌콘핑크', '로보얌의 분홍 리본 로봇이 기쁨, 슬픔부터 로딩, 에러, 방전까지 감정과 로봇다운 순간을 표현하는 움직이는 로보티콘입니다.',
         ARRAY['로봇', '로보얌', '핑크', '리액션', '귀여움'], v_base + 200, v_now);
    INSERT INTO emoticons (id, pack_id, created_at)
    SELECT v_base + CASE pack WHEN 'm' THEN 300 ELSE 400 END + no, v_base + CASE pack WHEN 'm' THEN 1 ELSE 2 END, v_now FROM seed_robotikon WHERE kind = 'EMOTICON';
    INSERT INTO emoticon_revision_items (revision_id, position, pack_id, emoticon_id, image_id, label)
    SELECT v_base + CASE pack WHEN 'm' THEN 3 ELSE 4 END, no, v_base + CASE pack WHEN 'm' THEN 1 ELSE 2 END, v_base + CASE pack WHEN 'm' THEN 300 ELSE 400 END + no,
           v_base + CASE pack WHEN 'm' THEN 100 ELSE 200 END + no, label
      FROM seed_robotikon WHERE kind = 'EMOTICON';
    UPDATE emoticon_pack_revisions SET version = 1, submitted_at = v_now WHERE id IN (v_base + 3, v_base + 4);
END
$$;
