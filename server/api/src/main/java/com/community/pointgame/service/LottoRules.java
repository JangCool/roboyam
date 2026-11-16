package com.community.pointgame.service;

import com.community.pointgame.dto.request.LottoDrawRequest;
import com.community.pointgame.entity.LottoPickMode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.random.RandomGenerator;

/**
 * 로또 번호 뽑기 규칙(point-games-contract 「로또 뽑기」·「뽑는 방법」·「내 번호」) — 값·보관 한도와 요청 검사, 번호 만들기(서버 난수). 상태가 없다.
 *
 * <ul>
 *   <li>1~45 중 6개. 1세트 {@value #SET_COST}P · {@value #BUNDLE_SIZE}세트 한 번에 {@value #BUNDLE_COST}P. 한 회원 {@value #STORAGE_LIMIT}세트까지.</li>
 *   <li>검사(칸 차례 {@code sets → mode → fixed → excluded}, 칸마다 첫 문구): 세트 수 1 또는 5 · 방법 AUTO|SEMI_AUTO · 자동은 고정·제외가 비어야 · 고정 1~45 서로 다른 0~5개 ·
 *       반자동은 고정 1개 이상 · 제외 1~45 서로 다름 · 고정·제외 겹침 금지 · 45 − 고정 − 제외 ≥ 6 − 고정 · <b>한 번에 뽑은 세트끼리 겹치지 않게</b> 만들 수 있는 조합 수 ≥ 세트 수.</li>
 *   <li>번호: 고정 번호 + 나머지(고정·제외를 뺀 수)에서 모자란 수만큼 무작위, 작은 수부터. 조합 수가 적으면 모든 조합을 섞어 앞에서부터, 많으면 겹치면 다시 뽑는다.</li>
 * </ul>
 */
public final class LottoRules {

    public static final int SET_COST = 20;
    public static final int BUNDLE_SIZE = 5;
    public static final int BUNDLE_COST = 80;
    public static final int STORAGE_LIMIT = 50;
    public static final int MAX_FIXED = 5;
    public static final int PICK = 6;
    public static final int MIN_NUMBER = 1;
    public static final int MAX_NUMBER = 45;
    /** 조합 수가 이 이하면 모두 늘어놓고 섞는다(그 위면 겹치면 다시 뽑기 — 겹칠 확률이 아주 작다) */
    private static final long ENUMERATE_MAX = 2_000;

    /** 검사를 통과한 요청 — 고정·제외는 작은 수부터 */
    public record Pick(int sets, LottoPickMode mode, List<Integer> fixed, List<Integer> excluded) {

        /** 이 뽑기의 값 */
        public int cost() {
            return sets == 1 ? SET_COST : BUNDLE_COST;
        }
    }

    private LottoRules() {
    }

    /**
     * 요청 검사.
     *
     * @throws ValidationException {@code fields.sets}·{@code fields.mode}·{@code fields.fixed}·{@code fields.excluded}
     */
    public static Pick validate(LottoDrawRequest body) {
        Integer sets = body == null ? null : body.sets();
        String modeText = body == null ? null : body.mode();
        List<Integer> fixed = body == null || body.fixed() == null ? List.of() : body.fixed();
        List<Integer> excluded = body == null || body.excluded() == null ? List.of() : body.excluded();
        Map<String, MessageRef> bad = new LinkedHashMap<>();

        boolean setsOk = sets != null && (sets == 1 || sets == BUNDLE_SIZE);
        if (!setsOk) {
            bad.put("sets", new MessageRef("pointGame.lotto.sets", BUNDLE_SIZE));
        }
        LottoPickMode mode = mode(modeText);
        if (mode == null) {
            bad.put("mode", new MessageRef("pointGame.lotto.mode"));
        } else if (mode == LottoPickMode.AUTO && (!fixed.isEmpty() || !excluded.isEmpty())) {
            bad.put("mode", new MessageRef("pointGame.lotto.autoNoPick"));
        }
        boolean fixedOk = numbers(fixed) && fixed.size() <= MAX_FIXED;
        if (!fixedOk) {
            bad.put("fixed", new MessageRef("pointGame.lotto.fixed"));
        } else if (mode == LottoPickMode.SEMI_AUTO && fixed.isEmpty()) {
            bad.put("fixed", new MessageRef("pointGame.lotto.fixedRequired"));
        }
        if (!numbers(excluded)) {
            bad.put("excluded", new MessageRef("pointGame.lotto.excluded"));
        } else if (fixedOk && excluded.stream().anyMatch(fixed::contains)) {
            bad.put("excluded", new MessageRef("pointGame.lotto.overlap"));
        } else if (fixedOk && pool(fixed.size(), excluded.size()) < PICK - fixed.size()) {
            bad.put("excluded", new MessageRef("pointGame.lotto.tooManyExcluded"));
        } else if (fixedOk && setsOk && combinations(pool(fixed.size(), excluded.size()), PICK - fixed.size()) < sets) {
            bad.put("excluded", new MessageRef("pointGame.lotto.notEnoughDistinct", sets));
        }
        if (!bad.isEmpty()) {
            throw new ValidationException(bad);
        }
        return new Pick(sets, mode, List.copyOf(new TreeSet<>(fixed)), List.copyOf(new TreeSet<>(excluded)));
    }

