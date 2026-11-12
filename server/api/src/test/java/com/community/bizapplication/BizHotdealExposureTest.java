package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.ad.dto.PublicAdView;
import com.community.ad.service.PublicAdService;
import com.community.bizapplication.dto.BizMemberView;
import com.community.bizapplication.dto.request.BizDealApplyRequest;
import com.community.bizapplication.dto.request.BizDealInfo;
import com.community.bizapplication.entity.BizProduct;
import com.community.hotdeal.dto.response.PostDealView;
import com.community.hotdeal.service.DealStateService;
import com.community.post.dto.request.PostUpdateRequest;
import com.community.post.service.PostQueryService;
import com.community.post.service.PostWriteService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사업자 핫딜 게시(biz-hotdeal-contract 규칙 5·7·8·9·11) — 노출 두 자리 판정 · 신청 검사(게시판·말머리·링크·재고) · 다시 열기 · 사업자 글 규칙(광고 표시·인기 제외·작성자 고치기·지우기·되돌리기 403).
 */
class BizHotdealExposureTest extends BizHotdealTestBase {

    @Autowired
    private PublicAdService publicAds;
    @Autowired
    private PostQueryService postQueries;
    @Autowired
    private PostWriteService postWrites;
    @Autowired
    private DealStateService states;

    private List<PublicAdView.Ad> ads(String types, String board, String prefix) {
        publicAds.invalidate();
        return publicAds.list(types, board, prefix).items();
    }

    // ── 규칙 7 노출 두 자리 ──────────────────────────────────────────────────

    @Test
    void 규칙7_상단_노출은_글이_진행_중_공개면_deal_을_글에서_채우고_다른_게시판에는_없다() {
        Booked b = postedBooking("HOME_TOP");
        long postId = postOf(b.id());
        String slug = boardSlug(dealBoard());
        List<PublicAdView.Ad> top = ads("HOTDEAL_TOP", slug, null).stream().filter(a -> a.id().equals(Long.toString(b.id()))).toList();
        assertThat(top).hasSize(1);
        PublicAdView.Ad ad = top.get(0);
        assertThat(ad.linkUrl()).isEqualTo("/posts/" + postId);
        assertThat(ad.deal().postId()).isEqualTo(Long.toString(postId));
        assertThat(ad.deal().dealPrice()).isEqualTo(15_900L);
        assertThat(ad.deal().listPrice()).isEqualTo(32_000L);
        assertThat(ad.deal().prefixName()).isEqualTo("로봇");
        assertThat(ad.advertiserName()).isEqualTo("시험상사");
        // 게시판 맥락이 아닌 호출(홈 등)도 낸다 · 다른 핫딜 게시판에는 없다
        assertThat(ads("HOTDEAL_TOP", null, null)).extracting(PublicAdView.Ad::id).contains(Long.toString(b.id()));
        String other = boardSlug(newDealBoard(false));
        assertThat(ads("HOTDEAL_TOP", other, null)).extracting(PublicAdView.Ad::id).doesNotContain(Long.toString(b.id()));
    }

    @Test
    void 규칙7_말머리_상단은_요청_말머리가_글_말머리일_때만() {
        Booked b = postedBooking("CATEGORY_TOP");
        String slug = boardSlug(dealBoard());
        String mine = Long.toString(dealPrefix());
        long otherPrefix = jdbc.queryForObject("SELECT id FROM board_prefixes WHERE board_id = ? AND id <> ?", Long.class, dealBoard(), dealPrefix());
        assertThat(ads("HOTDEAL_CATEGORY", slug, mine)).extracting(PublicAdView.Ad::id).contains(Long.toString(b.id()));
        assertThat(ads("HOTDEAL_CATEGORY", slug, Long.toString(otherPrefix))).extracting(PublicAdView.Ad::id).doesNotContain(Long.toString(b.id()));
        assertThat(ads("HOTDEAL_CATEGORY", slug, null)).extracting(PublicAdView.Ad::id).doesNotContain(Long.toString(b.id()));
        assertThat(view.detail(b.user(), b.id()).post().placement().status()).isEqualTo("SHOWING");
    }

