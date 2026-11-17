package com.community.grade.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * 레벨 곡선 (docs/features/grade-level-contract.md 1·6절) — 레벨 L(L≥2)에 필요한 누적 경험치 = {@code ceil(base × (L−1)^지수)}, Lv.1 = 0, 상한 없음.
 * {@code base} 는 등급 설정의 {@code grade_settings.xp_curve_base}(1~1000, 기본 10), 지수는 {@code grade_settings.xp_curve_exponent}(1.0·1.5·2.0·2.5·3.0 뿐, 기본 2.0 — V245).
 *
 * <p><b>이중 구현 주의</b>: 같은 식이 SQL 함수 {@code grade_level_xp(level, base, exponent)}·{@code grade_xp_level(xp, base, exponent)}(shared {@code V245__grade_curve_exponent.sql})에도
 * 있다 — 어느 쪽을 고치면 다른 쪽도 고친다({@code XpCurveSqlParityTest} 가 두 쪽이 같은 값을 내는지 본다).
 * 두 쪽이 부동소수 오차로 다르게 올림하지 않게 정수로 센다: 지수가 0.5 단위라 {@code k = 지수 × 2} 는 정수이고 {@code ceil(base·n^(k/2)) = ceil(√(base²·n^k))}(n = L−1) —
 * 정수 제곱근을 구하고 모자라면 1 을 더한다. 값이 long 을 넘으면 {@link Long#MAX_VALUE} 로 자른다(SQL 은 bigint 최댓값).
 */
public final class XpCurve {

    /** 곡선 기본값·범위(V196 CHECK 와 같다) */
    public static final int DEFAULT_BASE = 10;
    public static final int BASE_MIN = 1;
    public static final int BASE_MAX = 1000;
    /** 곡선 지수 — 이 다섯 값만(V245 CHECK 와 같다). 0.5 단위만 받는 까닭은 클래스 설명 */
    public static final List<BigDecimal> EXPONENTS = List.of(new BigDecimal("1.0"), new BigDecimal("1.5"), new BigDecimal("2.0"), new BigDecimal("2.5"),
            new BigDecimal("3.0"));
    public static final BigDecimal DEFAULT_EXPONENT = EXPONENTS.get(2);
    /** 레벨 상한 — 정수 범위 보호용(사실상 닿지 않는다). SQL {@code grade_xp_level} 의 상한과 같다 */
    public static final int MAX_LEVEL = 2_000_000_000;

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private XpCurve() {
    }

    /** 레벨 {@code level} 이 시작하는 누적 경험치(Lv.1 = 0). 값이 long 을 넘으면 {@link Long#MAX_VALUE} */
    public static long levelXp(int level, int base, BigDecimal exponent) {
        if (level <= 1) {
            return 0;
        }
        BigInteger b = BigInteger.valueOf(base);
        BigInteger v = b.multiply(b).multiply(BigInteger.valueOf(level - 1L).pow(doubled(exponent))); // (base·n^(k/2))² = base²·n^k
        BigInteger s = v.sqrt();
        if (s.multiply(s).compareTo(v) < 0) {
            s = s.add(BigInteger.ONE); // 올림
        }
        return s.compareTo(LONG_MAX) < 0 ? s.longValue() : Long.MAX_VALUE;
    }

    /**
     * 누적 경험치 {@code xp} 의 레벨 — {@code levelXp(L) <= xp < levelXp(L+1)} 인 L(0 이하면 1).
     * 경험치가 long 최댓값이면 그보다 1 작은 값으로 본다 — 필요 경험치가 넘쳐 최댓값으로 잘린 레벨들에 닿은 것으로 세지 않게(SQL 도 같다).
     */
    public static int levelOf(long xp, int base, BigDecimal exponent) {
        if (xp <= 0 || base <= 0) {
            return 1;
        }
        long capped = Math.min(xp, Long.MAX_VALUE - 1);
        double estimate = Math.floor(Math.pow((double) capped / base, 2.0 / doubled(exponent))) + 1; // 추정값 — 아래에서 정확한 경계로 맞춘다
        int level = (int) Math.max(1, Math.min(estimate, MAX_LEVEL));
        while (level > 1 && levelXp(level, base, exponent) > capped) {
            level--;
        }
        while (level < MAX_LEVEL && levelXp(level + 1, base, exponent) <= capped) {
            level++;
        }
        return level;
    }

    /** 저장된 곡선 값을 범위 안으로(범위 밖·없음은 기본값) */
    public static int baseOrDefault(Integer base) {
        return base == null || base < BASE_MIN || base > BASE_MAX ? DEFAULT_BASE : base;
    }

    /** 받는 지수인가(다섯 값 — 자릿수는 보지 않는다: 2 · 2.0 · 2.00 은 같은 값) */
    public static boolean isExponent(BigDecimal exponent) {
        return exponent != null && EXPONENTS.stream().anyMatch(allowed -> allowed.compareTo(exponent) == 0);
    }

    /** 저장된 지수를 다섯 값 중 하나로(그 밖·없음은 기본값) — 돌려주는 값은 늘 소수 한 자리 꼴({@link #EXPONENTS} 의 그 값) */
    public static BigDecimal exponentOrDefault(BigDecimal exponent) {
        if (exponent != null) {
            for (BigDecimal allowed : EXPONENTS) {
                if (allowed.compareTo(exponent) == 0) {
                    return allowed;
                }
            }
        }
        return DEFAULT_EXPONENT;
    }

    /** k = 지수 × 2(2~6) — 받지 않는 지수는 기본값으로 센다 */
    private static int doubled(BigDecimal exponent) {
        return exponentOrDefault(exponent).unscaledValue().intValue() / 5; // 소수 한 자리 꼴: 1.5 → 15 → 3
    }

    /**
     * 곡선 설정 한 벌(기준값·지수) — 설정 행에서 읽어 넘길 때 쓴다. 값은 늘 범위 안이다({@link #of} 로 만든다).
     *
     * @param base     기준값(1~1000)
     * @param exponent 지수({@link #EXPONENTS} 중 하나)
     */
    public record Shape(int base, BigDecimal exponent) {

        public static final Shape DEFAULT = new Shape(DEFAULT_BASE, DEFAULT_EXPONENT);

        /** 저장된 값으로(범위 밖·없음은 기본값) */
        public static Shape of(Integer base, BigDecimal exponent) {
            return new Shape(baseOrDefault(base), exponentOrDefault(exponent));
        }

        public long levelXp(int level) {
            return XpCurve.levelXp(level, base, exponent);
        }

        public int levelOf(long xp) {
            return XpCurve.levelOf(xp, base, exponent);
        }

        public Progress progress(long xp, int level) {
            return XpCurve.progress(xp, level, base, exponent);
        }

        /** 같은 곡선인가(지수는 값으로 견준다 — 자릿수 무시) */
        public boolean sameAs(Shape other) {
            return other != null && base == other.base && exponent.compareTo(other.exponent) == 0;
        }
    }

    /**
     * 회원 한 명의 레벨 진행도 — 공개 프로필·로그인 정보·내 등급·관리자 회원 상세가 같은 모양으로 쓴다.
     *
     * @param level       지금 레벨(저장된 {@code profiles.level})
     * @param xp          누적 경험치
     * @param levelXp     이 레벨이 시작한 누적 경험치
     * @param nextLevelXp 다음 레벨이 되는 누적 경험치
     */
    public record Progress(int level, long xp, long levelXp, long nextLevelXp) {
    }

    /** 저장된 경험치·레벨로 진행도를 만든다(경계는 지금 곡선으로 센다 — 곡선을 바꾸면 저장 서비스가 모든 회원의 레벨을 다시 매긴다) */
    public static Progress progress(long xp, int level, int base, BigDecimal exponent) {
        int lv = Math.max(1, level);
        return new Progress(lv, Math.max(0, xp), levelXp(lv, base, exponent), levelXp(lv == MAX_LEVEL ? lv : lv + 1, base, exponent));
    }
}
