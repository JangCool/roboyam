package com.community.hotdeal.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.PublicBoardService;
import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.entity.DealView;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.repository.master.DealQueries;
import com.community.hotdeal.repository.master.PostDealRepository;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.PostQueryService;
import com.community.zconfiguration.crypto.BlindIndex;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매하기 {@code GET /api/deals/{postId}/go}(D5 — 메모 1-3) — 어디로 302 할지 정하고 클릭을 센다. 로그인 없이 부른다.
 *
 * <ul>
 *   <li>글 없음·지움·숨김·소모임 글·꺼진 게시판·핫딜 게시판 아님·딜 줄 없음 → {@code /posts/{postId}}(화면이 그 오류를 보인다).</li>
 *   <li>지금 링크 규칙상 차단 호스트(최종 호스트 포함) → {@code /posts/{postId}}.</li>
 *   <li>종료·기간 종료 → 저장된 상품 링크 그대로(변환·집계 없음).</li>
 *   <li>진행 중(ACTIVE·FLAGGED) → 클릭 시점의 {@code affiliateMode}: {@code MEMBER} 저장 링크 그대로 · {@code SITE} 추적 파라미터(개인 제휴)를 떼고, 켠 사이트 제휴 몰이면 그 몰의
 *       변환(TEMPLATE·QUERY), 아니면 뗀 주소. 저장된 상품 링크는 바꾸지 않는다. 집계는 한국 날짜 하루 같은 방문자·같은 글 1번, 봇·빈 User-Agent·키 없음은 세지 않는다.</li>
 * </ul>
 */
@Service
public class DealGoService {

    private static final Logger log = LoggerFactory.getLogger(DealGoService.class);
    static final DataPurpose VISITOR = DataPurpose.of("deal.visitor");
    /** 세지 않는 User-Agent — 공개 광고 클릭({@code PublicAdService})과 같은 목록 */
    private static final Pattern BOT = Pattern.compile("(?i)(bot|crawl|spider|slurp|scrap|facebookexternalhit|embedly|preview|headless|phantomjs|lighthouse|pingdom|uptime"
            + "|monitor|curl|wget|python|java/|go-http|okhttp|axios|node-fetch|httpclient|libwww|postman|insomnia)");
    private static final int UA_MAX = 512;

    /** 방문자 — 로그인 회원이면 {@code memberId}, 아니면 IP + User-Agent(원본은 저장하지 않는다 — HMAC 만) */
    public record Visitor(Long memberId, String ip, String userAgent) {
    }

    private final PostRepository posts;
    private final PostDealRepository deals;
    private final DealQueries queries;
    private final BoardCatalog catalog;
    private final DealRuleBook rules;
    private final DealSettingsReader settings;
    private final BlindIndex index;
    private final ObjectProvider<Clock> clocks;

    public DealGoService(PostRepository posts, PostDealRepository deals, DealQueries queries, BoardCatalog catalog, DealRuleBook rules, DealSettingsReader settings,
                         BlindIndex index, ObjectProvider<Clock> clocks) {
        this.posts = posts;
        this.deals = deals;
        this.queries = queries;
        this.catalog = catalog;
        this.rules = rules;
        this.settings = settings;
        this.index = index;
        this.clocks = clocks;
    }

    /** @return 302 로 보낼 곳(절대 상품 주소 또는 {@code /posts/{id}}) */
    @Transactional
    public String target(String postIdText, Visitor visitor) {
        Long id = PostQueryService.parseId(postIdText);
        if (id == null) {
            return "/";
        }
        String back = "/posts/" + id;
        Instant now = clocks.getIfUnique(Clock::systemUTC).instant();
        PostEntity post = posts.findById(id).orElse(null);
        if (post == null || post.isGoneAt(now) || post.isHiddenAt(now) || post.isGroupPost()) {
            return back;
        }
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = s.board(post.getBoardId()).filter(PublicBoardService::isPublic).orElse(null);
        if (b == null || !b.active() || !DealWriter.dealBoard(b)) {
            return back;
        }
        PostDealEntity deal = deals.findById(id).orElse(null);
        if (deal == null) {
            return back;
        }
        if (rules.judge(DealWriter.hostsOf(deal)).blocked()) {
            return back;
        }
        DealSettings current = settings.current();
        DealView view = deal.view(current.autoExpireDays(), now);
        if (!view.live()) {
            return deal.getProductUrl();
        }
        String url = current.siteMode() ? siteUrl(deal) : deal.getProductUrl();
        count(deal.getPostId(), visitor, now);
        return url;
    }

    /** 사이트 제휴 — 개인 제휴(추적) 파라미터를 떼고, 켠 사이트 제휴 몰이면 그 몰의 변환 */
    private String siteUrl(PostDealEntity deal) {
        String stripped = DealUrlKey.stripTracking(deal.getProductUrl());
        return rules.siteStore(deal.getProductHost(), deal.getFinalHost()).map(store -> SiteAffiliateLinker.transform(store, stripped)).orElse(stripped);
    }

    private void count(long postId, Visitor visitor, Instant now) {
        String hash = visitorHash(visitor, now);
        if (hash != null) {
            queries.recordClick(postId, ServiceTime.dateOf(now), hash, now);
        }
    }

    /** 세지 않을 방문자(키 없음 · 봇 · 빈 User-Agent)면 null */
    String visitorHash(Visitor visitor, Instant now) {
        if (visitor == null || !index.isConfigured()) {
            return null;
        }
        String ua = visitor.userAgent() == null ? "" : visitor.userAgent().strip();
        if (ua.isEmpty() || BOT.matcher(ua).find()) {
            return null;
        }
        if (ua.length() > UA_MAX) {
            ua = ua.substring(0, UA_MAX);
        }
        String who = visitor.memberId() != null ? "m:" + visitor.memberId() : "g:" + (visitor.ip() == null ? "" : visitor.ip()) + "\n" + ua;
        try {
            return index.hash(VISITOR, ServiceTime.dateOf(now) + "|" + who);
        } catch (RuntimeException e) {
            log.warn("핫딜 방문자 해시를 만들지 못해 세지 않는다: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