    @Test
    void 말머리_없이_올라간_글의_말머리_상단은_어느_말머리에도_나가지_않는다() {
        Booked b = booked("CATEGORY_TOP");
        String mine = Long.toString(dealPrefix());
        jdbc.update("UPDATE board_prefixes SET active = false WHERE id = ?", dealPrefix());
        boardCatalog.invalidate();
        into(b, 1);
        publisher.publishDue(b.id());
        assertThat(postRow(postOf(b.id())).get("prefix_id")).isNull();
        String slug = boardSlug(dealBoard());
        long otherPrefix = jdbc.queryForObject("SELECT id FROM board_prefixes WHERE board_id = ? AND id <> ?", Long.class, dealBoard(), dealPrefix());
        assertThat(ads("HOTDEAL_CATEGORY", slug, mine)).extracting(PublicAdView.Ad::id).doesNotContain(Long.toString(b.id()));
        assertThat(ads("HOTDEAL_CATEGORY", slug, Long.toString(otherPrefix))).extracting(PublicAdView.Ad::id).doesNotContain(Long.toString(b.id()));
        assertThat(ads("HOTDEAL_CATEGORY", slug, null)).extracting(PublicAdView.Ad::id).doesNotContain(Long.toString(b.id()));
    }

    @Test
    void 규칙7_게시_실패_숨김_끝난_딜은_노출에_나가지_않는다() {
        // 실패
        Booked failed = booked("HOME_TOP");
        boardFixture.active(dealBoard(), false);
        boardCatalog.invalidate();
        into(failed, 1);
        publisher.publishDue(failed.id());
        assertThat(ads("HOTDEAL_TOP", null, null)).extracting(PublicAdView.Ad::id).doesNotContain(Long.toString(failed.id()));
        assertThat(view.detail(failed.user(), failed.id()).post().placement().status()).isEqualTo("WAITING");
        boardFixture.active(dealBoard(), true);
        boardCatalog.invalidate();

        // 숨김(운영자가 글을 숨김)
        Booked hidden = postedBooking("HOME_TOP");
        boardFixture.staffHide(postOf(hidden.id()));
        assertThat(ads("HOTDEAL_TOP", null, null)).extracting(PublicAdView.Ad::id).doesNotContain(Long.toString(hidden.id()));
        assertThat(view.detail(hidden.user(), hidden.id()).post().placement().stopReason()).isEqualTo("POST_HIDDEN");

        // 끝난 딜(사업자 종료)
        Booked ended = postedBooking("HOME_TOP");
        assertThat(ads("HOTDEAL_TOP", null, null)).extracting(PublicAdView.Ad::id).contains(Long.toString(ended.id()));
        view.endDeal(ended.user(), ended.id());
        assertThat(ads("HOTDEAL_TOP", null, null)).extracting(PublicAdView.Ad::id).doesNotContain(Long.toString(ended.id()));
    }

    @Test
    void 규칙7_의심_FLAGGED_는_진행_중이라_노출이_계속된다() {
        Booked b = postedBooking("HOME_TOP");
        // SQL 로 남김: 핫딜 칸을 바로 세운다 — 엔티티는 딜 칸 전체(DealValues)로만 만들고 바꾼다(다음 단계에서 픽스처로)
        jdbc.update("UPDATE post_deals SET flagged_at = now() WHERE post_id = ?", postOf(b.id()));
        assertThat(ads("HOTDEAL_TOP", null, null)).extracting(PublicAdView.Ad::id).contains(Long.toString(b.id()));
    }

    // ── 규칙 8 신청 검사 ────────────────────────────────────────────────────

    private java.util.Set<String> submitFields(long user, BizDealInfo d, String placement) {
        LocalDate start = today().plusDays(2);
        boolean none = "NONE".equals(placement);
        return fieldsOf(() -> categoryOpenIf(placement, () -> members.submitDeal(user, new BizDealApplyRequest("제목", d, none ? null : start.toString(),
                none ? null : start.plusDays(1).toString(), "ONLINE",
                POLICY, POLICY))));
    }

    private static BizDealInfo with(BizDealInfo d, String stock, String boardId, String prefixId) {
        return new BizDealInfo(d.linkUrl(), d.shopName(), d.listPrice(), d.dealPrice(), d.shippingFee(), d.endsOn(), stock, d.body(), d.placement(), boardId, prefixId,
                d.imageFileId(), null);
    }

    @Test
    void 규칙8_받는_게시판은_필수_꺼졌거나_핫딜이_아니면_400_deal_boardId() {
        long user = approvedBusiness();
        BizDealInfo d = deal("NONE");
        assertThat(submitFields(user, with(d, "IN_STOCK", null, null), "NONE")).contains("deal.boardId");
        long closed = newDealBoard(false);
        boardFixture.active(closed, false);
        boardCatalog.invalidate();
        assertThat(submitFields(user, with(d, "IN_STOCK", Long.toString(closed), null), "NONE")).contains("deal.boardId");
        long general = ids.nextId();
        boardFixture.board(general, "gz" + Long.toString(general, 36), "일반", 951);
        boardCatalog.invalidate();
        assertThat(submitFields(user, with(d, "IN_STOCK", Long.toString(general), null), "NONE")).contains("deal.boardId");
    }

