-- 움직이는 아바타 · 아바타 올리기 · 올리기 변경권 — docs/features/commerce-motion-avatar-contract.md(화면 확정 2026-11-06, 규칙 0~5 · 「API 계약」).
-- ⚠ 가번호 V157 — develop 최신 V156, profile-privacy 세션이 V157 예정. 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다.
-- ⚠ 합칠 때 합집합 확인 — 회원 알림 종류 CHECK 를 다시 만든다(V146 목록 ∪ AVATAR_UPLOAD_REVIEWED). 그 뒤에 이 CHECK 를 또 만드는 쪽이 모든 값을 담아야 한다.
--
-- 무엇이 바뀌나
--   · 품목 종류 NICKNAME_IMAGE_TICKET(닉네임 이미지 변경권)·AVATAR_UPLOAD_TICKET(아바타 변경권) — 둘 다 소모형만. 시드 두 품목(2,000P·3,000P).
--   · 움직이는 아바타 8종 품목(PROFILE_AVATAR · 영구 · 3,000P, code avatar-<id>) — 카탈로그 avatars/manifest.json 의 motion 카테고리와 같은 ID·이름·차례(시험이 지킨다).
--   · 보유 줄(user_items)의 예약 칸 — 올리기 변경권 한 장이 어느 신청에 예약됐는지·어느 신청이 썼는지:
--       reserved_at          예약된 동안만(그 장은 OWNED 그대로 — 쓸 수도 환불할 수도 없다). 반려·본인 취소면 지워지고(반환), 승인되면 지워지며 USED 가 된다(사용).
--       ref_type · ref_id    그 신청(NICKNAME_IMAGE · AVATAR_UPLOAD + 신청 ID). 반환되면 지운다, 쓰면 남긴다(어느 신청이 썼나). 새 신청이 대기 신청을 대체하면 ref_id 만 새 신청으로 옮긴다.
--     신청 하나에 장 하나(부분 유일 색인) · 예약된 장은 OWNED 만 · 이어진 OWNED 장은 늘 예약 중 · 쓴 장의 신청은 바꾸지 않는다(트리거).
--     이 칸이 생기기 전의 닉네임 이미지 대기 신청은 예약한 장이 없다(반환할 것도 없다 — 계약 「API 계약」).
--   · 아바타 올리기 신청(avatar_upload_requests) — 닉네임 이미지 신청(V72)을 본뜬 상태 모델. APPROVED 한 줄 = 그 회원의 「내가 올린 아바타」(새로 승인되면 앞 줄은 REMOVED).
--   · 프로필의 마지막 정지 아바타(profiles.last_still_avatar) — 움직이는 꾸미기는 하나만(규칙 1~3): 닉네임 이미지 다시 쓰기·움직이는 닉네임 이미지 승인 때 아바타가 돌아갈 곳.
--     값은 카탈로그 아바타 ID 또는 upload-mine(정지 그림을 올렸을 때), 없으면 basic-default 로 본다. 지금 고른 아바타는 모두 정지라 그대로 옮겨 둔다.
--   · 회원 알림 종류 AVATAR_UPLOAD_REVIEWED(아바타 올리기 심사 결과 — 승인·반려·해제). 운영자 알림 AVATAR_UPLOAD_PENDING 은 코드가 원천(표를 고치지 않는다).
-- 새 표는 users 에 외래키를 걸지 않는다(커머스 결정 5 — 회원 행은 익명화, 탈퇴 정리가 줄을 지운다).

-- ── 품목 종류 ────────────────────────────────────────────────────────────────
ALTER TABLE point_shop_items DROP CONSTRAINT ck_point_shop_items_kind;
ALTER TABLE point_shop_items ADD CONSTRAINT ck_point_shop_items_kind
    CHECK (kind IN ('NICKNAME_CHANGE_TICKET', 'NICKNAME_COLOR', 'PROFILE_BORDER', 'PROFILE_AVATAR', 'NICKNAME_IMAGE_TICKET', 'AVATAR_UPLOAD_TICKET'));
ALTER TABLE point_shop_items DROP CONSTRAINT ck_point_shop_items_kind_usage;
ALTER TABLE point_shop_items ADD CONSTRAINT ck_point_shop_items_kind_usage CHECK (
    (kind IN ('NICKNAME_CHANGE_TICKET', 'NICKNAME_IMAGE_TICKET', 'AVATAR_UPLOAD_TICKET') AND usage_type = 'CONSUMABLE')
    OR (kind IN ('NICKNAME_COLOR', 'PROFILE_BORDER') AND usage_type IN ('PERMANENT', 'PERIOD'))
    OR (kind = 'PROFILE_AVATAR' AND usage_type = 'PERMANENT'));

