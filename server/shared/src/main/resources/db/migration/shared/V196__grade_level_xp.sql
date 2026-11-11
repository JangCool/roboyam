-- 경험치 → 레벨 → 등급(docs/features/grade-level-contract.md). 등급 4단계를 「경험치(XP)가 레벨을 올리고, 레벨 구간마다 등급(11단계)·도트 캐릭터가 바뀌는」 구조로 바꾼다.
--
-- 1) 레벨 곡선 함수 — 레벨 L(L≥2)에 필요한 누적 XP = ceil(base × (L−1)^1.5), Lv.1 = 0. 상한 없음(정수 범위 안에서 계속).
--    ★ Java 에도 같은 식이 있다 — server/shared/.../grade/service/XpCurve.java(levelXp·levelOf). 어느 쪽을 고치면 다른 쪽도 고친다.
--    부동소수 오차가 두 쪽에서 다르게 나지 않게 정수로 셈한다: ceil(base·n·√n) = ceil(√(base²·n³)) (n = L−1) — 정수 제곱근을 구한 뒤 모자라면 1 을 더한다.
--    이 마이그레이션(기존 회원 레벨 채우기)과 관리자 등급 설정의 곡선 바꾸기(GradeRepositoryQDSL#recomputeLevels — 모든 회원 레벨 다시 매김)가 쓴다.
--    (함수는 다시 만들어도 되는 것이지만 이 파일이 바로 쓰므로 여기서 만든다. 고칠 때는 새 V 파일에서 CREATE OR REPLACE 한다.)
-- 2) grade_settings.xp_curve_base(1~1000, 기본 10) — 관리자 등급 설정의 「레벨 곡선」.
-- 3) grades.min_level(1~10000, 기본 1) · grades.sprite(도트 캐릭터 11종) · 등급 한도 10 → 11(ck_grades_level).
--    승급 조건 = 최소 레벨 AND 가입 후 일수(AND 기존 min_activity — 남기되 새 기본값 0). 첫 등급은 조건이 없다(최소 레벨 1).
-- 4) profiles.xp(누적 경험치, 0 이상) · profiles.level(1 이상). 앱이 포인트 원장 줄을 쓰는 같은 트랜잭션에서 증감한다(XpService).
--    경험치에 드는 사유 = 더함 POST·COMMENT·LIKE_BONUS·ATTENDANCE·ATTENDANCE_STREAK·POPULAR·POST_RESTORE / 뺌 POST_REVOKE·COMMENT_REVOKE·LIKE_BONUS_REVOKE
--    (Java XpService.GAIN·LOSS 와 같다). 그 밖의 사유(운영 조정·충전·가입·이벤트·사용·소멸 …)는 경험치가 아니다. 0 아래로 내려가지 않는다.
--    기존 회원은 원장에서 다시 계산해 채운다 — 회수 줄은 늘 그 앞의 적립 몫 안에서만 빼므로(실제로 뺀 양) 합계가 음수가 되는 일은 없지만 0 으로 막는다.
-- 5) 등급 목록 전환: 모든 사이트를 11단계(장인 계급)로 바꾼다(사용자 결정 2026-11-11 — 데이터를 지우지 않게).
--    시드 키 SEEDLING·REGULAR·LOYAL·VETERAN 은 서열 1·3·6·9 자리로, 운영자가 만든 키(GRADE_n)는 남은 자리 2·4·5·7·8·10·11 에 기존 서열 순으로
--    (키 유지 — 이름·최소 레벨·일수·도트는 표대로 덮어쓰고 min_activity = 0, 뱃지 색·아이콘은 그대로), 남은 자리와 지워진 시드 자리는 새 키(GRADE_<next_serial…>).
--    등급 행은 지우지 않고 회원의 grade_key 도 그대로다(키가 유지되므로).
--    등급 역할(ROLE_GRADE_<키>)은 앱(GradeRoleSynchronizer)이 원천이지만, 관리자 서버가 뜨기 전에 승급된 회원이 권한을 잃지 않게 여기서도 만든다 —
--    앱과 같은 규칙(권한은 바로 아래 등급 역할의 것을 복사, 바로 아래 역할이 없으면 만들지 않고 앱에 맡긴다, 이름은 등급 이름).
-- 6) 전환 뒤 소급 승급 한 번(올리기만) — 일수 승급 배치(R__batch_15_grade_promotion.sql)와 같은 판정(정상 회원, 수동 상향 옵션, 활동 조건 0 인 등급만).

