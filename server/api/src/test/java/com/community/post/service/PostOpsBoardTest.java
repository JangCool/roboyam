package com.community.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.community.board.service.BoardCatalog;
import com.community.comment.service.CommentService;
import com.community.commerce.CommerceApiTestBase;
import com.community.home.service.LatestFeedService;
import com.community.post.dto.request.PostProcessStatusRequest;
import com.community.post.dto.request.PostUpdateRequest;
import com.community.post.dto.request.PostWriteRequest;
import com.community.post.dto.response.PostDetailView;
import com.community.post.dto.response.PostFeedRowView;
import com.community.post.dto.response.PostListResponse;
import com.community.post.dto.response.PostProcessStatusView;
import com.community.post.dto.response.PostRowView;
import com.community.profile.service.UserProfileService;
import com.community.sanction.exception.BoardSanctionException;
import com.community.zconfiguration.exception.CommonException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 운영 게시판 설정(V166 — 비밀글 {@code secretMode}·처리 상태 {@code statusEnabled})의 공개 사이트 규칙을 실제 PostgreSQL 에서 확인한다.
 * 게시판·글은 픽스처로, 쓰기·고치기·상태 바꾸기는 서비스로 부른다(같은 작성자의 연속 쓰기는 작성 간격에 걸리므로 글마다 새 회원).
 */
class PostOpsBoardTest extends CommerceApiTestBase {

    private static final String IP = "127.0.0.1";
    private static final String BODY = "운영 게시판 시험 본문입니다. 열 글자를 넘깁니다.";

    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private PostQueryService queries;
    @Autowired
    private PostWriteService writes;
    @Autowired
    private PostProcessStatusService statuses;
    @Autowired
    private CommentService comments;
    @Autowired
    private LatestFeedService latest;
    @Autowired
    private UserProfileService profiles;

    private long board;
    private String slug;

    @BeforeEach
    void setUp() {
        board = ids.nextId();
        slug = "ops" + Long.toString(board, 36);
        boardFixture.board(board, slug, "운영 시험 " + Long.toString(board, 36), 962);
        ops("OPTIONAL", true);
    }

    // ── 재료 ───────────────────────────────────────────────────────────────

    private void ops(String secretMode, boolean statusEnabled) {
        boardFixture.ops(board, secretMode, statusEnabled);
        catalog.invalidate();
    }

    /** 이메일 인증을 마친 일반 등급 회원(프로필 포함) */
    private long member() {
        long id = newMember();
        memberFixture.verifyEmail(id);
        String name = Long.toString(id, 36);
        memberFixture.profile(id, "s" + name.substring(Math.max(0, name.length() - 11)), "REGULAR");
        return id;
    }

