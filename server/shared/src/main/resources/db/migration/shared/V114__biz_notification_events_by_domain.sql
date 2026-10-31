-- 사업자 신청 건 운영자 알림을 업무(광고 / 핫딜)별로 나눈다 — docs/plans/biz-ad-hotdeal/04-ia-review.md S5.
--   BIZ_APPLICATION_REVIEW_PENDING   → AD_REVIEW_PENDING(ad.manage, /ad-reviews)      + HOTDEAL_REVIEW_PENDING(hotdeal.manage, /hotdeal-reviews)
--   BIZ_APPLICATION_REFUND_REQUESTED → AD_REFUND_REQUESTED(deal-order.refund, /ad-reviews) + HOTDEAL_REFUND_REQUESTED(deal-order.refund, /hotdeal-reviews)
--
-- 이벤트 정의의 원천은 코드(AdminNotificationEvent)이고 서버가 뜰 때 NotificationCatalogSynchronizer 가 notification_events 를 맞춘다. 코드에서 사라진 옛 이름의 줄은
-- 그때 운영자가 정한 값(사용·이메일·브라우저·권한자 전원에게 보내기 · 전달 도구 · 추가 팀 · 개인 끄기)과 함께 지워지므로, 그보다 먼저 도는 이 마이그레이션이 그 값을
-- 새 두 이름으로 옮긴다(옛 이벤트가 하던 일을 두 이벤트가 나눠 맡으므로 둘 다 같은 설정에서 시작한다 — 끈 이벤트는 둘 다 꺼진 채, 켠 이메일은 둘 다 켜진 채).
-- AD_REVIEW_PENDING 은 예약(준비 중) 줄로 이미 있을 수 있다 — 준비 중이라 운영자가 정한 값이 없으므로 옛 이벤트의 값으로 덮는다.
--
-- 사용자 문구(notification_messages · notification_message_selections)는 ID(Snowflake)를 앱이 만들어 SQL 로 복제하지 않는다 — 옛 이벤트의 뜻을 이어받는 광고 쪽으로
-- 옮기고(같은 채널에 광고 쪽 선택이 이미 있으면 옛 것이 이긴다), 핫딜 쪽은 기본 문구에서 시작한다.
-- 이미 받은 알림(admin_notifications)은 묶음 키(= 신청 건 ID)로 건의 종류를 찾아 새 이름·새 문구 키·새 링크로 옮긴다(옛 이름은 코드에 없어 서랍에서 보이지 않게 된다).
-- 사업자 인증 알림(BUSINESS_APPLICATION_PENDING · BUSINESS_RE_REVIEW_PENDING)의 이미 받은 알림 링크도 /businesses?… → /business-reviews?… 로 바꾼다(「인증 심사」 화면을 나눴다).
-- 새 설치(옛 줄이 없음)에서는 아무 일도 하지 않는다.

-- ── 이벤트 설정 ──────────────────────────────────────────────────────────────
INSERT INTO notification_events (code, enabled, email_enabled, browser_enabled, default_recipients, updated_at, updated_by)
SELECT m.new_code, e.enabled, e.email_enabled, e.browser_enabled, e.default_recipients, e.updated_at, e.updated_by
  FROM notification_events e
  JOIN (VALUES ('BIZ_APPLICATION_REVIEW_PENDING', 'AD_REVIEW_PENDING'),
               ('BIZ_APPLICATION_REVIEW_PENDING', 'HOTDEAL_REVIEW_PENDING'),
               ('BIZ_APPLICATION_REFUND_REQUESTED', 'AD_REFUND_REQUESTED'),
               ('BIZ_APPLICATION_REFUND_REQUESTED', 'HOTDEAL_REFUND_REQUESTED')) AS m (old_code, new_code)
    ON e.code = m.old_code
    ON CONFLICT (code) DO UPDATE
   SET enabled            = EXCLUDED.enabled,
       email_enabled      = EXCLUDED.email_enabled,
       browser_enabled    = EXCLUDED.browser_enabled,
       default_recipients = EXCLUDED.default_recipients,
       updated_at         = EXCLUDED.updated_at,
       updated_by         = EXCLUDED.updated_by;

INSERT INTO notification_event_channels (event_code, channel_id, created_at)
SELECT m.new_code, c.channel_id, c.created_at
  FROM notification_event_channels c
  JOIN (VALUES ('BIZ_APPLICATION_REVIEW_PENDING', 'AD_REVIEW_PENDING'),
               ('BIZ_APPLICATION_REVIEW_PENDING', 'HOTDEAL_REVIEW_PENDING'),
               ('BIZ_APPLICATION_REFUND_REQUESTED', 'AD_REFUND_REQUESTED'),
               ('BIZ_APPLICATION_REFUND_REQUESTED', 'HOTDEAL_REFUND_REQUESTED')) AS m (old_code, new_code)
    ON c.event_code = m.old_code
    ON CONFLICT DO NOTHING;

