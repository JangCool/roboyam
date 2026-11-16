package com.community.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.board.service.BoardCatalog;
import com.community.board.service.PublicBoardService;
import com.community.commerce.CommerceApiTestBase;
import com.community.post.dto.response.PostFeedRowView;
import com.community.home.service.LatestFeedService;
import com.community.search.dto.response.SearchRowView;
import com.community.search.service.SearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 게시판 정책 「검색엔진 색인 · 통합검색 포함 · 홈·인기·최신 포함」(indexable · searchable · inHome)이 공개 쪽에 실제로 걸리는가 — 관리자 화면에만 있고 공개 쪽이 읽지 않던
 * 것(관리자 QA 2026-11-17 F-7). 실제 PostgreSQL.
 * <ul>
 *   <li>inHome 을 끄면 최신글 모음에서 빠진다(인기글·「전체 게시판」 가젯도 같은 판정 {@code EffectiveBoard#excludedFrom}).</li>
 *   <li>searchable 을 끄면 「전체 게시판」 통합검색에서 빠지고, 그 게시판만 고른 검색은 그대로다.</li>
 *   <li>indexable 을 끄면 공개 게시판 응답의 {@code indexable} 이 false — 화면이 robots noindex 를 단다.</li>
 * </ul>
 */
class BoardExposureFlagsTest extends CommerceApiTestBase {

    private static final String BODY = "<p>노출 칸 시험 본문이에요. 검색과 홈 모음에 나오는지 봅니다.</p>";

    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private LatestFeedService latest;
    @Autowired
    private SearchService search;
    @Autowired
    private PublicBoardService boards;

    private long board;
    private String slug;
    private long post;
    private String token;

    @BeforeEach
    void setUp() {
        board = ids.nextId();
        slug = "exp" + Long.toString(board, 36);
        boardFixture.board(board, slug, "노출 시험 " + Long.toString(board, 36), 963);
        token = "노출토큰" + Long.toString(board, 36);
        post = ids.nextId();
        boardFixture.post(post, board, newMember(), token + " 제목", BODY);
        catalog.invalidate();
    }

    private void flags(boolean indexable, boolean searchable, boolean inHome) {
        // SQL 로 남김: 게시판 정책 칸만 바꾸는 고정물이 없다(관리자 저장 경로는 admin 모듈)
        jdbc.update("UPDATE boards SET indexable = ?, searchable = ?, in_home = ? WHERE id = ?", indexable, searchable, inHome, board);
        catalog.invalidate();
    }

    private boolean inLatest() {
        return latest.latest("50", null, null).items().stream().map(PostFeedRowView::id).toList().contains(Long.toString(post));
    }

    private boolean inSearch(String boardSlug) {
        List<SearchRowView> rows = search.search(token, "all", boardSlug, null, null, null, "50", null).items();
        return rows.stream().anyMatch(r -> r.id().equals(Long.toString(post)));
    }

    @Test
    void 기본값이면_최신글_통합검색에_나오고_색인된다() {
        assertThat(inLatest()).isTrue();
        assertThat(inSearch(null)).isTrue();
        assertThat(boards.board(slug, null).indexable()).isTrue();
    }

    @Test
    void 홈_포함을_끄면_최신글_모음에서_빠진다() {
        flags(true, true, false);
        assertThat(inLatest()).isFalse();
        assertThat(inSearch(null)).as("검색은 그대로").isTrue();
    }

    @Test
    void 통합검색_포함을_끄면_전체_검색에서_빠지고_그_게시판만_고른_검색은_그대로다() {
        flags(true, false, true);
        assertThat(inSearch(null)).isFalse();
        assertThat(inSearch(slug)).isTrue();
        assertThat(inLatest()).as("홈 모음은 그대로").isTrue();
    }

    @Test
    void 색인을_끄면_공개_게시판_응답이_noindex_를_알린다() {
        flags(false, true, true);
        assertThat(boards.board(slug, null).indexable()).isFalse();
    }
}