-- ── 1) 레벨 곡선 ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION grade_level_xp(p_level integer, p_base integer) RETURNS bigint
LANGUAGE plpgsql IMMUTABLE STRICT AS $fn$
DECLARE
    v_n numeric;
    v_v numeric;
    v_s numeric;
BEGIN
    IF p_level <= 1 THEN
        RETURN 0;
    END IF;
    v_n := p_level - 1;
    v_v := (p_base::numeric * p_base) * v_n * v_n * v_n; -- (base·n^1.5)² = base²·n³
    v_s := floor(sqrt(v_v));
    WHILE v_s * v_s > v_v LOOP
        v_s := v_s - 1;
    END LOOP;
    WHILE (v_s + 1) * (v_s + 1) <= v_v LOOP
        v_s := v_s + 1;
    END LOOP;
    IF v_s * v_s < v_v THEN
        v_s := v_s + 1; -- 올림
    END IF;
    RETURN v_s::bigint;
END
$fn$;

-- 누적 XP 로 레벨을 찾는다 — 추정값에서 시작해 정확한 경계(grade_level_xp)로 맞춘다. 상한 2,000,000,000(Java XpCurve.MAX_LEVEL 과 같다).
CREATE OR REPLACE FUNCTION grade_xp_level(p_xp bigint, p_base integer) RETURNS integer
LANGUAGE plpgsql IMMUTABLE STRICT AS $fn$
DECLARE
    c_max CONSTANT bigint := 2000000000;
    v_l   bigint;
BEGIN
    IF p_xp <= 0 OR p_base <= 0 THEN
        RETURN 1;
    END IF;
    v_l := least(c_max, floor(power(p_xp::float8 / p_base, 2.0 / 3.0))::bigint + 1);
    IF v_l < 1 THEN
        v_l := 1;
    END IF;
    WHILE v_l > 1 AND grade_level_xp(v_l::int, p_base) > p_xp LOOP
        v_l := v_l - 1;
    END LOOP;
    WHILE v_l < c_max AND grade_level_xp((v_l + 1)::int, p_base) <= p_xp LOOP
        v_l := v_l + 1;
    END LOOP;
    RETURN v_l::int;
END
$fn$;

-- ── 2) 레벨 곡선 설정 ────────────────────────────────────────────────────────
ALTER TABLE grade_settings ADD COLUMN xp_curve_base integer NOT NULL DEFAULT 10;
ALTER TABLE grade_settings ADD CONSTRAINT ck_grade_settings_xp_curve_base CHECK (xp_curve_base BETWEEN 1 AND 1000);

-- ── 3) 등급 칸 ───────────────────────────────────────────────────────────────
ALTER TABLE grades DROP CONSTRAINT ck_grades_level;
ALTER TABLE grades ADD CONSTRAINT ck_grades_level CHECK (level BETWEEN 1 AND 11);
ALTER TABLE grades ADD COLUMN min_level integer NOT NULL DEFAULT 1;
ALTER TABLE grades ADD CONSTRAINT ck_grades_min_level CHECK (min_level BETWEEN 1 AND 10000);
ALTER TABLE grades ADD CONSTRAINT ck_grades_base_level CHECK (level > 1 OR min_level = 1);
ALTER TABLE grades ADD COLUMN sprite varchar(16);

