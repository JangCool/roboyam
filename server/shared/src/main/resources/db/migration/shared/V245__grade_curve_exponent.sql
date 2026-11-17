-- 레벨 곡선 지수·최소 레벨 재배치(docs/features/grade-level-contract.md 6절, 확정 2026-11-17).
-- 지수 1.5 에서는 Lv.100 이 9,851 XP 라 가입 일수(730일)만 채우면 2년에 최고 등급이 된다 — 지수를 설정으로 빼고 기본을 2.0 으로 올린다(Lv.100 = 98,010 XP).
--
-- 1) grade_settings.xp_curve_exponent(1.0·1.5·2.0·2.5·3.0 뿐, 기본 2.0) — 관리자 등급 설정의 「곡선 지수」. 모든 사이트가 2.0 에서 시작한다.
-- 2) 레벨 곡선 함수 — 인자 셋짜리로 새로 만들고 둘짜리(V196, 지수 1.5 고정)는 지운다(낡은 호출이 1.5 로 계산한 채 숨지 않게).
--    레벨 L(L≥2)에 필요한 누적 XP = ceil(base × (L−1)^지수), Lv.1 = 0.
--    ★ Java 에도 같은 식이 있다 — server/shared/.../grade/service/XpCurve.java(levelXp·levelOf). 어느 쪽을 고치면 다른 쪽도 고친다(XpCurveSqlParityTest 가 두 쪽을 견준다).
--    지수가 0.5 단위라 k = 지수 × 2 는 정수다: ceil(base·n^(k/2)) = ceil(√(base²·n^k)) (n = L−1) — 부동소수 power() 를 쓰지 않고 numeric 정수로 곱한 뒤 정수 제곱근을 구하고
--    모자라면 1 을 더한다. 값이 bigint 를 넘으면 bigint 최댓값으로 자른다(Java 는 Long.MAX_VALUE — V196 은 여기서 넘쳐 오류가 났다).
--    받지 않는 지수는 오류다(조용히 다른 곡선으로 세지 않는다 — 칸은 CHECK 가 막는다).
-- 3) 전 회원 레벨을 새 곡선(저장된 기준값 · 지수 2.0)으로 다시 매긴다. 레벨 숫자는 내려간다. **등급은 내리지 않는다**(grade_key 는 건드리지 않는다).
-- 4) 최소 레벨 재배치 — 등급이 11개(서열 1~11)이고 서열별 최소 레벨이 V196 기본값(1·10·20·…·100) 그대로인 사이트만 1·5·10·20·30·40·50·60·75·90·100 으로 바꾼다.
--    등급 키는 사이트마다 달라 서열(grades.level)로 본다. 운영자가 고친 사이트는 그대로 둔다(관리자 화면의 등급별 미리보기 표를 보고 고친다).
-- 5) 닉네임 이미지 신청 조건 — 4)의 재배치를 한 사이트이고 설정이 기본값(GRADE · VETERAN) 그대로면 서열 5(정예) 등급 키로 낮춘다(VETERAN 이 이미 서열 5 이하면 그대로).
-- 6) 낮아진 문턱(견습 270 → 160 XP, 정식 829 → 810 XP)으로 오를 회원은 **여기서 올리지 않는다** — 매일 도는 일수 승급 배치(R__batch_15_grade_promotion.sql, 기본 04:30)가
--    저장된 레벨(profiles.level)과 최소 레벨(grades.min_level)로 판정해 올린다(올리기만, 이력 kind=AUTO, 정상 회원, 수동 지정 옵션 존중 — 같은 규칙). 그 전에 레벨이 오르면
--    앱의 즉시 경로(XpService → GradePromotionService.promoteOnLevelUp)가 먼저 올린다.

-- ── 1) 곡선 지수 설정 ────────────────────────────────────────────────────────
ALTER TABLE grade_settings ADD COLUMN xp_curve_exponent numeric(2,1) NOT NULL DEFAULT 2.0;
ALTER TABLE grade_settings ADD CONSTRAINT ck_grade_settings_xp_curve_exponent CHECK (xp_curve_exponent IN (1.0, 1.5, 2.0, 2.5, 3.0));

-- ── 2) 레벨 곡선 함수(인자 셋) ───────────────────────────────────────────────
CREATE FUNCTION grade_level_xp(p_level integer, p_base integer, p_exponent numeric) RETURNS bigint
LANGUAGE plpgsql IMMUTABLE STRICT AS $fn$
DECLARE
    c_top CONSTANT numeric := 9223372036854775807; -- bigint 최댓값(Java Long.MAX_VALUE)
    v_k   integer;
    v_n   numeric;
    v_v   numeric;
    v_s   numeric;
