package com.community.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.community.auth.service.MeService;
import com.community.block.service.SocialMembers;
import com.community.board.dto.BoardActor;
import com.community.board.service.AuthorMembers;
import com.community.board.service.AuthorViews;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceApiTestBase;
import com.community.operator.exception.OperatorAccountLinkedException;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.permission.entity.Permission;
import com.community.post.dto.response.PostAuthorView;
import com.community.sanction.exception.BoardSanctionException;
import com.community.sanction.repository.master.BoardStaffActionStore.ActionRow;
import com.community.sanction.service.BoardSanctionService;
import com.community.sanction.service.BoardStaffActionService;
import com.community.withdrawal.WithdrawalTestSupport;
import com.community.withdrawal.service.WithdrawalService;
import com.community.zconfiguration.exception.CommonException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 운영자 회원 계정 — 공개 사이트 쪽 규칙(docs/features/operator-member-account-contract.md 정책 6~12) 을 실제 PostgreSQL 에서 확인한다.
 * 연결은 SQL 로 바로 넣는다(연결·해제 규칙은 관리자 쪽 시험 {@code OperatorMemberAccountAdminTest}).
 */
class OperatorMemberAccountSiteTest extends CommerceApiTestBase {

    private static final String IP = "127.0.0.1";

    @Autowired
    private OperatorMemberAccountService accounts;
    @Autowired
    private BoardStaffActionService staffActions;
    @Autowired
    private BoardSanctionService sanctions;
    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private BoardActorResolver actors;
    @Autowired
    private AuthorViews authors;
    @Autowired
    private SocialMembers socialMembers;
    @Autowired
    private MeService me;
    @Autowired
    private WithdrawalService withdrawals;
    @Autowired
    private PasswordEncoder encoder;

    private long board;

    @BeforeEach
    void setUp() {
        board = ids.nextId();
        boardFixture.board(board, "om" + Long.toString(board, 36), "운영자 계정 시험 " + Long.toString(board, 36), 961);
        catalog.invalidate();
    }

    // ── 재료 ───────────────────────────────────────────────────────────────

    /** 이메일 인증을 마친 일반 등급 회원(프로필 포함) */
    private long member() {
        long id = newMember();
        memberFixture.verifyEmail(id);
        String name = Long.toString(id, 36);
        memberFixture.profile(id, "o" + name.substring(Math.max(0, name.length() - 11)), "REGULAR");
        return id;
    }

    /** 고른 관리자 권한만 가진 역할 하나를 받은 활성 운영자(권한이 없으면 역할도 없다) */
    private long operatorWith(Permission... permissions) {
        long id = newOperator();
        if (permissions.length == 0) {
            return id;
        }
        for (Permission permission : permissions) {
            // SQL 로 남김(ON CONFLICT upsert): 권한 표는 관리자 앱이 뜰 때 맞춘다 — 공개 사이트 문맥만 띄운 시험이라 없으면 코드 값으로 넣는다
            jdbc.update("INSERT INTO permissions (code, axis, type_id, category, feature_ready, synced_at) VALUES (?, 'OPERATOR', ?, ?, ?, now()) ON CONFLICT (code) DO NOTHING",
                    permission.code(), permission.type().id(), permission.unit().name(), permission.featureReady());
        }
        operatorFixture.grantRole(id, "ROLE_ZZ_OM_" + id, "om-" + (id % 1_000_000_000L), java.util.Arrays.stream(permissions).map(Permission::code).toList());
        return id;
    }

    private void link(long operatorId, long userId) {
        jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", operatorId, userId);
    }

    /** 이어진 운영자 회원 계정(회원 ID) — 그 운영자의 권한 */
    private long operatorAccount(Permission... permissions) {
        long user = member();
        link(operatorWith(permissions), user);
        return user;
    }

    private long operatorOf(long userId) {
        return jdbc.queryForObject("SELECT operator_id FROM operator_member_accounts WHERE user_id = ?", Long.class, userId);
    }