-- ── 4) 회원 경험치·레벨 ──────────────────────────────────────────────────────
ALTER TABLE profiles ADD COLUMN xp bigint NOT NULL DEFAULT 0;
ALTER TABLE profiles ADD COLUMN level integer NOT NULL DEFAULT 1;
ALTER TABLE profiles ADD CONSTRAINT ck_profiles_xp CHECK (xp >= 0);
ALTER TABLE profiles ADD CONSTRAINT ck_profiles_level CHECK (level >= 1);

UPDATE profiles p
   SET xp = s.xp,
       level = grade_xp_level(s.xp, coalesce((SELECT xp_curve_base FROM grade_settings WHERE id = 1), 10))
  FROM (SELECT user_id, greatest(0, sum(delta))::bigint AS xp
          FROM point_ledger
         WHERE reason IN ('POST', 'COMMENT', 'LIKE_BONUS', 'ATTENDANCE', 'ATTENDANCE_STREAK', 'POPULAR', 'POST_RESTORE',
                          'POST_REVOKE', 'COMMENT_REVOKE', 'LIKE_BONUS_REVOKE')
         GROUP BY user_id) s
 WHERE p.user_id = s.user_id
   AND s.xp > 0;

-- ── 5) 등급 목록 전환(모든 사이트 — 사용자 결정 2026-11-11) ────────────────────
-- 11자리 표(서열 → 이름·최소 레벨·일수·도트, 새 키일 때만 쓰는 뱃지). 시드 키 자리는 1·3·6·9.
-- 키 배정: ① 남아 있는 시드 키 → 그 자리 ② 운영자가 만든 키(시드가 아닌 키)를 기존 서열 순으로 빈 자리 2·4·5·7·8·10·11 에
--   (그 수가 7개를 넘으면 — 시드를 지운 사이트에서만 생긴다 — 지워진 시드 자리까지 넣어 자리 순으로 다시 배정: 키를 지우지 않으려는 예외)
--   ③ 남은 자리 = 새 키 GRADE_<next_serial…>(지운 키를 다시 쓰지 않게, 혹시 있는 키는 건너뛴다).
-- 배정된 기존 키는 이름·최소 레벨·일수·도트를 표대로 덮어쓰고 min_activity = 0, 뱃지 색·아이콘은 그대로 둔다.
DO $conv$
DECLARE
    v_serial  integer;
    v_custom  text[];
    v_free    integer[];
    v_i       integer;
    v_key     text;
    v_base_ms bigint;
    v_seq     bigint := 0;
    v_lower   text;
    v_role    text;
    v_cnt     integer;
    r         record;
