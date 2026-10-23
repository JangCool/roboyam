package com.community.board.service;

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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 게시판 정보 — 전체 목록({@code GET /api/boards}), 쓸 수 있는 게시판({@code /writable}), 게시판 하나 + 유효 정책 + 보는 사람 판정({@code /{slug}}).
 *
 * <ul>
 *   <li>게시판·정책은 {@link BoardCatalog} 의 메모리 사본(15초)에서 읽는다 — 관리자 저장이 공개 사이트에 닿기까지 최대 15초.</li>
 *   <li>목록에는 켜진 게시판만 나온다. 꺼진 게시판을 주소로 열면 {@code 403 BOARD_INACTIVE}, 없으면 {@code 404 BOARD_NOT_FOUND}.</li>
 *   <li>판정은 {@link BoardAccessPolicy} 한 곳 — 쓰기 API 도 같은 함수로 거절한다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PublicBoardService {

    private final BoardCatalog catalog;
    private final BoardActorResolver actors;

    public PublicBoardService(BoardCatalog catalog, BoardActorResolver actors) {
        this.catalog = catalog;
        this.actors = actors;
    }

    public BoardListResponse boards() {
        BoardCatalogSnapshot s = catalog.snapshot();
        return new BoardListResponse(s.boards().stream().filter(EffectiveBoard::active).map(PublicBoardService::view).toList());
    }

    /** 지금 사용자가 새 글을 쓸 수 있는 켜진 게시판(글쓰기 화면의 게시판 고르기) */
    public BoardListResponse writable(Long userId) {
        BoardCatalogSnapshot s = catalog.snapshot();
        BoardActor actor = actors.resolve(userId, s);
        return new BoardListResponse(s.boards().stream()
                .filter(b -> b.active() && BoardAccessPolicy.writeBlocked(b, actor, s) == null)
                .map(PublicBoardService::view).toList());
    }

    public BoardDetailView board(String slug, Long userId) {
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = requireActive(s, slug);
        BoardActor actor = actors.resolve(userId, s);
        String writeBlocked = BoardAccessPolicy.writeBlocked(b, actor, s);
        String commentBlocked = BoardAccessPolicy.commentBlocked(b, actor, s);
        BoardSecurityValues sec = s.security();
        BoardView v = view(b);
        return new BoardDetailView(v.id(), v.slug(), v.name(), v.nameEn(), v.description(), v.investmentNotice(), v.staffOnlyWrite(), v.writeMin(),
                v.commentMin(), v.writeLock(), v.imageUploadAllowed(), v.postEditor(), v.commentEditor(), v.layout(), v.dealEnabled(), v.promo(), v.viewToggle(),
                v.allowAdult(), v.prefixes(),
                v.color(), s.gradeLabel(b.writeMin()), s.gradeLabel(b.commentMin()), b.introText(), b.writeGuideText(),
                Boolean.TRUE.equals(s.report().reportEnabled()),
                new BoardPolicyView(b.listSize(), b.pinnedMax(), b.commentProtectMin(), b.commentProtectEdit(), b.commentProtectDelete(),
                        b.commentProtectDelayMinutes(), b.commentGuestDelayMinutes(), s.defaults().bestCommentMinLikes()),
                new BoardGuestRulesView(sec.guestCaptchaRequired(), sec.guestNameMin(), sec.guestNameMax(), sec.guestPasswordMin(),
                        ContentRules.GUEST_PASSWORD_MAX, sec.guestLinkLimit(), sec.guestImageUploadAllowed()),
                new BoardNewMemberRulesView(BoardAccessPolicy.newMemberRules(b, actor, s), sec.newMemberLinkLimit(), sec.newMemberImageLimit()),
                new BoardViewerView(actor.guest(), actor.staffOf(b.id()), writeBlocked == null, writeBlocked, commentBlocked == null, commentBlocked));
    }

    /** 켜진 게시판 — 없으면 404, 꺼졌으면 403 */
    public static EffectiveBoard requireActive(BoardCatalogSnapshot s, String slug) {
        EffectiveBoard b = s.board(slug).orElseThrow(BoardNotFoundException::new);
        if (!b.active()) {
            throw new BoardInactiveException();
        }
        return b;
    }

    /** 이 게시판이 켜져 있는가(글·댓글 API) — 게시판 행이 없어졌으면 404, 꺼졌으면 403 */
    public static EffectiveBoard requireActive(BoardCatalogSnapshot s, long boardId) {
        EffectiveBoard b = s.board(boardId).orElseThrow(BoardNotFoundException::new);
        if (!b.active()) {
            throw new BoardInactiveException();
        }
        return b;
    }

    static BoardView view(EffectiveBoard b) {
        List<PrefixView> prefixes = b.activePrefixes().stream()
                .map(p -> new PrefixView(Long.toString(p.id()), p.name(), p.staffOnly(), p.common(), p.color()))
                .toList();
        return new BoardView(Long.toString(b.id()), b.slug(), b.name(), b.nameEn(), b.description(), b.investmentNotice(),
                BoardFields.STAFF.equals(b.writeMin()), b.writeMin(), b.commentMin(), b.writeLock(), b.imageUploadAllowed(), b.postEditor(), b.commentEditor(), b.layout(),
                BoardFields.KIND_DEAL.equals(b.kind()), BoardFields.KIND_PROMO.equals(b.kind()), b.viewToggle(), b.allowAdult(), prefixes, b.color());
    }
}
