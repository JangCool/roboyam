-- 소모임 3단계(매니저) — docs/features/group-contract.md 「전체 개발 계획」 3단계(community docs/api-groups.md 5-1 을 로보얌에 맞춘 것).
-- 매니저의 표·칸(group_members.role = 'MANAGER' · manager_perms · groups.manager_limit · group_settings 매니저 구획)은 V90 가 이미 만들었다 — 이 파일은 알림 종류만 더한다.
-- 번호는 develop 병합(2026-10-26, 커머스 1-3 의 V89 다음) 때 V90~V93 으로 다시 매겼다.

-- ── 회원 알림 종류: 매니저 알림 5종(api-groups.md 7절 「매니저 알림 5종」) ──
-- GROUP_MANAGER_APPOINTED(임명 → 임명된 사람) · GROUP_MANAGER_PERMISSIONS_CHANGED(맡은 일 바뀜 → 그 매니저) · GROUP_MANAGER_REMOVED(해임 → 해임된 사람)
-- · GROUP_MANAGER_RESIGNED(사퇴 → 모임장) · GROUP_KICKED_BY_MANAGER(매니저가 강퇴 → 모임장). 모두 끌 수 없다. 알림 설정 행은 넣지 않는다(V90 와 같다).
-- 종류 목록은 V91 의 목록 ∪ 위 5종.
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다).
--
-- type 칸을 varchar(30) → varchar(40) 로 넓힌다: GROUP_MANAGER_PERMISSIONS_CHANGED 가 33자다(community 의 notifications.type 도 varchar(40),
-- 회원 이벤트 설정 member_event_settings.code 도 varchar(40)). varchar 길이를 늘리는 것은 표를 다시 쓰지 않는다(카탈로그만 바뀐다).
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ALTER COLUMN type TYPE varchar(40);
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'DM', 'NICKNAME_IMAGE_REVIEWED', 'OPINION_PIN_REQUESTED',
                    'OPINION_PIN_REVIEWED', 'POST_REMOVED', 'COMMENT_REMOVED', 'REPORT_RESULT', 'FEATURE_RESTRICTED', 'POINTS_EXPIRING',
                    'POINT_SHOP_REFUND_APPROVED', 'POINT_SHOP_REFUND_REJECTED',
                    'GROUP_APPROVED', 'GROUP_REJECTED', 'GROUP_JOIN_REQUEST', 'GROUP_JOIN_APPROVED', 'GROUP_JOIN_REJECTED', 'GROUP_KICKED',
                    'GROUP_DISSOLVED', 'GROUP_LEADER_TRANSFERRED', 'GROUP_DEACTIVATED', 'GROUP_REACTIVATED',
                    'GROUP_POST_UNPUBLISHED',
                    'GROUP_MANAGER_APPOINTED', 'GROUP_MANAGER_PERMISSIONS_CHANGED', 'GROUP_MANAGER_REMOVED', 'GROUP_MANAGER_RESIGNED',
                    'GROUP_KICKED_BY_MANAGER'));
