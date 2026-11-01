package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.dto.response.PostDealView;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * 종료 요청(서버 단계 고정 규칙 4, 확정 가정 「종료 요청」) — 자격 셋 · 자기 글 · 중복 409 는 한도를 쓰지 않음 · 하루 = 한국 0시 · 같은 작성자 기간 한도 · 끝난 딜 ·
 * 문턱 → 의심 + 작성자 알림 · 의심 구간 표시 · 동시 요청(문턱 경계).
 */
class HotdealEndRequestTest extends HotdealStage2TestBase {

    @Test
    void 자격은_쓰기_등급_이메일_인증_정지_아님() {
        long post = newDeal(member());
        assertThat(error(() -> request(post, member("SEEDLING", true), "SOLD_OUT")).getErrorCode()).isEqualTo("DEAL_END_REQUEST_NOT_ALLOWED");
        assertThat(error(() -> request(post, member("REGULAR", false), "SOLD_OUT")).getErrorCode()).isEqualTo("DEAL_END_REQUEST_NOT_ALLOWED");
        long suspended = member();
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended);
        assertThat(error(() -> request(post, suspended, "SOLD_OUT")).getErrorCode()).isEqualTo("DEAL_END_REQUEST_NOT_ALLOWED");
        assertThat(error(() -> states.request(Long.toString(post), null, "SOLD_OUT")).getErrorCode()).isEqualTo("UNAUTHENTICATED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ?", Integer.class, post)).isZero();

        PostDealView ok = request(post, member(), "PRICE_CHANGED");
        assertThat(ok.endRequests().total()).isEqualTo(1);
        assertThat(ok.endRequests().byPriceChanged()).isEqualTo(1);
        assertThat(ok.endRequests().mine()).isEqualTo("PRICE_CHANGED");
    }

    @Test
    void 자기_글에는_요청할_수_없고_사유는_셋_중_하나() {
        long author = member();
        long post = newDeal(author);
        assertThat(error(() -> request(post, author, "SOLD_OUT")).getErrorCode()).isEqualTo("DEAL_END_REQUEST_OWN");
        CommonException bad = error(() -> request(post, member(), "OTHER"));
        assertThat(bad.getFields()).containsKey("reason");
        assertThat(error(() -> request(post, member(), null)).getFields()).containsKey("reason");
    }

    @Test
    void 일반_게시판_글에는_딜이_없다() {
        long author = member();
        long id = Long.parseLong(posts.create(new com.community.post.dto.request.PostWriteRequest(generalBoard(), null, "일반 글 " + uniq(), "TEXT", BODY, false, null,
                null, null, null), author, "127.0.0.1").id());
        assertThat(error(() -> request(id, member(), "SOLD_OUT")).getErrorCode()).isEqualTo("DEAL_NOT_FOUND");
    }

    @Test
    void 중복_요청은_409_이고_하루_한도를_쓰지_않는다() {
        setting("end_request_daily_limit", 2);
        long m = member();
        long a = newDeal(member());
        long b = newDeal(member());
        long c = newDeal(member());
        request(a, m, "SOLD_OUT");
        assertThat(error(() -> request(a, m, "LINK_DEAD")).getErrorCode()).isEqualTo("DEAL_END_REQUEST_EXISTS");
        request(b, m, "SOLD_OUT"); // 중복이 한도를 썼다면 여기서 429
        CommonException limit = error(() -> request(c, m, "SOLD_OUT"));
        assertThat(limit.getErrorCode()).isEqualTo("DEAL_END_REQUEST_DAILY_LIMIT");
        assertThat(limit.getExtras()).containsEntry("limit", 2);
    }

    @Test
    void 하루는_한국_0시에_바뀐다() {
        setting("end_request_daily_limit", 1);
        long m = member();
        long a = newDeal(member());
        long b = newDeal(member());
        long c = newDeal(member());
        Instant midnight = ServiceTime.dateOf(clock.instant()).plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant();
        clock.set(midnight.minusSeconds(60));
        request(a, m, "SOLD_OUT");
        clock.set(midnight.minusSeconds(1));
        assertThat(error(() -> request(b, m, "SOLD_OUT")).getErrorCode()).isEqualTo("DEAL_END_REQUEST_DAILY_LIMIT");
        clock.set(midnight);
        request(b, m, "SOLD_OUT");
        assertThat(error(() -> request(c, m, "SOLD_OUT")).getErrorCode()).isEqualTo("DEAL_END_REQUEST_DAILY_LIMIT");
    }

    @Test
    void 같은_작성자의_글에는_기간_한도가_있다() {
        setting("end_request_per_author_limit", 2);
        setting("end_request_per_author_days", 7);
        setting("same_store_interval_minutes", 0);
        long author = member();
        long m = member();
        long a = newDeal(author);
        long b = newDeal(author);
        long c = newDeal(author);
        request(a, m, "SOLD_OUT");
        request(b, m, "SOLD_OUT");
        CommonException limit = error(() -> request(c, m, "SOLD_OUT"));
        assertThat(limit.getErrorCode()).isEqualTo("DEAL_END_REQUEST_AUTHOR_LIMIT");
        assertThat(limit.getExtras()).containsEntry("limit", 2).containsEntry("days", 7);
        request(newDeal(member()), m, "SOLD_OUT"); // 다른 작성자의 글은 된다
        clock.set(clock.instant().plus(Duration.ofDays(7)).plusSeconds(1));
        request(c, m, "SOLD_OUT"); // 기간이 지나면 다시
    }

    @Test
    void 작성자_기간_한도_0_은_없음() {
        setting("end_request_per_author_limit", 0);
        setting("same_store_interval_minutes", 0);
        long author = member();
        long m = member();
        for (int i = 0; i < 5; i++) {
            request(newDeal(author), m, "SOLD_OUT");
        }
    }

    @Test
    void 끝난_딜과_기간_종료된_딜에는_요청할_수_없다() {
        long author = member();
        long ended = newDeal(author);
        states.end(Long.toString(ended), author);
        assertThat(error(() -> request(ended, member(), "SOLD_OUT")).getErrorCode()).isEqualTo("DEAL_ENDED");

        long expired = newDeal(member());
        clock.set(clock.instant().plus(Duration.ofDays(30)).plusSeconds(1));
        assertThat(error(() -> request(expired, member(), "SOLD_OUT")).getErrorCode()).isEqualTo("DEAL_ENDED");
    }

    @Test
    void 문턱에_닿으면_의심이_되고_작성자에게_한_번_알린다() {
        long author = member();
        long post = newDeal(author);
        requestBy(post, 2);
        assertThat(dealRow(post).get("flagged_at")).isNull();
        assertThat(notices(author, "DEAL_FLAGGED", post)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ? AND flagged_period", Integer.class, post)).isZero();

        PostDealView third = request(post, member(), "LINK_DEAD");
        assertThat(third.view()).isEqualTo("FLAGGED");
        assertThat(third.flaggedAt()).isNotNull();
        assertThat(third.autoEndAt()).isEqualTo(third.flaggedAt().plus(Duration.ofHours(12)));
        assertThat(notices(author, "DEAL_FLAGGED", post)).isEqualTo(1);
        // 의심이 된 회차와 의심인 동안의 요청이 의심 구간
        request(post, member(), "SOLD_OUT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ? AND flagged_period", Integer.class, post)).isEqualTo(4);
        assertThat(notices(author, "DEAL_FLAGGED", post)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload->>'title' FROM member_notifications WHERE user_id = ? AND type = 'DEAL_FLAGGED' AND post_id = ?", String.class,
                author, post)).startsWith("로봇 청소기");
    }

    @Test
    void 문턱은_설정값이다() {
        setting("end_request_threshold", 1);
        long post = newDeal(member());
        assertThat(request(post, member(), "SOLD_OUT").view()).isEqualTo("FLAGGED");
    }

    @Test
    void 동시_요청이_문턱을_넘어도_의심과_알림은_한_번() throws Exception {
        long author = member();
        long post = newDeal(author);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            members.add(member());
        }
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<PostDealView>> results = new ArrayList<>();
            for (Long m : members) {
                Callable<PostDealView> call = () -> {
                    start.await();
                    return request(post, m, "SOLD_OUT");
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            for (Future<PostDealView> f : results) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ?", Integer.class, post)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ? AND flagged_period", Integer.class, post)).isEqualTo(6);
        assertThat(notices(author, "DEAL_FLAGGED", post)).isEqualTo(1);
        assertThat(dealRow(post).get("flagged_at")).isNotNull();
    }

    @Test
    void 같은_회원의_동시_요청도_하루_한도를_넘지_못한다() throws Exception {
        setting("end_request_daily_limit", 2);
        long m = member();
        List<Long> deals = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            deals.add(newDeal(member()));
        }
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        int failed = 0;
        try {
            List<Future<PostDealView>> results = new ArrayList<>();
            for (Long d : deals) {
                results.add(pool.submit(() -> {
                    start.await();
                    return request(d, m, "SOLD_OUT");
                }));
            }
            start.countDown();
            for (Future<PostDealView> f : results) {
                try {
                    f.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    assertThat(((CommonException) e.getCause()).getErrorCode()).isEqualTo("DEAL_END_REQUEST_DAILY_LIMIT");
                    failed++;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(failed).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE member_id = ?", Integer.class, m)).isEqualTo(2);
    }
}
