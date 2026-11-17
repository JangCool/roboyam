-- 체험단·칭호 점검 고치기(docs/features/trial-group-contract.md 「점검 고치기」, 2026-11-17 — 점검 결정 D2·D3·D4·D5·D6·D8·D10·D11·D12·D13·D14)
--
-- 바뀌는 표·CHECK: trial_photos(비공개 암호문) · trial_settings(사진 보관 일수) · trial_address_views(운영자 주소 열람 기록, 새 표) ·
-- report_cases.action(협찬 표시 붙임) · member_notifications.type(입고 부족) · 권한 trial.address.reveal(역할 부여).

-- ── 1) 체험단 사진 비공개(D3) — 미디어 저장소 공개 키 → 다시 인코딩한 JPEG 의 DataCipher 바이트 암호문(용도 trial.photo) ─────────────────
-- 구매 인증 사진(purchase_proofs)과 같은 방식: 올릴 때 다시 인코딩(EXIF·위치 정보 빠짐) → encryptBytes → content(bytea)·content_type. 보기는 권한 API(관리자·광고주·회원 본인 파손 사진)만.
-- 보관: 모집이 끝나거나(CLOSED) 취소된(CANCELLED) 뒤 trial_settings.photo_keep_days(기본 90일) — 그 모집에 판정 안 된 파손 신고가 있으면 판정 뒤 같은 일수가 지나야 —
-- 지나면 하루 배치가 content NULL · purged_at(줄은 남긴다 — 「사진이 있었다」는 처리 기록).
--
-- ⚠ 이미 있는 줄(image_key — 공개 저장소의 trial/… 파일)의 처리: V241 은 같은 날(2026-11-17) 들어와 운영 DB 에 체험단 사진이 없다(로컬·개발 데이터뿐).
--   저장소 파일을 SQL 에서 읽어 암호문으로 옮길 방법이 없으므로 옛 줄은 「파기됨」으로 둔다(content NULL · purged_at = 이 마이그레이션 시각) — 처리 기록·사진 수는 그대로 남는다.
--   로컬·개발 저장소에 남은 옛 파일(키 앞머리 groupbuy/trial/… — 붙지 않은 사진은 배치가 이미 지웠다)은 필요하면 손으로 지운다(DB 가 더는 가리키지 않는다).
ALTER TABLE trial_photos
    ADD COLUMN content       bytea,
    ADD COLUMN content_type  varchar(20),
    ADD COLUMN purged_at     timestamptz;
UPDATE trial_photos SET purged_at = now() WHERE purged_at IS NULL;
ALTER TABLE trial_photos DROP COLUMN image_key;
ALTER TABLE trial_photos ADD CONSTRAINT ck_trial_photos_content CHECK ((purged_at IS NULL) = (content IS NOT NULL) AND (content IS NULL) = (content_type IS NULL));
CREATE INDEX ix_trial_photos_purge ON trial_photos (trial_id) WHERE purged_at IS NULL AND attached_at IS NOT NULL;
COMMENT ON COLUMN trial_photos.content IS '다시 인코딩한 JPEG 의 DataCipher 바이트 암호문(용도 trial.photo) — 보관 기한 뒤 NULL(purged_at) — V243';

-- photo_keep_days: 모집이 끝나거나 취소된 뒤(열린 파손 신고가 있으면 판정 뒤) 체험단 사진을 지우기까지 일수(기본 90, 1~365)
ALTER TABLE trial_settings ADD COLUMN photo_keep_days integer NOT NULL DEFAULT 90;
ALTER TABLE trial_settings ADD CONSTRAINT ck_trial_settings_photo_keep CHECK (photo_keep_days BETWEEN 1 AND 365);