-- ── 보유 줄의 예약 칸 ────────────────────────────────────────────────────────
ALTER TABLE user_items
    ADD COLUMN reserved_at timestamptz,
    ADD COLUMN ref_type    varchar(20),
    ADD COLUMN ref_id      bigint;
ALTER TABLE user_items ADD CONSTRAINT ck_user_items_ref CHECK ((ref_type IS NULL) = (ref_id IS NULL)
                                                               AND (ref_type IS NULL OR (ref_type IN ('NICKNAME_IMAGE', 'AVATAR_UPLOAD') AND usage_type = 'CONSUMABLE')));
-- 예약된 장은 가진(OWNED) 장이고 신청이 있다 · 신청에 이어진 가진 장은 예약 중이다(반환하면 신청 칸도 지운다)
ALTER TABLE user_items ADD CONSTRAINT ck_user_items_reserved CHECK ((reserved_at IS NULL OR (status = 'OWNED' AND ref_id IS NOT NULL))
                                                                    AND (ref_id IS NULL OR status <> 'OWNED' OR reserved_at IS NOT NULL));
-- 신청 하나에 장 하나 — 동시에 두 장을 잡지 못한다(서비스가 프로필 행을 잠가 먼저 막고 이 색인이 끝까지 막는다)
CREATE UNIQUE INDEX ux_user_items_ref ON user_items (ref_type, ref_id) WHERE ref_id IS NOT NULL;

-- V86 의 보호 + 쓴 장의 신청은 바꾸지 않는다
CREATE OR REPLACE FUNCTION user_items_guard() RETURNS trigger LANGUAGE plpgsql AS $$
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
    IF OLD.status <> 'OWNED' AND (NEW.ref_type IS DISTINCT FROM OLD.ref_type OR NEW.ref_id IS DISTINCT FROM OLD.ref_id) THEN
        RAISE EXCEPTION 'user_items ref of a used ticket is kept';
    END IF;
    RETURN NEW;
END $$;

-- ── 아바타 올리기 신청 ───────────────────────────────────────────────────────
-- status(닉네임 이미지 V72 와 같은 뜻):
--   PENDING   심사 대기 — 회원당 하나(부분 유일 색인). 변경권 한 장이 예약돼 있다. 새로 올리면 앞 대기 신청은 CANCELED(예약된 장은 새 신청으로 넘어간다).
--   APPROVED  「내가 올린 아바타」 — 회원당 하나(부분 유일 색인). 변경권을 썼다. 새 신청이 승인되면 앞 그림은 REMOVED.
--   REJECTED  반려(reject_reason 필수) — 변경권 반환. 파일은 다음 신청 때까지 둔다(반려 화면이 보인다).
--   REVOKED   운영자가 「내가 올린 아바타」를 해제(revoke_reason 필수) — 변경권 반환 없음. 파일은 지운다.
--   CANCELED  회원이 대기 신청을 취소(변경권 반환)했거나 새 신청으로 대체됨. 파일은 지운다.
--   REMOVED   회원이 지웠거나(변경권 반환 없음) 다른 그림이 승인돼 바뀜. 파일은 지운다.
-- image_key·still_key: 저장소(ImageStorage) 키 — 파일을 지운 신청은 NULL. still_key 는 움직이는 GIF 의 첫 장면(PNG)만.
-- bytes 는 올린 파일 크기(300KB 한도가 재는 것), width·height 는 저장한 그림 크기, frames 는 GIF 장면 수(그 밖 1).
CREATE TABLE avatar_upload_requests (
    id            bigint       PRIMARY KEY,
    user_id       bigint       NOT NULL,
    status        varchar(10)  NOT NULL,
    image_key     varchar(200),
    still_key     varchar(200),
    content_type  varchar(40)  NOT NULL,
    bytes         integer      NOT NULL,
    width         integer      NOT NULL,
    height        integer      NOT NULL,
    frames        integer      NOT NULL DEFAULT 1,
    reject_reason varchar(200),
    revoke_reason varchar(200),
    reviewed_by   bigint       REFERENCES operators (id) ON DELETE SET NULL,
    reviewed_at   timestamptz,
    revoked_by    bigint       REFERENCES operators (id) ON DELETE SET NULL,
    revoked_at    timestamptz,
    ended_at      timestamptz,
    created_at    timestamptz  NOT NULL,
    CONSTRAINT ck_avatar_upload_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED', 'CANCELED', 'REMOVED')),
    CONSTRAINT ck_avatar_upload_requests_size CHECK (bytes > 0 AND width > 0 AND height > 0 AND frames >= 1),
    CONSTRAINT ck_avatar_upload_requests_reasons CHECK ((status <> 'REJECTED' OR reject_reason IS NOT NULL) AND (status <> 'REVOKED' OR revoke_reason IS NOT NULL))
);
CREATE UNIQUE INDEX ux_avatar_upload_requests_pending ON avatar_upload_requests (user_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX ux_avatar_upload_requests_approved ON avatar_upload_requests (user_id) WHERE status = 'APPROVED';
-- 내 최근 신청 · 오늘 올린 수(속도 제한) · 관리자 목록(상태별) · 작성자 표시(승인된 그림을 여러 회원 한 번에)
CREATE INDEX ix_avatar_upload_requests_user ON avatar_upload_requests (user_id, id DESC);
CREATE INDEX ix_avatar_upload_requests_status ON avatar_upload_requests (status, id);

-- ── 프로필의 마지막 정지 아바타 ──────────────────────────────────────────────
ALTER TABLE profiles ADD COLUMN last_still_avatar varchar(70);
UPDATE profiles SET last_still_avatar = substr(profile_image, 8) WHERE profile_image LIKE 'avatar:%';

-- ── 시드 ─────────────────────────────────────────────────────────────────────
-- ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — V86 과 같은 마이그레이션용 노드) + 순번: 변경권 두 품목 0·1, 움직이는 아바타 2..9(카탈로그 차례).
-- 움직이는 아바타의 sort_order 는 유료 아바타 안 차례(V86 의 306종 1..306 다음 307..314 — 카탈로그 manifest.json 에서도 맨 뒤 8줄).
DO $$
DECLARE
    v_base bigint := ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22) | (1023::bigint << 12);
