package com.community.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.community.board.dto.request.GuestCredentials;
import com.community.board.service.BoardCatalog;
import com.community.comment.service.CommentService;
import com.community.commerce.CommerceApiTestBase;
import com.community.group.dto.request.GroupPostVisibilityRequest;
import com.community.group.dto.response.GroupPostPageView;
import com.community.group.dto.response.GroupPostRowView;
import com.community.group.service.GroupPostService;
import com.community.group.service.GroupPostVisibilityService;
import com.community.post.dto.request.PostProcessStatusRequest;
import com.community.post.dto.request.PostUpdateRequest;
import com.community.post.dto.request.PostWriteRequest;
import com.community.post.dto.response.PostDetailView;
import com.community.post.dto.response.PostProcessStatusHistoryItemView;
import com.community.post.dto.response.PostProcessStatusView;
import com.community.sanction.exception.BoardSanctionException;
import com.community.zconfiguration.exception.CommonException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 운영 게시판 미확정 처리(V168)의 공개 사이트 규칙을 실제 PostgreSQL 에서 확인한다 — 처리 상태 메모·이력 · 비회원 비밀글(비밀번호 열람) · 소모임 게시판의 비밀글·처리 상태.
 * 게시판·글은 픽스처·서비스로, 비밀번호 열람의 세션은 {@link MockHttpServletRequest} 를 요청 문맥에 걸어 본다(서버 세션 속성 — {@link SecretPostUnlocks}).
 */
class PostOpsFollowUpTest extends CommerceApiTestBase {

    private static final String IP = "127.0.0.1";
    private static final String BODY = "운영 게시판 후속 시험 본문입니다. 열 글자를 넘깁니다.";
    private static final String PASSWORD = "guest-pass-1234";

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
    private GroupPostService groupPosts;
    @Autowired
    private GroupPostVisibilityService visibility;

    private long board;
    private String slug;
    /** 비회원 작성 간격은 IP 마다 — 실행마다 다른 자리에서 시작해 글마다 새 IP */
    private static final java.util.concurrent.atomic.AtomicInteger IP_SEQ = new java.util.concurrent.atomic.AtomicInteger((int) (System.nanoTime() % 40_000));

    @BeforeEach
    void setUp() {
        board = ids.nextId();
        slug = "fu" + Long.toString(board, 36);
        boardFixture.board(board, slug, "후속 시험 " + Long.toString(board, 36), 963, "GENERAL", "GUEST");
        ops(board, "OPTIONAL", true);
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ── 재료 ───────────────────────────────────────────────────────────────

    private void ops(long boardId, String secretMode, boolean statusEnabled) {
        boardFixture.ops(boardId, secretMode, statusEnabled);
        catalog.invalidate();
    }

    private long member() {
        long id = newMember();
        memberFixture.verifyEmail(id);
        String name = Long.toString(id, 36);
        memberFixture.profile(id, "f" + name.substring(Math.max(0, name.length() - 11)), "REGULAR");
        return id;
    }

    private long staff() {
        long id = member();
        // SQL 로 남김: board_staff 팩터리(byOperator)는 지정한 운영자를 요구한다 — 시험은 지정자 없는 행
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'STAFF')", board, id);
        return id;
    }

    private long operatorAccount() {
        long id = member();
        // SQL 로 남김: 연결 규칙은 관리자 쪽 시험(OperatorMemberAccountAdminTest) — 여기서는 연결된 상태만 세운다
        jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", newOperator(), id);
        return id;
    }

    private long post(long authorId, boolean secret) {
        long id = ids.nextId();
        boardFixture.post(id, board, authorId, "후속 글 " + (id % 100_000), BODY, secret, true);
        return id;
    }