INSERT INTO notification_event_teams (event_code, team_id, created_at)
SELECT m.new_code, t.team_id, t.created_at
  FROM notification_event_teams t
  JOIN (VALUES ('BIZ_APPLICATION_REVIEW_PENDING', 'AD_REVIEW_PENDING'),
               ('BIZ_APPLICATION_REVIEW_PENDING', 'HOTDEAL_REVIEW_PENDING'),
               ('BIZ_APPLICATION_REFUND_REQUESTED', 'AD_REFUND_REQUESTED'),
               ('BIZ_APPLICATION_REFUND_REQUESTED', 'HOTDEAL_REFUND_REQUESTED')) AS m (old_code, new_code)
    ON t.event_code = m.old_code
    ON CONFLICT DO NOTHING;

-- 개인 끄기 — 옛 이벤트를 끈 운영자는 새 두 이벤트도 끈 채로
INSERT INTO operator_notification_mutes (operator_id, event_code, created_at)
SELECT u.operator_id, m.new_code, u.created_at
  FROM operator_notification_mutes u
  JOIN (VALUES ('BIZ_APPLICATION_REVIEW_PENDING', 'AD_REVIEW_PENDING'),
               ('BIZ_APPLICATION_REVIEW_PENDING', 'HOTDEAL_REVIEW_PENDING'),
               ('BIZ_APPLICATION_REFUND_REQUESTED', 'AD_REFUND_REQUESTED'),
               ('BIZ_APPLICATION_REFUND_REQUESTED', 'HOTDEAL_REFUND_REQUESTED')) AS m (old_code, new_code)
    ON u.event_code = m.old_code
    ON CONFLICT DO NOTHING;

-- ── 사용자 문구 — 광고 쪽으로 옮긴다 ──────────────────────────────────────────
DELETE FROM notification_message_selections s
 USING notification_message_selections o,
       (VALUES ('BIZ_APPLICATION_REVIEW_PENDING', 'AD_REVIEW_PENDING'),
               ('BIZ_APPLICATION_REFUND_REQUESTED', 'AD_REFUND_REQUESTED')) AS m (old_code, new_code)
 WHERE s.target_type = 'EVENT' AND s.target_code = m.new_code
   AND o.target_type = 'EVENT' AND o.target_code = m.old_code AND o.channel = s.channel;

UPDATE notification_message_selections s
   SET target_code = m.new_code
  FROM (VALUES ('BIZ_APPLICATION_REVIEW_PENDING', 'AD_REVIEW_PENDING'),
               ('BIZ_APPLICATION_REFUND_REQUESTED', 'AD_REFUND_REQUESTED')) AS m (old_code, new_code)
 WHERE s.target_type = 'EVENT' AND s.target_code = m.old_code;

UPDATE notification_messages g
   SET target_code = m.new_code
  FROM (VALUES ('BIZ_APPLICATION_REVIEW_PENDING', 'AD_REVIEW_PENDING'),
               ('BIZ_APPLICATION_REFUND_REQUESTED', 'AD_REFUND_REQUESTED')) AS m (old_code, new_code)
 WHERE g.target_type = 'EVENT' AND g.target_code = m.old_code;

-- ── 이미 받은 알림 ──────────────────────────────────────────────────────────
UPDATE admin_notifications n
   SET event_code = m.new_code,
       title_key  = 'notification.event.' || m.new_code || '.title',
       body_key   = 'notification.event.' || m.new_code || '.body',
       link_path  = m.path || '?id=' || a.id
  FROM biz_applications a
  JOIN (VALUES ('BIZ_APPLICATION_REVIEW_PENDING', 'AD', 'AD_REVIEW_PENDING', '/ad-reviews'),
               ('BIZ_APPLICATION_REVIEW_PENDING', 'HOTDEAL', 'HOTDEAL_REVIEW_PENDING', '/hotdeal-reviews'),
               ('BIZ_APPLICATION_REFUND_REQUESTED', 'AD', 'AD_REFUND_REQUESTED', '/ad-reviews'),
               ('BIZ_APPLICATION_REFUND_REQUESTED', 'HOTDEAL', 'HOTDEAL_REFUND_REQUESTED', '/hotdeal-reviews')) AS m (old_code, domain, new_code, path)
    ON m.domain = CASE WHEN a.kind IN ('HOTDEAL_POST', 'HOTDEAL_PLACEMENT') THEN 'HOTDEAL' ELSE 'AD' END
 WHERE n.event_code = m.old_code
   AND n.dedupe_key = a.id::text;

UPDATE admin_notifications
   SET link_path = '/business-reviews' || substr(link_path, length('/businesses') + 1)
 WHERE event_code IN ('BUSINESS_APPLICATION_PENDING', 'BUSINESS_RE_REVIEW_PENDING')
   AND link_path LIKE '/businesses?%';

-- ── 옛 이름 정리(서버가 떠도 지울 줄 — 옮긴 뒤 여기서 지운다. 전달 도구·추가 팀은 외래키 CASCADE) ──
DELETE FROM operator_notification_mutes WHERE event_code IN ('BIZ_APPLICATION_REVIEW_PENDING', 'BIZ_APPLICATION_REFUND_REQUESTED');
DELETE FROM notification_events WHERE code IN ('BIZ_APPLICATION_REVIEW_PENDING', 'BIZ_APPLICATION_REFUND_REQUESTED');
