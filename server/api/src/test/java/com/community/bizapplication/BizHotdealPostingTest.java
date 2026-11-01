package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.dto.request.BizDealInfo;
import com.community.bizapplication.dto.response.BizDealPostView;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.service.BizHotdealPublisher;
import com.community.hotdeal.service.DealEndService;
import com.community.hotdeal.service.DealStateService;
import com.community.permission.entity.Permission;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사업자 핫딜 게시(biz-hotdeal-contract 규칙 1~4·6·10) — 시작 시각 게시 · 1:1 · 게시 직전 재판정과 실패 · 실패 건 운영자 취소 전액 환불 · 숨김·되돌림 · 사업자 종료·자동 종료.
 */
class BizHotdealPostingTest extends BizHotdealTestBase {

    @Autowired
    private DealEndService ends;
    @Autowired
    private DealStateService states;

    // ── 규칙 1·2 게시 ──────────────────────────────────────────────────────

    @Test
    void 규칙1_시작_전에는_배치가_글을_만들지_않고_시작_뒤_배치가_글과_사업자_딜을_만든다() {
        Booked b = booked("NONE");
        clock.set(b.period().start().minus(Duration.ofMinutes(1)));
        runPublishJob();
        assertThat(resultCount(b.id())).isZero();
        assertThat(businessPosts(b.id())).isZero();

        into(b, 0);
        runPublishJob();
        long postId = postOf(b.id());
        assertThat(result(b.id()).get("status")).isEqualTo("POSTED");
        assertThat(events(b.id())).contains("POSTED");
        assertThat(memberNotices(b.user(), "POSTED")).isEqualTo(1);
        assertThat(view.detail(b.user(), b.id()).post().status()).isEqualTo("POSTED");
        assertThat(postId).isPositive();
    }