    /** 비회원 글(서비스로 쓴다 — 비회원 작성 간격은 IP 마다라 글마다 다른 IP) */
    private long guestWrite(Boolean secret) {
        int n = IP_SEQ.getAndIncrement();
        String ip = "10." + (n / 40_000 % 200 + 20) + "." + (n / 200 % 200) + "." + (n % 200 + 1);
        return Long.parseLong(writes.create(new PostWriteRequest(slug, null, "손님 글 " + System.nanoTime() % 100_000, "TEXT", BODY + " " + ip, false,
                new GuestCredentials("손님", PASSWORD, null), null, null, null, secret), null, ip).id());
    }

    private boolean secretOf(long postId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT secret FROM posts WHERE id = ?", Boolean.class, postId));
    }

    private CommonException error(Runnable call) {
        Throwable thrown = catchThrowableOfType(RuntimeException.class, call::run);
        assertThat(thrown).isInstanceOf(CommonException.class);
        return (CommonException) thrown;
    }

    /** 새 방문자(빈 세션)의 요청 문맥을 건다 */
    private MockHttpServletRequest visitor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    private <T> T as(MockHttpServletRequest request, Supplier<T> call) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            return call.get();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private int historyRows(long postId) {
        return jdbc.queryForObject("SELECT count(*) FROM post_process_status_history WHERE post_id = ?", Integer.class, postId);
    }

    // ── A. 처리 상태 메모 · 이력 ────────────────────────────────────────────

    @Nested
    class StatusNote {

        @Test
        void 메모는_앞뒤_공백을_떼고_빈_글자는_없음_200자_초과와_줄바꿈은_400() {
            assertThat(PostProcessStatusChanger.note("  확인했어요  ")).isEqualTo("확인했어요");
            assertThat(PostProcessStatusChanger.note("   ")).isNull();
            assertThat(PostProcessStatusChanger.note(null)).isNull();
            assertThat(PostProcessStatusChanger.note("가".repeat(200))).hasSize(200);
            long id = post(member(), false);
            long staff = staff();
            CommonException tooLong = error(() -> statuses.change(Long.toString(id), new PostProcessStatusRequest("DONE", "가".repeat(201)), staff));
            assertThat(tooLong.getFields()).containsKey("note");
            CommonException control = error(() -> statuses.change(Long.toString(id), new PostProcessStatusRequest("DONE", "첫 줄\n둘째 줄"), staff));
            assertThat(control.getFields()).containsKey("note");
            assertThat(historyRows(id)).as("거절되면 아무것도 바꾸지 않는다").isZero();
        }

        @Test
        void 바뀔_때마다_이력_한_줄_같은_값이면_없음_상세와_응답에_마지막_메모() {
            long author = member();
            long id = post(author, false);
            long staff = staff();
            PostProcessStatusView checking = statuses.change(Long.toString(id), new PostProcessStatusRequest("CHECKING", " 재현했어요 "), staff);
            assertThat(checking.processStatusNote()).isEqualTo("재현했어요");
            assertThat(historyRows(id)).isEqualTo(1);
            statuses.change(Long.toString(id), new PostProcessStatusRequest("CHECKING", "다른 메모"), staff);
            assertThat(historyRows(id)).as("같은 값이면 메모가 달라도 아무것도 하지 않는다").isEqualTo(1);

            Map<String, Object> row = jdbc.queryForMap("SELECT board_id, from_status, to_status, note, actor_kind, changed_by_user_id, changed_by_operator_id "
                    + "FROM post_process_status_history WHERE post_id = ?", id);
            assertThat(row).containsEntry("board_id", board).containsEntry("from_status", "RECEIVED").containsEntry("to_status", "CHECKING")
                    .containsEntry("note", "재현했어요").containsEntry("actor_kind", "BOARD_STAFF").containsEntry("changed_by_user_id", staff);
            assertThat(row.get("changed_by_operator_id")).isNull();
            assertThat(queries.detail(Long.toString(id), author, IP).processStatusNote()).isEqualTo("재현했어요");
            String payload = jdbc.queryForObject("SELECT payload::text FROM member_notifications WHERE user_id = ? AND type = 'POST_STATUS_CHANGED'", String.class, author);
            assertThat(payload).as("글쓴이 알림에 메모").contains("재현했어요");

            PostProcessStatusView done = statuses.change(Long.toString(id), new PostProcessStatusRequest("DONE"), operatorAccount());
            assertThat(done.processStatusNote()).as("마지막 변경에 메모가 없으면 null").isNull();
            assertThat(queries.detail(Long.toString(id), author, IP).processStatusNote()).isNull();

            List<PostProcessStatusHistoryItemView> items = statuses.history(Long.toString(id), author).items();
            assertThat(items).extracting(PostProcessStatusHistoryItemView::toStatus).containsExactly("DONE", "CHECKING");
            assertThat(items).extracting(PostProcessStatusHistoryItemView::actor).containsExactly("OPERATOR", "BOARD_STAFF");
            assertThat(items).extracting(PostProcessStatusHistoryItemView::actorName).containsOnlyNulls();
            assertThat(items.get(1).note()).isEqualTo("재현했어요");
        }

