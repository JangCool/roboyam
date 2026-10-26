package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.point.entity.PointReason;
import com.community.point.service.PointService;
import com.community.point.service.PointService.CommentEarn;
import com.community.point.service.PointService.PostEarn;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 커머스 1-3 적립 규칙 보강(commerce.md 6-2, 계약 「1-3 화면 목업」 확정 1·2·6) — 최소 글자 수(서식 태그·공백 제외)·게시판 스위치·게시판 pointMode OFF·자기 글 댓글·같은 글 댓글 1회·
 * 작성 간격·하루 총 한도(걸치면 남은 만큼, 남은 0 이면 줄 없음, 제외 사유)·동시 적립. 시험 DB 기본 정책(글 20P·댓글 5P·간격 30초·글 50자·댓글 10자·총 한도 200P). 불변식은 시험마다.
 */
class PointEarnRulesTest extends PointTestBase {

    private static final String LONG = PointTestKit.LONG_BODY;
    private static final String COMMENT_OK = "댓글열글자를넘기는내용";

    private long post(long member, String body, boolean boardPoints, boolean minLengthApplied) {
        return kit.earnPost(new PostEarn(member, kit.ids.nextId(), body, boardPoints, minLengthApplied));
    }

    /** 그 글에 그 회원의 댓글 행을 만든다(같은 글 댓글 1회가 댓글 표를 본다) — 댓글 ID */
    private long commentRow(long postId, long author) {
        long id = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO comments (id, post_id, board_id, author_id, content) VALUES (?, ?, 1, ?, '시험 댓글')", id, postId, author);
        return id;
    }

    private long comment(long member, long postId, Long postAuthor, String body, boolean boardPoints) {
        long id = commentRow(postId, member);
        return kit.earnComment(new CommentEarn(member, id, postId, postAuthor, body, boardPoints));
    }

    private void later(int seconds) {
        kit.clock.advance(Duration.ofSeconds(seconds));
    }

    private int lines(long member, String reason) {
        return kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = ?", member, reason);
    }

    // ── 최소 글자 수 ──────────────────────────────────────────────────────────

    @Test
    void 글자_수는_모든_공백을_빼고_코드_포인트로_센다() {
        assertThat(PointService.visibleLength("가 나 다\n\t라　마​  ﻿")).isEqualTo(5);
        assertThat(PointService.visibleLength("😀😀 a")).isEqualTo(3);
        assertThat(PointService.visibleLength("   \n ")).isZero();
        assertThat(PointService.visibleLength(null)).isZero();
    }

    @Test
    void 본문_실제_글자가_최소_글자_수_미만이면_0이고_글자만_세어_50자면_준다() {
        long member = kit.newMember();
        String spaced49 = String.join("  \n", "가".repeat(49).split("")); // 공백을 아무리 넣어도 49자
        assertThat(post(member, spaced49, true, true)).isZero();
        assertThat(lines(member, "POST")).isZero();
        later(31);
        assertThat(post(member, "가".repeat(50), true, true)).isEqualTo(20);
        kit.assertInvariants(member);
    }

    @Test
    void 게시판_스위치를_끄면_글_최소_글자_수만_빠지고_댓글_최소_글자_수는_그대로다() {
        long member = kit.newMember();
        long postId = kit.ids.nextId();
        assertThat(kit.earnPost(new PostEarn(member, postId, "짧은 사진 글", true, false))).isEqualTo(20);
        later(31);
        long other = kit.newMember();
        assertThat(comment(member, kit.ids.nextId(), other, "짧은댓글", true)).as("댓글 최소 글자 수는 게시판 스위치와 무관").isZero();
        later(31);
        assertThat(comment(member, kit.ids.nextId(), other, COMMENT_OK, true)).isEqualTo(5);
        kit.assertInvariants(member);
    }

    @Test
    void 정책_최소_글자_수가_0이면_조건이_없다() {
        long member = kit.newMember();
        kit.withSettings(Map.of("post_min_length", 0, "comment_min_length", 0), () -> {
            assertThat(post(member, "", true, true)).isEqualTo(20);
            later(31);
            assertThat(comment(member, kit.ids.nextId(), kit.newMember(), "a", true)).isEqualTo(5);
        });
        kit.assertInvariants(member);
    }

    // ── 게시판 pointMode OFF ──────────────────────────────────────────────────

