package com.community.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.adminauth.service.AdminActorService;
import com.community.board.dto.request.BoardStaffAppointRequest;
import com.community.board.service.BoardStaffService;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.member.service.MemberService;
import com.community.operator.dto.request.OperatorMemberLinkRequest;
import com.community.operator.dto.response.OperatorMemberAccountView;
import com.community.operator.dto.response.OperatorView;
import com.community.operator.entity.OperatorTab;
import com.community.operator.exception.OperatorAccountLinkedException;
import com.community.operator.exception.OperatorMemberAccountException;
import com.community.operator.service.OperatorMemberAccountAdminService;
import com.community.operator.service.OperatorService;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.role.entity.MemberRoleIds;
import com.community.withdrawal.dto.MemberWithdrawalAdminRequest;
import com.community.withdrawal.service.MemberWithdrawalAdminService;
import com.community.zconfiguration.exception.CommonException;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 운영자 회원 계정 — 관리자 쪽 규칙(docs/features/operator-member-account-contract.md 정책 1~5 · 12 · 「API > 관리자」)을 실제 PostgreSQL 에서 확인한다.
 * 연결 화면 API 는 실제 보안 필터·권한 인터셉터를 거쳐(HTTP), 나머지(자격 · 게시판 담당 · 강제 탈퇴)는 서비스로 부른다.
 */
class OperatorMemberAccountAdminTest extends Commerce04AdminTestBase {

    private static final String IP = "127.0.0.1";

    @Autowired
    private OperatorMemberAccountAdminService service;
    @Autowired
    private AdminActorService actors;
    @Autowired
    private BoardStaffService boardStaff;
    @Autowired
    private MemberWithdrawalAdminService withdrawals;
    @Autowired
    private OperatorService operatorService;
    @Autowired
    private MemberService members;

    // ── 재료 ───────────────────────────────────────────────────────────────

    private AdminActor actor(long operatorId) {
        return actors.load(operatorId, IP).orElseThrow();
    }

    /** 연결을 바꿀 수 있는 운영자(operator.manage) */
    private AdminActor manager() {
        return actor(operatorWith(Permission.OPERATOR_READ, Permission.OPERATOR_MANAGE));
    }

    /** 그 운영자와 같은 이메일로 가입한 회원(인증 여부를 고른다) */
    private long memberOf(long operatorId, boolean verified) {
        long id = newMember();
        // SQL 로 남김: 회원 상태(정지·차단·탈퇴)·이메일·인증 칸을 그대로 두는 도메인 메서드가 없다
        jdbc.update("UPDATE users SET email = ?, email_verified_at = ? WHERE id = ?", email(operatorId), verified ? ts(java.time.Instant.now()) : null, id);
        return id;
    }

    private long board() {
        long id = ids.nextId();
        boardFixture.board(id, "oa" + Long.toString(id, 36), "운영자 계정 관리 시험 " + Long.toString(id, 36), 993);
        return id;
    }

    private OperatorMemberLinkRequest link(long userId) {
        return new OperatorMemberLinkRequest(Long.toString(userId));
    }

    private static String code(Throwable thrown) {
        assertThat(thrown).isInstanceOf(CommonException.class);
        return ((CommonException) thrown).getErrorCode();
    }

    private String code(Runnable call) {
        return code(catchThrowableOfType(RuntimeException.class, call::run));
    }

    private Map<String, Object> lastAudit(String action, long operatorId) {
        return auditLogs.where().action(action).target(Long.toString(operatorId)).lastById().orElseThrow().asMap();
    }

    // ── 연결 화면 API(HTTP) ─────────────────────────────────────────────────

