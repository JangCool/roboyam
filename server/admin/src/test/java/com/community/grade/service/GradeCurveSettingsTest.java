package com.community.grade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.grade.dto.GradePlan;
import com.community.grade.dto.request.GradeRequest;
import com.community.grade.dto.request.GradesSaveRequest;
import com.community.grade.dto.response.GradesResponse;
import com.community.grade.entity.GradeEntity;
import com.community.point.dto.PointRules;
import com.community.zconfiguration.exception.ValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 관리자 등급 설정의 레벨 곡선 지수·하루 적립 기준(grade-level-contract 6절) — DB 없이 보는 규칙:
 * 지수는 다섯 값뿐(400 {@code fields.xpCurveExponent}), 빠지면 지금 값 그대로 · {@code pace.typicalDaily} = 출석 + 글 1 + 댓글 3(횟수 상한·하루 총 한도 반영, 기본값에서 45).
 */
class GradeCurveSettingsTest {

    private static final Instant NOW = Instant.parse("2026-11-17T00:00:00Z");
    private static final XpCurve.Shape CURRENT = XpCurve.Shape.of(10, new BigDecimal("2.5"));

    private final GradeSavePlanner planner = new GradeSavePlanner();

    private static List<GradeEntity> current() {
        return List.of(GradeEntity.create("SEEDLING", 1, "새싹", 1, 0, 0, "gray", null, "SPROUT", NOW),
                GradeEntity.create("REGULAR", 2, "정식", 5, 3, 0, "blue", null, "BOLT", NOW));
    }

    private static GradesSaveRequest request(Integer base, BigDecimal exponent) {
        return new GradesSaveRequest(List.of(new GradeRequest("SEEDLING", "새싹", 0, 0, "gray", null), new GradeRequest("REGULAR", "정식", 3, 0, "blue", null)),
                "CONTINUE_AUTO", base, exponent);
    }

    private static PointRules rules(int attendance, int post, int postCap, int comment, int commentCap, int dailyTotalCap) {
        PointRules d = PointRules.DEFAULTS;
        return new PointRules(attendance, post, postCap, comment, commentCap, d.popularRewardPoints(), d.popularRewardTopN(), d.likeBonusThreshold(), d.likeBonusPoints(),
                d.likeBonusDailyLimit(), d.dealEndRequestPoints(), d.dealEndRequestDailyLimit(), d.signupPoints(), d.postMinLength(), d.commentMinLength(),
                d.commentOwnPostExcluded(), d.commentOncePerPost(), d.writeIntervalSeconds(), dailyTotalCap, d.attendanceStreakDays(), d.attendanceStreakBonus());
    }

    @Test
    void 지수가_빠지면_지금_값이고_다섯_값은_받는다() {
        GradePlan kept = planner.plan(request(null, null), current(), CURRENT);
        assertThat(kept.xpCurveBase()).isEqualTo(10);
        assertThat(kept.xpCurveExponent().toPlainString()).isEqualTo("2.5");
        assertThat(kept.curve().sameAs(CURRENT)).isTrue();

        for (String value : List.of("1", "1.5", "2", "2.5", "3", "3.0", "2.00")) {
            GradePlan plan = planner.plan(request(null, new BigDecimal(value)), current(), CURRENT);
            assertThat(plan.xpCurveExponent()).as(value).isEqualByComparingTo(value);
            assertThat(plan.xpCurveExponent().scale()).as("소수 한 자리 꼴로 맞춘다 — %s", value).isEqualTo(1);
        }
        assertThat(planner.plan(request(20, new BigDecimal("1.5")), current(), CURRENT).curve()).isEqualTo(new XpCurve.Shape(20, new BigDecimal("1.5")));
    }

    @Test
    void 다섯_값이_아닌_지수는_칸_오류다() {
        for (String value : List.of("0", "0.5", "1.7", "2.25", "3.5", "-2", "10")) {
            assertThatThrownBy(() -> planner.plan(request(null, new BigDecimal(value)), current(), CURRENT)).as(value)
                    .isInstanceOfSatisfying(ValidationException.class, e -> {
                        assertThat(e.getFields()).containsOnlyKeys("xpCurveExponent");
                        assertThat(e.getFields().get("xpCurveExponent").key()).isEqualTo("grade.xpCurveExponent.invalid");
                    });
        }
        // 기준값 오류와 함께 모아 알린다
        assertThatThrownBy(() -> planner.plan(request(0, new BigDecimal("1.7")), current(), CURRENT))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsOnlyKeys("xpCurveBase", "xpCurveExponent"));
    }

    @Test
    void 보통_회원의_하루_적립은_출석_글_1개_댓글_3개다() {
        GradesResponse.Pace defaults = GradeService.pace(PointRules.DEFAULTS);
        assertThat(defaults.typicalDaily()).as("10 + 20×1 + 5×3").isEqualTo(45);
        assertThat(defaults.dailyTotalCap()).isEqualTo(200);

        assertThat(GradeService.pace(rules(10, 20, 5, 5, 20, 0))).as("하루 총 한도 0 = 한도 없음").isEqualTo(new GradesResponse.Pace(45, 0));
        assertThat(GradeService.pace(rules(10, 20, 5, 5, 20, 30))).as("하루 총 한도보다 크지 않게").isEqualTo(new GradesResponse.Pace(30, 30));
        assertThat(GradeService.pace(rules(10, 20, 5, 5, 2, 200)).typicalDaily()).as("댓글 하루 상한 2 → 댓글 2개만").isEqualTo(40);
        assertThat(GradeService.pace(rules(10, 20, 0, 5, 20, 200)).typicalDaily()).as("글 하루 상한 0 = 글로는 지급하지 않음").isEqualTo(25);
        assertThat(GradeService.pace(rules(10, 20, 5, 5, 0, 200)).typicalDaily()).as("댓글 하루 상한 0 = 댓글로는 지급하지 않음").isEqualTo(30);
        assertThat(GradeService.pace(rules(0, 0, 5, 0, 20, 200)).typicalDaily()).isZero();
    }
}