BEGIN
    INSERT INTO point_shop_items (id, code, kind, usage_type, name, name_en, description, description_en, price_point, sort_order)
    VALUES (v_base, 'nickname-image-ticket', 'NICKNAME_IMAGE_TICKET', 'CONSUMABLE', '닉네임 이미지 변경권', 'Nickname image ticket',
            '닉네임 이미지를 한 번 신청할 수 있어요. 반려되면 돌려받아요.', 'Apply for a nickname image once. Returned if rejected.', 2000, 1),
           (v_base + 1, 'avatar-upload-ticket', 'AVATAR_UPLOAD_TICKET', 'CONSUMABLE', '아바타 변경권', 'Avatar upload ticket',
            '직접 만든 그림(정지·움직이는 GIF)을 아바타로 한 번 신청할 수 있어요. 반려되면 돌려받아요.',
            'Apply once to use your own image (still or animated GIF) as your avatar. Returned if rejected.', 3000, 2);

    INSERT INTO point_shop_items (id, code, kind, usage_type, name, description, description_en, avatar_id, price_point, sort_order)
    SELECT v_base + 1 + a.seq, 'avatar-' || a.avatar_id, 'PROFILE_AVATAR', 'PERMANENT', a.name, '프로필 동그라미에 움직이는 이 아바타를 보여요.',
           'Shows this animated avatar in your profile circle.', a.avatar_id, 3000, 306 + a.seq
      FROM (VALUES
        (1, 'motion-robot-blink', '깜빡 로보'),
        (2, 'motion-robot-antenna', '안테나 로보'),
        (3, 'motion-robot-bob', '둥실 로보'),
        (4, 'motion-robot-wave', '손인사 로보'),
        (5, 'motion-robot-heart', '두근 로보'),
        (6, 'motion-robot-rainbow', '무지개 로보'),
        (7, 'motion-robot-sparkle', '반짝별 로보'),
        (8, 'motion-robot-scan', '레이더 로보')
      ) AS a(seq, avatar_id, name);
END $$;

-- ── 회원 알림 종류: V146 목록 ∪ AVATAR_UPLOAD_REVIEWED ──
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
                    'EMOTICON_PACK',
                    'EMOTICON_PURCHASE',
                    'AVATAR_UPLOAD_REVIEWED'));
