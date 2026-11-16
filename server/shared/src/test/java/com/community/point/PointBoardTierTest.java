package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.grade.service.XpService;
import com.community.point.entity.PointKind;
import com.community.point.entity.PointReason;
import com.community.point.service.PointService;
import com.community.point.service.PointService.FeatureReward;
import com.community.point.service.PointService.HeldRelease;
import com.community.point.service.PointService.PostEarn;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 게시판 등급별 포인트(point-board-tier-contract 「규칙」·「보류 확정 방식」) — 실제 PostgreSQL 위의 {@link PointService}. 시험 DB 기본 등급 값(V238): 일반 = 전역 글 20P·50자 ·
 * 추천 보너스 10P · 우수 글 0 · 보류 0, 정보 30P·100자·추천 50P·우수 글 100P·보류 24시간, 핵심 글 50P·500자·사진 1장·추천 150P·우수 글 500P·보류 24시간. 불변식은 시험마다.
 */
class PointBoardTierTest extends PointTestBase {

    private static final String INFO = "INFO";
    private static final String CORE = "CORE";
    private static final String NORMAL = "NORMAL";

    private long board;

    @BeforeEach
    void board() {
        board = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO boards (id, slug, name, sort_order) VALUES (?, ?, ?, 971)", board, "pt" + Long.toString(board, 36), "등급 시험 " + Long.toString(board, 36));
    }

    // ── 도우미 ────────────────────────────────────────────────────────────────

    /** 글 행(출처 목록 JSON 은 없으면 null) — 글 ID */
    private long postRow(long author, String sourcesJson) {
        long id = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO posts (id, board_id, author_id, title, content, sources) VALUES (?, ?, ?, '등급 시험', '본문', ?::jsonb)", id, board, author, sourcesJson);
        return id;
    }

    private long commentRow(long postId, long author) {
        long id = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO comments (id, post_id, board_id, author_id, content) VALUES (?, ?, ?, ?, '시험 댓글')", id, postId, board, author);
        return id;
    }

    /** 추천이 인정되는 회원(정상 · 새싹보다 높은 등급의 프로필) */
    private long liker() {
        long id = kit.newMember();
        kit.jdbc.update("INSERT INTO profiles (user_id, nickname, grade_key) VALUES (?, ?, (SELECT grade_key FROM grades ORDER BY level LIMIT 1 OFFSET 1))", id,
                "lk" + Long.toString(id % 1_000_000_000L, 36));
        return id;
    }

    /** 글에 추천 하나 → 문턱 1 로 추천 보너스(그 게시판 등급) — 지금 준 양 */
    private long likePost(long author, long postId, String pointMode) {
        long liker = liker();
        kit.jdbc.update("INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)", postId, liker);
        return kit.inTx(() -> kit.points.likeBonus(author, PointService.REF_POST, postId, liker, pointMode));
    }

    private long likeComment(long author, long postId, long commentId, String pointMode) {
        long liker = liker();
        kit.jdbc.update("INSERT INTO comment_likes (comment_id, user_id, post_id) VALUES (?, ?, ?)", commentId, liker, postId);
        return kit.inTx(() -> kit.points.likeBonus(author, PointService.REF_COMMENT, commentId, liker, pointMode));
    }

    private long earn(long member, long postId, String body, String mode, boolean minLengthApplied, int images) {
        kit.clock.advance(Duration.ofSeconds(31)); // 작성 간격(30초)에 걸리지 않게
        return kit.earnPost(new PostEarn(member, postId, body, mode, minLengthApplied, images));
    }

    private List<Map<String, Object>> pendings(String refType, long refId) {
        return kit.jdbc.queryForList("SELECT * FROM point_pending_grants WHERE ref_type = ? AND ref_id = ? ORDER BY id", refType, refId);
    }

    private List<Integer> ledger(long member, String reason) {
        return kit.jdbc.queryForList("SELECT delta FROM point_ledger WHERE user_id = ? AND reason = ? ORDER BY id", Integer.class, member, reason);
    }

    private HeldRelease release(Map<String, Object> pending) {
        return kit.inTx(() -> kit.points.releaseHeld(((Number) pending.get("user_id")).longValue(), PointReason.valueOf((String) pending.get("reason")),
                (String) pending.get("ref_type"), ((Number) pending.get("ref_id")).longValue(), ((Number) pending.get("amount")).intValue()));
    }