    @Test
    void 게시판_pointMode_OFF면_글_댓글_추천_보너스_모두_0이고_줄이_없다() {
        long member = kit.newMember();
        assertThat(post(member, LONG, false, true)).isZero();
        later(31);
        assertThat(comment(member, kit.ids.nextId(), kit.newMember(), COMMENT_OK, false)).isZero();
        long liker = kit.newMember();
        kit.withSettings(Map.of("like_bonus_threshold", 1), () -> assertThat(kit.inTx(() -> kit.points.likeBonus(member, PointService.REF_POST, kit.ids.nextId(),
                liker, false))).isZero());
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member)).isZero();
    }

    // ── 댓글 ──────────────────────────────────────────────────────────────────

    @Test
    void 자기_글에_단_댓글은_0이고_끄면_준다() {
        long member = kit.newMember();
        assertThat(comment(member, kit.ids.nextId(), member, COMMENT_OK, true)).isZero();
        kit.withSettings(Map.of("comment_own_post_excluded", false), () -> assertThat(comment(member, kit.ids.nextId(), member, COMMENT_OK, true)).isEqualTo(5));
        kit.assertInvariants(member);
    }

    @Test
    void 같은_글의_두_번째_댓글은_0이고_첫_댓글을_지워_회수해도_0이다() {
        long member = kit.newMember();
        long author = kit.newMember();
        long postId = kit.ids.nextId();
        long first = commentRow(postId, member);
        assertThat(kit.earnComment(new CommentEarn(member, first, postId, author, COMMENT_OK, true))).isEqualTo(5);
        later(31);
        assertThat(comment(member, postId, author, COMMENT_OK, true)).as("같은 글 두 번째").isZero();

        // 첫 댓글을 지우고 회수해도 「받은 적이 있다」는 그대로다
        kit.jdbc.update("UPDATE comments SET deleted_at = now() WHERE post_id = ? AND id = ?", postId, first);
        assertThat(kit.inTx(() -> kit.points.revokeContent(member, PointService.REF_COMMENT, first))).isEqualTo(5);
        later(31);
        assertThat(comment(member, postId, author, COMMENT_OK, true)).as("지운 댓글로 받은 것도 센다").isZero();

        later(31);
        assertThat(comment(member, kit.ids.nextId(), author, COMMENT_OK, true)).as("다른 글").isEqualTo(5);
        assertThat(lines(member, "COMMENT")).isEqualTo(2);
        kit.assertInvariants(member);
    }

    @Test
    void 같은_글_댓글_1회를_끄면_같은_글에도_다시_준다() {
        long member = kit.newMember();
        long author = kit.newMember();
        long postId = kit.ids.nextId();
        kit.withSettings(Map.of("comment_once_per_post", false), () -> {
            assertThat(comment(member, postId, author, COMMENT_OK, true)).isEqualTo(5);
            later(31);
            assertThat(comment(member, postId, author, COMMENT_OK, true)).isEqualTo(5);
        });
        kit.assertInvariants(member);
    }

    // ── 작성 간격 ─────────────────────────────────────────────────────────────

    @Test
    void 포인트를_받은_글_댓글_뒤_간격_안의_적립은_0이다_글과_댓글을_합쳐_본다() {
        long member = kit.newMember();
        long other = kit.newMember();
        assertThat(post(member, LONG, true, true)).isEqualTo(20);
        later(10);
        assertThat(comment(member, kit.ids.nextId(), other, COMMENT_OK, true)).as("글 10초 뒤 댓글").isZero();
        later(19);
        assertThat(post(member, LONG, true, true)).as("29초").isZero();
        later(1);
        assertThat(comment(member, kit.ids.nextId(), other, COMMENT_OK, true)).as("정확히 30초").isEqualTo(5);
        kit.assertInvariants(member);
    }

    @Test
    void 포인트를_못_받은_글은_간격의_기준이_아니다() {
        long member = kit.newMember();
        assertThat(post(member, LONG, true, true)).isEqualTo(20);
        later(31);
        assertThat(post(member, "짧은 글", true, true)).as("글자 수 미달 — 줄 없음").isZero();
        later(14); // 못 받은 글에서 14초, 받은 글에서 45초
        assertThat(post(member, LONG, true, true)).isEqualTo(20);
        kit.assertInvariants(member);
    }

    @Test
    void 간격이_0이면_조건이_없다() {
        long member = kit.newMember();
        kit.withSettings(Map.of("write_interval_seconds", 0), () -> {
            assertThat(post(member, LONG, true, true)).isEqualTo(20);
            assertThat(post(member, LONG, true, true)).isEqualTo(20);
        });
    }

    // ── 하루 총 한도 ──────────────────────────────────────────────────────────

    @Test
    void 하루_총_한도에_걸치면_남은_만큼만_주고_남은_0이면_줄도_횟수도_없다() {
        long member = kit.newMember();
        kit.withSettings(Map.of("comment_points", 190), () -> {
            assertThat(comment(member, kit.ids.nextId(), kit.newMember(), COMMENT_OK, true)).isEqualTo(190);
            later(31);
            assertThat(post(member, LONG, true, true)).as("190P 에서 글 20P").isEqualTo(10);
            later(31);
            assertThat(post(member, LONG, true, true)).as("남은 0").isZero();
        });
        assertThat(kit.jdbc.queryForList("SELECT delta FROM point_ledger WHERE user_id = ? AND reason = 'POST'", Integer.class, member)).containsExactly(10);
        assertThat(kit.inTx(() -> kit.points.countToday(member, PointReason.POST))).as("남은 0 이면 개별 횟수에도 안 든다").isEqualTo(1);
        assertThat(kit.inTx(() -> kit.points.earnedToday(member))).isEqualTo(200);
        kit.assertInvariants(member);
    }

    @Test
    void 하루_총_한도는_상계_전_양으로_세고_지운_글로_받은_것도_센다() {
        long member = kit.newMember();
        kit.withSettings(Map.of("daily_total_cap", 30, "write_interval_seconds", 0), () -> {
            long first = kit.ids.nextId();
            assertThat(kit.earnPost(new PostEarn(member, first, LONG, true, true))).isEqualTo(20);
            kit.use(member, 20);
            kit.revokePost(member, first); // 회수 0, 미회수 20
            assertThat(kit.debt(member)).isEqualTo(20);
            assertThat(post(member, LONG, true, true)).as("지운 글 20 + 이번 10 = 30").isEqualTo(10);
            assertThat(kit.debt(member)).as("10 은 바로 상계").isEqualTo(10);
            assertThat(post(member, LONG, true, true)).as("상계로 빠진 양도 한도에 든다").isZero();
        });
        kit.assertInvariants(member);
    }

    @Test
    void 운영_조정과_가입_포인트는_하루_총_한도에_들지_않는다() {
        long member = kit.newMember();
        kit.withSettings(Map.of("daily_total_cap", 20, "signup_points", 500), () -> {
            kit.adjust(member, 1000);
            assertThat(kit.inTx(() -> kit.points.signup(member))).isEqualTo(500);
            assertThat(kit.inTx(() -> kit.points.earnedToday(member))).isZero();
            assertThat(post(member, LONG, true, true)).as("조정·가입 포인트 뒤에도 한도 20 이 그대로 남았다").isEqualTo(20);
        });
        assertThat(kit.balance(member)[3]).isEqualTo(1520);
        kit.assertInvariants(member);
    }

    @Test
    void 총_한도가_0이면_한도가_없다() {
        long member = kit.newMember();
        kit.withSettings(Map.of("daily_total_cap", 0, "comment_points", 10000), () -> {
            assertThat(comment(member, kit.ids.nextId(), kit.newMember(), COMMENT_OK, true)).isEqualTo(10000);
            later(31);
            assertThat(post(member, LONG, true, true)).isEqualTo(20);
        });
    }

    @Test
    void 동시에_적립해도_하루_총_한도와_작성_간격을_넘지_않는다() throws Exception {
        long totalMember = kit.newMember();
        long intervalMember = kit.newMember();
        kit.withSettings(Map.of("daily_total_cap", 50, "write_interval_seconds", 0), () -> assertThat(together(totalMember, 12)).isEqualTo(50));
        assertThat(kit.jdbc.queryForList("SELECT delta FROM point_ledger WHERE user_id = ? AND reason = 'POST' ORDER BY id", Integer.class, totalMember))
                .containsExactly(20, 20, 10);
        // 간격 30초 — 같은 순간 여럿이 써도 하나만
        assertThat(together(intervalMember, 8)).isEqualTo(20);
        kit.assertInvariants(totalMember, intervalMember);
    }

    /** 한 회원이 {@code threads} 개 글을 동시에 쓴다 — 지급 합 */
    private long together(long member, int threads) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                long postId = kit.ids.nextId();
                Callable<Long> job = () -> {
                    start.await();
                    return kit.earnPost(new PostEarn(member, postId, LONG, true, true));
                };
                futures.add(pool.submit(job));
            }
            start.countDown();
            long sum = 0;
            for (Future<Long> future : futures) {
                sum += future.get(60, TimeUnit.SECONDS);
            }
            return sum;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            pool.shutdownNow();
        }
    }
}
