-- 로보티콘 후속 결정(2026-11-05 — docs/features/emoticon-contract.md 「후속 결정 반영(2026-11-05)」). ⚠ 가번호 — V142~V145 과 함께 develop 에 합칠 때 번호를 옮긴다.
--   · 회원 알림 종류 ∪ EMOTICON_PURCHASE(구매 완료·자동 취소·셀프 환불 접수·예외 환불 — 결정 2)
--   · 공휴일 표 public_holidays + 2026·2027 한국 공휴일 시드(작가 정산 지급일을 다음 업무일로 미루고, 지급 기록의 이체일을 업무일만 받는다 — 결정 4)
-- 작가 자기 팩(결정 1)은 파생 규칙(creator_id = 나)이라 스키마 변경이 없다. 미성년 작가(결정 3)·탈퇴 작가 정산 정보 파기(결정 5 — R__batch_41)도 이 파일에 없다.

-- ── 회원 알림 종류: V142 목록 ∪ EMOTICON_PURCHASE ──
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다). develop 의 V142·V143(2026-11-05 기준)은 이 CHECK 를 건드리지 않는다.
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
                    'EMOTICON_PURCHASE'));

-- ── 공휴일 표 ──
-- 날짜 하나에 한 줄(한국 날짜). 종류: HOLIDAY 공휴일 · SUBSTITUTE 대체공휴일 · ELECTION 선거일 · TEMPORARY 임시공휴일. 업무일 = 토·일이 아니고 이 표에 없는 날(BusinessDayCalendar).
-- ⚠ 해마다 다음 해를 새 V 마이그레이션으로 더해야 한다(우주항공청 월력요항은 그 전해 6월쯤 나온다). 임시공휴일·선거일도 정해지면 더한다. 표에 없는 해는 주말만 거른다(서버 WARN 한 번).
CREATE TABLE public_holidays (
    holiday_date date         PRIMARY KEY,
    name         varchar(40)  NOT NULL,
    kind         varchar(12)  NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_public_holidays_kind CHECK (kind IN ('HOLIDAY', 'SUBSTITUTE', 'ELECTION', 'TEMPORARY')),
    CONSTRAINT ck_public_holidays_name CHECK (length(btrim(name)) BETWEEN 1 AND 40)
);

-- 2026 — 한국천문연구원 천문우주지식정보 「2026년 월력요항」(astro.kasi.re.kr calendarData?search_year=2026) + 노동절·제헌절 공휴일 지정
--        (인사혁신처 보도자료 「올해부터 노동절, 제헌절 공휴일로」 korea.kr pressReleaseView newsId=156758983 — 관공서의 공휴일에 관한 규정 개정 2026-04-28 국무회의 의결,
--        2026-05-01(금)·2026-07-17(금)은 대체공휴일이 생기지 않는다) + 전국동시지방선거 2026-06-03(수).
INSERT INTO public_holidays (holiday_date, name, kind) VALUES
    ('2026-01-01', '1월 1일', 'HOLIDAY'),
    ('2026-02-16', '설날 전날', 'HOLIDAY'),
    ('2026-02-17', '설날', 'HOLIDAY'),
    ('2026-02-18', '설날 다음날', 'HOLIDAY'),
    ('2026-03-01', '3·1절', 'HOLIDAY'),
    ('2026-03-02', '대체공휴일(3·1절)', 'SUBSTITUTE'),
    ('2026-05-01', '노동절', 'HOLIDAY'),
    ('2026-05-05', '어린이날', 'HOLIDAY'),
    ('2026-05-24', '부처님오신날', 'HOLIDAY'),
    ('2026-05-25', '대체공휴일(부처님오신날)', 'SUBSTITUTE'),
    ('2026-06-03', '전국동시지방선거', 'ELECTION'),
    ('2026-06-06', '현충일', 'HOLIDAY'),
    ('2026-07-17', '제헌절', 'HOLIDAY'),
    ('2026-08-15', '광복절', 'HOLIDAY'),
    ('2026-08-17', '대체공휴일(광복절)', 'SUBSTITUTE'),
    ('2026-09-24', '추석 전날', 'HOLIDAY'),
    ('2026-09-25', '추석', 'HOLIDAY'),
    ('2026-09-26', '추석 다음날', 'HOLIDAY'),
    ('2026-10-03', '개천절', 'HOLIDAY'),
    ('2026-10-05', '대체공휴일(개천절)', 'SUBSTITUTE'),
    ('2026-10-09', '한글날', 'HOLIDAY'),
    ('2026-12-25', '기독탄신일', 'HOLIDAY');

-- 2027 — 우주항공청 「2027년 월력요항」(2026-06-29 발표, 한국천문연구원 astro.kasi.re.kr calendarData?search_year=2027 의 공휴일 표 — 노동절·제헌절과 그 대체공휴일 포함).
--        월력요항에 없는 선거일·임시공휴일은 없다(2027 년 정기 선거 없음).
INSERT INTO public_holidays (holiday_date, name, kind) VALUES
    ('2027-01-01', '1월 1일', 'HOLIDAY'),
    ('2027-02-06', '설날 전날', 'HOLIDAY'),
    ('2027-02-07', '설날', 'HOLIDAY'),
    ('2027-02-08', '설날 다음날', 'HOLIDAY'),
    ('2027-02-09', '대체공휴일(설날)', 'SUBSTITUTE'),
    ('2027-03-01', '3·1절', 'HOLIDAY'),
    ('2027-05-01', '노동절', 'HOLIDAY'),
    ('2027-05-03', '대체공휴일(노동절)', 'SUBSTITUTE'),
    ('2027-05-05', '어린이날', 'HOLIDAY'),
    ('2027-05-13', '부처님오신날', 'HOLIDAY'),
    ('2027-06-06', '현충일', 'HOLIDAY'),
    ('2027-07-17', '제헌절', 'HOLIDAY'),
    ('2027-07-19', '대체공휴일(제헌절)', 'SUBSTITUTE'),
    ('2027-08-15', '광복절', 'HOLIDAY'),
    ('2027-08-16', '대체공휴일(광복절)', 'SUBSTITUTE'),
    ('2027-09-14', '추석 전날', 'HOLIDAY'),
    ('2027-09-15', '추석', 'HOLIDAY'),
    ('2027-09-16', '추석 다음날', 'HOLIDAY'),
    ('2027-10-03', '개천절', 'HOLIDAY'),
    ('2027-10-04', '대체공휴일(개천절)', 'SUBSTITUTE'),
    ('2027-10-09', '한글날', 'HOLIDAY'),
    ('2027-10-11', '대체공휴일(한글날)', 'SUBSTITUTE'),
    ('2027-12-25', '기독탄신일', 'HOLIDAY'),
    ('2027-12-27', '대체공휴일(기독탄신일)', 'SUBSTITUTE');

-- 2028 — 넣지 않았다: 2028년 월력요항은 아직 발표되지 않았다(2027년 6월쯤 예정). 발표되면 새 V 마이그레이션으로 더한다(제23대 국회의원 선거일 2028-04-12 도 그때 확인해 넣는다).