    private void opsAccount(long userId) {
        kit.jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", kit.newOperator(), userId);
    }

    private void feature(long postId, long author) {
        kit.jdbc.update("INSERT INTO post_features (id, post_id, author_id, featured_by, featured_at) VALUES (?, ?, ?, ?, now())", kit.ids.nextId(), postId, author,
                kit.operator);
    }

    // ── 쓰면 바로: 등급별 금액·최소 글자 수·사진 최소 ─────────────────────────────

    @Test
    void 쓰면_바로_금액_최소_글자_수_사진_최소는_게시판_등급_값이고_일반은_전역_칸이다() {
        long member = kit.newMember();
        assertThat(earn(member, kit.ids.nextId(), "가".repeat(50), NORMAL, true, 0)).as("일반 = 전역 20P·50자").isEqualTo(20);
        assertThat(earn(member, kit.ids.nextId(), "가".repeat(99), INFO, true, 0)).as("정보 100자 미만").isZero();
        assertThat(earn(member, kit.ids.nextId(), "가".repeat(100), INFO, true, 0)).as("정보 30P").isEqualTo(30);
        assertThat(earn(member, kit.ids.nextId(), "가".repeat(500), CORE, true, 0)).as("핵심 글 사진 1장 미만").isZero();
        assertThat(earn(member, kit.ids.nextId(), "가".repeat(499), CORE, true, 1)).as("핵심 글 500자 미만").isZero();
        assertThat(earn(member, kit.ids.nextId(), "가".repeat(500), CORE, true, 1)).as("핵심 글 50P").isEqualTo(50);
        assertThat(earn(member, kit.ids.nextId(), "짧은 글", CORE, false, 1)).as("게시판 「최소 글자 수 적용」을 끄면 글자 수만 빠진다(사진은 그대로)").isEqualTo(50);
        assertThat(earn(member, kit.ids.nextId(), "짧은 글", CORE, false, 0)).isZero();
        assertThat(earn(member, kit.ids.nextId(), "가".repeat(500), "OFF", true, 3)).as("끈 게시판").isZero();
        kit.withSettings(Map.of("post_points", 7, "post_min_length", 3), () ->
                assertThat(earn(member, kit.ids.nextId(), "가나다", NORMAL, true, 0)).as("일반 등급은 전역 칸을 따른다").isEqualTo(7));
        kit.assertInvariants(member);
    }

    // ── 하루 총 한도는 쓰면 바로만 ─────────────────────────────────────────────

    @Test
    void 쓰면_바로만_하루_총_한도에_들고_추천_보너스_우수_글_인기글은_한도_밖이다() {
        long member = kit.newMember();
        kit.withSettings(Map.of("daily_total_cap", 20, "like_bonus_threshold", 1, "tier_normal_featured_points", 70), () -> {
            assertThat(earn(member, postRow(member, null), "가".repeat(60), NORMAL, true, 0)).isEqualTo(20);
            assertThat(earn(member, postRow(member, null), "가".repeat(60), NORMAL, true, 0)).as("총 한도 20P 를 채웠다").isZero();
            assertThat(likePost(member, postRow(member, null), NORMAL)).as("추천 보너스는 한도 밖").isEqualTo(10);
            long featured = postRow(member, null);
            assertThat(kit.inTx(() -> kit.points.featurePost(member, featured, NORMAL)).granted()).as("우수 글은 한도 밖").isEqualTo(70);
            assertThat(kit.inTx(() -> kit.points.popularReward(member, postRow(member, null), 15))).as("인기글 보상은 한도 밖").isEqualTo(15);
            assertThat(kit.points.earnedToday(member)).as("한도가 세는 양 = 쓰면 바로만").isEqualTo(20);
        });
        assertThat(PointService.DAILY_TOTAL_REASONS).doesNotContain(PointReason.LIKE_BONUS, PointReason.POST_FEATURED, PointReason.POPULAR);
        kit.assertInvariants(member);
    }

    // ── 정보 등급: 같은 출처 링크 1회 ─────────────────────────────────────────

