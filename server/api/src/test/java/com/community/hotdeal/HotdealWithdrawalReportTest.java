package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.service.DealWithdrawal;
import com.community.report.dto.request.ReportRequest;
import com.community.report.service.ReportService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 탈퇴·정지(서버 단계 고정 규칙 6) · 신고 연결(D4 · 규칙 11) — 가격 허위 신고 = 종료 요청 PRICE_CHANGED 1건(같은 회원 1회 · 이미 요청했으면 신고만 · 자격 미달이면 신고만),
 * 자동 숨김 셈 제외 / 광고 숨김 사유는 셈 포함 · 핫딜 사유는 핫딜 글에서만(일반 글·댓글 400).
 */
class HotdealWithdrawalReportTest extends HotdealStage2TestBase {

    @Autowired
    private DealWithdrawal withdrawal;
    @Autowired
    private ReportService reports;

    // ── 탈퇴·정지 ────────────────────────────────────────────────────────────

    @Test
    void 탈퇴_작성자의_진행_중_딜은_작성자_종료되고_의심이었으면_보상한다() {
        long author = member();
        long flagged = newDeal(author);
        List<Long> requesters = requestBy(flagged, 3);
        setting("same_store_interval_minutes", 0);
        long plain = newDeal(author);
        long alreadyEnded = newDeal(author);
        ends.endByManager(alreadyEnded, hotdealOperator(), null);

        DealWithdrawal.Result result = withdrawal.withdraw(author);
        assertThat(result.endedDeals()).isEqualTo(2);
        assertThat(dealRow(flagged)).containsEntry("status", "ENDED").containsEntry("ended_by", "AUTHOR");
        assertThat(dealRow(plain)).containsEntry("status", "ENDED").containsEntry("ended_by", "AUTHOR");
        assertThat(dealRow(alreadyEnded)).containsEntry("ended_by", "MANAGER");
        requesters.forEach(m -> assertThat(rewards(m)).isEqualTo(1));
        assertInvariants(requesters);
    }

    @Test
    void 탈퇴_요청자의_요청_줄은_지우지만_이미_의심인_상태는_그대로() {
        long post = newDeal(member());
        List<Long> requesters = requestBy(post, 3);
        Object flaggedAt = dealRow(post).get("flagged_at");
        assertThat(flaggedAt).isNotNull();

        DealWithdrawal.Result result = withdrawal.withdraw(requesters.get(0));
        assertThat(result.removedRequests()).isEqualTo(1);
        assertThat(result.endedDeals()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ?", Integer.class, post)).isEqualTo(2);
        assertThat(dealRow(post).get("flagged_at")).isEqualTo(flaggedAt);
        assertThat(postQueries.detail(Long.toString(post), null, "127.0.0.1").deal().view()).isEqualTo("FLAGGED");
    }

    @Test
    void 정지_회원의_딜은_그대로이고_요청도_받는다() {
        long author = member();
        long post = newDeal(author);
        // SQL 로 남김: 회원 상태(정지·차단·탈퇴)·이메일·인증 칸을 그대로 두는 도메인 메서드가 없다
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", author);
        assertThat(dealRow(post)).containsEntry("status", "ACTIVE");
        assertThat(request(post, member(), "SOLD_OUT").endRequests().total()).isEqualTo(1);
        assertThat(dealRow(post)).containsEntry("status", "ACTIVE");
    }

    // ── 신고 연결(D4) ─────────────────────────────────────────────────────────

    private void report(long reporter, String type, long target, String reason) {
        reports.report(new ReportRequest(type, Long.toString(target), reason, null), reporter);
    }

    @Test
    void 가격_허위_신고는_PRICE_CHANGED_종료_요청_한_건() {
        long post = newDeal(member());
        long reporter = member();
        report(reporter, "POST", post, "DEAL_FALSE_PRICE");
        Map<String, Object> row = jdbc.queryForMap("SELECT reason, from_report FROM deal_end_requests WHERE post_id = ? AND member_id = ?", post, reporter);
        assertThat(row).containsEntry("reason", "PRICE_CHANGED").containsEntry("from_report", true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE target_id = ? AND reporter_id = ? AND reason = 'DEAL_FALSE_PRICE'", Integer.class, post,
                reporter)).isEqualTo(1);
        // 같은 회원은 한 번 — API 요청은 409
        assertThat(error(() -> request(post, reporter, "SOLD_OUT")).getErrorCode()).isEqualTo("DEAL_END_REQUEST_EXISTS");
    }

    @Test
    void 이미_요청한_회원의_가격_허위_신고는_신고만_받고_한도를_쓰지_않는다() {
        setting("end_request_daily_limit", 1);
        long post = newDeal(member());
        long reporter = member();
        request(post, reporter, "SOLD_OUT");
        report(reporter, "POST", post, "DEAL_FALSE_PRICE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE member_id = ?", Integer.class, reporter)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reason FROM deal_end_requests WHERE member_id = ?", String.class, reporter)).isEqualTo("SOLD_OUT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE target_id = ? AND reporter_id = ?", Integer.class, post, reporter)).isEqualTo(1);
    }

    @Test
    void 자격_미달_회원의_가격_허위_신고는_신고만() {
        long post = newDeal(member());
        long unverified = member("REGULAR", false);
        report(unverified, "POST", post, "DEAL_FALSE_PRICE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE member_id = ?", Integer.class, unverified)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE target_id = ? AND reporter_id = ?", Integer.class, post, unverified)).isEqualTo(1);
    }

    @Test
    void 가격_허위는_자동_숨김_셈에서_빠지고_광고_숨김은_센다() {
        int threshold = jdbc.queryForObject("SELECT auto_hide_threshold FROM board_report_settings LIMIT 1", Integer.class);
        assertThat(threshold).isPositive();
        long priced = newDeal(member());
        for (int i = 0; i < threshold; i++) {
            report(member(), "POST", priced, "DEAL_FALSE_PRICE");
        }
        assertThat(jdbc.queryForObject("SELECT hidden_at FROM posts WHERE id = ?", Object.class, priced)).isNull();
        assertThat(dealRow(priced).get("flagged_at")).isNotNull(); // 문턱만큼 종료 요청이 됐다

        long ad = newDeal(member());
        for (int i = 0; i < threshold; i++) {
            report(member(), "POST", ad, "DEAL_HIDDEN_AD");
        }
        assertThat(jdbc.queryForObject("SELECT hidden_reason FROM posts WHERE id = ?", String.class, ad)).isEqualTo("REPORTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ?", Integer.class, ad)).isZero();
    }

    @Test
    void 핫딜_사유는_일반_글과_댓글에서_400() {
        long author = member();
        long general = Long.parseLong(posts.create(new com.community.post.dto.request.PostWriteRequest(generalBoard(), null, "일반 글 " + uniq(), "TEXT", BODY, false, null,
                null, null, null), author, "127.0.0.1").id());
        assertThat(error(() -> report(member(), "POST", general, "DEAL_FALSE_PRICE")).getFields()).containsKey("reason");
        assertThat(error(() -> report(member(), "POST", general, "DEAL_HIDDEN_AD")).getFields()).containsKey("reason");

        long post = newDeal(author);
        long commentId = ids.nextId(); // 댓글 사유 체계에 핫딜 사유가 없어 대상을 보기 전에 400
        assertThat(error(() -> report(member(), "COMMENT", commentId, "DEAL_HIDDEN_AD")).getFields()).containsKey("reason");
        report(member(), "POST", post, "SPAM_AD"); // 핫딜 글도 일반 사유는 그대로
    }
}
