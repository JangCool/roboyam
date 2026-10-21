-- 등급 설정 — docs/features/roles-contract.md 2단계, docs/domain/operator-permissions.md "회원 역할"(등급)
--
-- 등급은 4단계 고정(profiles.grade 1~4 + grade_rules)이 아니라 **순서 있는 목록(2~10단계)** 이고 등급 설정(grades)이 원천이다.
-- 등급마다 이름·서열·뱃지(색·아이콘)·승급 조건(가입 후 일수, 글+댓글 수)을 갖고, 전역 옵션(수동으로 올린 회원의 이후 자동 승급)은 grade_settings(행 하나)에 둔다.
-- 등급 역할(roles 의 ROLE_GRADE_<grade_key>)은 앱이 이 목록에서 만든다 — 이 파일은 표만 만든다.
--
-- 기존 데이터 이전:
--   · grade_rules(4행)의 이름·일수·활동 수를 grades 시드로 옮긴다(기존 값 1~4 → SEEDLING·REGULAR·LOYAL·VETERAN, 뱃지는 화면 목업 시드와 같다).
--   · profiles.grade(1~4) → profiles.grade_key 로 옮긴 뒤 grade 칸과 CHECK(1~4)를 지운다.
--   · grade_rules 는 이 파일이 더는 쓰지 않는다(앱도 읽지 않는다). 데이터 손실을 피해 표는 지우지 않고 남긴다 — 정리는 후속 마이그레이션에서.
-- 이 마이그레이션은 profiles.grade 를 지우므로 공개 서버(api)·관리자 서버(admin)를 이 버전으로 함께 올려야 한다(이전 버전 앱은 그 칸을 읽는다).

-- ── 등급 ─────────────────────────────────────────────────────────────────────
-- grade_key: 불변 식별자(시드 SEEDLING·REGULAR·LOYAL·VETERAN, 새 등급은 서버가 GRADE_<번호> 로 발급). 역할 아이디가 ROLE_GRADE_ + 키(30자 이하)라 19자까지.
-- level: 서열 1..N(목록 순서). 같은 서열은 없다 — 저장 한 번에 순서를 바꾸므로 커밋 때 검사한다(DEFERRABLE).
-- 첫 등급(level 1)은 기본 등급이라 조건이 없다.
CREATE TABLE grades (
    grade_key    varchar(19)  PRIMARY KEY,
    level        integer      NOT NULL,
    label        varchar(8)   NOT NULL,                          -- 표시 이름(운영자가 입력). 고유 검사는 앱이 한다(대소문자 무시)
    min_days     integer      NOT NULL DEFAULT 0,                -- 가입 후 일수
    min_activity integer      NOT NULL DEFAULT 0,                -- 글+댓글 수
    badge_color  varchar(10)  NOT NULL,                          -- 색 이름(허용 목록은 앱이 정한다)
    badge_icon   varchar(30),                                    -- PrimeIcons 클래스(예: pi pi-star). NULL = 아이콘 없음
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_grades_key CHECK (grade_key ~ '^[A-Z][A-Z0-9_]{0,18}$'),
    CONSTRAINT ck_grades_level CHECK (level BETWEEN 1 AND 10),
    CONSTRAINT ck_grades_label CHECK (char_length(label) BETWEEN 1 AND 8 AND label = btrim(label)),
    CONSTRAINT ck_grades_min_days CHECK (min_days BETWEEN 0 AND 3650),
    CONSTRAINT ck_grades_min_activity CHECK (min_activity BETWEEN 0 AND 100000),
    CONSTRAINT ck_grades_base CHECK (level > 1 OR (min_days = 0 AND min_activity = 0)),
    CONSTRAINT ck_grades_badge_color CHECK (badge_color ~ '^[a-z]{3,10}$'),
    CONSTRAINT ck_grades_badge_icon CHECK (badge_icon IS NULL OR badge_icon ~ '^pi pi-[a-z0-9-]{1,24}$'),
    CONSTRAINT ux_grades_level UNIQUE (level) DEFERRABLE INITIALLY DEFERRED
);