    @Test
    void 정보_등급은_같은_출처_링크로_사이트_전체에서_한_번만_적립한다() {
        String unique = Long.toString(kit.ids.nextId(), 36);
        long a = kit.newMember();
        long b = kit.newMember();
        String body = "가".repeat(120);
        String first = "[{\"name\":\"뉴스\",\"url\":\"https://news.example.com/" + unique + "?utm_source=x\"}]";
        String same = "[{\"name\":\"다른 이름\",\"url\":\"HTTPS://www.NEWS.example.com/" + unique + "/#top\"}]";
        String other = "[{\"name\":\"뉴스\",\"url\":\"https://news.example.com/" + unique + "-2\"}]";
        assertThat(earn(a, postRow(a, first), body, INFO, true, 0)).isEqualTo(30);
        assertThat(earn(b, postRow(b, same), body, INFO, true, 0)).as("추적 파라미터·대소문자·www·끝 / 만 다른 같은 링크").isZero();
        assertThat(earn(b, postRow(b, other), body, INFO, true, 0)).as("다른 링크").isEqualTo(30);
        assertThat(earn(b, postRow(b, same), body, NORMAL, true, 0)).as("일반 등급은 출처를 보지 않는다").isEqualTo(20);
        assertThat(earn(a, postRow(a, null), body, INFO, true, 0)).as("출처가 없는 글은 그대로").isEqualTo(30);
        kit.withSettings(Map.of("info_source_once", false), () -> assertThat(earn(b, postRow(b, same), body, INFO, true, 0)).as("스위치를 끄면 준다").isEqualTo(30));
        kit.assertInvariants(a, b);
    }

    @Test
    void 핫딜_글은_같은_딜_키로_한_번만_적립한다() {
        long a = kit.newMember();
        long b = kit.newMember();
        String key = String.format("%064x", kit.ids.nextId());
        long first = postRow(a, null);
        long second = postRow(b, null);
        for (long[] row : List.of(new long[] {first, a}, new long[] {second, b})) {
            kit.jdbc.update("INSERT INTO post_deals (post_id, board_id, author_id, store_name, product_url, product_host, final_host, product_url_key, deal_price, "
                    + "created_at, updated_at) VALUES (?, ?, ?, '시험몰', 'https://shop.example.com/p', 'shop.example.com', 'shop.example.com', ?, 1000, now(), now())",
                    row[0], board, row[1], key);
        }
        assertThat(earn(a, first, "가".repeat(120), INFO, true, 0)).isEqualTo(30);
        assertThat(earn(b, second, "가".repeat(120), INFO, true, 0)).as("같은 딜 링크").isZero();
        assertThat(kit.count("SELECT count(*) FROM point_source_claims WHERE source_key = ?", key)).isEqualTo(1);
    }

