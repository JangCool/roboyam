-- 유료 체험단 결제 연결(docs/features/trial-group-contract.md 「유료 결제 연결」, 2026-11-17 — 점검 결정 D1·D7·D9 금액·L5)
--
-- 뽑기로 견적이 굳고 금액 > 0 이면 그 광고 신청 건(kind TRIAL)이 관리자 광고 심사 큐에 「승인 대기」로 나타나고, 승인 → 기존 온라인/수동 결제 → SCHEDULED 를 탄다.
-- 승인 금액은 뽑기 때 굳힌 견적 그대로다(요금표로 다시 셈하지 않는다). 결제가 끝나기 전에는 입고·발송을 앱이 막는다(409 TRIAL_PAYMENT_PENDING).
-- 금액 0 이면 결제 흐름 밖 — 건은 SUBMITTED·견적 0 으로 남고(심사 큐는 견적 > 0 인 TRIAL 만 보인다 — 앱), 모집이 끝나면 정리(CANCELED)한다.
--
-- 고치지 않은 CHECK(이유):
--   · ck_ba_approved_amount(승인 공급가 ≥ 1) — 0원 체험단은 승인하지 않으므로 그대로 둔다(0원 승인 줄이 생기지 않는다).
--   · ck_ba_source_operator·ck_ba_source_business — 체험단 건은 운영자 등록(OPERATOR) 그대로이고 user_id(광고주 회원)는 뽑기 때 앱이 넣는다. OPERATOR 쪽 CHECK 는 user_id 를 막지 않는다.
--   · ck_bai_amount(단가 ≥ 0 · 수량 ≥ 1 · 공급가 = 단가 × 수량) — 발송비 줄도 이 모양(1명당 발송비 × 인원)이라 그대로 맞는다.

-- ── 1) 발송비 품목 TRIAL_SHIPPING ─────────────────────────────────────────────────────────────
-- 체험단 금액 = 상품 줄(TRIAL: 단가 × 인원, 건당이면 × 1) + 운영자가 대신 보낼 때 발송비 줄(TRIAL_SHIPPING: 1명당 발송비 × 인원). 신청 건 줄은 (건, 상품)마다 하나(ux_biz_items)이고
-- product 가 요금표의 외래키라, 발송비를 따로 줄로 담으려면 요금표에 품목 행이 있어야 한다 — 그래서 상품 이름 CHECK(V241 목록 ∪ TRIAL_SHIPPING)를 다시 만든다.
-- 이 행은 금액 원천이 아니다(1명당 발송비는 TRIAL 행·가격 기간표의 shipping_fee_per_person). 받기 꺼짐 · 단가 0 · 건당(POST — 한도 없음). 관리자 요금표 화면(코드의 차례 목록)에는 나오지 않는다.
-- ck_bpr_trial_terms 는 「TRIAL 만 단위·발송비 칸이 있다」라 이 행(두 칸 NULL)도 그대로 맞는다.
ALTER TABLE biz_price_rules DROP CONSTRAINT ck_bpr_product;
ALTER TABLE biz_price_rules ADD CONSTRAINT ck_bpr_product CHECK (product IN ('WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'HALF_BANNER', 'WING_BANNER',
                                                                         'TEXT_LINE', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY', 'HOTDEAL_POST', 'TRIAL', 'TRIAL_SHIPPING'));
INSERT INTO biz_price_rules (product, unit, unit_price, capacity, max_days, max_lead_days, accepting) VALUES
    ('TRIAL_SHIPPING', 'POST', 0, NULL, 1, 365, false);

-- ── 2) 뽑힌 줄 운영자 취소(발송 전에만 — D9) ─────────────────────────────────────────────────────
-- cancelled_at: 운영자가 뽑힌 줄을 취소한 때(주소 사본은 그때 파기) · cancelled_by: 운영자(모집 통째 취소가 광고 쪽 만료·정지에서 오면 NULL) · cancel_reason: 사유(운영자가 적은 글, 모집 취소면 코드).
-- 취소한 줄은 보낸 적이 없다(발송 전에만 취소) — 자동 끝남 판정·발송·사용기 연결에서 빠진다(앱).
ALTER TABLE trial_participants
    ADD COLUMN cancelled_at   timestamptz,
    ADD COLUMN cancelled_by   bigint,
    ADD COLUMN cancel_reason  varchar(200);
ALTER TABLE trial_participants
    ADD CONSTRAINT ck_trial_participants_cancelled CHECK ((cancelled_at IS NULL) = (cancel_reason IS NULL) AND (cancelled_at IS NULL OR shipped_at IS NULL)
                                                         AND (cancel_reason IS NULL OR char_length(btrim(cancel_reason)) BETWEEN 1 AND 200));

-- ── 3) 회원 알림 종류: V241 목록 ∪ TRIAL_CANCELLED_BY_OPERATOR(뽑힌 체험단이 운영자 취소·모집 취소로 없어졌다) ───────────────────
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
                    'TRIAL_DAMAGE_REPORTED', 'TRIAL_DAMAGE_DECIDED', 'TRIAL_CANCELLED_BY_OPERATOR',
                    'PURCHASE_PROOF_APPROVED', 'PURCHASE_PROOF_REJECTED'));

-- ── 4) 이미 뽑기를 마친 유료 체험단 건 — 광고주 회원을 넣는다(온라인 결제·사업자 알림 — 앱이 뽑기 때 넣는 값과 같다) ─────────────────────
-- 광고주(ad_advertisers)에 이어진 사업자 신원의 회원. 신원이 없거나 지운 광고주는 NULL(수동 결제만). 발송비 줄은 채우지 않는다 —
-- V241 은 같은 날 들어와 운영 DB 에 유료 체험단이 없다(로컬·개발 데이터만 — 견적 합계 칸은 이미 발송비를 담고 있다).
UPDATE biz_applications b
   SET user_id = bp.user_id
  FROM trials t
  JOIN ad_advertisers ad ON ad.id = t.advertiser_id
  JOIN business_profiles bp ON bp.id = ad.business_profile_id AND bp.erased_at IS NULL
 WHERE b.id = t.biz_application_id AND b.kind = 'TRIAL' AND b.status = 'SUBMITTED' AND b.quote_total > 0 AND b.user_id IS NULL;
