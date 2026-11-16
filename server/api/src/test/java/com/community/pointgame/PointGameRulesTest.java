package com.community.pointgame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.pointgame.dto.request.LottoDrawRequest;
import com.community.pointgame.entity.LottoPickMode;
import com.community.pointgame.service.LottoRules;
import com.community.pointgame.service.RouletteTable;
import com.community.zconfiguration.exception.ValidationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 포인트 게임 규칙 중 DB 없이 막히는 것(docs/features/point-games-contract.md 「룰렛」·「로또 뽑기」·「뽑는 방법」) — 룰렛 확률표(합 10000 · 평균 돌려받는 양 19.6% · 칸 경계 ·
 * 굴린 값 분포), 로또 요청 검사(자동·반자동 표)와 번호 만들기(6개 · 작은 수부터 · 고정 포함 · 제외 없음 · 한 번에 뽑은 세트끼리 안 겹침).
 */
class PointGameRulesTest {

    // ── 룰렛 ────────────────────────────────────────────────────────────────

    @Test
    void 룰렛_확률표는_합이_10000_이고_계약의_값이다() {
        assertThat(RouletteTable.PRIZES.stream().mapToInt(RouletteTable.Prize::chanceBp).sum()).isEqualTo(10_000);
        assertThat(RouletteTable.PRIZES).extracting(RouletteTable.Prize::key).containsExactly("MISS", "P10", "P20", "P30", "P50", "P100", "P500");
        assertThat(RouletteTable.PRIZES).extracting(RouletteTable.Prize::points).containsExactly(0, 10, 20, 30, 50, 100, 500);
        assertThat(RouletteTable.PRIZES).extracting(RouletteTable.Prize::chanceBp).containsExactly(7000, 1200, 800, 500, 300, 150, 50);
        assertThat(RouletteTable.COST).isEqualTo(50);
        assertThat(RouletteTable.DAILY_LIMIT).isEqualTo(5);
    }

    @Test
    void 룰렛_평균_돌려받는_양은_값의_19_6_퍼센트() {
        long expectedTimes10000 = RouletteTable.PRIZES.stream().mapToLong(p -> (long) p.points() * p.chanceBp()).sum(); // 9.8P × 10000
        assertThat(expectedTimes10000).isEqualTo(98_000);
        assertThat(expectedTimes10000 * 1000 / (RouletteTable.COST * 10_000L)).as("천분율").isEqualTo(196);
    }