    @Test
    void 같은_링크로_동시에_쓴_글도_하나만_받는다() throws Exception {
        String sources = "[{\"name\":\"뉴스\",\"url\":\"https://race.example.com/" + Long.toString(kit.ids.nextId(), 36) + "\"}]";
        int writers = 6;
        List<long[]> posts = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            long member = kit.newMember();
            posts.add(new long[] {member, postRow(member, sources)});
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (long[] post : posts) {
                Callable<Long> task = () -> {
                    start.await();
                    return kit.earnPost(new PostEarn(post[0], post[1], "가".repeat(120), INFO, true, 0));
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            long paid = 0;
            for (Future<Long> result : results) {
                paid += result.get(30, TimeUnit.SECONDS) > 0 ? 1 : 0;
            }
            assertThat(paid).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ── AI 회원·운영팀 계정 ───────────────────────────────────────────────────

    @Test
    void 운영팀_계정은_스위치가_켜져_있으면_글_댓글_추천_보너스_우수_글_인기글을_받지_않는다() {
        long ops = kit.newMember();
        opsAccount(ops);
        long other = kit.newMember();
        kit.withSettings(Map.of("like_bonus_threshold", 1, "tier_normal_featured_points", 40), () -> {
            assertThat(earn(ops, postRow(ops, null), "가".repeat(60), NORMAL, true, 0)).isZero();
            long postId = postRow(other, null);
            long commentId = commentRow(postId, ops);
            assertThat(kit.earnComment(new PointService.CommentEarn(ops, commentId, postId, other, "댓글열글자를넘기는내용", true))).isZero();
            assertThat(likePost(ops, postRow(ops, null), NORMAL)).isZero();
            assertThat(likePost(ops, postRow(ops, null), INFO)).isZero();
            assertThat(kit.inTx(() -> kit.points.featurePost(ops, postRow(ops, null), NORMAL))).isEqualTo(FeatureReward.NONE);
            assertThat(kit.inTx(() -> kit.points.popularReward(ops, postRow(ops, null), 100))).isZero();
            assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ?", ops)).isZero();
            assertThat(kit.count("SELECT count(*) FROM point_pending_grants WHERE user_id = ?", ops)).isZero();

            kit.withSettings(Map.of("exclude_ai_and_ops", false), () -> {
                assertThat(earn(ops, postRow(ops, null), "가".repeat(60), NORMAL, true, 0)).as("스위치를 끄면 운영팀 계정도 받는다").isEqualTo(20);
                assertThat(likePost(ops, postRow(ops, null), NORMAL)).isEqualTo(10);
            });
        });
        kit.assertInvariants(ops);
    }

    // ── 보류 ──────────────────────────────────────────────────────────────────

    @Test
    void 보류_시간이_있는_등급의_글_추천_보너스는_보류로_남고_금액은_그때_정책값으로_고정된다() {
        long author = kit.newMember();
        long postId = postRow(author, null);
        kit.withSettings(Map.of("like_bonus_threshold", 1), () -> {
            Instant at = kit.clock.instant();
            assertThat(likePost(author, postId, INFO)).as("지금은 주지 않는다").isZero();
            List<Map<String, Object>> rows = pendings("POST", postId);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0)).containsEntry("reason", "LIKE_BONUS").containsEntry("amount", 50).containsEntry("status", "PENDING").containsEntry("user_id", author);
            assertThat(((Timestamp) rows.get(0).get("release_at")).toInstant()).isEqualTo(at.plus(Duration.ofHours(24)));
            assertThat(ledger(author, "LIKE_BONUS")).isEmpty();

            kit.withSettings(Map.of("tier_info_like_bonus_points", 999), () -> assertThat(release(rows.get(0))).isEqualTo(HeldRelease.RELEASED));
            assertThat(ledger(author, "LIKE_BONUS")).as("보류 때 고정한 금액").containsExactly(50);
            assertThat(release(rows.get(0))).as("확정을 다시 불러도 원장 유일로 두 번 주지 않는다").isEqualTo(HeldRelease.RELEASED);
            assertThat(ledger(author, "LIKE_BONUS")).containsExactly(50);
        });
        kit.assertInvariants(author);
    }

    @Test
    void 댓글_추천_보너스는_전역_금액이고_보류_시간은_그_게시판_등급을_따른다() {
        long author = kit.newMember();
        long postId = postRow(kit.newMember(), null);
        long commentId = commentRow(postId, author);
        kit.withSettings(Map.of("like_bonus_threshold", 1, "like_bonus_points", 13), () -> {
            assertThat(likeComment(author, postId, commentId, CORE)).isZero();
            assertThat(pendings("COMMENT", commentId)).singleElement().satisfies(row -> assertThat(row).containsEntry("amount", 13).containsEntry("reason", "LIKE_BONUS"));
            long other = commentRow(postId, author);
            assertThat(likeComment(author, postId, other, NORMAL)).as("일반 등급(보류 0) 댓글은 바로 전역 금액").isEqualTo(13);
        });
        kit.assertInvariants(author);
    }

    @Test
    void 추천_보너스_하루_횟수는_대기_중인_보류도_센다() {
        long author = kit.newMember();
        kit.withSettings(Map.of("like_bonus_threshold", 1, "like_bonus_daily_limit", 1), () -> {
            long first = postRow(author, null);
            assertThat(likePost(author, first, INFO)).isZero();
            assertThat(pendings("POST", first)).hasSize(1);
            long second = postRow(author, null);
            assertThat(likePost(author, second, INFO)).isZero();
            assertThat(pendings("POST", second)).as("오늘 보류 1건이 하루 1회를 채웠다").isEmpty();
            assertThat(likePost(author, postRow(author, null), NORMAL)).as("바로 주는 등급도 같은 횟수").isZero();
        });
    }

    @Test
    void 확정_때_대상이_사라졌거나_숨겨졌거나_회원이_정상이_아니거나_동결이면_주지_않는다() {
        long deletedAuthor = kit.newMember();
        long hiddenAuthor = kit.newMember();
        long suspended = kit.newMember();
        long frozen = kit.newMember();
        long deleted = postRow(deletedAuthor, null);
        long hidden = postRow(hiddenAuthor, null);
        long ofSuspended = postRow(suspended, null);
        long ofFrozen = postRow(frozen, null);
        kit.withSettings(Map.of("like_bonus_threshold", 1), () -> {
            for (long[] pair : List.of(new long[] {deletedAuthor, deleted}, new long[] {hiddenAuthor, hidden}, new long[] {suspended, ofSuspended},
                    new long[] {frozen, ofFrozen})) {
                likePost(pair[0], pair[1], CORE);
            }
        });
        kit.jdbc.update("UPDATE posts SET deleted_at = now() WHERE id = ?", deleted);
        kit.jdbc.update("UPDATE posts SET hidden_at = now(), hidden_reason = 'REPORTED' WHERE id = ?", hidden);
        kit.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended);
        kit.freeze(frozen);

        assertThat(release(pendings("POST", deleted).get(0))).isEqualTo(HeldRelease.TARGET_GONE);
        assertThat(release(pendings("POST", hidden).get(0))).isEqualTo(HeldRelease.TARGET_GONE);
        assertThat(release(pendings("POST", ofSuspended).get(0))).isEqualTo(HeldRelease.MEMBER_INACTIVE);
        assertThat(release(pendings("POST", ofFrozen).get(0))).isEqualTo(HeldRelease.POINT_FROZEN);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id IN (?, ?, ?, ?)", deletedAuthor, hiddenAuthor, suspended, frozen)).isZero();
    }

