-- 커머스 2b-4 — 판매 스위치·운영 체크리스트·사업자 정보 + 회원 알림 종류 GROUP_BUY(docs/features/commerce-groupbuy-contract.md 결정 11·12·18·56, docs/domain/commerce.md 20절).
-- 번호: V103 은 2b-3 보강 작업이 쓸 수 있어 비워 둔다(같은 날 다른 세션 — 합칠 때 다시 확인).
--   · commerce_sales_settings(1행): 공구 판매 스위치 — 설치 직후 꺼짐. 켜기는 최고 관리자 + 자동 조건 + 체크리스트 전부, 끄기는 commerce.settings 도(20절).
--   · commerce_sales_checklist: 운영 체크 항목 여섯(사업자등록·통신판매업 신고·이용약관·개인정보처리방침·PG 계약/웹훅·반품 주소) — 누가·언제 체크했는지.
--   · commerce_business_info(1행): 사업자 정보 열두 칸(결정 18 — 평문). 필수 여덟 칸(+ 등록번호 체크섬)이 차야 판매를 켤 수 있다.
--   · 시드는 꺼짐·체크 없음·빈 칸 — local 은 GroupBuyLocalSeeder 가 조건을 실제로 채운다(commerce.md 20절 「local 은 시드가 채운다」).

CREATE TABLE commerce_sales_settings (
    id            integer      PRIMARY KEY,
    groupbuy_open boolean      NOT NULL DEFAULT false,
    changed_at    timestamptz,
    changed_by    bigint       REFERENCES operators (id) ON DELETE SET NULL,
    change_reason varchar(200),
    CONSTRAINT ck_commerce_sales_settings_single_row CHECK (id = 1)
);
INSERT INTO commerce_sales_settings (id) VALUES (1);

CREATE TABLE commerce_sales_checklist (
    item_key   varchar(30)  PRIMARY KEY,
    sort_order integer      NOT NULL,
    checked    boolean      NOT NULL DEFAULT false,
    checked_by bigint       REFERENCES operators (id) ON DELETE SET NULL,
    checked_at timestamptz,
    CONSTRAINT ck_commerce_sales_checklist_key CHECK (item_key IN ('BUSINESS_REG', 'MAIL_ORDER_REPORT', 'TERMS', 'PRIVACY_POLICY', 'PG_CONTRACT', 'RETURN_ADDRESS')),
    CONSTRAINT ck_commerce_sales_checklist_who CHECK (checked = (checked_at IS NOT NULL))
);
INSERT INTO commerce_sales_checklist (item_key, sort_order) VALUES
    ('BUSINESS_REG', 0), ('MAIL_ORDER_REPORT', 1), ('TERMS', 2), ('PRIVACY_POLICY', 3), ('PG_CONTRACT', 4), ('RETURN_ADDRESS', 5);

CREATE TABLE commerce_business_info (
    id                     integer       PRIMARY KEY,
    company_name           varchar(100)  NOT NULL DEFAULT '',
    representative         varchar(50)   NOT NULL DEFAULT '',
    business_no            varchar(12)   NOT NULL DEFAULT '',
    mail_order_no          varchar(50)   NOT NULL DEFAULT '',
    mail_order_authority   varchar(50)   NOT NULL DEFAULT '',
    address                varchar(300)  NOT NULL DEFAULT '',
    phone                  varchar(30)   NOT NULL DEFAULT '',
    email                  varchar(200)  NOT NULL DEFAULT '',
    business_hours         varchar(100)  NOT NULL DEFAULT '',
    privacy_officer        varchar(100)  NOT NULL DEFAULT '',
    hosting_provider       varchar(100)  NOT NULL DEFAULT '',
    shipping_refund_policy varchar(3000) NOT NULL DEFAULT '',
    updated_at             timestamptz,
    updated_by             bigint        REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_commerce_business_info_single_row CHECK (id = 1),
    CONSTRAINT ck_commerce_business_info_no CHECK (business_no = '' OR business_no ~ '^[0-9]{3}-[0-9]{2}-[0-9]{5}$')
);
INSERT INTO commerce_business_info (id) VALUES (1);

-- ── 회원 알림 종류: 공구 GROUP_BUY 한 종류(덧붙임 kind 로 주문 확인·관심·수량 풀림·연장·차수 시작·환불 완료 등을 가른다 — 끌 수 없는 거래 알림) ──
-- 종류 목록은 V94 의 목록 ∪ GROUP_BUY. ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다).
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
                    'GROUP_BUY'));

-- ── 처리 기한 경고(P35)를 요청·단계마다 한 번만 — 1시간 점검 워커(GroupBuyDeadlineWatch)가 WARNING → CRITICAL 차례로 적는다 ──
ALTER TABLE group_buy_requests ADD COLUMN deadline_alert_level varchar(10);
ALTER TABLE group_buy_requests ADD CONSTRAINT ck_gbrq_deadline_alert CHECK (deadline_alert_level IS NULL OR deadline_alert_level IN ('WARNING', 'CRITICAL'));

-- ── 회원 권한 commerce.purchase(공구 주문 — 10-5 자격) ──────────────────────────
-- 기능이 생겨 featureReady = true(서버가 뜰 때 코드 값으로 다시 맞춘다). 지금 포인트 상점 이용(pointshop.use)을 가진 회원 역할에 같이 준다 —
-- 「상점에서 살 수 있는 회원은 공구도 주문할 수 있다」로 시작한다(본인인증·만 19세·판매 스위치는 주문 때 따로 본다). V90 group.create 와 같은 방식.
INSERT INTO permissions (code, axis, type_id, category, feature_ready, synced_at)
VALUES ('commerce.purchase', 'MEMBER', 'memberSite', 'USE', true, now())
ON CONFLICT (code) DO UPDATE SET feature_ready = true, synced_at = now();
INSERT INTO role_permissions (id, role_id, permission_code, granted_at)
SELECT ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12) | (row_number() OVER (ORDER BY rp.role_id))::bigint,
       rp.role_id, 'commerce.purchase', now()
  FROM role_permissions rp
 WHERE rp.permission_code = 'pointshop.use'
   AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_code = 'commerce.purchase');

-- ── 2b-2 보강: 옵션 이름 없이 이름으로 가리는 단품 여럿(쌀 10kg·20kg — 「상품 단품 목록이 정본」 2b-2 반영 ①)을 공구에 담을 수 있게 ──
-- 상품 단품의 조합 유일(ux_gbps_combination)은 값이 있는 단품만인데 공구 단품은 MAIN 전부였다 — 값이 빈 MAIN 이 둘이면 공구를 만들지 못했다(local 시드 시험이 잡음).
-- 공구 단품은 상품 단품당 하나(ux (공구, 상품 단품))라 값이 빈 MAIN 이 여럿이어도 겹치지 않는다.
DROP INDEX ux_group_buy_skus_combination;
CREATE UNIQUE INDEX ux_group_buy_skus_combination ON group_buy_skus (group_buy_id, value_ids) WHERE kind = 'MAIN' AND cardinality(value_ids) > 0;
