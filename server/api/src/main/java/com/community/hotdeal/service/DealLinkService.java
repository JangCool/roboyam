package com.community.hotdeal.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.board.service.PublicBoardService;
import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.dto.response.DealImageView;
import com.community.hotdeal.dto.response.DealLinkCheckView;
import com.community.hotdeal.dto.response.DealOgView;
import com.community.hotdeal.dto.response.DealPrefillView;
import com.community.hotdeal.dto.response.DealSameRefView;
import com.community.hotdeal.dto.response.DealSiteAffiliateRefView;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.exception.DealNotEndedException;
import com.community.hotdeal.og.DealLinkInspector;
import com.community.hotdeal.repository.master.PostDealRepository;
import com.community.hotdeal.service.DealLinkJudge.ChainVerdict;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostHiddenException;
import com.community.post.exception.PostNotFoundException;
import com.community.post.service.PostQueryService;
import com.community.post.service.PostWriteService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 공개 핫딜 링크 API — 링크 판정({@code GET /api/deals/link-check}) · 끝난 딜에서 새 딜({@code GET /api/deals/prefill}) · 같은 딜({@code GET /api/posts/{id}/deal/same}).
 *
 * <ul>
 *   <li><b>link-check</b>(로그인): 핫딜 게시판 쓰기 자격(게시판 판정 + 핫딜 자격)이 있어야 한다 — 외부로 나가는 요청이라 쓸 수 있는 사람만. 회원 분당 10회.
 *       판정 = 리디렉션 체인의 모든 호스트(차단 &gt; 제휴), OG 는 우리 서버 사본, 같은 딜은 공개·진행 중만(최대 10). 트랜잭션 밖(외부 요청이 DB 연결을 붙잡지 않게).</li>
 *   <li><b>prefill</b>(로그인): 볼 수 있는 핫딜 글의 <b>끝난</b> 딜(종료·기간 종료)만 — 진행 중이면 {@code 409 DEAL_NOT_ENDED}. 링크·몰·제목 + OG 사본 이미지(올린 이미지는 그 글의 것이라 싣지 않는다).</li>
 * </ul>
 */
@Service
public class DealLinkService {

    public static final int LINK_CHECK_SAME_MAX = 10;

    private final BoardCatalog catalog;
    private final BoardActorResolver actors;
    private final DealLinkInspector inspector;
    private final DealRuleBook rules;
    private final DealSettingsReader settings;
    private final DealReader reader;
    private final DealLinkCheckLimiter limiter;
    private final PostQueryService posts;
    private final PostDealRepository deals;
    private final ObjectProvider<Clock> clocks;

    public DealLinkService(BoardCatalog catalog, BoardActorResolver actors, DealLinkInspector inspector, DealRuleBook rules, DealSettingsReader settings,
                           DealReader reader, DealLinkCheckLimiter limiter, PostQueryService posts, PostDealRepository deals, ObjectProvider<Clock> clocks) {
        this.catalog = catalog;
        this.actors = actors;
        this.inspector = inspector;
        this.rules = rules;
        this.settings = settings;
        this.reader = reader;
        this.limiter = limiter;
        this.posts = posts;
        this.deals = deals;
        this.clocks = clocks;
    }

    private Instant now() {
        return clocks.getIfUnique(Clock::systemUTC).instant();
    }

    public DealLinkCheckView linkCheck(String url, String boardSlug, Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        BoardCatalogSnapshot s = catalog.snapshot();
        if (boardSlug == null || boardSlug.isBlank()) {
            throw ValidationException.of("board", new MessageRef("hotdeal.board.notDeal"));
        }
        EffectiveBoard b = PublicBoardService.requireActive(s, boardSlug.strip());
        if (!DealWriter.dealBoard(b)) {
            throw ValidationException.of("board", new MessageRef("hotdeal.board.notDeal"));
        }
        BoardActor actor = actors.resolve(userId, s);
        PostWriteService.requireWritable(BoardAccessPolicy.writeBlocked(b, actor, s), b, s);
        DealWriter.requireWriter(b, actor, s);
        String link = url == null ? null : url.strip();
        if (link == null || link.isEmpty() || link.length() > DealHosts.URL_MAX || !DealHosts.isHttpUrl(link)) {
            throw ValidationException.of("url", new MessageRef("hotdeal.productUrl.invalid", DealHosts.URL_MAX));
        }
        Instant now = now();
        limiter.acquire(userId, now);
        DealLinkInspector.Inspection inspection = inspector.inspect(link, userId, true);
        ChainVerdict verdict = rules.judge(inspection.hosts().isEmpty() ? List.of(DealHosts.hostOf(link)) : inspection.hosts());
        String key = DealUrlKey.of(link, verdict.keyParams());
        List<DealSameRefView> same = reader.same(key, null, now, LINK_CHECK_SAME_MAX);
        DealSettings current = settings.current();
        DealSiteAffiliateRefView site = current.siteMode()
                ? rules.siteStore(verdict.host(), verdict.finalHost()).map(st -> new DealSiteAffiliateRefView(st.programName())).orElse(null) : null;
        DealLinkInspector.Og og = inspection.og();
        return new DealLinkCheckView(verdict.host(), verdict.finalHost(), verdict.rule(), verdict.common(), verdict.shortener(), verdict.storeName(),
                new DealOgView(og.status(), og.imageUrl(), og.title(), og.storeName()), same, current.affiliateMode(), site);
    }

    public DealPrefillView prefill(String fromId, Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        Instant now = now();
        PostEntity post = visibleDealPost(fromId, now);
        PostDealEntity deal = deals.findById(post.getId()).orElseThrow(PostNotFoundException::new);
        if (deal.view(settings.current().autoExpireDays(), now).live()) {
            throw new DealNotEndedException();
        }
        DealImageView image = PostDealEntity.IMAGE_OG.equals(deal.getImageSource()) ? new DealImageView(deal.getImageUrl(), PostDealEntity.IMAGE_OG, null, null) : null;
        return new DealPrefillView(Long.toString(post.getId()), post.getTitle(), deal.getStoreName(), deal.getProductUrl(), image);
    }

    public List<DealSameRefView> same(String postId) {
        Instant now = now();
        PostEntity post = visibleDealPost(postId, now);
        return deals.findById(post.getId()).map(d -> reader.same(d, now)).orElse(List.of());
    }

    /** 볼 수 있는 핫딜 글 — 없거나 지웠으면 404, 숨겼으면 403 POST_HIDDEN, 핫딜 게시판 글이 아니면 404 */
    private PostEntity visibleDealPost(String idText, Instant now) {
        PostEntity post = posts.requireVisible(idText, now);
        if (post.isGroupPost()) {
            throw new PostNotFoundException();
        }
        EffectiveBoard b = PublicBoardService.requireActive(catalog.snapshot(), post.getBoardId());
        if (!DealWriter.dealBoard(b)) {
            throw new PostNotFoundException();
        }
        if (post.isHiddenAt(now)) {
            throw new PostHiddenException();
        }
        return post;
    }
}