    @Test
    void 글_댓글을_지우거나_숨기면_대기_중인_보류가_같은_트랜잭션에서_취소된다() {
        long author = kit.newMember();
        long deleted = postRow(author, null);
        long hidden = postRow(author, null);
        long postId = postRow(kit.newMember(), null);
        long comment = commentRow(postId, author);
        kit.withSettings(Map.of("like_bonus_threshold", 1), () -> {
            likePost(author, deleted, INFO);
            likePost(author, hidden, INFO);
            likeComment(author, postId, comment, INFO);
        });
        assertThat(kit.inTx(() -> kit.points.revokeContent(author, PointService.REF_POST, deleted))).isZero();
        assertThat(kit.inTx(() -> kit.points.cancelHeld(PointService.REF_POST, hidden, null, PointService.CANCEL_HIDDEN))).isEqualTo(1);
        kit.inTx(() -> kit.points.revokeContent(author, PointService.REF_COMMENT, comment));

        assertThat(pendings("POST", deleted).get(0)).containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "DELETED");
        assertThat(pendings("POST", hidden).get(0)).containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "HIDDEN");
        assertThat(pendings("COMMENT", comment).get(0)).containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "DELETED");
        assertThat(kit.count("SELECT count(*) FROM point_pending_grants WHERE user_id = ? AND status = 'PENDING'", author)).isZero();
    }

    // ── 우수 글 ───────────────────────────────────────────────────────────────

    @Test
    void 보류_없는_등급의_우수_글은_바로_주고_원장_유일로_글마다_한_번이다() {
        long author = kit.newMember();
        long postId = postRow(author, null);
        kit.withSettings(Map.of("tier_normal_featured_points", 100), () -> {
            assertThat(kit.inTx(() -> kit.points.featurePost(author, postId, NORMAL))).isEqualTo(new FeatureReward(100, 0, null));
            assertThat(kit.inTx(() -> kit.points.featurePost(author, postId, NORMAL))).as("다시 선정해도 두 번 주지 않는다").isEqualTo(FeatureReward.NONE);
        });
        assertThat(kit.jdbc.queryForList("SELECT kind FROM point_ledger WHERE user_id = ? AND reason = 'POST_FEATURED' AND ref_type = 'POST' AND ref_id = ?", String.class,
                author, postId)).containsExactly(PointKind.EARN.name());
        assertThat(kit.inTx(() -> kit.points.featurePost(author, postRow(author, null), NORMAL))).as("일반 등급 기본 0P — 선정만").isEqualTo(FeatureReward.NONE);
        assertThat(kit.inTx(() -> kit.points.featurePost(author, postRow(author, null), "OFF"))).as("끈 게시판 — 선정만").isEqualTo(FeatureReward.NONE);
        assertThat(kit.inTx(() -> kit.points.featurePost(null, postRow(author, null), CORE))).as("비회원 글").isEqualTo(FeatureReward.NONE);
        kit.assertInvariants(author);
    }

    @Test
    void 보류_등급의_우수_글은_보류로_남고_선정이_취소되면_주지_않는다() {
        long author = kit.newMember();
        long kept = postRow(author, null);
        long cancelled = postRow(author, null);
        long sneaky = postRow(author, null);
        feature(kept, author);
        FeatureReward reward = kit.inTx(() -> kit.points.featurePost(author, kept, CORE));
        assertThat(reward.granted()).isZero();
        assertThat(reward.held()).isEqualTo(500);
        assertThat(reward.releaseAt()).isEqualTo(kit.clock.instant().plus(Duration.ofHours(24)));
        assertThat(pendings("POST", kept)).singleElement().satisfies(row -> assertThat(row).containsEntry("reason", "POST_FEATURED").containsEntry("amount", 500));

        kit.inTx(() -> kit.points.featurePost(author, cancelled, INFO));
        assertThat(kit.inTx(() -> kit.points.cancelHeld(PointService.REF_POST, cancelled, PointReason.POST_FEATURED, HeldRelease.UNFEATURED.name()))).isEqualTo(1);
        assertThat(pendings("POST", cancelled).get(0)).containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "UNFEATURED");

        kit.inTx(() -> kit.points.featurePost(author, sneaky, INFO)); // 선정 줄이 없다(취소됐는데 보류 취소가 빠진 경우) — 확정 때 다시 본다
        assertThat(release(pendings("POST", sneaky).get(0))).isEqualTo(HeldRelease.UNFEATURED);

        assertThat(release(pendings("POST", kept).get(0))).isEqualTo(HeldRelease.RELEASED);
        assertThat(ledger(author, "POST_FEATURED")).containsExactly(500);
        kit.assertInvariants(author);
    }

    @Test
    void 확정_뒤_글을_지우면_우수_글_보상을_받은_양만_회수하고_복구하면_되돌린다() {
        long author = kit.newMember();
        long postId = postRow(author, null);
        kit.withSettings(Map.of("tier_normal_featured_points", 100), () -> kit.inTx(() -> kit.points.featurePost(author, postId, NORMAL)));
        kit.adjust(author, -60); // 일부를 써서 40P 만 남았다 — 받은 양만, 0P 바닥(모자라면 미회수)
        assertThat(kit.revokePost(author, postId)).isEqualTo(40);
        assertThat(ledger(author, "POST_FEATURED_REVOKE")).containsExactly(-40);
        assertThat(kit.debt(author)).isEqualTo(60);
        assertThat(kit.revokePost(author, postId)).as("두 번 회수하지 않는다").isZero();
        assertThat(kit.restorePost(author, postId)).as("복구는 회수한 몫을 되돌린다").isEqualTo(40);
        kit.assertInvariants(author);
    }

    // ── 인기글 보상 · 사유 ─────────────────────────────────────────────────────

    @Test
    void 인기글_보상은_글마다_한_번이다() {
        long author = kit.newMember();
        long postId = postRow(author, null);
        assertThat(kit.inTx(() -> kit.points.popularReward(author, postId, 100))).isEqualTo(100);
        assertThat(kit.inTx(() -> kit.points.popularReward(author, postId, 100))).isZero();
        assertThat(ledger(author, "POPULAR")).containsExactly(100);
        kit.assertInvariants(author);
    }

    @Test
    void 우수_글_사유는_적립_회수_종류이고_경험치에_더하고_뺀다() {
        assertThat(PointReason.POST_FEATURED.kind(100)).isEqualTo(PointKind.EARN);
        assertThat(PointReason.POST_FEATURED_REVOKE.kind(-100)).isEqualTo(PointKind.REVOKE);
        assertThat(XpService.GAIN).contains(PointReason.POST_FEATURED);
        assertThat(XpService.LOSS).contains(PointReason.POST_FEATURED_REVOKE);
        assertThat(XpService.GAIN).doesNotContain(PointReason.POST_FEATURED_REVOKE);
    }
}
