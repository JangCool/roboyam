package com.community.board.service;

import com.community.adultboard.service.AdultBoardGate;
import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardSecurityValues;
import com.community.board.dto.EffectiveBoard;
import com.community.board.dto.response.BoardDetailView;
import com.community.board.dto.response.BoardGuestRulesView;
import com.community.board.dto.response.BoardListResponse;
import com.community.board.dto.response.BoardNewMemberRulesView;
import com.community.board.dto.response.BoardPolicyView;
import com.community.board.dto.response.BoardView;
import com.community.board.dto.response.BoardViewerView;
import com.community.board.dto.response.PrefixView;
import com.community.board.exception.BoardInactiveException;
import com.community.board.exception.BoardNotFoundException;
import com.community.boardstaff.dto.response.BoardPendingWarningView;
import com.community.boardstaff.dto.response.BoardStaffToolsView;
import com.community.boardstaff.dto.response.BoardWriteRestrictionView;
import com.community.sanction.service.BoardSanctionService;
import com.community.sanction.service.BoardStaffActionService;
import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.dto.response.DealRulesView;
import com.community.hotdeal.service.DealSettingsReader;
import com.community.post.dto.response.TagCountView;
import com.community.post.repository.master.PostTagQueries;
import java.util.List;
import java.util.function.Predicate;
import com.community.board.dto.response.BoardPointTierView;
import com.community.point.service.PointPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 게시판 정보 — 전체 목록({@code GET /api/boards}), 쓸 수 있는 게시판({@code /writable}), 게시판 하나 + 유효 정책 + 보는 사람 판정({@code /{slug}}).
 *
 * <ul>
 *   <li>게시판·정책은 {@link BoardCatalog} 의 메모리 사본(15초)에서 읽는다 — 관리자 저장이 공개 사이트에 닿기까지 최대 15초.</li>
 *   <li>목록에는 켜진 게시판만 나온다. 꺼진 게시판을 주소로 열면 {@code 403 BOARD_INACTIVE}, 없으면 {@code 404 BOARD_NOT_FOUND}.</li>
 *   <li>판정은 {@link BoardAccessPolicy} 한 곳 — 쓰기 API 도 같은 함수로 거절한다.</li>
 *   <li>성인 전용 게시판(V226 — adult-board-contract · adult-board-pin-contract)은 판정이 {@link AdultBoardGate} 한 곳이다 — 성인이 아니거나 「19금 게시판 접근」을
 *       켜지 않았으면 <b>없는 게시판</b>(목록·쓸 수 있는 게시판에서 빠지고, 게시판 하나·글 목록·태그 추천·글쓰기는 {@code 404 BOARD_NOT_FOUND}),
 *       켰지만 이 기기에서 핀번호를 확인하지 않았으면 목록에는 보이고 열면 {@code 403 ADULT_BOARD_PIN_REQUIRED}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PublicBoardService {

    private final BoardCatalog catalog;
    private final BoardActorResolver actors;
    private final DealSettingsReader dealSettings;
    private final BoardStaffActionService staffActions;
    private final BoardSanctionService boardSanctions;
    private final PostTagQueries tagQueries;
    private final AdultBoardGate adultBoards;
    private final PointPolicy pointPolicy;

    /** 글쓰기 태그 추천으로 한 번에 주는 최대 태그 수 */
    static final int TAG_SUGGEST_MAX = 300;

    public PublicBoardService(BoardCatalog catalog, BoardActorResolver actors, DealSettingsReader dealSettings, BoardStaffActionService staffActions,
                              BoardSanctionService boardSanctions, PostTagQueries tagQueries, AdultBoardGate adultBoards, PointPolicy pointPolicy) {
        this.pointPolicy = pointPolicy;
        this.adultBoards = adultBoards;
        this.catalog = catalog;
        this.tagQueries = tagQueries;
        this.staffActions = staffActions;
        this.boardSanctions = boardSanctions;
        this.actors = actors;
        this.dealSettings = dealSettings;
    }

    /** @param viewerId 보는 회원(비회원 null) — 성인이 아니거나 「19금 게시판 접근」을 켜지 않았으면 성인 전용 게시판을 뺀다(핀 확인은 보지 않는다) */
    public BoardListResponse boards(Long viewerId) {
        BoardCatalogSnapshot s = catalog.snapshot();
        Predicate<EffectiveBoard> visible = adultVisible(viewerId);
        return new BoardListResponse(s.boards().stream().filter(b -> b.active() && isPublic(b) && visible.test(b)).map(PublicBoardService::view).toList());
    }

    /**
     * 이 사람의 목록에 보이는 게시판인가(성인 전용 게시판만 본다 — 성인 + 「19금 게시판 접근」 켜짐, 핀 확인은 보지 않는다). 판정은 성인 전용 게시판을 처음 만날 때
     * 읽고 요청 하나 안에서는 한 번만 읽는다({@link AdultBoardGate}).
     */
    public Predicate<EffectiveBoard> adultVisible(Long viewerId) {
        return adultBoards.listedBoards(viewerId);
    }

    /**
     * 공개 게시판 API 가 다루는 게시판인가 — 소모임 게시판(종류 {@code GROUP})은 소모임 화면의 그릇이라 게시판 목록·상세·글 목록·글쓰기에서
     * <b>없는 게시판과 같다</b>(목록에서 빠지고 404 {@code BOARD_NOT_FOUND}, docs/features/group-contract.md). 소모임 서버 단계가 자기 경로로 연다.
     */
    public static boolean isPublic(EffectiveBoard b) {
        return com.community.post.service.PostOpenRules.isPublicBoard(b); // 규칙은 shared 한 곳(AI 회원 쓰기와 같은 코드)
    }

    /** 지금 사용자가 새 글을 쓸 수 있는 켜진 게시판(글쓰기 화면의 게시판 고르기) */
    public BoardListResponse writable(Long userId) {
        BoardCatalogSnapshot s = catalog.snapshot();
        BoardActor actor = actors.resolve(userId, s);
        Predicate<EffectiveBoard> visible = adultVisible(userId);
        return new BoardListResponse(s.boards().stream()
                .filter(b -> b.active() && isPublic(b) && visible.test(b) && BoardAccessPolicy.withWriteRestriction(BoardAccessPolicy.writeBlocked(b, actor, s), b, actor) == null)
                .map(PublicBoardService::view).toList());
    }

    public BoardDetailView board(String slug, Long userId) {
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = adultBoards.requireBoard(requireActive(s, slug), userId); // 성인 전용 게시판 — 숨김이면 404, 핀이 필요하면 403 ADULT_BOARD_PIN_REQUIRED
        BoardActor actor = actors.resolve(userId, s);
        // 게시판 제재(board-sanction-contract) — 쓰기 제한 중이면 다른 까닭이 없을 때 BOARD_WRITE_RESTRICTED.
        // 매니저·스텝 본인에게는 할 수 있는 일 + 오늘 남은 글·댓글·제재 한도(board-staff-action-contract — 네 처리 권한 중 하나라도 있을 때만)
        BoardStaffToolsView tools = actor.staffOf(b.id()) ? staffActions.tools(b.id(), actor.userId()).map(BoardStaffToolsView::of).orElse(null) : null;
        // 확인하지 않은 게시판 경고 — 경고를 준 게시판 머리에만 한 줄(색인 ix_board_sanctions_user)
        List<BoardPendingWarningView> warnings = actor.userId() == null ? List.of()
                : boardSanctions.pendingWarnings(actor.userId(), b.id()).stream().map(BoardPendingWarningView::of).toList();
        return detail(s, b, actor, BoardAccessPolicy.withWriteRestriction(BoardAccessPolicy.writeBlocked(b, actor, s), b, actor),
                BoardAccessPolicy.withWriteRestriction(BoardAccessPolicy.commentBlocked(b, actor, s), b, actor),
                BoardFields.KIND_DEAL.equals(b.kind()) ? dealSettings.current() : null, tools, BoardWriteRestrictionView.of(actor.writeRestriction(b.id())), warnings,
                BoardPointTierView.of(b, pointPolicy.current(), pointPolicy.tiers())); // 글쓰기 화면의 포인트 안내(point-board-tier-contract)
    }

    /**
     * 게시판 하나 + 유효 정책 + 보는 사람 판정 — 쓰기·댓글 판정은 부르는 쪽이 정한다(공개 게시판은 {@link BoardAccessPolicy}, 소모임 게시판은 소모임 판정 —
     * {@code GET /api/groups/{id}/board}, 소모임 2단계).
     */
    public static BoardDetailView detail(BoardCatalogSnapshot s, EffectiveBoard b, BoardActor actor, String writeBlocked, String commentBlocked) {
        return detail(s, b, actor, writeBlocked, commentBlocked, null, null, null, List.of(), null);
    }

    /**
     * @param deal            핫딜 게시판이면 핫딜 설정(공개 칸 {@code dealAffiliateMode}·{@code dealRules} 를 싣는다), 아니면 null
     * @param pendingWarnings 보는 내가 이 게시판에서 확인하지 않은 경고(없으면 빈 목록)
     */
    public static BoardDetailView detail(BoardCatalogSnapshot s, EffectiveBoard b, BoardActor actor, String writeBlocked, String commentBlocked, DealSettings deal,
                                         BoardStaffToolsView staffTools, BoardWriteRestrictionView writeRestriction, List<BoardPendingWarningView> pendingWarnings,
                                         BoardPointTierView pointTier) {
        BoardSecurityValues sec = s.security();
        BoardView v = view(b);
        return new BoardDetailView(v.id(), v.slug(), v.name(), v.nameEn(), v.description(), v.investmentNotice(), v.staffOnlyWrite(), v.writeMin(),
                v.commentMin(), v.writeLock(), v.imageUploadAllowed(), v.postEditor(), v.commentEditor(), v.layout(), v.dealEnabled(), v.promo(), v.viewToggle(),
                v.allowAdult(), v.prefixes(),
                v.color(), s.gradeLabel(b.writeMin()), s.gradeLabel(b.commentMin()), b.introText(), b.writeGuideText(), b.writeTemplate(),
                Boolean.TRUE.equals(s.report().reportEnabled()),
                new BoardPolicyView(b.listSize(), b.pinnedMax(), b.commentProtectMin(), b.commentProtectEdit(), b.commentProtectDelete(),
                        b.commentProtectDelayMinutes(), b.commentGuestDelayMinutes(), s.defaults().bestCommentMinLikes()),
                new BoardGuestRulesView(sec.guestCaptchaRequired(), sec.guestNameMin(), sec.guestNameMax(), sec.guestPasswordMin(),
                        ContentRules.GUEST_PASSWORD_MAX, sec.guestLinkLimit(), sec.guestImageUploadAllowed()),
                new BoardNewMemberRulesView(BoardAccessPolicy.newMemberRules(b, actor, s), sec.newMemberLinkLimit(), sec.newMemberImageLimit()),
                new BoardViewerView(actor.guest(), actor.staffOf(b.id()), writeBlocked == null, writeBlocked, commentBlocked == null, commentBlocked, staffTools,
                        writeRestriction, pendingWarnings),
                v.debateEnabled(), v.pollEnabled(), b.adsHidden(), b.kind(), deal == null ? null : deal.affiliateMode(),
                deal == null ? null : new DealRulesView(deal.priceMaxWon(), deal.discountWarnPercent(), deal.autoExpireDays(), deal.hotLikeThreshold()),
                v.secretMode(), v.statusEnabled(), v.chipPosition(), v.authorMode(), v.prefixStyle(), b.authorThumbnailEnabled(), b.tagsEnabled(),
                v.detailList(), v.detailFeedTabsPc(), v.detailFeedTabsMobile(), v.prefixRequired(), v.tagRequired(), v.adultOnly(), b.sourcesEnabled(), b.indexable(), pointTier, b.purchaseSourceMode(),
                b.trialReviewsEnabled(), b.postJjalAllowed(), b.commentJjalAllowed());
    }

    /**
     * 글쓰기 태그 추천 — 이 게시판에서 쓰인 태그(많이 쓴 순, 최대 {@value #TAG_SUGGEST_MAX}개 · 집계 표라 최대 10분 늦다 — post-tags-contract 「글쓰기 추천」).
     * 「태그 쓰기」가 꺼진 게시판이면 빈 목록. 없거나 소모임 게시판이면 404, 꺼졌으면 403.
     */
    public List<TagCountView> tags(String slug, Long viewerId) {
        EffectiveBoard b = adultBoards.requireBoard(requireActive(catalog.snapshot(), slug), viewerId); // 성인 전용 게시판 — 숨김 404 · 핀 필요 403
        return b.tagsEnabled() ? tagQueries.suggestions(b.id(), TAG_SUGGEST_MAX) : List.of();
    }

    /** 켜진 게시판 — 없거나 소모임 게시판이면 404, 꺼졌으면 403 */
    public static EffectiveBoard requireActive(BoardCatalogSnapshot s, String slug) {
        EffectiveBoard b = s.board(slug).filter(PublicBoardService::isPublic).orElseThrow(BoardNotFoundException::new);
        if (!b.active()) {
            throw new BoardInactiveException();
        }
        return b;
    }

    /** 이 게시판이 켜져 있는가(글·댓글 API) — 게시판 행이 없어졌거나 소모임 게시판이면 404, 꺼졌으면 403 */
    public static EffectiveBoard requireActive(BoardCatalogSnapshot s, long boardId) {
        EffectiveBoard b = s.board(boardId).filter(PublicBoardService::isPublic).orElseThrow(BoardNotFoundException::new);
        if (!b.active()) {
            throw new BoardInactiveException();
        }
        return b;
    }

    static BoardView view(EffectiveBoard b) {
        List<PrefixView> prefixes = b.activePrefixes().stream()
                .map(p -> new PrefixView(Long.toString(p.id()), p.name(), p.staffOnly(), p.common(), p.color(), p.adultOnly(), p.counsel()))
                .toList();
        return new BoardView(Long.toString(b.id()), b.slug(), b.name(), b.nameEn(), b.description(), b.investmentNotice(),
                BoardFields.STAFF.equals(b.writeMin()), b.writeMin(), b.commentMin(), b.writeLock(), b.imageUploadAllowed(), b.postEditor(), b.commentEditor(), b.layout(),
                BoardFields.KIND_DEAL.equals(b.kind()), BoardFields.KIND_PROMO.equals(b.kind()), b.viewToggle(), b.allowAdult(), prefixes, b.color(),
                b.debateEnabled(), b.pollEnabled(), b.kind(), b.secretMode(), b.statusEnabled(), b.chipPosition(), b.authorMode(), b.prefixStyle(),
                b.detailList(), b.detailFeedTabsPc(), b.detailFeedTabsMobile(), b.prefixRequired(), b.tagRequired(), b.adultOnly());
    }
}
