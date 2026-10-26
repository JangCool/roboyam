package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.board.service.BoardCatalog;
import com.community.comment.dto.request.CommentWriteRequest;
import com.community.comment.service.CommentService;
import com.community.commerce.CommerceApiTestBase;
import com.community.post.dto.request.PostWriteRequest;
import com.community.post.service.PostWriteService;
import com.community.testsupport.PointInvariants;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 글·댓글 쓰기가 게시판 정책을 적립에 넘기는가(커머스 1-3 확정 1·2) — 실제 {@link PostWriteService#create}·{@link CommentService#create} + 공개 사이트 앱 문맥 + PostgreSQL.
 * 게시판 유효 {@code pointMode} OFF 면 글·댓글 0, 「적립 최소 글자 수 적용」을 끄면 글 본문 최소 글자 수만 빠진다(댓글은 그대로), 게시판 칸이 비면 기본값(켬)을 따른다.
 * 조건 판정 자체(간격·한도 등)는 shared {@code PointEarnRulesTest}.
 */
class PointEarnWiringTest extends CommerceApiTestBase {

    private static final String LONG = "포인트 적립 조건을 넉넉히 넘기는 본문입니다. ".repeat(4);
    private static final String COMMENT_OK = "댓글 열 글자를 넘기는 내용이에요";

    @Autowired
    private PostWriteService posts;
    @Autowired
    private CommentService comments;
    @Autowired
    private BoardCatalog catalog;

    private String on;
    private String off;
    private String photo;

    @BeforeEach
    void boards() {
        on = board(null, null);
        off = board("OFF", null);
        photo = board(null, false);
    }

    /** 게시판을 만든다 — pointMode·pointMinLengthApplied 는 null 이면 기본값 따름 */
    private String board(String pointMode, Boolean minLengthApplied) {
        long id = ids.nextId();
        String slug = "pe" + Long.toString(id, 36);
        jdbc.update("INSERT INTO boards (id, slug, name, sort_order, point_mode, point_min_length_applied) VALUES (?, ?, ?, 900, ?, ?)", id, slug,
                "적립 " + slug.substring(slug.length() - 6), pointMode, minLengthApplied);
        catalog.invalidate();
        return slug;
    }

    /** 이메일 인증을 마친 정상 회원 + 프로필 */
    private long member() {
        long id = newMember();
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", id);
        jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?)", id, "w" + Long.toString(id, 36).substring(Math.max(0, Long.toString(id, 36).length() - 11)));
        return id;
    }

    private long write(long member, String slug, String content) {
        return Long.parseLong(posts.create(new PostWriteRequest(slug, null, "적립 시험 글", "TEXT", content, false, null, null, null), member, "127.0.0.1").id());
    }

    private long reply(long member, long postId, String content) {
        comments.create(Long.toString(postId), new CommentWriteRequest("TEXT", content, null, null, null, null, null), member, "127.0.0.1");
        return jdbc.queryForObject("SELECT max(id) FROM comments WHERE post_id = ? AND author_id = ?", Long.class, postId, member);
    }

    private List<Integer> earned(long member, String reason) {
        return jdbc.queryForList("SELECT delta FROM point_ledger WHERE user_id = ? AND reason = ?", Integer.class, member, reason);
    }

    @Test
    void 포인트_켠_게시판은_조건을_채운_글과_댓글에_준다() {
        long writer = member();
        long commenter = member();
        long postId = write(writer, on, LONG);
        reply(commenter, postId, COMMENT_OK);

        assertThat(earned(writer, "POST")).containsExactly(20);
        assertThat(earned(commenter, "COMMENT")).containsExactly(5);
        assertThat(PointInvariants.violations(jdbc, writer, commenter)).isEmpty();
    }

    @Test
    void 게시판_pointMode_OFF면_글도_댓글도_0이고_글은_그대로_올라간다() {
        long writer = member();
        long commenter = member();
        long postId = write(writer, off, LONG);
        long commentId = reply(commenter, postId, COMMENT_OK);

        assertThat(earned(writer, "POST")).isEmpty();
        assertThat(earned(commenter, "COMMENT")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posts WHERE id = ? AND deleted_at IS NULL", Integer.class, postId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comments WHERE post_id = ? AND id = ?", Integer.class, postId, commentId)).isEqualTo(1);
    }

    @Test
    void 스위치를_끈_게시판은_짧은_글에도_주지만_짧은_댓글에는_주지_않는다() {
        long writer = member();
        long commenter = member();
        long postId = write(writer, photo, "오늘 찍은 사진 한 장 올려요");
        reply(commenter, postId, "멋져요");

        assertThat(earned(writer, "POST")).containsExactly(20);
        assertThat(earned(commenter, "COMMENT")).as("댓글 최소 글자 수는 그대로").isEmpty();
    }

    @Test
    void 게시판_칸이_비면_기본값_켬을_따라_짧은_글은_0이다() {
        long writer = member();
        write(writer, on, "<b>짧은</b>        글      ");
        assertThat(earned(writer, "POST")).isEmpty();
    }

    @Test
    void 기본값의_스위치를_끄면_칸이_빈_게시판도_따른다() {
        jdbc.update("UPDATE board_defaults SET point_min_length_applied = false WHERE id = 1");
        catalog.invalidate();
        try {
            long writer = member();
            write(writer, on, "오늘 찍은 사진 한 장 올려요");
            assertThat(earned(writer, "POST")).containsExactly(20);
        } finally {
            jdbc.update("UPDATE board_defaults SET point_min_length_applied = true WHERE id = 1");
            catalog.invalidate();
        }
    }
}