    private void staff(long userId, String role) {
        // SQL 로 남김: board_staff 팩터리(byOperator)는 지정한 운영자를 요구한다 — 시험은 지정자 없는 행
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, ?)", board, userId, role);
    }

    private long post(long authorId) {
        long id = ids.nextId();
        boardFixture.post(id, board, authorId, "시험 글 " + (id % 1000), "본문");
        return id;
    }

    private long comment(long postId, long authorId) {
        long id = ids.nextId();
        boardFixture.comment(id, postId, board, authorId, "시험 댓글입니다");
        return id;
    }

    private static String code(Throwable thrown) {
        assertThat(thrown).isInstanceOf(CommonException.class);
        return ((CommonException) thrown).getErrorCode();
    }

    private String code(Runnable call) {
        return code(catchThrowableOfType(RuntimeException.class, call::run));
    }

    private BoardSanctionService.Issue warning(long actorId, long targetId) {
        return new BoardSanctionService.Issue(actorId, board, targetId, "WARNING", "SPAM", null, null, null, IP);
    }

    // ── 힘 판정(정책 6) ─────────────────────────────────────────────────────

    @Nested
    class Powers {

        @Test
        void 연결되고_운영자와_회원이_정상이면_힘이_살아_있고_권한은_관리자_앱_거울이다() {
            long user = operatorAccount(Permission.POST_MANAGE);
            Optional<OperatorMemberAccountService.ActiveOperatorAccount> active = accounts.activeOperator(user);
            assertThat(active).isPresent();
            assertThat(active.get().operatorId()).isEqualTo(operatorOf(user));
            assertThat(active.get().can(Permission.POST_MANAGE)).isTrue();
            assertThat(active.get().can(Permission.SANCTION_MANAGE)).isFalse();
            assertThat(accounts.isActiveOperatorAccount(user)).isTrue();
        }

        @Test
        void 운영자_사용_중지면_꺼진다() {
            long user = operatorAccount(Permission.POST_MANAGE);
            operatorFixture.changeStatus(operatorOf(user), null, null, null, false);
            assertThat(accounts.activeOperator(user)).isEmpty();
            assertThat(accounts.isActiveOperatorAccount(user)).isFalse();
        }

        @Test
        void 운영자_잠금이면_꺼진다() {
            long user = operatorAccount(Permission.POST_MANAGE);
            operatorFixture.changeStatus(operatorOf(user), null, true, null, null);
            assertThat(accounts.isActiveOperatorAccount(user)).isFalse();
        }

        @Test
        void 운영자_기간_밖이면_꺼진다() {
            long user = operatorAccount(Permission.POST_MANAGE);
            // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
            jdbc.update("UPDATE operators SET account_end_at = now() - interval '1 day' WHERE id = ?", operatorOf(user));
            assertThat(accounts.isActiveOperatorAccount(user)).isFalse();
            jdbc.update("UPDATE operators SET account_end_at = NULL, account_start_at = now() + interval '1 day' WHERE id = ?", operatorOf(user));
            assertThat(accounts.isActiveOperatorAccount(user)).isFalse();
        }

        @Test
        void 회원이_사이트_정지_중이면_꺼진다() {
            long user = operatorAccount(Permission.POST_MANAGE);
            // SQL 로 남김: 회원 상태(정지·차단·탈퇴)·이메일·인증 칸을 그대로 두는 도메인 메서드가 없다
            jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", user);
            assertThat(accounts.isActiveOperatorAccount(user)).isFalse();
        }

        @Test
        void 목록_표시는_살아_있는_연결만_고른다() {
            long active = operatorAccount();
            long paused = operatorAccount();
            operatorFixture.changeStatus(operatorOf(paused), null, null, null, false);
            long plain = member();
            assertThat(accounts.activeOperatorUserIds(List.of(active, paused, plain))).containsExactly(active);
        }
    }

    // ── 게시판 관리 — 권한 거울 · 한도 없음 · 기록(정책 7·11) ─────────────────

    @Nested
    class Moderation {

        @Test
        void post_manage_가_없으면_글_처리는_403이다() {
            long user = operatorAccount(Permission.SANCTION_MANAGE);
            long target = post(member());
            assertThat(code(() -> staffActions.hidePost(user, target, "SPAM", IP))).isEqualTo(BoardSanctionException.STAFF_FORBIDDEN);
            assertThat(code(() -> staffActions.deletePost(user, target, "SPAM", IP))).isEqualTo(BoardSanctionException.STAFF_FORBIDDEN);
        }

        @Test
        void 힘이_멈추면_처리할_수_없다() {
            long user = operatorAccount(Permission.POST_MANAGE);
            operatorFixture.changeStatus(operatorOf(user), null, null, null, false);
            long target = post(member());
            assertThat(code(() -> staffActions.hidePost(user, target, "SPAM", IP))).isEqualTo(BoardSanctionException.STAFF_FORBIDDEN);
        }

        @Test
        void 글_숨기기는_한도를_세지_않고_운영자로_기록된다() {
            long user = operatorAccount(Permission.POST_MANAGE);
            long operator = operatorOf(user);
            long target = post(member());

            BoardStaffActionService.Done done = staffActions.hidePost(user, target, "SPAM", IP);

            assertThat(done.quota()).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM board_staff_usage WHERE board_id = ? AND user_id = ?", Integer.class, board, user)).isZero();
            Map<String, Object> action = jdbc.queryForMap("SELECT actor_id, actor_role, actor_operator_id FROM board_staff_actions WHERE id = ?", done.actionId());
            assertThat(action).containsEntry("actor_id", user).containsEntry("actor_role", "OPERATOR").containsEntry("actor_operator_id", operator);
            Map<String, Object> event = jdbc.queryForMap("SELECT actor_kind, actor_id, detail::text AS detail FROM post_moderation_events WHERE post_id = ? ORDER BY id DESC LIMIT 1",
                    target);
            assertThat(event).containsEntry("actor_kind", "OPERATOR").containsEntry("actor_id", operator);
            assertThat((String) event.get("detail")).contains("\"viaUserId\"").contains(Long.toString(user));
            Map<String, Object> audit = lastAudit("POST", Long.toString(target), "SITE_POST_HIDE");
            assertThat(audit).isNotNull().containsEntry("actor_type", "OPERATOR").containsEntry("actor_id", operator);
            assertThat((String) audit.get("detail")).contains("\"viaUserId\":\"" + user + "\"").contains("\"reasonCode\":\"SPAM\"")
                    .contains("\"boardId\":\"" + board + "\"");
            assertThat(jdbc.queryForObject("SELECT hidden_reason FROM posts WHERE id = ?", String.class, target)).isEqualTo("STAFF");
        }

        @Test
        void 하루_한도를_넘겨도_계속_처리된다() {
            // SQL 로 남김: 정책·기본 칸 하나만 바꾸는 도메인 메서드가 없다 — changePolicy·changeBasic 은 묶음 전체를 바꾼다
            jdbc.update("UPDATE boards SET staff_comment_daily_limit = 1 WHERE id = ?", board);
            catalog.invalidate();
            long user = operatorAccount(Permission.POST_MANAGE);
            long target = post(member());
            for (int i = 0; i < 3; i++) {
                long c = comment(target, member());
                assertThat(staffActions.deleteComment(user, target, c, "ABUSE", IP).quota()).isNull();
            }
            long last = comment(target, member());
            staffActions.deleteComment(user, target, last, "ABUSE", IP);
            Map<String, Object> audit = lastAudit("COMMENT", Long.toString(last), "SITE_COMMENT_DELETE");
            assertThat(audit).isNotNull().containsEntry("actor_id", operatorOf(user));
            assertThat((String) audit.get("detail")).contains("\"commentId\":\"" + last + "\"").contains("\"viaUserId\":\"" + user + "\"");
        }

        @Test
        void 다른_운영자_회원_계정의_글_댓글은_409이다() {
            long user = operatorAccount(Permission.POST_MANAGE);
            long other = operatorAccount(Permission.POST_MANAGE);
            long target = post(other);
            assertThat(code(() -> staffActions.hidePost(user, target, "SPAM", IP))).isEqualTo(BoardSanctionException.TARGET_PROTECTED);
            long c = comment(post(member()), other);
            long cPost = jdbc.queryForObject("SELECT post_id FROM comments WHERE id = ?", Long.class, c);
            assertThat(code(() -> staffActions.deleteComment(user, cPost, c, "SPAM", IP))).isEqualTo(BoardSanctionException.TARGET_PROTECTED);
        }

        @Test
        void 본인_글은_409이다() {
            long user = operatorAccount(Permission.POST_MANAGE);
            long mine = post(user);
            assertThat(code(() -> staffActions.hidePost(user, mine, "SPAM", IP))).isEqualTo(BoardSanctionException.TARGET_PROTECTED);
        }

        @Test
        void 매니저_스텝의_글은_운영자_회원_계정이_처리할_수_있다() {
            long user = operatorAccount(Permission.POST_MANAGE);
            long manager = member();
            staff(manager, "MANAGER");
            assertThat(staffActions.hidePost(user, post(manager), "OFF_TOPIC", IP).quota()).isNull();
        }

        @Test
        void 매니저_스텝이_운영자_회원_계정의_글을_처리하면_409이다() {
            long manager = member();
            staff(manager, "MANAGER");
            long user = operatorAccount(Permission.POST_MANAGE);
            long target = post(user);
            assertThat(code(() -> staffActions.hidePost(manager, target, "SPAM", IP))).isEqualTo(BoardSanctionException.TARGET_PROTECTED);
            long c = comment(post(member()), user);
            long cPost = jdbc.queryForObject("SELECT post_id FROM comments WHERE id = ?", Long.class, c);
            assertThat(code(() -> staffActions.deleteComment(manager, cPost, c, "SPAM", IP))).isEqualTo(BoardSanctionException.TARGET_PROTECTED);
        }

        @Test
        void 보호_대상_집합_매니저에게는_운영자_회원_계정이_운영자_회원_계정에게는_매니저가_빠진다() {
            long manager = member();
            staff(manager, "MANAGER");
            long user = operatorAccount(Permission.POST_MANAGE);
            assertThat(staffActions.protectedAuthorsFor(board, manager)).contains(manager, user);
            assertThat(staffActions.protectedAuthorsFor(board, user)).contains(user).doesNotContain(manager);
            assertThat(staffActions.protectedAuthorsFor(board, member())).isEmpty();
        }

        @Test
        void 매니저는_운영자_회원_계정의_처리를_되돌리지_못한다() {
            long manager = member();
            staff(manager, "MANAGER");
            long user = operatorAccount(Permission.POST_MANAGE);
            long actionId = staffActions.hidePost(user, post(member()), "SPAM", IP).actionId();

            BoardStaffActionService.Page page = staffActions.page(manager, board, "ALL", 1);
            ActionRow row = page.rows().stream().filter(r -> r.id() == actionId).findFirst().orElseThrow();
            assertThat(row.actorRole()).isEqualTo("OPERATOR");
            assertThat(page.canRestore(row)).isFalse();
            assertThat(code(() -> staffActions.restoreByManager(manager, actionId, "되돌려 봅니다", IP))).isEqualTo(BoardSanctionException.STAFF_FORBIDDEN);
        }

        @Test
        void 운영자_회원_계정은_keeper_action_restore_로_매니저_처리를_되돌린다() {
            long manager = member();
            staff(manager, "MANAGER");
            long target = post(member());
            long actionId = staffActions.hidePost(manager, target, "SPAM", IP).actionId();
            long reader = operatorAccount(Permission.KEEPER_ACTION_READ);
            BoardStaffActionService.Page page = staffActions.page(reader, board, "ALL", 1);
            assertThat(page.manager()).isTrue();
            assertThat(page.rows()).extracting(ActionRow::id).contains(actionId);
            assertThat(code(() -> staffActions.restoreByManager(reader, actionId, "되돌립니다", IP))).isEqualTo(BoardSanctionException.STAFF_FORBIDDEN);

            long restorer = operatorAccount(Permission.KEEPER_ACTION_RESTORE);
            ActionRow restored = staffActions.restoreByManager(restorer, actionId, "운영자가 되돌립니다", IP);
            assertThat(restored.restored()).isTrue();
            assertThat(restored.restoredByKind()).isEqualTo("OPERATOR");
            assertThat(restored.restoredById()).isEqualTo(operatorOf(restorer));
            Map<String, Object> audit = lastAudit("POST", Long.toString(target), "SITE_ACTION_RESTORE");
            assertThat(audit).isNotNull().containsEntry("actor_id", operatorOf(restorer));
            assertThat((String) audit.get("detail")).contains("\"viaUserId\":\"" + restorer + "\"");
        }

        @Test
        void keeper_action_read_가_없으면_처리_내역은_404이다() {
            long user = operatorAccount(Permission.POST_MANAGE);
            assertThat(code(() -> staffActions.page(user, board, "ALL", 1))).isEqualTo(BoardSanctionException.STAFF_NOT_FOUND);
        }

        @Test
        void 도구는_운영자_표시와_한도_없음으로_오고_네_권한이_없으면_빈_값이다() {
            long user = operatorAccount(Permission.POST_MANAGE, Permission.KEEPER_ACTION_RESTORE);
            BoardStaffActionService.Tools tools = staffActions.tools(board, user).orElseThrow();
            assertThat(tools.operator()).isTrue();
            assertThat(tools.manager()).isTrue();
            assertThat(tools.postHide()).isTrue();
            assertThat(tools.commentDelete()).isTrue();
            assertThat(tools.sanction()).isFalse();
            assertThat(tools.restore()).isTrue();
            assertThat(tools.post()).isNull();
            assertThat(tools.comment()).isNull();
            assertThat(tools.sanctionQuota()).isNull();
            assertThat(staffActions.tools(board, operatorAccount(Permission.MEMBER_READ))).isEmpty();
        }
    }

    // ── 게시판 제재(정책 7) ─────────────────────────────────────────────────

    @Nested
    class Sanctions {

        @Test
        void sanction_manage_가_없으면_제재는_403이다() {
            long user = operatorAccount(Permission.POST_MANAGE);
            long target = member();
            assertThat(code(() -> sanctions.issue(warning(user, target)))).isEqualTo(BoardSanctionException.STAFF_FORBIDDEN);
            assertThat(code(() -> sanctions.preview(user, board, target))).isEqualTo(BoardSanctionException.STAFF_FORBIDDEN);
        }

        @Test
        void 제재는_한도를_세지_않고_운영자로_기록된다() {
            long user = operatorAccount(Permission.SANCTION_MANAGE);
            long operator = operatorOf(user);
            long target = member();

            assertThat(sanctions.preview(user, board, target).quota()).isNull();
            BoardSanctionService.Issued issued = sanctions.issue(warning(user, target));

            assertThat(issued.quota()).isNull();
            assertThat(issued.row().issuedRole()).isEqualTo("OPERATOR");
            assertThat(issued.row().issuedOperatorId()).isEqualTo(operator);
            assertThat(issued.row().issuedBy()).isEqualTo(user);
            Map<String, Object> action = jdbc.queryForMap("SELECT actor_role, actor_operator_id FROM board_staff_actions WHERE board_sanction_id = ?", issued.row().id());
            assertThat(action).containsEntry("actor_role", "OPERATOR").containsEntry("actor_operator_id", operator);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM board_staff_usage WHERE board_id = ? AND user_id = ?", Integer.class, board, user)).isZero();
            Map<String, Object> audit = lastAudit("MEMBER", Long.toString(target), "SITE_BOARD_SANCTION");
            assertThat(audit).isNotNull().containsEntry("actor_type", "OPERATOR").containsEntry("actor_id", operator);
            assertThat((String) audit.get("detail")).contains("\"viaUserId\":\"" + user + "\"").contains("\"kind\":\"WARNING\"");
        }

        @Test
        void 다른_운영자_회원_계정과_본인은_제재할_수_없다() {
            long user = operatorAccount(Permission.SANCTION_MANAGE);
            long other = operatorAccount();
            assertThat(code(() -> sanctions.issue(warning(user, other)))).isEqualTo(BoardSanctionException.TARGET_PROTECTED);
            assertThat(code(() -> sanctions.issue(warning(user, user)))).isEqualTo(BoardSanctionException.TARGET_PROTECTED);
        }

        @Test
        void 매니저_스텝은_운영자_회원_계정을_제재할_수_없고_운영자_회원_계정은_스텝을_제재할_수_있다() {
            long manager = member();
            staff(manager, "MANAGER");
            long user = operatorAccount(Permission.SANCTION_MANAGE);
            assertThat(code(() -> sanctions.issue(warning(manager, user)))).isEqualTo(BoardSanctionException.TARGET_PROTECTED);
            long stepped = member();
            staff(stepped, "STAFF");
            assertThat(sanctions.issue(warning(user, stepped)).row().issuedRole()).isEqualTo("OPERATOR");
        }

        @Test
        void 반복_검토는_운영자_회원_계정의_제재도_합산한다() {
            long manager = member();
            staff(manager, "MANAGER");
            long user = operatorAccount(Permission.SANCTION_MANAGE);
            long target = member();
            sanctions.issue(warning(manager, target));
            sanctions.issue(warning(user, target));
            sanctions.issue(warning(user, target));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM board_sanction_reviews WHERE user_id = ? AND board_id = ? AND status = 'OPEN'", Integer.class, target, board))
                    .isEqualTo(1);
        }

        @Test
        void 매니저는_운영자_회원_계정이_준_쓰기_제한을_풀지_못한다() {
            long manager = member();
            staff(manager, "MANAGER");
            long user = operatorAccount(Permission.SANCTION_MANAGE, Permission.KEEPER_ACTION_READ);
            long target = member();
            long id = sanctions.issue(new BoardSanctionService.Issue(user, board, target, "RESTRICT_1", "SPAM", null, null, null, IP)).row().id();
            assertThat(code(() -> sanctions.liftByManager(manager, id, "풀어 봅니다", IP))).isEqualTo(BoardSanctionException.STAFF_FORBIDDEN);
            BoardSanctionService.StaffPage managerPage = sanctions.staffPage(manager, board, "ALL", 1);
            assertThat(managerPage.rows()).filteredOn(r -> r.id() == id).allSatisfy(r -> assertThat(managerPage.canLift(r, sanctions.now())).isFalse());

            BoardSanctionService.StaffPage operatorPage = sanctions.staffPage(user, board, "ALL", 1);
            assertThat(operatorPage.manager()).isTrue();
            assertThat(operatorPage.rows()).filteredOn(r -> r.id() == id).allSatisfy(r -> assertThat(operatorPage.canLift(r, sanctions.now())).isTrue());
            assertThat(sanctions.liftByManager(user, id, "운영자가 풉니다", IP).liftedByType()).isEqualTo("OPERATOR");
            assertThat(lastAudit("MEMBER", Long.toString(target), "SITE_BOARD_SANCTION_LIFT")).isNotNull().containsEntry("actor_id", operatorOf(user));
        }
    }

    // ── 표시 · 운영진 · 보호 · 탈퇴(정책 8·9·10·12) ─────────────────────────

    @Nested
    class Site {

        @Test
        void 작성자_운영자_표시는_힘이_살아_있을_때만이다() {
            long user = operatorAccount();
            long plain = member();
            AuthorMembers members = authors.members(List.of(user, plain));
            PostAuthorView author = authors.of(user, null, null, members, catalog.snapshot());
            assertThat(author.operator()).isTrue();
            assertThat(AuthorViews.blank(author).operator()).isFalse();
            assertThat(authors.of(plain, null, null, members, catalog.snapshot()).operator()).isFalse();
            // SQL 로 남김: 회원 상태(정지·차단·탈퇴)·이메일·인증 칸을 그대로 두는 도메인 메서드가 없다
            jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", user);
            assertThat(authors.of(user, null, null, authors.members(List.of(user)), catalog.snapshot()).operator()).isFalse();
        }

        @Test
        void 운영자_회원_계정은_모든_공개_게시판의_운영진이다() {
            long user = operatorAccount();
            BoardActor actor = actors.resolve(user, catalog.snapshot());
            assertThat(actor.staffOf(board)).isTrue();
            operatorFixture.changeStatus(operatorOf(user), null, null, null, false);
            assertThat(actors.resolve(user, catalog.snapshot()).staffOf(board)).isFalse();
        }

        @Test
        void 차단에서_운영진처럼_보호된다() {
            long user = operatorAccount();
            assertThat(socialMembers.staff(user)).isTrue();
            assertThat(socialMembers.staff(member())).isFalse();
        }

        @Test
        void 로그인_정보에_운영자_이름이_실린다() {
            long user = operatorAccount();
            String name = operatorFixture.operatorName(operatorOf(user));
            assertThat(me.of(user).orElseThrow().operatorAccount()).isNotNull().extracting(a -> a.operatorName()).isEqualTo(name);
            assertThat(me.of(member()).orElseThrow().operatorAccount()).isNull();
        }

        @Test
        void 이어진_동안은_탈퇴_신청이_409이다() {
            long user = operatorAccount();
            WithdrawalTestSupport.withPassword(jdbc, encoder, user, clock.instant());
            assertThat(code(() -> withdrawals.request(user, WithdrawalTestSupport.plain(), IP))).isEqualTo(OperatorAccountLinkedException.CODE);
            assertThat(memberFixture.status(user)).isEqualTo("ACTIVE");
            // 힘이 멈춰도(운영자 사용 중지) 이어져 있으면 막는다
            operatorFixture.changeStatus(operatorOf(user), null, null, null, false);
            assertThat(code(() -> withdrawals.request(user, WithdrawalTestSupport.plain(), IP))).isEqualTo(OperatorAccountLinkedException.CODE);
        }
    }
}