        @Test
        void 값_없던_글에_RECEIVED_는_이력이_없다() {
            long legacy = ids.nextId();
            boardFixture.post(legacy, board, member(), "켜기 전 글", BODY);
            statuses.change(Long.toString(legacy), new PostProcessStatusRequest("RECEIVED", "그대로"), staff());
            assertThat(historyRows(legacy)).isZero();
        }

        @Test
        void 이력은_글을_읽을_수_있는_사람만_비밀글은_403_상태를_안_쓰면_빈_목록() {
            long author = member();
            long secret = post(author, true);
            long staff = staff();
            statuses.change(Long.toString(secret), new PostProcessStatusRequest("CHECKING"), staff);
            assertThat(statuses.history(Long.toString(secret), author).items()).hasSize(1);
            assertThat(statuses.history(Long.toString(secret), staff).items()).hasSize(1);
            assertThat(error(() -> statuses.history(Long.toString(secret), member())).getErrorCode()).isEqualTo("SECRET_POST");
            assertThat(error(() -> statuses.history(Long.toString(secret), null)).getErrorCode()).isEqualTo("SECRET_POST");
            ops(board, "OPTIONAL", false);
            assertThat(statuses.history(Long.toString(secret), author).items()).isEmpty();
        }

        @Test
        void 지운_글의_원문을_파기하면_이력_메모도_비우고_줄은_남긴다() {
            long id = post(member(), false);
            statuses.change(Long.toString(id), new PostProcessStatusRequest("CHECKING", "개인 정보가 든 메모"), staff());
            // SQL 로 남김: 보유기간이 지난 「지운 글」을 세운다(시각을 옮기는 도메인 메서드가 없다) — 가장 오래된 지운 글이 되게 아주 옛날로
            jdbc.update("UPDATE posts SET deleted_at = now() - interval '3000 days' WHERE id = ?", id);
            Long changed = jdbc.queryForObject("SELECT board_privacy_process('deletedContent', 50)", Long.class);
            assertThat(changed).isPositive();
            assertThat(jdbc.queryForObject("SELECT purged_at IS NOT NULL FROM posts WHERE id = ?", Boolean.class, id)).isTrue();
            assertThat(historyRows(id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT note FROM post_process_status_history WHERE post_id = ?", String.class, id)).isNull();
        }

        @Test
        void 상세의_viewer_canChangeStatus_는_운영진만_true() {
            long author = member();
            long id = post(author, false);
            assertThat(queries.detail(Long.toString(id), staff(), IP).viewer().canChangeStatus()).isTrue();
            assertThat(queries.detail(Long.toString(id), operatorAccount(), IP).viewer().canChangeStatus()).isTrue();
            assertThat(queries.detail(Long.toString(id), author, IP).viewer().canChangeStatus()).isFalse();
            assertThat(queries.detail(Long.toString(id), null, IP).viewer().canChangeStatus()).isFalse();
            long staff = staff();
            ops(board, "OPTIONAL", false);
            assertThat(queries.detail(Long.toString(id), staff, IP).viewer().canChangeStatus()).as("상태를 안 쓰는 게시판").isFalse();
        }
    }

    // ── B. 비회원 비밀글 ────────────────────────────────────────────────────

    @Nested
    class GuestSecret {

        private Boolean captcha;

        @BeforeEach
        void noCaptcha() {
            // SQL 로 남김: 보안 설정 저장은 관리자 앱(BoardSettingsService) — 시험은 캡차(외부 Turnstile)만 잠깐 끈다
            captcha = jdbc.queryForObject("SELECT guest_captcha_required FROM board_security_settings WHERE id = 1", Boolean.class);
            jdbc.update("UPDATE board_security_settings SET guest_captcha_required = false WHERE id = 1");
            catalog.invalidate();
        }

        @AfterEach
        void restoreCaptcha() {
            jdbc.update("UPDATE board_security_settings SET guest_captcha_required = ? WHERE id = 1", captcha);
            catalog.invalidate();
        }

        @Test
        void OPTIONAL_은_비회원도_고르고_ALWAYS_는_비회원_글도_비밀_고치기도_같은_규칙() {
            assertThat(secretOf(guestWrite(true))).isTrue();
            assertThat(secretOf(guestWrite(null))).isFalse();
            ops(board, "ALWAYS", true);
            long always = guestWrite(false);
            assertThat(secretOf(always)).isTrue();
            ops(board, "OPTIONAL", true);
            // 비회원 비밀글은 비밀번호로 고친다 — 비밀번호가 글쓴이임을 보이므로 열람 권한 없이도 고친다
            writes.update(Long.toString(always), new PostUpdateRequest(null, "고친 손님 글", "TEXT", BODY, false, PASSWORD, null, null, false), null, "10.8.0.1");
            assertThat(secretOf(always)).isFalse();
            CommonException wrong = error(() -> writes.update(Long.toString(always), new PostUpdateRequest(null, "남의 고침", "TEXT", BODY, false, "wrong-pass", null,
                    null, true), null, "10.8.0.2"));
            assertThat(wrong.getErrorCode()).isEqualTo("PASSWORD_MISMATCH");
        }

        @Test
        void 볼_수_없으면_403_에_guestPost_true_회원_글이면_false() {
            long guest = guestWrite(true);
            CommonException denied = error(() -> queries.detail(Long.toString(guest), null, IP));
            assertThat(denied.getErrorCode()).isEqualTo("SECRET_POST");
            assertThat(denied.getExtras()).containsEntry("guestPost", true).containsEntry("boardSlug", slug).doesNotContainKey("groupId");
            long memberPost = post(member(), true);
            assertThat(error(() -> queries.detail(Long.toString(memberPost), null, IP)).getExtras()).containsEntry("guestPost", false);
            assertThat(queries.detail(Long.toString(guest), staff(), IP).secret()).as("게시판 운영진은 비회원 비밀글도 읽는다").isTrue();
        }

        @Test
        void 비밀번호를_맞히면_그_방문자만_상세_댓글을_보고_다른_방문자는_못_본다() {
            long guest = guestWrite(true);
            MockHttpServletRequest me = visitor();
            writes.unlockSecret(Long.toString(guest), PASSWORD, "10.7.0.1");
            assertThat(me.getSession(false)).isNotNull();
            assertThat(me.getSession(false).getMaxInactiveInterval()).as("새로 만든 세션은 열람 시간만큼").isEqualTo(3600);
            RequestContextHolder.resetRequestAttributes();

            PostDetailView view = as(me, () -> queries.detail(Long.toString(guest), null, "10.7.0.1"));
            assertThat(view.content()).startsWith(BODY);
            assertThat(as(me, () -> comments.list(Long.toString(guest), null, null, null, "10.7.0.1"))).isNotNull();

            MockHttpServletRequest other = new MockHttpServletRequest();
            CommonException denied = as(other, () -> error(() -> queries.detail(Long.toString(guest), null, "10.7.0.2")));
            assertThat(denied.getErrorCode()).isEqualTo("SECRET_POST");
            assertThat(error(() -> queries.detail(Long.toString(guest), null, IP)).getErrorCode()).as("요청 밖(세션 없음)").isEqualTo("SECRET_POST");
        }

        @Test
        void 틀리면_403_다섯_번_틀리면_429_회원_글은_400() {
            long guest = guestWrite(true);
            visitor();
            for (int i = 0; i < 5; i++) {
                assertThat(error(() -> writes.unlockSecret(Long.toString(guest), "wrong-pass", "10.6.0.1")).getErrorCode()).isEqualTo("PASSWORD_MISMATCH");
            }
            assertThat(error(() -> writes.unlockSecret(Long.toString(guest), PASSWORD, "10.6.0.1")).getErrorCode()).isEqualTo("PASSWORD_ATTEMPTS_EXCEEDED");
            assertThat(error(() -> queries.detail(Long.toString(guest), null, "10.6.0.1")).getErrorCode()).as("열람 권한이 생기지 않았다").isEqualTo("SECRET_POST");

            long memberPost = post(member(), true);
            assertThat(error(() -> writes.unlockSecret(Long.toString(memberPost), PASSWORD, "10.6.0.2")).getErrorCode()).isEqualTo("SECRET_UNLOCK_NOT_GUEST");
            long open = guestWrite(false);
            assertThat(error(() -> writes.unlockSecret(Long.toString(open), PASSWORD, "10.6.0.3")).getErrorCode()).isEqualTo("SECRET_UNLOCK_NOT_GUEST");
            assertThat(error(() -> writes.unlockSecret("1", PASSWORD, "10.6.0.4")).getErrorCode()).isEqualTo("POST_NOT_FOUND");
        }
    }

    // ── C. 소모임 게시판 ────────────────────────────────────────────────────

    @Nested
    class GroupBoard {

        private long groupBoard;
        private long leader;

        @BeforeEach
        void groupSetUp() {
            groupBoard = ids.nextId();
            boardFixture.board(groupBoard, "fg" + Long.toString(groupBoard, 36), "후속 소모임 " + Long.toString(groupBoard, 36), 964, "GROUP", null);
            ops(groupBoard, "OPTIONAL", true);
            leader = member();
        }

        /** 소모임(열림) — 모임장 + 멤버들. {@code visibility} PRIVATE · PUBLIC */
        private long group(String visibility, long... memberIds) {
            long id = ids.nextId();
            // SQL 로 남김: 소모임 엔티티는 신청 → 승인 흐름으로만 열린다 — 시험은 열린 소모임 행을 바로 세운다(WithdrawalFlowTest 와 같다)
            jdbc.update("INSERT INTO groups (id, name, name_key, description, board_id, join_type, capacity, status, leader_id, creator_id, member_count, visibility, "
                    + "requested_at, opened_at) VALUES (?, ?, ?, '시험', ?, 'OPEN', 50, 'ACTIVE', ?, ?, ?, ?, now(), now())", id, "g" + Long.toString(id, 36),
                    "g" + Long.toString(id, 36), groupBoard, leader, leader, 1 + memberIds.length, visibility);
            jdbc.update("INSERT INTO group_members (group_id, user_id, role, status, joined_at) VALUES (?, ?, 'LEADER', 'ACTIVE', now())", id, leader);
            int i = 0;
            for (long m : memberIds) {
                jdbc.update("INSERT INTO group_members (group_id, user_id, role, status, joined_at) VALUES (?, ?, 'MEMBER', 'ACTIVE', now() + ?::interval)", id, m,
                        (i++) + " seconds");
            }
            return id;
        }

        private void manager(long groupId, long userId, int perms) {
            jdbc.update("UPDATE group_members SET role = 'MANAGER', manager_perms = ?, role_changed_at = now() WHERE group_id = ? AND user_id = ?", perms, groupId, userId);
        }

        private long groupWrite(long groupId, long authorId, Boolean secret) {
            return Long.parseLong(groupPosts.create(Long.toString(groupId), authorId, new PostWriteRequest(null, null, "소모임 글 " + authorId % 100_000, "TEXT",
                    BODY + " " + authorId, null, null, null, null, null, secret), IP).id());
        }

        @Test
        void 소모임_게시판도_비밀글을_고르고_처음_상태는_RECEIVED() {
            long author = member();
            long g = group("PRIVATE", author);
            long secret = groupWrite(g, author, true);
            assertThat(secretOf(secret)).isTrue();
            assertThat(jdbc.queryForObject("SELECT process_status FROM posts WHERE id = ?", String.class, secret)).isEqualTo("RECEIVED");
            assertThat(secretOf(groupWrite(g, leader, null))).isFalse();
        }

        @Test
        void 비밀글은_글쓴이_소모임장_글_관리_매니저만_읽고_다른_멤버는_403_groupId() {
            long author = member();
            long plain = member();
            long helper = member();
            long keeper = member();
            long g = group("PRIVATE", author, plain, helper, keeper);
            manager(g, helper, 1); // 가입 신청만 맡은 매니저
            manager(g, keeper, 16); // 글 관리(UNPUBLISH)를 맡은 매니저
            long secret = groupWrite(g, author, true);
            assertThat(queries.detail(Long.toString(secret), author, IP).secret()).isTrue();
            assertThat(queries.detail(Long.toString(secret), leader, IP).secret()).isTrue();
            assertThat(queries.detail(Long.toString(secret), keeper, IP).secret()).isTrue();
            CommonException denied = error(() -> queries.detail(Long.toString(secret), plain, IP));
            assertThat(denied.getErrorCode()).isEqualTo("SECRET_POST");
            assertThat(denied.getExtras()).containsEntry("groupId", Long.toString(g)).containsEntry("guestPost", false);
            assertThat(error(() -> queries.detail(Long.toString(secret), helper, IP)).getErrorCode()).isEqualTo("SECRET_POST");
            assertThat(error(() -> comments.list(Long.toString(secret), null, null, plain, IP)).getErrorCode()).isEqualTo("SECRET_POST");
        }

        @Test
        void 운영자_회원_계정은_공개_소모임의_비밀글을_읽고_상태를_바꾼다() {
            long author = member();
            long g = group("PUBLIC", author);
            long secret = groupWrite(g, author, true);
            long account = operatorAccount();
            assertThat(queries.detail(Long.toString(secret), account, IP).viewer().canChangeStatus()).isTrue();
            assertThat(error(() -> queries.detail(Long.toString(secret), member(), IP)).getErrorCode()).as("비멤버").isEqualTo("SECRET_POST");
            statuses.change(Long.toString(secret), new PostProcessStatusRequest("CHECKING"), account);
            assertThat(jdbc.queryForObject("SELECT actor_kind FROM post_process_status_history WHERE post_id = ?", String.class, secret)).isEqualTo("OPERATOR");
        }

        @Test
        void 목록은_줄을_남기고_읽을_수_없는_사람에게_내용_칸을_비운다() {
            long author = member();
            long plain = member();
            long g = group("PRIVATE", author, plain);
            long secret = groupWrite(g, author, true);
            jdbc.update("UPDATE posts SET has_image = true WHERE id = ?", secret); // 내용 칸이 비는지 보려고 이미지 표시를 세운다
            GroupPostRowView sealed = row(groupPosts.page(Long.toString(g), plain, 1, null, null, null), secret);
            assertThat(sealed.post().secret()).isTrue();
            assertThat(sealed.post().title()).startsWith("소모임 글");
            assertThat(sealed.post().hasImage()).isFalse();
            assertThat(row(groupPosts.page(Long.toString(g), author, 1, null, null, null), secret).post().hasImage()).isTrue();
            assertThat(row(groupPosts.page(Long.toString(g), leader, 1, null, null, null), secret).post().hasImage()).isTrue();
            assertThat(row(groupPosts.list(Long.toString(g), plain, null, null), secret).post().hasImage()).as("커서 목록도 같다").isFalse();
        }

        @Test
        void 비밀글은_공개_전환을_할_수_없다() {
            long author = member();
            long g = group("PRIVATE", author);
            long secret = groupWrite(g, author, true);
            CommonException refused = error(() -> visibility.setPostVisibility(Long.toString(g), Long.toString(secret), leader,
                    new GroupPostVisibilityRequest("PUBLIC", true)));
            assertThat(refused.getErrorCode()).isEqualTo("GROUP_POST_SECRET");
            assertThat(jdbc.queryForObject("SELECT is_public FROM posts WHERE id = ?", Boolean.class, secret)).isFalse();
            PostDetailView view = queries.detail(Long.toString(secret), leader, IP);
            assertThat(view.group().canPublish()).isFalse();
        }

        @Test
        void 상태는_소모임장_글_관리_매니저와_운영자_계정만_바꾸고_목록을_상태로_거른다() {
            long author = member();
            long plain = member();
            long keeper = member();
            long g = group("PRIVATE", author, plain, keeper);
            manager(g, keeper, 16);
            long id = groupWrite(g, author, false);
            long other = groupWrite(g, plain, false);
            assertThat(error(() -> statuses.change(Long.toString(id), new PostProcessStatusRequest("DONE"), plain)).getErrorCode())
                    .isEqualTo(BoardSanctionException.STAFF_FORBIDDEN);
            assertThat(error(() -> statuses.change(Long.toString(id), new PostProcessStatusRequest("DONE"), author)).getErrorCode())
                    .as("글쓴이도 운영진이 아니면 못 바꾼다").isEqualTo(BoardSanctionException.STAFF_FORBIDDEN);
            assertThat(statuses.change(Long.toString(id), new PostProcessStatusRequest("CHECKING", "보는 중"), keeper).processStatus()).isEqualTo("CHECKING");
            assertThat(statuses.change(Long.toString(id), new PostProcessStatusRequest("DONE"), leader).processStatus()).isEqualTo("DONE");
            assertThat(jdbc.queryForList("SELECT actor_kind FROM post_process_status_history WHERE post_id = ? ORDER BY id", String.class, id))
                    .containsExactly("GROUP_STAFF", "GROUP_STAFF");
            assertThat(queries.detail(Long.toString(id), leader, IP).viewer().canChangeStatus()).isTrue();
            assertThat(queries.detail(Long.toString(id), plain, IP).viewer().canChangeStatus()).isFalse();
            assertThat(queries.detail(Long.toString(id), author, IP).processStatus()).isEqualTo("DONE");

            List<String> done = groupPosts.page(Long.toString(g), plain, 1, null, null, null, "DONE").items().stream().map(r -> r.post().id()).toList();
            assertThat(done).containsExactly(Long.toString(id));
            assertThat(groupPosts.page(Long.toString(g), plain, 1, null, null, null, "RECEIVED").items()).extracting(r -> r.post().id())
                    .contains(Long.toString(other)).doesNotContain(Long.toString(id));
            assertThat(groupPosts.list(Long.toString(g), plain, null, null, "NOPE").items()).isEmpty();
        }

        private GroupPostRowView row(GroupPostPageView page, long postId) {
            return page.items().stream().filter(r -> r.post().id().equals(Long.toString(postId))).findFirst().orElseThrow();
        }
    }
}