    @Test
    void 규칙1_다시_돌려도_두_번_만들지_않는다_신청_건과_글은_1대1() {
        Booked b = postedBooking("NONE");
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.SKIPPED);
        runPublishJob();
        publisher.onScheduled(b.id());
        assertThat(businessPosts(b.id())).isEqualTo(1);
        assertThat(resultCount(b.id())).isEqualTo(1);
        assertThat(events(b.id()).stream().filter("POSTED"::equals).count()).isEqualTo(1);
        // DB 유일 제약 — 같은 신청 건의 두 번째 결과 줄은 들어가지 않는다
        assertThat(jdbc.update("INSERT INTO biz_deal_posts (application_id, post_id, status, created_at, updated_at) VALUES (?, ?, 'POSTED', now(), now()) "
                + "ON CONFLICT DO NOTHING", b.id(), ids.nextId())).isZero();
    }

    @Test
    void 규칙1_onScheduled_는_시작이_지난_건만_바로_게시한다() {
        // 시작 전 결제 확정 → 글 없음(배치 몫)
        Booked before = booked("NONE");
        assertThat(resultCount(before.id())).isZero();
        // 수동 결제 — 승인 뒤 시작이 지나서 입금 확인·예약 확정 → 그 순간 게시
        long user = approvedBusiness();
        long id = submitDeal(user, "NONE", null, null, "MANUAL");
        var p = freshPeriod(3);
        approve(id, "MANUAL", p);
        clock.set(p.start().plus(Duration.ofHours(2)));
        confirmManual(id);
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(result(id).get("status")).isEqualTo("POSTED");
        assertThat(businessPosts(id)).isEqualTo(1);
    }

    @Test
    void 규칙2_작성자_게시판_말머리_상호_마감_재고_이미지가_신청_값이다() {
        long user = approvedBusiness();
        long image = adImage(user);
        var d = deal("NONE");
        var withImage = new com.community.bizapplication.dto.request.BizDealInfo(d.linkUrl(), d.shopName(), d.listPrice(), d.dealPrice(), d.shippingFee(), d.endsOn(),
                "LOW", d.body(), d.placement(), d.boardId(), d.prefixId(), Long.toString(image), null);
        Booked b = booked(user, "NONE", withImage);
        into(b, 1);
        publisher.publishDue(b.id());
        long postId = postOf(b.id());
        var post = postRow(postId);
        var deal = dealRow(postId);
        assertThat(post.get("author_id")).isEqualTo(b.user());
        assertThat(post.get("board_id")).isEqualTo(dealBoard());
        assertThat(post.get("prefix_id")).isEqualTo(dealPrefix());
        assertThat(post.get("title")).isEqualTo("[시험상사] 텀블러 1+1");
        assertThat(deal.get("source")).isEqualTo("BUSINESS");
        assertThat(deal.get("biz_application_id")).isEqualTo(b.id());
        assertThat(deal.get("business_name")).isEqualTo("시험상사");
        assertThat(((Timestamp) deal.get("ends_at")).toInstant()).isEqualTo(b.period().end());
        assertThat(deal.get("stock_state")).isEqualTo("LOW");
        assertThat(deal.get("image_source")).isEqualTo("UPLOAD");
        assertThat(deal.get("image_id")).isEqualTo(image);
        assertThat(deal.get("deal_price")).isEqualTo(15_900L);
        assertThat(deal.get("normal_price")).isEqualTo(32_000L);
        // 신청 건 상세의 「게시된 글」
        BizDealPostView v = view.detail(b.user(), b.id()).post();
        assertThat(v.postId()).isEqualTo(Long.toString(postId));
        assertThat(v.view()).isEqualTo("ACTIVE");
        assertThat(v.prefixName()).isEqualTo("로봇");
        assertThat(v.publishAt()).isEqualTo(b.period().start());
        assertThat(v.endRequests().total()).isZero();
        assertThat(view.detail(b.user(), b.id()).deal().imageFileId()).isEqualTo(Long.toString(image));
    }

    // ── 규칙 3 게시 직전 재판정 ──────────────────────────────────────────────

    @Test
    void 규칙3_게시판이_꺼졌으면_글_없이_POST_FAILED_BOARD_CLOSED_와_운영자_사업자_알림() {
        long operator = operatorWith(Permission.HOTDEAL_MANAGE);
        Booked b = booked("NONE");
        jdbc.update("UPDATE boards SET active = false WHERE id = ?", dealBoard());
        boardCatalog.invalidate();
        into(b, 1);
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.FAILED);
        assertFailed(b, "BOARD_CLOSED");
        assertThat(adminNotices(operator, "HOTDEAL_POST_FAILED")).isEqualTo(1);
        // 다시 돌려도 다시 시도하지 않는다(게시판을 켜도)
        jdbc.update("UPDATE boards SET active = true WHERE id = ?", dealBoard());
        boardCatalog.invalidate();
        runPublishJob();
        assertThat(businessPosts(b.id())).isZero();
        assertThat(view.detail(b.user(), b.id()).post().status()).isEqualTo("FAILED");
        assertThat(view.detail(b.user(), b.id()).post().failReason()).isEqualTo("BOARD_CLOSED");
    }

    @Test
    void 규칙3_링크가_지금_규칙에서_차단이면_LINK_BLOCKED() {
        String host = uniqueHost();
        Booked b = booked("NONE", dealAt(host, "NONE"));
        block(host);
        into(b, 1);
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.FAILED);
        assertFailed(b, "LINK_BLOCKED");
    }

    @Test
    void 규칙3_사업자가_정지됐으면_BUSINESS_SUSPENDED() {
        Booked b = booked("NONE");
        jdbc.update("UPDATE business_profiles SET status = 'SUSPENDED', suspend_code = 'OTHER', suspend_text = '시험', suspended_at = now(), suspend_pending_action = 'KEEP', "
                + "suspend_scheduled_action = 'HOLD', suspend_running_action = 'TAKEDOWN_ONLY' WHERE id = ?", profileOf(b.user()));
        into(b, 1);
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.FAILED);
        assertFailed(b, "BUSINESS_SUSPENDED");
    }

    @Test
    void 말머리가_꺼졌으면_실패가_아니라_말머리_없이_게시하고_말머리_상단_노출이_나오지_않는다고_운영자에게_알린다() {
        long operator = operatorWith(Permission.HOTDEAL_MANAGE);
        Booked b = booked("CATEGORY_TOP");
        jdbc.update("UPDATE board_prefixes SET active = false WHERE id = ?", dealPrefix());
        boardCatalog.invalidate();
        into(b, 1);
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.POSTED);
        long postId = postOf(b.id());
        assertThat(postRow(postId).get("prefix_id")).isNull();
        assertThat(result(b.id()).get("status")).isEqualTo("POSTED");
        assertThat(events(b.id())).contains("POSTED", "POSTED_WITHOUT_PREFIX").doesNotContain("POST_FAILED");
        assertThat(eventNote(b.id(), "POSTED_WITHOUT_PREFIX")).isEqualTo(BizHotdealPublisher.CATEGORY_TOP_OFF);
        assertThat(adminNotices(operator, "HOTDEAL_POST_PREFIX_DROPPED")).isEqualTo(1);
        assertThat(adminNotices(operator, "HOTDEAL_POST_FAILED")).isZero();
        assertThat(memberNotices(b.user(), "POSTED")).isEqualTo(1);
        BizDealPostView v = view.detail(b.user(), b.id()).post();
        assertThat(v.status()).isEqualTo("POSTED");
        assertThat(v.prefixName()).isNull();
        assertThat(v.placement().status()).isEqualTo("STOPPED");
        assertThat(v.placement().stopReason()).isEqualTo("PREFIX_MISSING");
        assertThat(app(b.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
    }

    @Test
    void 말머리가_지워졌고_말머리_상단이_없으면_말머리_없이_게시하고_노출_문구는_붙이지_않는다() {
        long operator = operatorWith(Permission.HOTDEAL_MANAGE);
        Booked b = booked("HOME_TOP");
        jdbc.update("DELETE FROM board_prefixes WHERE id = ?", dealPrefix());
        boardCatalog.invalidate();
        into(b, 1);
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.POSTED);
        assertThat(postRow(postOf(b.id())).get("prefix_id")).isNull();
        assertThat(events(b.id())).contains("POSTED_WITHOUT_PREFIX");
        assertThat(eventNote(b.id(), "POSTED_WITHOUT_PREFIX")).isNull();
        assertThat(adminNotices(operator, "HOTDEAL_POST_PREFIX_DROPPED")).isEqualTo(1);
        // 게시판 상단 노출은 말머리와 무관해 그대로 나온다
        assertThat(view.detail(b.user(), b.id()).post().placement().status()).isEqualTo("SHOWING");
    }

    @Test
    void 말머리가_있으면_그대로_게시하고_말머리_없이_게시_기록_알림은_없다() {
        long operator = operatorWith(Permission.HOTDEAL_MANAGE);
        Booked b = booked("CATEGORY_TOP");
        into(b, 1);
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.POSTED);
        assertThat(postRow(postOf(b.id())).get("prefix_id")).isEqualTo(dealPrefix());
        assertThat(events(b.id())).doesNotContain("POSTED_WITHOUT_PREFIX");
        assertThat(adminNotices(operator, "HOTDEAL_POST_PREFIX_DROPPED")).isZero();
        assertThat(view.detail(b.user(), b.id()).post().placement().status()).isEqualTo("SHOWING");
    }

    @Test
    void 말머리를_안_고른_신청은_말머리_없이_게시하고_말머리_없이_게시_기록_알림은_없다() {
        long operator = operatorWith(Permission.HOTDEAL_MANAGE);
        BizDealInfo d = deal("HOME_TOP");
        Booked b = booked("HOME_TOP", new BizDealInfo(d.linkUrl(), d.shopName(), d.listPrice(), d.dealPrice(), d.shippingFee(), d.endsOn(), d.stock(), d.body(),
                d.placement(), d.boardId(), null, d.imageFileId(), null));
        into(b, 1);
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.POSTED);
        assertThat(postRow(postOf(b.id())).get("prefix_id")).isNull();
        assertThat(events(b.id())).contains("POSTED").doesNotContain("POSTED_WITHOUT_PREFIX", "POST_FAILED");
        assertThat(adminNotices(operator, "HOTDEAL_POST_PREFIX_DROPPED")).isZero();
        BizDealPostView v = view.detail(b.user(), b.id()).post();
        assertThat(v.prefixName()).isNull();
        assertThat(v.placement().status()).isEqualTo("SHOWING");
    }

    private void assertFailed(Booked b, String reason) {
        assertThat(businessPosts(b.id())).isZero();
        assertThat(result(b.id()).get("status")).isEqualTo("FAILED");
        assertThat(result(b.id()).get("fail_reason")).isEqualTo(reason);
        assertThat(result(b.id()).get("post_id")).isNull();
        assertThat(eventNote(b.id(), "POST_FAILED")).isEqualTo(reason);
        assertThat(memberNotices(b.user(), "POST_FAILED")).isEqualTo(1);
        assertThat(app(b.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
    }

    // ── 규칙 10 실패 건 운영자 취소 ─────────────────────────────────────────

    @Test
    void 규칙10_게시_실패_건은_시작_뒤에도_운영자_취소가_전액_환불이고_실패가_아닌_진행_중_건은_409() {
        Booked failed = booked("NONE");
        jdbc.update("UPDATE boards SET active = false WHERE id = ?", dealBoard());
        boardCatalog.invalidate();
        into(failed, 5);
        publisher.publishDue(failed.id());
        long paid = app(failed.id()).approved().total();
        review.cancel(operator, failed.id(), "게시 실패 — 운영자 정리");
        assertThat(app(failed.id()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        var refund = refunds.latest(failed.id()).orElseThrow();
        assertThat(refund.type().name()).isEqualTo("FULL_AUTO");
        assertThat(refund.status().name()).isEqualTo("APPROVED");
        assertThat(refund.approvedAmount()).isEqualTo(paid);

        jdbc.update("UPDATE boards SET active = true WHERE id = ?", dealBoard());
        boardCatalog.invalidate();
        Booked running = postedBooking("NONE");
        assertThat(codeOf(() -> review.cancel(operator, running.id(), "진행 중은 환불 승인으로"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }

    // ── 규칙 4 숨김·되돌림 ──────────────────────────────────────────────────

    @Test
    void 규칙4_긴급_내림은_글을_숨기고_되돌리기는_다시_보이며_딜_마감을_연장된_끝에_맞춘다() {
        Booked b = postedBooking("NONE");
        long postId = postOf(b.id());
        ads.takedown(operator, b.id(), "허위 표기 신고", false);
        assertThat(postRow(postId).get("hidden_reason")).isEqualTo("STAFF");
        assertThat(postRow(postId).get("deleted_at")).isNull(); // 숨김은 삭제가 아니다
        assertThat(events(b.id())).contains("POST_HIDDEN");
        assertThat(view.detail(b.user(), b.id()).post().status()).isEqualTo("HIDDEN");

        clock.set(clock.instant().plus(Duration.ofHours(2)));
        ads.restore(operator, b.id(), "EXTEND", "오해였어요");
        assertThat(postRow(postId).get("hidden_at")).isNull();
        assertThat(events(b.id())).contains("POST_RESTORED");
        Instant end = app(b.id()).endAt();
        assertThat(end).isAfter(b.period().end());
        assertThat(((Timestamp) dealRow(postId).get("ends_at")).toInstant()).isEqualTo(end);
        assertThat(view.detail(b.user(), b.id()).post().status()).isEqualTo("POSTED");
    }

    @Test
    void 규칙4_운영자가_따로_숨긴_글은_되돌리기가_풀지_않는다() {
        Booked b = postedBooking("NONE");
        long postId = postOf(b.id());
        jdbc.update("UPDATE posts SET hidden_at = now(), hidden_reason = 'STAFF' WHERE id = ?", postId); // 운영자가 먼저 숨겼다
        ads.takedown(operator, b.id(), "긴급", false);
        assertThat(events(b.id())).doesNotContain("POST_HIDDEN");
        clock.set(clock.instant().plus(Duration.ofMinutes(30)));
        ads.restore(operator, b.id(), "EXTEND", "되돌림");
        assertThat(postRow(postId).get("hidden_at")).isNotNull();
        assertThat(events(b.id())).doesNotContain("POST_RESTORED");
    }

    @Test
    void 규칙4_진행_중_환불_승인으로_취소되면_글을_숨긴다() {
        Booked b = postedBooking("NONE");
        long postId = postOf(b.id());
        members.requestRefund(b.user(), b.id(), "판매 종료");
        review.approveRefund(operator, b.id(), 1000L, "일할");
        assertThat(app(b.id()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(postRow(postId).get("hidden_reason")).isEqualTo("STAFF");
        assertThat(events(b.id())).contains("POST_HIDDEN");
        assertThat(view.detail(b.user(), b.id()).post().status()).isEqualTo("HIDDEN");
    }

    // ── 규칙 6 사업자 종료·자동 종료 ────────────────────────────────────────

    @Test
    void 규칙6_사업자_종료는_딜만_끝내고_신청_건_상태_그대로_환불_없음_두_번째는_409_DEAL_ENDED() {
        Booked b = postedBooking("HOME_TOP");
        long postId = postOf(b.id());
        BizDealPostView v = view.endDeal(b.user(), b.id());
        assertThat(v.view()).isEqualTo("ENDED");
        assertThat(v.endedBy()).isEqualTo("AUTHOR");
        assertThat(v.endedAt()).isNotNull();
        assertThat(v.placement().status()).isEqualTo("STOPPED");
        assertThat(v.placement().stopReason()).isEqualTo("DEAL_ENDED");
        assertThat(dealRow(postId).get("status")).isEqualTo("ENDED");
        assertThat(app(b.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(refundRows(b.id())).isZero();
        assertThat(events(b.id())).contains("DEAL_ENDED_BY_BUSINESS");
        assertThat(codeOf(() -> view.endDeal(b.user(), b.id()))).isEqualTo("DEAL_ENDED");
        assertThat(codeOf(() -> view.endDeal(approvedBusiness(), b.id()))).isEqualTo("BIZ_APPLICATION_NOT_FOUND");
    }

    @Test
    void 규칙6_글이_없으면_사업자_종료는_409_STATE_CHANGED() {
        Booked b = booked("NONE");
        assertThat(codeOf(() -> view.endDeal(b.user(), b.id()))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }

    @Test
    void 규칙6_글_보기의_작성자_딜_종료도_DEAL_ENDED_BY_BUSINESS_환불_없음() {
        Booked b = postedBooking("NONE");
        long postId = postOf(b.id());
        states.end(Long.toString(postId), b.user());
        assertThat(dealRow(postId).get("ended_by")).isEqualTo("AUTHOR");
        assertThat(events(b.id())).contains("DEAL_ENDED_BY_BUSINESS");
        assertThat(app(b.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(refundRows(b.id())).isZero();
    }

    @Test
    void 규칙6_종료_요청_자동_종료는_DEAL_AUTO_ENDED_신청_건_상태_그대로_환불_없음() {
        Booked b = postedBooking("NONE");
        long postId = postOf(b.id());
        jdbc.update("UPDATE post_deals SET flagged_at = ? WHERE post_id = ?", Timestamp.from(clock.instant()), postId);
        assertThat(view.detail(b.user(), b.id()).post().autoEndAt()).isNotNull();
        clock.set(clock.instant().plus(Duration.ofHours(13)));
        assertThat(ends.autoEnd(postId, clock.instant())).isTrue();
        assertThat(dealRow(postId).get("ended_by")).isEqualTo("AUTO");
        assertThat(events(b.id())).contains("DEAL_AUTO_ENDED");
        assertThat(app(b.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(refundRows(b.id())).isZero();
        assertThat(view.detail(b.user(), b.id()).post().endedBy()).isEqualTo("AUTO");
    }

    @Test
    void 기간이_끝나면_딜은_EXPIRED_노출은_ENDED() {
        Booked b = postedBooking("HOME_TOP");
        clock.set(b.period().end().plus(Duration.ofMinutes(1)));
        BizDealPostView v = view.detail(b.user(), b.id()).post();
        assertThat(v.view()).isEqualTo("EXPIRED");
        assertThat(v.placement().status()).isEqualTo("ENDED");
    }
}