    /** 이 게시판의 스텝 */
    private long staff() {
        long id = member();
        // SQL 로 남김: board_staff 팩터리(byOperator)는 지정한 운영자를 요구한다 — 시험은 지정자 없는 행
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'STAFF')", board, id);
        return id;
    }

    /** 픽스처 글 — 비밀글 여부, 처리 상태는 지금 게시판 설정대로(RECEIVED) */
    private long post(long authorId, boolean secret) {
        long id = ids.nextId();
        boardFixture.post(id, board, authorId, "운영 글 " + (id % 100_000), BODY, secret, true);
        return id;
    }

    /** 서비스로 쓴 글 */
    private long write(long authorId, Boolean secret) {
        return Long.parseLong(writes.create(new PostWriteRequest(slug, null, "쓰기 시험 " + authorId % 100_000, "TEXT", BODY + " " + authorId, false, null, null, null,
                null, secret), authorId, IP).id());
    }

    private boolean secretOf(long postId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT secret FROM posts WHERE id = ?", Boolean.class, postId));
    }

    private CommonException error(Runnable call) {
        Throwable thrown = catchThrowableOfType(RuntimeException.class, call::run);
        assertThat(thrown).isInstanceOf(CommonException.class);
        return (CommonException) thrown;
    }

    private PostRowView row(PostListResponse list, long postId) {
        return list.items().stream().filter(r -> r.id().equals(Long.toString(postId))).findFirst().orElseThrow();
    }

    private int notices(long userId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = ?", Integer.class, userId, type);
    }

    // ── 비밀글 ─────────────────────────────────────────────────────────────

    @Nested
    class Secret {

        @Test
        void 글쓰기_OPTIONAL_은_요청대로_ALWAYS_는_늘_OFF_는_무시한다() {
            assertThat(secretOf(write(member(), true))).isTrue();
            assertThat(secretOf(write(member(), null))).isFalse();
            ops("ALWAYS", false);
            assertThat(secretOf(write(member(), false))).isTrue();
            ops("OFF", false);
            assertThat(secretOf(write(member(), true))).isFalse();
        }

        @Test
        void 고치기_OPTIONAL_은_글쓴이가_바꾸고_ALWAYS_는_비밀_OFF_는_그대로다() {
            long author = member();
            long id = write(author, true);
            writes.update(Long.toString(id), new PostUpdateRequest(null, "고친 제목", "TEXT", BODY, false, null, null, null, null), author, IP);
            assertThat(secretOf(id)).as("칸이 없으면 그대로").isTrue();
            writes.update(Long.toString(id), new PostUpdateRequest(null, "고친 제목", "TEXT", BODY, false, null, null, null, false), author, IP);
            assertThat(secretOf(id)).isFalse();
            ops("ALWAYS", true);
            writes.update(Long.toString(id), new PostUpdateRequest(null, "고친 제목", "TEXT", BODY, false, null, null, null, false), author, IP);
            assertThat(secretOf(id)).isTrue();
            ops("OFF", true);
            writes.update(Long.toString(id), new PostUpdateRequest(null, "고친 제목", "TEXT", BODY, false, null, null, null, false), author, IP);
            assertThat(secretOf(id)).as("게시판을 꺼도 쓴 비밀글은 그대로").isTrue();
        }

        @Test
        void 상세는_글쓴이와_운영진만_보고_그_밖은_403_SECRET_POST() {
            long author = member();
            long id = post(author, true);
            CommonException stranger = error(() -> queries.detail(Long.toString(id), member(), IP));
            assertThat(stranger.getErrorCode()).isEqualTo("SECRET_POST");
            assertThat(stranger.getExtras()).containsEntry("boardSlug", slug);
            assertThat(error(() -> queries.detail(Long.toString(id), null, IP)).getErrorCode()).isEqualTo("SECRET_POST");

            PostDetailView mine = queries.detail(Long.toString(id), author, IP);
            assertThat(mine.secret()).isTrue();
            assertThat(queries.detail(Long.toString(id), staff(), IP).content()).isEqualTo(BODY);
        }

        @Test
        void 운영자_회원_계정은_모든_일반_게시판의_비밀글을_읽고_상태를_바꾼다() {
            long author = member();
            long id = post(author, true);
            long account = member();
            // SQL 로 남김: 연결 규칙은 관리자 쪽 시험(OperatorMemberAccountAdminTest) — 여기서는 연결된 상태만 세운다
            jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", newOperator(), account);
            assertThat(queries.detail(Long.toString(id), account, IP).secret()).isTrue();
            assertThat(statuses.change(Long.toString(id), new PostProcessStatusRequest("CHECKING"), account).processStatus()).isEqualTo("CHECKING");
        }

        @Test
        void 댓글_목록_쓰기_추천도_막는다() {
            long id = post(member(), true);
            long stranger = member();
            assertThat(error(() -> comments.list(Long.toString(id), null, null, stranger, IP)).getErrorCode()).isEqualTo("SECRET_POST");
            assertThat(error(() -> writes.like(Long.toString(id), stranger)).getErrorCode()).isEqualTo("SECRET_POST");
        }

        @Test
        void 게시판_목록은_줄과_제목을_남기고_읽을_수_없는_사람에게_내용_칸을_비운다() {
            long author = member();
            long id = post(author, true);
            PostRowView seen = row(queries.list(slug, null, "LATEST", null, 1, member()), id);
            assertThat(seen.secret()).isTrue();
            assertThat(seen.title()).startsWith("운영 글");
            assertThat(seen.thumbnailUrl()).isNull();
            assertThat(seen.hasPoll()).isFalse();
            assertThat(row(queries.list(slug, null, "LATEST", null, 1, author), id).secret()).isTrue();
        }

        @Test
        void 섞인_목록에서_빠지고_프로필은_본인에게만_보인다() {
            long author = member();
            long open = post(author, false);
            long secret = post(author, true);
            List<String> feed = latest.latest("50", null, null).items().stream().map(PostFeedRowView::id).toList();
            assertThat(feed).contains(Long.toString(open)).doesNotContain(Long.toString(secret));

            List<PostFeedRowView> own = profiles.posts(Long.toString(author), "20", null, author).items();
            assertThat(own).extracting(PostFeedRowView::id).contains(Long.toString(open), Long.toString(secret));
            assertThat(own.stream().filter(r -> r.id().equals(Long.toString(secret))).findFirst().orElseThrow().secret()).isTrue();
            assertThat(profiles.posts(Long.toString(author), "20", null, member()).items()).extracting(PostFeedRowView::id)
                    .contains(Long.toString(open)).doesNotContain(Long.toString(secret));
        }

        @Test
        void 이웃_글은_읽을_수_없는_비밀글을_건너뛴다() {
            long author = member();
            long older = post(author, false);
            post(member(), true);
            long newest = post(author, false);
            PostDetailView view = queries.detail(Long.toString(newest), member(), IP);
            assertThat(view.prev().id()).isEqualTo(Long.toString(older));
        }
    }

    // ── 처리 상태 ──────────────────────────────────────────────────────────

    @Nested
    class Status {

        @Test
        void 상태를_쓰는_게시판의_새_글은_RECEIVED_이고_끄면_null_이다() {
            long author = member();
            long id = write(author, null);
            assertThat(jdbc.queryForObject("SELECT process_status FROM posts WHERE id = ?", String.class, id)).isEqualTo("RECEIVED");
            assertThat(queries.detail(Long.toString(id), author, IP).processStatus()).isEqualTo("RECEIVED");
            ops("OFF", false);
            assertThat(queries.detail(Long.toString(id), author, IP).processStatus()).isNull();
        }

        @Test
        void 스텝이_바꾸면_글쓴이에게_알리고_본인이_바꾸면_알리지_않는다() {
            long author = member();
            long id = post(author, false);
            PostProcessStatusView done = statuses.change(Long.toString(id), new PostProcessStatusRequest("done"), staff());
            assertThat(done.processStatus()).isEqualTo("DONE");
            assertThat(done.processStatusAt()).isNotNull();
            assertThat(notices(author, "POST_STATUS_CHANGED")).isEqualTo(1);
            PostDetailView view = queries.detail(Long.toString(id), author, IP);
            assertThat(view.processStatus()).isEqualTo("DONE");
            assertThat(view.processStatusAt()).isNotNull();

            // 같은 값이면 아무것도 남기지 않는다
            statuses.change(Long.toString(id), new PostProcessStatusRequest("DONE"), staff());
            assertThat(notices(author, "POST_STATUS_CHANGED")).isEqualTo(1);

            long selfStaff = staff();
            long own = post(selfStaff, false);
            statuses.change(Long.toString(own), new PostProcessStatusRequest("IN_PROGRESS"), selfStaff);
            assertThat(notices(selfStaff, "POST_STATUS_CHANGED")).isZero();
        }

        @Test
        void 운영진이_아니면_403_비로그인_401_꺼진_게시판_409_모르는_값_400() {
            long id = post(member(), false);
            assertThat(error(() -> statuses.change(Long.toString(id), new PostProcessStatusRequest("DONE"), member())).getErrorCode())
                    .isEqualTo(BoardSanctionException.STAFF_FORBIDDEN);
            assertThat(error(() -> statuses.change(Long.toString(id), new PostProcessStatusRequest("DONE"), null)).getErrorCode()).isEqualTo("UNAUTHENTICATED");
            CommonException bad = error(() -> statuses.change(Long.toString(id), new PostProcessStatusRequest("WHATEVER"), staff()));
            assertThat(bad.getErrorCode()).isEqualTo("VALIDATION");
            assertThat(bad.getFields()).containsKey("status");
            ops("OPTIONAL", false);
            assertThat(error(() -> statuses.change(Long.toString(id), new PostProcessStatusRequest("DONE"), staff())).getErrorCode())
                    .isEqualTo("POST_STATUS_DISABLED");
        }

        @Test
        void 목록_거르기_RECEIVED_는_값_없는_글도_포함한다() {
            long author = member();
            long received = post(author, false);
            long legacy = ids.nextId();
            boardFixture.post(legacy, board, author, "켜기 전 글", BODY); // 값 없음
            long done = post(author, false);
            statuses.change(Long.toString(done), new PostProcessStatusRequest("DONE"), staff());

            List<String> receivedIds = queries.list(slug, null, "LATEST", null, 1, author, null, null, "RECEIVED").items().stream().map(PostRowView::id).toList();
            assertThat(receivedIds).contains(Long.toString(received), Long.toString(legacy)).doesNotContain(Long.toString(done));
            PostListResponse doneList = queries.list(slug, null, "LATEST", null, 1, author, null, null, "DONE");
            assertThat(doneList.items()).extracting(PostRowView::id).containsExactly(Long.toString(done));
            assertThat(row(queries.list(slug, null, "LATEST", null, 1, author), legacy).processStatus()).isEqualTo("RECEIVED");
            assertThat(queries.list(slug, null, "LATEST", null, 1, author, null, null, "NOPE").items()).isEmpty();
        }

        @Test
        void 알림_한_줄은_게시판_이름과_새_상태를_싣는다() {
            long author = member();
            long id = post(author, false);
            statuses.change(Long.toString(id), new PostProcessStatusRequest("REJECTED"), staff());
            Map<String, Object> row = jdbc.queryForMap("SELECT post_id, payload::text AS payload FROM member_notifications WHERE user_id = ? AND type = 'POST_STATUS_CHANGED'",
                    author);
            assertThat(row).containsEntry("post_id", id);
            assertThat((String) row.get("payload")).contains("\"status\": \"REJECTED\"").contains("운영 시험");
        }
    }
}
