package com.community.site;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceApiTestBase;
import com.community.site.dto.PostLinkPreview;
import com.community.site.dto.PublicSiteSnapshot;
import com.community.site.dto.SiteLogoUrls;
import com.community.site.dto.response.SiteInfoResponse;
import com.community.site.service.PostLinkPreviewService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 글 공유 미리보기 칸({@link PostLinkPreviewService}) — 비회원이 읽을 수 있는 글만 제목·본문 앞부분을 내고, 비밀글·19금·숨김·지운 글·쉬는 게시판은 비어 있다
 * (사이트 미리보기 그대로 — 제목도 내지 않는다). 관리자 QA 2026-11-17. 실제 PostgreSQL.
 */
class PostLinkPreviewTest extends CommerceApiTestBase {

    @Autowired
    private PostLinkPreviewService previews;
    @Autowired
    private BoardCatalog catalog;

    private long board;
    private long author;

    @BeforeEach
    void setUp() {
        board = ids.nextId();
        boardFixture.board(board, "lp" + Long.toString(board, 36), "미리보기 시험 " + Long.toString(board, 36), 964);
        author = newMember();
        catalog.invalidate();
    }

    private static PublicSiteSnapshot snapshot() {
        SiteInfoResponse info = new SiteInfoResponse("아이로빗", null, null, null, "/og.png", null, null, null, null, null, List.of(), false, SiteLogoUrls.EMPTY, false);
        return new PublicSiteSnapshot(info, null, null, false, null, null, "e");
    }

    private long post(String title, String body) {
        long id = ids.nextId();
        boardFixture.post(id, board, author, title, body);
        return id;
    }

    private Optional<PostLinkPreview> of(long post) {
        return previews.forPath("/posts/" + post, snapshot());
    }

    @Test
    void 공개_글이면_제목과_본문_앞부분을_낸다() {
        long id = post("로봇 청소기 후기", "흡입력이\n\n아주   좋아요.");
        assertThat(of(id)).hasValueSatisfying(p -> {
            assertThat(p.title()).isEqualTo("로봇 청소기 후기");
            assertThat(p.description()).isEqualTo("흡입력이 아주 좋아요.");
            assertThat(p.imageUrl()).isNull();
        });
        assertThat(previews.forPath("/posts/" + id + "/", snapshot())).as("끝 / 도 같은 글").isPresent();
    }

    @Test
    void 긴_본문은_150자에서_자르고_줄임표를_붙인다() {
        long id = post("긴 글", "가".repeat(400));
        assertThat(of(id)).hasValueSatisfying(p -> assertThat(p.description()).isEqualTo("가".repeat(150) + "…"));
    }

    @Test
    void 비밀글_19금_숨긴_글_지운_글은_비어_있다() {
        long secret = ids.nextId();
        boardFixture.ops(board, "OPTIONAL", false);
        boardFixture.post(secret, board, author, "비밀 제목", "비밀 본문", true, false);
        long adult = post("19금 제목", "본문");
        jdbc.update("UPDATE posts SET adult = true WHERE id = ?", adult);
        long hidden = post("숨긴 제목", "본문");
        boardFixture.staffHide(hidden);
        long deleted = post("지운 제목", "본문");
        boardFixture.delete(deleted);
        catalog.invalidate();

        assertThat(of(secret)).isEmpty();
        assertThat(of(adult)).isEmpty();
        assertThat(of(hidden)).isEmpty();
        assertThat(of(deleted)).isEmpty();
    }

    @Test
    void 쉬는_게시판의_글과_없는_글_글이_아닌_주소는_비어_있다() {
        long id = post("쉬는 게시판 글", "본문");
        boardFixture.active(board, false);
        catalog.invalidate();
        assertThat(of(id)).isEmpty();
        assertThat(of(ids.nextId())).isEmpty();
        assertThat(previews.forPath("/boards/free", snapshot())).isEmpty();
        assertThat(previews.forPath("/posts/abc", snapshot())).isEmpty();
        assertThat(previews.forPath("/posts/1/edit", snapshot())).isEmpty();
    }
}