    @Test
    void 후보는_같은_이메일_회원이고_operator_read_가_없으면_403이다() throws Exception {
        long target = operatorWith(Permission.POST_MANAGE);
        long user = memberOf(target, true);
        Cookie reader = login(operatorWith(Permission.OPERATOR_READ));
        mvc.perform(withCsrf(get("/api/admin/operators/" + target + "/member-account"), reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linked").doesNotExist())
                .andExpect(jsonPath("$.data.candidate.userId").value(Long.toString(user)))
                .andExpect(jsonPath("$.data.candidate.eligible").value(true))
                .andExpect(jsonPath("$.data.powers.dailyLimit").doesNotExist())
                .andExpect(jsonPath("$.data.powers.items[0].key").value("ROBOTICON_ALL"))
                .andExpect(jsonPath("$.data.powers.items[2].key").value("POST_MODERATE"))
                .andExpect(jsonPath("$.data.powers.items[2].granted").value(true))
                .andExpect(jsonPath("$.data.powers.items[4].key").value("AUTHOR_SANCTION"))
                .andExpect(jsonPath("$.data.powers.items[4].granted").value(false));

        Cookie nobody = login(operatorWith(Permission.MEMBER_READ));
        mvc.perform(withCsrf(get("/api/admin/operators/" + target + "/member-account"), nobody)).andExpect(status().isForbidden());
        // 읽기만 있으면 잇지 못한다(operator.manage)
        mvc.perform(withCsrf(put("/api/admin/operators/" + target + "/member-account"), reader).content("{\"userId\":\"" + user + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 잇고_끊으면_화면이_바뀌고_감사에_남는다() throws Exception {
        long target = operatorWith();
        long user = memberOf(target, true);
        long actorId = operatorWith(Permission.OPERATOR_READ, Permission.OPERATOR_MANAGE);
        Cookie cookie = login(actorId);

        mvc.perform(withCsrf(put("/api/admin/operators/" + target + "/member-account"), cookie).content("{\"userId\":\"" + user + "\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidate").doesNotExist())
                .andExpect(jsonPath("$.data.linked.userId").value(Long.toString(user)))
                .andExpect(jsonPath("$.data.linked.active").value(true))
                .andExpect(jsonPath("$.data.linked.linkedBy.operatorId").value(Long.toString(actorId)));
        Map<String, Object> linked = lastAudit("OPERATOR_MEMBER_LINK", target);
        assertThat(linked).containsEntry("actor_type", "OPERATOR").containsEntry("actor_id", actorId).containsEntry("target_type", "OPERATOR");
        assertThat((String) linked.get("detail")).contains("\"userId\":\"" + user + "\"");

        mvc.perform(withCsrf(delete("/api/admin/operators/" + target + "/member-account"), cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linked").doesNotExist())
                .andExpect(jsonPath("$.data.candidate.userId").value(Long.toString(user)));
        assertThat((String) lastAudit("OPERATOR_MEMBER_UNLINK", target).get("detail")).contains("\"userId\":\"" + user + "\"");
        mvc.perform(withCsrf(delete("/api/admin/operators/" + target + "/member-account"), cookie)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(OperatorMemberAccountException.NOT_LINKED));
    }

    // ── 연결 자격(정책 2·3) ─────────────────────────────────────────────────

    @Test
    void 같은_이메일이_아닌_회원은_후보가_아니다() {
        long target = operatorWith();
        memberOf(target, true);
        long stranger = newMember();
        memberFixture.verifyEmail(stranger);
        assertThat(code(() -> service.link(manager(), Long.toString(target), link(stranger)))).isEqualTo(OperatorMemberAccountException.NOT_CANDIDATE);
    }

    @Test
    void 이메일_인증을_마치지_않은_회원은_자격_미달이다() {
        long target = operatorWith();
        long user = memberOf(target, false);
        OperatorMemberAccountView view = service.get(Long.toString(target));
        assertThat(view.candidate().eligible()).isFalse();
        assertThat(view.candidate().ineligibleReasons()).containsExactly("EMAIL_NOT_VERIFIED");
        assertThat(code(() -> service.link(manager(), Long.toString(target), link(user)))).isEqualTo(OperatorMemberAccountException.NOT_ELIGIBLE);
    }

    @Test
    void 정지_중인_회원은_자격_미달이다() {
        long target = operatorWith();
        long user = memberOf(target, true);
        // SQL 로 남김: 회원 상태(정지·차단·탈퇴)·이메일·인증 칸을 그대로 두는 도메인 메서드가 없다
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", user);
        assertThat(service.get(Long.toString(target)).candidate().ineligibleReasons()).containsExactly("MEMBER_RESTRICTED");
        assertThat(code(() -> service.link(manager(), Long.toString(target), link(user)))).isEqualTo(OperatorMemberAccountException.NOT_ELIGIBLE);
    }

    @Test
    void 사업자_계정은_자격_미달이다() {
        long target = operatorWith();
        long user = memberOf(target, true);
        // SQL 로 남김: user_roles 엔티티에 팩터리가 없다
        jdbc.update("INSERT INTO user_roles (id, user_id, role_id) VALUES (?, ?, ?)", ids.nextId(), user, MemberRoleIds.BUSINESS);
        assertThat(service.get(Long.toString(target)).candidate().ineligibleReasons()).containsExactly("BUSINESS_ACCOUNT");
        assertThat(code(() -> service.link(manager(), Long.toString(target), link(user)))).isEqualTo(OperatorMemberAccountException.NOT_ELIGIBLE);
    }

    @Test
    void 다른_운영자에_이어진_회원은_자격_미달이고_그_운영자_이름을_준다() {
        long other = operatorWith();
        long target = operatorWith();
        long user = memberOf(target, true); // 다른 운영자에 이은 뒤 이메일을 이 운영자 것으로 바꾼 회원(연결은 그대로 — 정책 2)
        jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", other, user);
        OperatorMemberAccountView.Candidate candidate = service.get(Long.toString(target)).candidate();
        assertThat(candidate.ineligibleReasons()).containsExactly("LINKED_TO_OTHER");
        assertThat(candidate.linkedOperatorName()).isEqualTo(operatorName(other));
        assertThat(code(() -> service.link(manager(), Long.toString(target), link(user)))).isEqualTo(OperatorMemberAccountException.NOT_ELIGIBLE);
    }

    @Test
    void 이미_이어진_운영자는_다시_잇지_못한다() {
        long target = operatorWith();
        long user = memberOf(target, true);
        AdminActor actor = manager();
        service.link(actor, Long.toString(target), link(user));
        assertThat(code(() -> service.link(actor, Long.toString(target), link(user)))).isEqualTo(OperatorMemberAccountException.ALREADY_LINKED);
    }

    @Test
    void 이으면_그_회원의_게시판_매니저_스텝_지정이_모두_끝난다() {
        long target = operatorWith();
        long user = memberOf(target, true);
        long b1 = board();
        long b2 = board();
        // SQL 로 남김: board_staff 팩터리(byOperator)는 지정한 운영자를 요구한다 — 시험은 지정자 없는 행
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'MANAGER'), (?, ?, 'STAFF')", b1, user, b2, user);
        OperatorMemberAccountView.Candidate candidate = service.get(Long.toString(target)).candidate();
        assertThat(candidate.boardManagerCount()).isEqualTo(1);
        assertThat(candidate.boardStaffCount()).isEqualTo(1);

        service.link(manager(), Long.toString(target), link(user));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_staff WHERE user_id = ?", Integer.class, user)).isZero();
        assertThat((String) lastAudit("OPERATOR_MEMBER_LINK", target).get("detail")).contains("\"releasedBoards\"").contains(Long.toString(b1)).contains(Long.toString(b2));
    }

    @Test
    void 최고_관리자의_연결은_최고_관리자_본인만_바꾼다() {
        long root = operatorFixture.rootOrCreate(id -> "oa-root-" + id + "@test.local", id -> "root" + (id % 1_000_000_000L), "x");
        long stranger = newMember();
        assertThat(code(() -> service.link(manager(), Long.toString(root), link(stranger)))).isEqualTo(OperatorMemberAccountException.ROOT_PROTECTED);
        assertThat(code(() -> service.unlink(manager(), Long.toString(root)))).isEqualTo(OperatorMemberAccountException.ROOT_PROTECTED);
        // 본인은 보호에 걸리지 않는다(후보가 아니라서 다음 판정에서 막힌다)
        assertThat(code(() -> service.link(actor(root), Long.toString(root), link(stranger)))).isNotEqualTo(OperatorMemberAccountException.ROOT_PROTECTED);
    }

    @Test
    void 힘이_멈춘_까닭을_준다() {
        long target = operatorWith();
        long user = memberOf(target, true);
        service.link(manager(), Long.toString(target), link(user));
        operatorFixture.changeStatus(target, null, null, null, false);
        // SQL 로 남김: 회원 상태(정지·차단·탈퇴)·이메일·인증 칸을 그대로 두는 도메인 메서드가 없다
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", user);
        OperatorMemberAccountView.Linked linked = service.get(Long.toString(target)).linked();
        assertThat(linked.active()).isFalse();
        assertThat(linked.pausedReasons()).containsExactly("OPERATOR_UNAVAILABLE", "MEMBER_SUSPENDED");
    }

    // ── 다른 화면 · 다른 업무와의 맞물림 ─────────────────────────────────────

    @Test
    void 운영자_목록과_회원_상세에_연결이_보인다() {
        long target = operatorWith();
        long user = memberOf(target, true);
        AdminActor actor = manager();
        service.link(actor, Long.toString(target), link(user));
        OperatorView row = operatorService.list(OperatorTab.ACTIVE, email(target)).items().stream().filter(v -> v.operatorId().equals(Long.toString(target))).findFirst()
                .orElseThrow();
        assertThat(row.memberAccount()).isNotNull();
        assertThat(row.memberAccount().userId()).isEqualTo(Long.toString(user));
        assertThat(row.memberAccount().active()).isTrue();
        assertThat(members.detail(actor, Long.toString(user)).operatorAccount().operatorId()).isEqualTo(Long.toString(target));
    }

    @Test
    void 이어진_회원은_게시판_담당_후보에서_자격_미달이고_지정할_수_없다() {
        long target = operatorWith();
        long user = memberOf(target, true);
        AdminActor actor = manager();
        service.link(actor, Long.toString(target), link(user));
        long board = board();
        assertThat(boardStaff.candidates(Long.toString(board), Long.toString(user))).filteredOn(c -> c.memberId().equals(Long.toString(user)))
                .extracting(c -> c.ineligible()).containsExactly("OPERATOR_ACCOUNT");
        assertThat(code(() -> boardStaff.appoint(actor, Long.toString(board), new BoardStaffAppointRequest(Long.toString(user), "STAFF"))))
                .isEqualTo("BOARD_STAFF_INELIGIBLE");
    }

    @Test
    void 이어진_동안은_강제_탈퇴가_409이다() {
        long target = operatorWith();
        long user = memberOf(target, true);
        AdminActor actor = manager();
        service.link(actor, Long.toString(target), link(user));
        assertThat(code(() -> withdrawals.force(actor, Long.toString(user), new MemberWithdrawalAdminRequest.Force("POLICY_VIOLATION", "운영 정책 위반 시험"))))
                .isEqualTo(OperatorAccountLinkedException.CODE);
        assertThat(memberFixture.status(user)).isEqualTo("ACTIVE");
        List<Long> left = jdbc.queryForList("SELECT id FROM member_withdrawals WHERE user_id = ?", Long.class, user);
        assertThat(left).isEmpty();
    }
}