    @Test
    void 규칙8_말머리는_선택_말머리_있는_게시판도_안_고르면_받는다_말머리_상단만_말머리_필수() {
        long user = approvedBusiness();
        BizDealInfo d = deal("NONE");
        // 사용자 결정 2026-11-02 — 회원 글쓰기와 같게, 게시판에 말머리가 있어도 안 골라도 된다
        assertThat(members.submitDeal(user, new BizDealApplyRequest("제목", with(d, "IN_STOCK", Long.toString(dealBoard()), null), null, null, "ONLINE", POLICY, POLICY)))
                .isPositive();
        LocalDate start = today().plusDays(2);
        assertThat(members.submitDeal(user, new BizDealApplyRequest("제목", with(deal("HOME_TOP"), "IN_STOCK", Long.toString(dealBoard()), null), start.toString(),
                start.plusDays(1).toString(), "ONLINE", POLICY, POLICY))).isPositive();
        long bare = newDealBoard(false);
        assertThat(submitFields(user, with(d, "IN_STOCK", Long.toString(bare), Long.toString(dealPrefix())), "NONE")).contains("deal.prefixId"); // 남의 게시판 말머리
        // 말머리 상단은 시험 안에서 다시 열면 말머리 필수(말머리 있는 게시판·없는 게시판 둘 다)
        BizDealInfo category = deal("CATEGORY_TOP");
        assertThat(submitFields(user, with(category, "IN_STOCK", Long.toString(dealBoard()), null), "CATEGORY_TOP")).contains("deal.prefixId");
        assertThat(submitFields(user, with(category, "IN_STOCK", Long.toString(bare), null), "CATEGORY_TOP")).contains("deal.prefixId");
        // 말머리 없는 게시판 · 노출 없음 → 받는다
        assertThat(members.submitDeal(user, new BizDealApplyRequest("제목", with(d, "UNKNOWN", Long.toString(bare), null), null, null, "ONLINE", POLICY, POLICY)))
                .isPositive();
    }

    @Test
    void 규칙8_링크는_회원_핫딜_링크_규칙으로_차단이면_400_deal_linkUrl_검사_API_도_같은_판정() {
        long user = approvedBusiness();
        String host = uniqueHost();
        block(host);
        assertThat(submitFields(user, dealAt(host, "NONE"), "NONE")).contains("deal.linkUrl");
        BizMemberView.DealLinkCheck check = view.checkLink("https://" + host + "/p/1");
        assertThat(check.blocked()).isTrue();
        assertThat(check.blockedReason()).isNotBlank();
        assertThat(view.checkLink("https://bit.ly/abc").blocked()).isTrue(); // 단축 주소(링크 규칙 시드)
        BizMemberView.DealLinkCheck ok = view.checkLink("https://smartstore.naver.com/irobit/products/1");
        assertThat(ok.blocked()).isFalse();
        assertThat(ok.shopName()).isEqualTo("네이버 스마트스토어");
    }

    @Test
    void 규칙8_재고는_IN_STOCK_LOW_UNKNOWN_만_옛_PLENTY_LIMITED_는_400_deal_stock() {
        long user = approvedBusiness();
        BizDealInfo d = deal("NONE");
        assertThat(submitFields(user, with(d, "LIMITED", d.boardId(), d.prefixId()), "NONE")).contains("deal.stock");
        assertThat(submitFields(user, with(d, "PLENTY", d.boardId(), d.prefixId()), "NONE")).contains("deal.stock");
        assertThat(submitFields(user, with(d, "SOLD_OUT", d.boardId(), d.prefixId()), "NONE")).contains("deal.stock");
        for (String stock : List.of("IN_STOCK", "LOW", "UNKNOWN")) {
            assertThat(members.submitDeal(user, new BizDealApplyRequest("제목", with(d, stock, d.boardId(), d.prefixId()), null, null, "ONLINE", POLICY, POLICY)))
                    .as(stock).isPositive();
        }
    }

    @Test
    void 규칙8_소재_바꾸기도_같은_검사() {
        long user = approvedBusiness();
        long id = submitDeal(user, "NONE", null, null, "ONLINE");
        BizDealInfo d = deal("NONE");
        assertThat(fieldsOf(() -> members.changeCreative(user, id, new com.community.bizapplication.dto.request.BizCreativeRequest(null, null, null, null, "새 제목",
                with(d, "LIMITED", null, null))))).contains("deal.stock", "deal.boardId");
    }

    // ── 규칙 9 다시 열기 · 말머리 상단 닫힘(V124) ───────────────────────────