-- ── 2) 운영자 주소 열람 기록(D4) — 공동구매 배송지 열람(group_buy_address_views)과 같은 뜻 ─────────────────────────────────
-- 운영자 모집 상세의 받는 분·연락처·주소는 가린 값이고, 「주소 보기」(권한 trial.address.reveal · 사유 5~200자)를 누르면 한 줄을 남긴 뒤에만 평문을 준다(감사 TRIAL_ADDRESS_REVEAL 도 같은 트랜잭션).
CREATE TABLE trial_address_views (
    id              bigint        PRIMARY KEY,
    trial_id        bigint        NOT NULL REFERENCES trials (id),
    participant_id  bigint        NOT NULL REFERENCES trial_participants (id),
    operator_id     bigint        NOT NULL,
    reason          varchar(200)  NOT NULL,
    created_at      timestamptz   NOT NULL,
    CONSTRAINT ck_trial_address_views_reason CHECK (char_length(btrim(reason)) BETWEEN 5 AND 200)
);
CREATE INDEX ix_trial_address_views_participant ON trial_address_views (participant_id, created_at DESC);
COMMENT ON TABLE trial_address_views IS '체험단 운영자 주소 열람 기록 — V243';

-- ── 3) 신고 사건 조치: V136 목록 ∪ SPONSOR_LABEL(D2 — 협찬 숨김 확인할 일에서 「확인」해 글에 협찬 표시를 붙이며 닫은 사건) ─────────────────
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다
ALTER TABLE report_cases DROP CONSTRAINT ck_report_cases_action;
ALTER TABLE report_cases ADD CONSTRAINT ck_report_cases_action
    CHECK (action IS NULL OR action IN ('HIDE', 'DELETE', 'KEEP_HIDDEN', 'DEACTIVATE', 'DEAL_END', 'SANCTION_ONLY', 'SPONSOR_LABEL', 'DISMISS'));

-- ── 4) 회원 알림 종류: V242 목록 ∪ TRIAL_STOCK_SHORT(D10 — 운영자 입고 수량이 뽑힌 인원보다 적다, 광고주 회원에게) ───────────────────
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
                    'EMOTICON_PACK',
                    'EMOTICON_PURCHASE',
                    'AVATAR_UPLOAD_REVIEWED',
                    'REWARD_EVENT_GRANTED', 'REWARD_EVENT_REVOKED',
                    'POST_STATUS_CHANGED',
                    'TITLE_GRANTED',
                    'TRIAL_SELECTED', 'TRIAL_NOT_SELECTED', 'TRIAL_SHIPPED', 'TRIAL_REVIEW_DUE', 'TRIAL_RETURN_DUE', 'TRIAL_UNRETURNED',
                    'TRIAL_DAMAGE_REPORTED', 'TRIAL_DAMAGE_DECIDED', 'TRIAL_CANCELLED_BY_OPERATOR', 'TRIAL_STOCK_SHORT',
                    'PURCHASE_PROOF_APPROVED', 'PURCHASE_PROOF_REJECTED'));

-- ── 5) 권한 trial.address.reveal(D4 — 운영자 체험단 주소 평문 열람, 민감 조치) ─────────────────────────────────────────────
-- 권한 행은 admin 의 PermissionCatalogSynchronizer 가 맞추지만 이 파일이 먼저 돌 수 있어 여기서도 넣는다(있으면 그대로 — 선례 V241). 칸은 같은 유형·단위의 ad.contact.reveal 을 본뜬다.
-- 공구 배송지 열람(commerce.address.reveal) 또는 체험단 처리(trial.manage)를 가진 운영자 역할에 준다. trial.review 만 가진 역할은 받지 않는다(주소를 볼 일이 없다).
INSERT INTO permissions (code, axis, type_id, category, feature_ready, synced_at)
SELECT 'trial.address.reveal', p.axis, p.type_id, p.category, true, now()
  FROM permissions p
 WHERE p.code = 'ad.contact.reveal'
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (id, role_id, permission_code, granted_at)
SELECT ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12) | (300 + row_number() OVER (ORDER BY g.role_id))::bigint,
       g.role_id, 'trial.address.reveal', now()
  FROM (SELECT DISTINCT rp.role_id
          FROM role_permissions rp
          JOIN roles r ON r.role_id = rp.role_id AND r.axis = 'OPERATOR'
         WHERE rp.permission_code IN ('commerce.address.reveal', 'trial.manage')) g
 WHERE EXISTS (SELECT 1 FROM permissions WHERE code = 'trial.address.reveal')
   AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = g.role_id AND x.permission_code = 'trial.address.reveal');