BEGIN
    CREATE TEMP TABLE v196_grade_slots (
        slot        integer PRIMARY KEY,
        seed_key    text,
        label       text    NOT NULL,
        min_level   integer NOT NULL,
        min_days    integer NOT NULL,
        sprite      text    NOT NULL,
        badge_color text    NOT NULL,
        badge_icon  text,
        grade_key   text
    ) ON COMMIT DROP;
    INSERT INTO v196_grade_slots (slot, seed_key, label, min_level, min_days, sprite, badge_color, badge_icon)
    VALUES (1, 'SEEDLING', '새싹', 1, 0, 'SPROUT', 'gray', 'pi pi-circle'),
           (2, NULL, '견습', 10, 3, 'BOLT', 'teal', 'pi pi-bolt'),
           (3, 'REGULAR', '정식', 20, 7, 'ANTENNA', 'blue', 'pi pi-star'),
           (4, NULL, '숙련', 30, 14, 'BATTERY', 'cyan', 'pi pi-shield'),
           (5, NULL, '정예', 40, 30, 'GEAR', 'indigo', 'pi pi-verified'),
           (6, 'LOYAL', '고수', 50, 60, 'HEART', 'green', 'pi pi-heart'),
           (7, NULL, '달인', 60, 90, 'LIGHTNING', 'orange', 'pi pi-lightbulb'),
           (8, NULL, '명인', 70, 180, 'STAR', 'violet', 'pi pi-star-fill'),
           (9, 'VETERAN', '대가', 80, 365, 'CROWN', 'amber', 'pi pi-crown'),
           (10, NULL, '전설', 90, 540, 'LEGEND', 'red', 'pi pi-trophy'),
           (11, NULL, '신화', 100, 730, 'MYTH', 'pink', 'pi pi-sparkles');

    -- ① 남아 있는 시드 키
    UPDATE v196_grade_slots s SET grade_key = s.seed_key
     WHERE s.seed_key IS NOT NULL AND EXISTS (SELECT 1 FROM grades g WHERE g.grade_key = s.seed_key);

    -- ② 운영자가 만든 키 — 기존 서열 순으로 빈 자리에(등급은 최대 10개라 자리는 늘 모자라지 않다)
    SELECT array_agg(grade_key ORDER BY level) INTO v_custom
      FROM grades WHERE grade_key NOT IN ('SEEDLING', 'REGULAR', 'LOYAL', 'VETERAN');
    SELECT array_agg(slot ORDER BY slot) INTO v_free FROM v196_grade_slots WHERE seed_key IS NULL;
    IF coalesce(cardinality(v_custom), 0) > cardinality(v_free) THEN
        SELECT array_agg(slot ORDER BY slot) INTO v_free FROM v196_grade_slots WHERE grade_key IS NULL;
    END IF;
    FOR v_i IN 1 .. coalesce(cardinality(v_custom), 0) LOOP
        UPDATE v196_grade_slots SET grade_key = v_custom[v_i] WHERE slot = v_free[v_i];
    END LOOP;

    -- ③ 남은 자리 = 새 키
    SELECT next_serial INTO v_serial FROM grade_settings WHERE id = 1;
    v_serial := greatest(coalesce(v_serial, 5), 5);
    FOR r IN SELECT slot FROM v196_grade_slots WHERE grade_key IS NULL ORDER BY slot LOOP
        LOOP
            v_key := 'GRADE_' || v_serial;
            v_serial := v_serial + 1;
            EXIT WHEN NOT EXISTS (SELECT 1 FROM grades WHERE grade_key = v_key);
        END LOOP;
        UPDATE v196_grade_slots SET grade_key = v_key WHERE slot = r.slot;
    END LOOP;
    UPDATE grade_settings SET next_serial = v_serial, updated_at = now() WHERE id = 1;

    -- 기존 키: 서열·이름·조건·도트를 표대로(뱃지는 그대로). 서열 유일성은 커밋 때 검사한다(DEFERRABLE)
    UPDATE grades g
       SET level = s.slot, label = s.label, min_level = s.min_level, min_days = s.min_days, min_activity = 0, sprite = s.sprite, updated_at = now()
      FROM v196_grade_slots s
     WHERE g.grade_key = s.grade_key;
    -- 새 키
    INSERT INTO grades (grade_key, level, label, min_level, min_days, min_activity, badge_color, badge_icon, sprite)
    SELECT s.grade_key, s.slot, s.label, s.min_level, s.min_days, 0, s.badge_color, s.badge_icon, s.sprite
      FROM v196_grade_slots s
     WHERE NOT EXISTS (SELECT 1 FROM grades g WHERE g.grade_key = s.grade_key);

    -- 등급 역할(파생): 있는 역할은 이름만 등급 이름으로, 없는 역할은 바로 아래 등급 역할의 권한을 복사해 만든다(GradeRoleSynchronizer 와 같은 규칙)
    v_base_ms := greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000);
    v_lower := NULL;
    FOR r IN SELECT grade_key, level, label FROM grades ORDER BY level LOOP
        v_role := 'ROLE_GRADE_' || r.grade_key;
        IF EXISTS (SELECT 1 FROM roles WHERE role_id = v_role) THEN
            UPDATE roles SET role_name = r.label, updated_at = now() WHERE role_id = v_role AND role_name <> r.label;
        ELSIF v_lower IS NOT NULL AND EXISTS (SELECT 1 FROM roles WHERE role_id = v_lower) THEN
            INSERT INTO roles (role_id, role_type_id, axis, role_name, description, scope, grant_mode, builtin, created_at, updated_at)
            VALUES (v_role, 'memberGrade', 'MEMBER', r.label, NULL, 'SITE', 'AUTO', true,
                    now() + make_interval(secs => r.level / 1000.0), now() + make_interval(secs => r.level / 1000.0));
            INSERT INTO role_permissions (id, role_id, permission_code, granted_at)
            SELECT (v_base_ms << 22) | (1023::bigint << 12) | (v_seq + row_number() OVER (ORDER BY rp.permission_code))::bigint,
                   v_role, rp.permission_code, now()
              FROM role_permissions rp
             WHERE rp.role_id = v_lower;
            GET DIAGNOSTICS v_cnt = ROW_COUNT;
            v_seq := v_seq + v_cnt;
        END IF;
        v_lower := v_role;
    END LOOP;