    @Test
    void 규칙9_V124_뒤_말머리_상단만_닫힘_신청은_409_게시_글과_게시판_상단은_받는다() {
        assertThat(priceRules.find(BizProduct.HOTDEAL_POST).orElseThrow().accepting()).isTrue();
        assertThat(priceRules.find(BizProduct.HOTDEAL_TOP).orElseThrow().accepting()).isTrue();
        assertThat(priceRules.find(BizProduct.HOTDEAL_CATEGORY).orElseThrow().accepting()).isFalse();
        long user = approvedBusiness();
        LocalDate start = today().plusDays(2);
        assertThat(submitDeal(user, "HOME_TOP", start, start.plusDays(1), "ONLINE")).isPositive();
        assertThat(submitDeal(user, "NONE", null, null, "ONLINE")).isPositive();
        assertThat(codeOf(() -> members.submitDeal(user, new BizDealApplyRequest("[시험상사] 텀블러 1+1", deal("CATEGORY_TOP"), start.toString(),
                start.plusDays(1).toString(), "ONLINE", POLICY, POLICY)))).isEqualTo("BIZ_PRODUCT_CLOSED");
        // 신청 화면 — 글 게시는 받고, 노출 자리는 말머리 상단만 「지금은 받지 않아요」(placements[].accepting)
        BizMemberView.DealProduct product = view.dealProduct();
        assertThat(product.accepting()).isTrue();
        assertThat(product.placements()).extracting(BizMemberView.DealPlacement::placement, BizMemberView.DealPlacement::accepting)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("HOME_TOP", true), org.assertj.core.groups.Tuple.tuple("CATEGORY_TOP", false));
        BizMemberView.DealBoard board = product.boards().stream().filter(x -> x.id().equals(Long.toString(dealBoard()))).findFirst().orElseThrow();
        assertThat(board.prefixes()).extracting(BizMemberView.Prefix::name).containsExactly("로봇", "AI");
        // 시험 안에서 다시 열면 말머리 상단도 받는다(코드는 남아 있음)
        assertThat(submitDeal(user, "CATEGORY_TOP", start, start.plusDays(1), "ONLINE")).isPositive();
        assertThat(priceRules.find(BizProduct.HOTDEAL_CATEGORY).orElseThrow().accepting()).isFalse();
        // 운영자가 요금표에서 글 게시를 끄면 409 BIZ_PRODUCT_CLOSED(규칙 그대로)
        tx.executeWithoutResult(s -> priceRules.setAccepting(BizProduct.HOTDEAL_POST, false));
        try {
            assertThat(codeOf(() -> submitDeal(user, "NONE", null, null, "ONLINE"))).isEqualTo("BIZ_PRODUCT_CLOSED");
        } finally {
            tx.executeWithoutResult(s -> priceRules.setAccepting(BizProduct.HOTDEAL_POST, true));
        }
    }

    // ── 규칙 5·11 사업자 글 ─────────────────────────────────────────────────

    @Test
    void 규칙5_공개_글의_딜은_source_BUSINESS_상호_인기_표시_없음_되돌리기_기한_없음() {
        Booked b = postedBooking("NONE");
        long postId = postOf(b.id());
        // SQL 로 남김: 글 카운터 칸 — 엔티티 메서드가 없다(운영은 PostCounterQueries)
        jdbc.update("UPDATE posts SET like_count = 999 WHERE id = ?", postId);
        PostDealView d = postQueries.detail(Long.toString(postId), null, "127.0.0.1").deal();
        assertThat(d.source()).isEqualTo("BUSINESS");
        assertThat(d.business().name()).isEqualTo("시험상사");
        assertThat(d.hot()).isFalse();
        assertThat(d.endsAt()).isEqualTo(b.period().end());
        states.end(Long.toString(postId), b.user());
        assertThat(postQueries.detail(Long.toString(postId), null, "127.0.0.1").deal().reopenUntil()).isNull();
    }

    @Test
    void 규칙11_사업자_글은_작성자도_고치기_지우기_되돌리기가_403_DEAL_BUSINESS_POST_LOCKED() {
        Booked b = postedBooking("NONE");
        String postId = Long.toString(postOf(b.id()));
        assertThat(codeOf(() -> postWrites.update(postId, new PostUpdateRequest(null, "고친 제목", "TEXT", "고친 본문입니다", false, null, null), b.user(), "127.0.0.1")))
                .isEqualTo("DEAL_BUSINESS_POST_LOCKED");
        assertThat(codeOf(() -> postWrites.delete(postId, null, b.user(), "127.0.0.1"))).isEqualTo("DEAL_BUSINESS_POST_LOCKED");
        states.end(postId, b.user());
        assertThat(codeOf(() -> states.reopen(postId, b.user()))).isEqualTo("DEAL_BUSINESS_POST_LOCKED");
        assertThat(postRow(Long.parseLong(postId)).get("deleted_at")).isNull();
        assertThat(postRow(Long.parseLong(postId)).get("title")).isEqualTo("[시험상사] 텀블러 1+1");
    }
}