    /** 세트 수만큼 서로 다른 번호 세트(각각 작은 수부터) — 검사를 통과한 요청만 */
    public static List<List<Integer>> draw(Pick pick, RandomGenerator random) {
        Set<Integer> banned = new HashSet<>(pick.fixed());
        banned.addAll(pick.excluded());
        List<Integer> pool = new ArrayList<>();
        for (int n = MIN_NUMBER; n <= MAX_NUMBER; n++) {
            if (!banned.contains(n)) {
                pool.add(n);
            }
        }
        int need = PICK - pick.fixed().size();
        List<List<Integer>> out = new ArrayList<>();
        if (combinations(pool.size(), need) <= ENUMERATE_MAX) {
            List<List<Integer>> all = new ArrayList<>();
            choose(pool, need, 0, new ArrayList<>(), all);
            shuffle(all, random);
            for (int i = 0; i < pick.sets(); i++) {
                out.add(withFixed(pick.fixed(), all.get(i)));
            }
            return out;
        }
        Set<List<Integer>> seen = new HashSet<>();
        while (out.size() < pick.sets()) {
            List<Integer> shuffled = new ArrayList<>(pool);
            shuffle(shuffled, random);
            List<Integer> set = withFixed(pick.fixed(), shuffled.subList(0, need));
            if (seen.add(set)) {
                out.add(set);
            }
        }
        return out;
    }

    /** C(n, k) — n ≤ 45, k ≤ 6 이라 long 에 들어간다 */
    static long combinations(int n, int k) {
        if (k < 0 || k > n) {
            return 0;
        }
        long c = 1;
        for (int i = 1; i <= k; i++) {
            c = c * (n - k + i) / i;
        }
        return c;
    }

    private static int pool(int fixed, int excluded) {
        return MAX_NUMBER - fixed - excluded;
    }

    private static LottoPickMode mode(String text) {
        if (text == null) {
            return null;
        }
        for (LottoPickMode m : LottoPickMode.values()) {
            if (m.name().equals(text)) {
                return m;
            }
        }
        return null;
    }

    /** 모두 1~45 이고 서로 다른가(null 칸 없음) */
    private static boolean numbers(List<Integer> list) {
        if (list.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        return list.stream().allMatch(n -> n >= MIN_NUMBER && n <= MAX_NUMBER) && new HashSet<>(list).size() == list.size();
    }

    private static List<Integer> withFixed(List<Integer> fixed, List<Integer> rest) {
        TreeSet<Integer> set = new TreeSet<>(fixed);
        set.addAll(rest);
        return List.copyOf(set);
    }

    private static void choose(List<Integer> pool, int k, int from, List<Integer> current, List<List<Integer>> out) {
        if (current.size() == k) {
            out.add(List.copyOf(current));
            return;
        }
        for (int i = from; i <= pool.size() - (k - current.size()); i++) {
            current.add(pool.get(i));
            choose(pool, k, i + 1, current, out);
            current.removeLast();
        }
    }

    /** 피셔-예이츠 섞기(서버 난수) */
    private static <T> void shuffle(List<T> list, RandomGenerator random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }
}
