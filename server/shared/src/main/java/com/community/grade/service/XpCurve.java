package com.community.grade.service;

import java.math.BigInteger;

/**
 * 레벨 곡선 (docs/features/grade-level-contract.md 1절) — 레벨 L(L≥2)에 필요한 누적 경험치 = {@code ceil(base × (L−1)^1.5)}, Lv.1 = 0, 상한 없음.
 * {@code base} 는 등급 설정의 {@code grade_settings.xp_curve_base}(1~1000, 기본 10).
 *
 * <p><b>이중 구현 주의</b>: 같은 식이 SQL 함수 {@code grade_level_xp}·{@code grade_xp_level}(shared {@code V196__grade_level_xp.sql})에도 있다 — 어느 쪽을 고치면 다른 쪽도 고친다.
 * 두 쪽이 부동소수 오차로 다르게 올림하지 않게 정수로 센다: {@code ceil(base·n·√n) = ceil(√(base²·n³))}(n = L−1) — 정수 제곱근을 구하고 모자라면 1 을 더한다.
 */
public final class XpCurve {

    /** 곡선 기본값·범위(V196 CHECK 와 같다) */
    public static final int DEFAULT_BASE = 10;
    public static final int BASE_MIN = 1;
    public static final int BASE_MAX = 1000;
    /** 레벨 상한 — 정수 범위 보호용(사실상 닿지 않는다). SQL {@code grade_xp_level} 의 상한과 같다 */
    public static final int MAX_LEVEL = 2_000_000_000;

    private XpCurve() {
    }

    /** 레벨 {@code level} 이 시작하는 누적 경험치(Lv.1 = 0). 값이 long 을 넘으면 {@link Long#MAX_VALUE} */
    public static long levelXp(int level, int base) {
        if (level <= 1) {
            return 0;
        }
        BigInteger n = BigInteger.valueOf(level - 1L);
        BigInteger b = BigInteger.valueOf(base);
        BigInteger v = b.multiply(b).multiply(n).multiply(n).multiply(n); // (base·n^1.5)² = base²·n³
        BigInteger s = v.sqrt();
        if (s.multiply(s).compareTo(v) < 0) {
            s = s.add(BigInteger.ONE); // 올림
        }
        return s.bitLength() < 63 ? s.longValue() : Long.MAX_VALUE;
    }

    /** 누적 경험치 {@code xp} 의 레벨 — {@code levelXp(L) <= xp < levelXp(L+1)} 인 L(0 이하면 1) */
    public static int levelOf(long xp, int base) {
        if (xp <= 0 || base <= 0) {
            return 1;
        }
        double estimate = Math.floor(Math.pow((double) xp / base, 2.0 / 3.0)) + 1; // 추정값 — 아래에서 정확한 경계로 맞춘다
        int level = (int) Math.max(1, Math.min(estimate, MAX_LEVEL));
        while (level > 1 && levelXp(level, base) > xp) {
            level--;
        }
        while (level < MAX_LEVEL && levelXp(level + 1, base) <= xp) {
            level++;
        }
        return level;
    }

    /** 저장된 곡선 값을 범위 안으로(범위 밖·없음은 기본값) */
    public static int baseOrDefault(Integer base) {
        return base == null || base < BASE_MIN || base > BASE_MAX ? DEFAULT_BASE : base;
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
    public static Progress progress(long xp, int level, int base) {
        int lv = Math.max(1, level);
        return new Progress(lv, Math.max(0, xp), levelXp(lv, base), levelXp(lv == MAX_LEVEL ? lv : lv + 1, base));
    }
}