END
$conv$;

-- 서열 고유(ux_grades_level)는 DEFERRABLE INITIALLY DEFERRED 라 위 전환의 검사가 커밋까지 미뤄져 있다 — 그대로 grades 를 ALTER 하면
-- 「pending trigger events」로 실패한다. 미룬 검사를 지금 실행해 끝내고(서열이 겹치면 여기서 실패) 칸 제약을 건다
SET CONSTRAINTS ALL IMMEDIATE;

ALTER TABLE grades ALTER COLUMN sprite SET NOT NULL;
ALTER TABLE grades ADD CONSTRAINT ck_grades_sprite CHECK (sprite IN ('SPROUT', 'BOLT', 'ANTENNA', 'BATTERY', 'GEAR', 'HEART', 'LIGHTNING', 'STAR', 'CROWN', 'LEGEND', 'MYTH'));

-- ── 6) 소급 승급 한 번(올리기만) ─────────────────────────────────────────────
-- R__batch_15_grade_promotion.sql 과 같은 판정: 정상(ACTIVE)·미탈퇴 회원, 수동 상향 회원은 옵션이 CONTINUE_AUTO 일 때만, 활동 조건 0 인 등급만(일괄 경로의 가정),
-- 지금 등급보다 서열이 높고 최소 레벨·가입 후 일수를 채운 가장 높은 등급. 올린 회원마다 등급 변경 이력(kind=AUTO) 한 줄.
WITH opt AS (
    SELECT coalesce((SELECT manual_policy = 'CONTINUE_AUTO' FROM grade_settings WHERE id = 1), true) AS include_manual
), picked AS (
    SELECT p.user_id, p.grade_key AS from_key, t.to_key
      FROM profiles p
      JOIN users u    ON u.id = p.user_id
      JOIN grades cur ON cur.grade_key = p.grade_key
     CROSS JOIN opt
     CROSS JOIN LATERAL (
           SELECT g.grade_key AS to_key
             FROM grades g
            WHERE g.level > cur.level
              AND g.min_activity <= 0
              AND g.min_level <= p.level
              AND u.created_at <= now() - make_interval(days => g.min_days)
            ORDER BY g.level DESC
            LIMIT 1) t
     WHERE u.status = 'ACTIVE' AND u.deleted_at IS NULL
       AND (opt.include_manual OR NOT p.grade_manual)
), moved AS (
    UPDATE profiles p
       SET grade_key = picked.to_key, updated_at = now()
      FROM picked
     WHERE p.user_id = picked.user_id AND p.grade_key = picked.from_key
 RETURNING p.user_id, picked.from_key, picked.to_key
)
INSERT INTO member_grade_changes (user_id, from_grade_key, to_grade_key, kind, created_at)
SELECT user_id, from_key, to_key, 'AUTO', now() FROM moved;