BEGIN
    IF p_exponent NOT IN (1.0, 1.5, 2.0, 2.5, 3.0) THEN
        RAISE EXCEPTION '레벨 곡선 지수는 1.0·1.5·2.0·2.5·3.0 뿐이다: %', p_exponent;
    END IF;
    IF p_level <= 1 THEN
        RETURN 0;
    END IF;
    v_k := (p_exponent * 2)::integer; -- 2~6
    v_n := p_level - 1;
    v_v := p_base::numeric * p_base;  -- (base·n^(k/2))² = base²·n^k — 정수 곱셈이라 정확하다
    FOR i IN 1 .. v_k LOOP
        v_v := v_v * v_n;
    END LOOP;
    v_s := floor(sqrt(v_v)); -- 추정값 — 아래에서 정확한 정수 제곱근으로 맞춘다
    WHILE v_s * v_s > v_v LOOP
        v_s := v_s - 1;
    END LOOP;
    WHILE (v_s + 1) * (v_s + 1) <= v_v LOOP
        v_s := v_s + 1;
    END LOOP;
    IF v_s * v_s < v_v THEN
        v_s := v_s + 1; -- 올림
    END IF;
    RETURN least(v_s, c_top)::bigint;
END
$fn$;

-- 누적 XP 로 레벨을 찾는다 — 추정값에서 시작해 정확한 경계(grade_level_xp)로 맞춘다. 상한 2,000,000,000(Java XpCurve.MAX_LEVEL 과 같다).
-- XP 가 bigint 최댓값이면 그보다 1 작은 값으로 본다 — 필요 XP 가 넘쳐 최댓값으로 잘린 레벨들에 닿은 것으로 세지 않게(Java 도 같다).
CREATE FUNCTION grade_xp_level(p_xp bigint, p_base integer, p_exponent numeric) RETURNS integer
LANGUAGE plpgsql IMMUTABLE STRICT AS $fn$
DECLARE
    c_max CONSTANT bigint := 2000000000;
    v_xp  bigint;
    v_l   bigint;
BEGIN
    IF p_exponent NOT IN (1.0, 1.5, 2.0, 2.5, 3.0) THEN
        RAISE EXCEPTION '레벨 곡선 지수는 1.0·1.5·2.0·2.5·3.0 뿐이다: %', p_exponent;
    END IF;
    IF p_xp <= 0 OR p_base <= 0 THEN
        RETURN 1;
    END IF;
    v_xp := least(p_xp, 9223372036854775806);
    -- 추정값은 실수 범위에서 먼저 상한으로 자른다(지수 1.0 에서는 XP 만큼 커져 bigint 로 바꿀 때 넘친다)
    v_l := least(c_max::float8, floor(power(v_xp::float8 / p_base, 1.0 / p_exponent::float8)))::bigint + 1;
    IF v_l < 1 THEN
        v_l := 1;
    END IF;
    IF v_l > c_max THEN
        v_l := c_max;
    END IF;
    WHILE v_l > 1 AND grade_level_xp(v_l::int, p_base, p_exponent) > v_xp LOOP
        v_l := v_l - 1;
    END LOOP;
    WHILE v_l < c_max AND grade_level_xp((v_l + 1)::int, p_base, p_exponent) <= v_xp LOOP
        v_l := v_l + 1;
    END LOOP;
    RETURN v_l::int;
END
$fn$;

DROP FUNCTION grade_xp_level(bigint, integer);
DROP FUNCTION grade_level_xp(integer, integer);

-- ── 3) 전 회원 레벨 다시 매기기(등급은 그대로) ───────────────────────────────
UPDATE profiles p
   SET level = c.level
  FROM (SELECT pr.user_id,
               grade_xp_level(pr.xp, coalesce((SELECT xp_curve_base FROM grade_settings WHERE id = 1), 10),
                              coalesce((SELECT xp_curve_exponent FROM grade_settings WHERE id = 1), 2.0)) AS level
          FROM profiles pr) c
 WHERE p.user_id = c.user_id
   AND p.level <> c.level;

-- ── 4)·5) 최소 레벨 재배치 · 닉네임 이미지 기본 조건 ─────────────────────────
DO $relocate$
DECLARE
    v_ranks  integer[];
    v_levels integer[];
    v_key    text;
BEGIN
    SELECT array_agg(level ORDER BY level), array_agg(min_level ORDER BY level) INTO v_ranks, v_levels FROM grades;
    IF v_ranks IS DISTINCT FROM ARRAY[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]
       OR v_levels IS DISTINCT FROM ARRAY[1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100] THEN
        RETURN; -- 운영자가 고친 사이트(또는 등급 수가 다른 사이트) — 그대로 둔다
    END IF;

    UPDATE grades g
       SET min_level = n.min_level, updated_at = now()
      FROM (VALUES (1, 1), (2, 5), (3, 10), (4, 20), (5, 30), (6, 40), (7, 50), (8, 60), (9, 75), (10, 90), (11, 100)) AS n(level, min_level)
     WHERE g.level = n.level
       AND g.min_level <> n.min_level;

    -- 닉네임 이미지: 기본값(GRADE · VETERAN) 그대로일 때만, 서열 5 등급으로 「낮춘다」(VETERAN 이 서열 5 이하면 올리는 셈이라 그대로 둔다)
    SELECT grade_key INTO v_key FROM grades WHERE level = 5;
    UPDATE nickname_image_settings s
       SET grade_key = v_key
     WHERE s.id = 1
       AND s.mode = 'GRADE'
       AND s.grade_key = 'VETERAN'
       AND NOT EXISTS (SELECT 1 FROM grades v WHERE v.grade_key = 'VETERAN' AND v.level <= 5);
END
$relocate$;
