package com.community.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.analytics.settings.StatsCollectionSettings;
import com.community.board.dto.response.BoardView;
import com.community.board.repository.master.BoardDefaultsRepository;
import com.community.board.service.BoardCatalog;
import com.community.board.service.PublicBoardService;
import com.community.commerce.CommerceApiTestBase;
import com.community.site.dto.response.BoardDisplayView;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.site.repository.master.SiteThemeMenuRepository;
import com.community.site.service.PublicSiteService;
import com.community.site.service.SiteAssets;
import com.community.site.service.SiteOperatorReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 글 목록 말머리·상태 칩 위치(V169 — post-chip-position-contract)의 공개 쪽 — 게시판 목록·게시판 하나의 {@code chipPosition} 은 게시판 값 ?? 사이트 기본을 푼 실제 값,
 * 사이트 기본은 {@code /api/site/info} 의 {@code boardDisplay.listChipPosition}. 값은 SQL 로 넣고(관리자 저장은 관리자 앱 시험), 사본은 비우거나 새로 만들어 읽는다.
 */
class BoardChipPositionPublicTest extends CommerceApiTestBase {

    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private PublicBoardService boards;
    @Autowired
    private SiteSettingRepository siteSettings;
    @Autowired
    private SiteAssets assets;
    @Autowired
    private BoardDefaultsRepository boardDefaults;
    @Autowired
    private SiteThemeMenuRepository themeMenus;
    @Autowired
    private StatsCollectionSettings statsSettings;
    @Autowired
    private SiteOperatorReader operators;

    private long board;
    private String slug;

    @BeforeEach
    void setUp() {
        board = ids.nextId();
        slug = "cp" + Long.toString(board, 36);
        boardFixture.board(board, slug, "칩 위치 " + Long.toString(board, 36), 963);
        catalog.invalidate();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("UPDATE board_defaults SET list_chip_position = 'ABOVE', list_like_highlight = 10, list_view_highlight = 1000, list_highlight_color = '#e5484d' WHERE id = 1");
        boardFixture.deleteBoard(board);
        catalog.invalidate();
    }

    private BoardView listed() {
        return boards.boards().boards().stream().filter(b -> b.slug().equals(slug)).findFirst().orElseThrow();
    }

    /** 사이트 설정 사본은 20초 캐시라 시험마다 새로 만든다 */
    private BoardDisplayView siteBoardDisplay() {
        return new PublicSiteService(siteSettings, assets, boardDefaults, themeMenus, statsSettings, operators, new org.springframework.cache.support.NoOpCacheManager()).snapshot().info().boardDisplay();
    }

    private String siteListChipPosition() {
        return siteBoardDisplay().listChipPosition();
    }

    @Test
    void 게시판이_고르지_않으면_사이트_기본을_푼_값이_나간다() {
        assertThat(listed().chipPosition()).isEqualTo("ABOVE");
        assertThat(boards.board(slug, null).chipPosition()).isEqualTo("ABOVE");
        assertThat(siteListChipPosition()).isEqualTo("ABOVE");

        jdbc.update("UPDATE board_defaults SET list_chip_position = 'BELOW' WHERE id = 1");
        catalog.invalidate();
        assertThat(listed().chipPosition()).isEqualTo("BELOW");
        assertThat(boards.board(slug, null).chipPosition()).isEqualTo("BELOW");
        assertThat(siteListChipPosition()).isEqualTo("BELOW");
    }

    @Test
    void 숫자_강조_기준과_색이_사이트_정보에_나간다() {
        BoardDisplayView initial = siteBoardDisplay();
        assertThat(initial.likeHighlight()).isEqualTo(10);
        assertThat(initial.viewHighlight()).isEqualTo(1000);
        assertThat(initial.highlightColor()).isEqualTo("#e5484d");

        jdbc.update("UPDATE board_defaults SET list_like_highlight = 0, list_view_highlight = 5000, list_highlight_color = '#1a2b3c' WHERE id = 1");
        BoardDisplayView changed = siteBoardDisplay();
        assertThat(changed.likeHighlight()).as("0 = 강조 안 함").isZero();
        assertThat(changed.viewHighlight()).isEqualTo(5000);
        assertThat(changed.highlightColor()).isEqualTo("#1a2b3c");
    }

    @Test
    void 게시판_값이_있으면_사이트_기본보다_앞선다() {
        jdbc.update("UPDATE board_defaults SET list_chip_position = 'BELOW' WHERE id = 1");
        jdbc.update("UPDATE boards SET chip_position = 'BEFORE' WHERE id = ?", board);
        catalog.invalidate();
        assertThat(listed().chipPosition()).isEqualTo("BEFORE");
        assertThat(boards.board(slug, null).chipPosition()).isEqualTo("BEFORE");
        assertThat(siteListChipPosition()).as("섞인 목록은 사이트 기본").isEqualTo("BELOW");
    }
}