    @Test
    void 룰렛_칸_경계() {
        assertThat(RouletteTable.at(0).key()).isEqualTo("MISS");
        assertThat(RouletteTable.at(6999).key()).isEqualTo("MISS");
        assertThat(RouletteTable.at(7000).key()).isEqualTo("P10");
        assertThat(RouletteTable.at(8199).key()).isEqualTo("P10");
        assertThat(RouletteTable.at(8200).key()).isEqualTo("P20");
        assertThat(RouletteTable.at(9799).key()).isEqualTo("P50");
        assertThat(RouletteTable.at(9800).key()).isEqualTo("P100");
        assertThat(RouletteTable.at(9949).key()).isEqualTo("P100");
        assertThat(RouletteTable.at(9950).key()).isEqualTo("P500");
        assertThat(RouletteTable.at(9999).key()).isEqualTo("P500");
        assertThatThrownBy(() -> RouletteTable.at(10_000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RouletteTable.at(-1)).isInstanceOf(IllegalArgumentException.class);
        // 모든 굴린 값을 한 번씩 → 칸마다 정확히 확률 만큼
        Map<String, Integer> hits = new HashMap<>();
        IntStream.range(0, 10_000).forEach(roll -> hits.merge(RouletteTable.at(roll).key(), 1, Integer::sum));
        RouletteTable.PRIZES.forEach(p -> assertThat(hits.get(p.key())).as(p.key()).isEqualTo(p.chanceBp()));
    }

    // ── 로또 검사 ───────────────────────────────────────────────────────────

    private static Map<String, String> problems(LottoDrawRequest body) {
        try {
            LottoRules.validate(body);
            return Map.of();
        } catch (ValidationException e) {
            Map<String, String> out = new HashMap<>();
            e.getFields().forEach((k, v) -> out.put(k, v.key()));
            return out;
        }
    }

    private static LottoDrawRequest req(Integer sets, String mode, List<Integer> fixed, List<Integer> excluded) {
        return new LottoDrawRequest(sets, mode, fixed, excluded);
    }

    @Test
    void 로또_자동은_고정_제외_없이_1세트_또는_5세트() {
        assertThat(problems(req(1, "AUTO", List.of(), List.of()))).isEmpty();
        assertThat(problems(req(5, "AUTO", null, null))).as("빈 목록은 null 로 와도 된다").isEmpty();
        assertThat(problems(req(3, "AUTO", List.of(), List.of()))).containsEntry("sets", "pointGame.lotto.sets");
        assertThat(problems(req(null, "AUTO", List.of(), List.of()))).containsEntry("sets", "pointGame.lotto.sets");
        assertThat(problems(req(1, "AUTO", List.of(7), List.of()))).containsEntry("mode", "pointGame.lotto.autoNoPick");
        assertThat(problems(req(1, "AUTO", List.of(), List.of(7)))).containsEntry("mode", "pointGame.lotto.autoNoPick");
        assertThat(problems(req(1, "MANUAL", List.of(), List.of()))).containsEntry("mode", "pointGame.lotto.mode");
        assertThat(problems(req(1, null, List.of(), List.of()))).containsEntry("mode", "pointGame.lotto.mode");
        assertThat(problems(null)).containsKeys("sets", "mode");
    }

    @Test
    void 로또_반자동은_고정_1_5개_필수_제외는_선택() {
        assertThat(problems(req(1, "SEMI_AUTO", List.of(3), List.of()))).isEmpty();
        assertThat(problems(req(5, "SEMI_AUTO", List.of(1, 2, 3, 4, 5), List.of(6, 7)))).isEmpty();
        assertThat(problems(req(1, "SEMI_AUTO", List.of(), List.of(3)))).containsEntry("fixed", "pointGame.lotto.fixedRequired");
        assertThat(problems(req(1, "SEMI_AUTO", List.of(1, 2, 3, 4, 5, 6), List.of()))).containsEntry("fixed", "pointGame.lotto.fixed");
        assertThat(problems(req(1, "SEMI_AUTO", List.of(0), List.of()))).containsEntry("fixed", "pointGame.lotto.fixed");
        assertThat(problems(req(1, "SEMI_AUTO", List.of(46), List.of()))).containsEntry("fixed", "pointGame.lotto.fixed");
        assertThat(problems(req(1, "SEMI_AUTO", List.of(4, 4), List.of()))).containsEntry("fixed", "pointGame.lotto.fixed");
        List<Integer> withNull = new ArrayList<>();
        withNull.add(null);
        assertThat(problems(req(1, "SEMI_AUTO", withNull, List.of()))).containsEntry("fixed", "pointGame.lotto.fixed");
    }

    @Test
    void 로또_제외는_1_45_서로_다르고_고정과_겹치지_않고_남은_수가_모자라면_안_된다() {
        assertThat(problems(req(1, "SEMI_AUTO", List.of(1), List.of(46)))).containsEntry("excluded", "pointGame.lotto.excluded");
        assertThat(problems(req(1, "SEMI_AUTO", List.of(1), List.of(9, 9)))).containsEntry("excluded", "pointGame.lotto.excluded");
        assertThat(problems(req(1, "SEMI_AUTO", List.of(1, 2), List.of(2)))).containsEntry("excluded", "pointGame.lotto.overlap");
        // 45 − 고정 1 − 제외 40 = 4 < 6 − 1 = 5
        List<Integer> forty = IntStream.rangeClosed(2, 41).boxed().toList();
        assertThat(problems(req(1, "SEMI_AUTO", List.of(1), forty))).containsEntry("excluded", "pointGame.lotto.tooManyExcluded");
        // 45 − 1 − 39 = 5 = 5 → 딱 한 세트만 가능
        List<Integer> thirtyNine = IntStream.rangeClosed(2, 40).boxed().toList();
        assertThat(problems(req(1, "SEMI_AUTO", List.of(1), thirtyNine))).isEmpty();
        assertThat(problems(req(5, "SEMI_AUTO", List.of(1), thirtyNine))).as("서로 다른 5세트를 만들 수 없다").containsEntry("excluded", "pointGame.lotto.notEnoughDistinct");
    }

    @Test
    void 로또_여러_칸이_틀리면_칸마다_하나씩_모두_알린다() {
        assertThat(problems(req(2, "AUTO", List.of(50), List.of(50)))).containsOnlyKeys("sets", "mode", "fixed", "excluded");
    }

    // ── 로또 번호 만들기 ────────────────────────────────────────────────────

    @Test
    void 자동_번호는_1_45_서로_다른_6개_작은_수부터이고_한_번에_뽑은_세트끼리_겹치지_않는다() {
        SplittableRandom random = new SplittableRandom(7);
        for (int round = 0; round < 200; round++) {
            List<List<Integer>> sets = LottoRules.draw(LottoRules.validate(req(5, "AUTO", List.of(), List.of())), random);
            assertThat(sets).hasSize(5);
            assertThat(new HashSet<>(sets)).hasSize(5);
            for (List<Integer> set : sets) {
                assertThat(set).hasSize(6).isSorted().doesNotHaveDuplicates().allMatch(n -> n >= 1 && n <= 45);
            }
        }
    }

    @Test
    void 반자동_번호는_고정을_모두_넣고_제외는_넣지_않는다() {
        SplittableRandom random = new SplittableRandom(11);
        LottoRules.Pick pick = LottoRules.validate(req(5, "SEMI_AUTO", List.of(45, 3), List.of(4, 5, 6)));
        assertThat(pick.mode()).isEqualTo(LottoPickMode.SEMI_AUTO);
        assertThat(pick.fixed()).containsExactly(3, 45);
        assertThat(pick.cost()).isEqualTo(80);
        for (int round = 0; round < 200; round++) {
            List<List<Integer>> sets = LottoRules.draw(pick, random);
            assertThat(new HashSet<>(sets)).hasSize(5);
            for (List<Integer> set : sets) {
                assertThat(set).hasSize(6).isSorted().contains(3, 45).doesNotContain(4, 5, 6);
            }
        }
    }

    @Test
    void 조합이_적어도_서로_다른_세트를_만든다() {
        // 고정 5개 + 남은 수 5개 → 조합 5개 = 세트 5개(모두 다 나온다)
        List<Integer> excluded = IntStream.rangeClosed(11, 45).boxed().toList();
        LottoRules.Pick pick = LottoRules.validate(req(5, "SEMI_AUTO", List.of(1, 2, 3, 4, 5), excluded));
        List<List<Integer>> sets = LottoRules.draw(pick, new SplittableRandom(3));
        assertThat(new HashSet<>(sets)).hasSize(5);
        assertThat(sets).allMatch(s -> s.containsAll(List.of(1, 2, 3, 4, 5)) && s.get(5) >= 6 && s.get(5) <= 10);
    }

    @Test
    void 값은_1세트_20P_5세트_80P_보관_50세트() {
        assertThat(LottoRules.validate(req(1, "AUTO", null, null)).cost()).isEqualTo(20);
        assertThat(LottoRules.validate(req(5, "AUTO", null, null)).cost()).isEqualTo(80);
        assertThat(LottoRules.STORAGE_LIMIT).isEqualTo(50);
    }
}