-- 시드: 기존 4단계. 이름·조건은 grade_rules 값을 쓰고(없으면 기본값), 첫 등급의 조건은 0 이다.
INSERT INTO grades (grade_key, level, label, min_days, min_activity, badge_color, badge_icon)
SELECT s.grade_key,
       s.level,
       coalesce(r.label, s.label),
       CASE WHEN s.level = 1 THEN 0 ELSE least(greatest(coalesce(r.min_days, s.min_days), 0), 3650) END,
       CASE WHEN s.level = 1 THEN 0 ELSE least(greatest(coalesce(r.min_activity, s.min_activity), 0), 100000) END,
       s.badge_color,
       s.badge_icon
  FROM (VALUES
            (1, 'SEEDLING', '새싹',     0,   0, 'gray',  'pi pi-circle'),
            (2, 'REGULAR',  '일반',     7,  10, 'blue',  'pi pi-star'),
            (3, 'LOYAL',    '단골',    30,  50, 'green', 'pi pi-heart'),
            (4, 'VETERAN',  '베테랑',  90, 200, 'amber', 'pi pi-crown')
       ) AS s (level, grade_key, label, min_days, min_activity, badge_color, badge_icon)
  LEFT JOIN grade_rules r ON r.grade = s.level;

-- ── 등급 옵션 (행이 하나뿐이다) ─────────────────────────────────────────────
-- manual_policy: 운영자가 등급을 수동으로 올린 회원의 이후 동작 — CONTINUE_AUTO 계속 자동 승급(기본) · STOP_AUTO 자동 승급 중지. 수동으로 올린 회원에게만 적용된다.
-- next_serial: 새 등급 키 GRADE_<번호> 의 다음 번호 — 등급을 지워도 키를 다시 쓰지 않는다(감사 기록·역할 아이디가 헷갈리지 않게).
CREATE TABLE grade_settings (
    id            integer      PRIMARY KEY,
    manual_policy varchar(16)  NOT NULL DEFAULT 'CONTINUE_AUTO',
    next_serial   integer      NOT NULL DEFAULT 5,
    updated_by    bigint       REFERENCES operators (id) ON DELETE SET NULL,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_grade_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_grade_settings_policy CHECK (manual_policy IN ('CONTINUE_AUTO', 'STOP_AUTO')),
    CONSTRAINT ck_grade_settings_serial CHECK (next_serial >= 5)
);
INSERT INTO grade_settings (id) VALUES (1);

-- ── 회원의 등급 ──────────────────────────────────────────────────────────────
ALTER TABLE profiles ADD COLUMN grade_key varchar(19);
UPDATE profiles SET grade_key = CASE grade WHEN 1 THEN 'SEEDLING' WHEN 2 THEN 'REGULAR' WHEN 3 THEN 'LOYAL' WHEN 4 THEN 'VETERAN' ELSE 'SEEDLING' END;
ALTER TABLE profiles ALTER COLUMN grade_key SET NOT NULL;
ALTER TABLE profiles ALTER COLUMN grade_key SET DEFAULT 'SEEDLING';
ALTER TABLE profiles ADD CONSTRAINT fk_profiles_grade FOREIGN KEY (grade_key) REFERENCES grades (grade_key);
ALTER TABLE profiles DROP CONSTRAINT ck_profiles_grade;
ALTER TABLE profiles DROP COLUMN grade;

-- 운영자가 등급을 수동으로 올린 회원 표시 — 등급 옵션(manual_policy)이 이 회원들에게만 적용된다. 수동 변경 기능은 후속(grade.override)이라 지금은 모두 false.
ALTER TABLE profiles ADD COLUMN grade_manual boolean NOT NULL DEFAULT false;
CREATE INDEX ix_profiles_grade ON profiles (grade_key);

-- 회원 목록의 기본 정렬(가입 시각 최근순)
CREATE INDEX ix_users_created ON users (created_at DESC, id DESC);
