package com.community.board.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.request.BoardStaffAppointRequest;
import com.community.board.dto.response.BoardStaffCandidateView;
import com.community.board.dto.response.BoardStaffMemberView;
import com.community.board.entity.BoardEntity;
import com.community.board.entity.BoardStaffEntity;
import com.community.board.entity.BoardStaffId;
import com.community.board.exception.BoardNotFoundException;
import com.community.board.exception.BoardStaffIneligibleException;
import com.community.board.exception.BoardStaffKindException;
import com.community.board.exception.BoardStaffLimitException;
import com.community.board.repository.master.BoardRepository;
import com.community.board.repository.master.BoardStaffQueries;
import com.community.board.repository.master.BoardStaffRepository;
import com.community.member.exception.MemberNotFoundException;
import com.community.member.service.MemberMasking;
import com.community.permission.dto.AdminActor;
import com.community.user.entity.UserStatus;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 매니저·스텝 조회·지정·해제·후보 검색 (docs/domain/operator-permissions.md 「게시판 매니저 · 스텝」).
 *
 * <ul>
 *   <li>지정 자격(지금 회원 표로 판정할 수 있는 것): 탈퇴 아님 · 상태 정상(정지·영구 정지 불가) · 이메일 인증 · 첫 등급(새싹) 아님 · 맡은 게시판 5개 미만 · 이 게시판에 아직 없음.
 *       <b>사업자 계정 조건은 회원 표에 사업자 칸이 없어 건너뛴다</b>(사업자 기능 때 더한다).</li>
 *   <li>일반 게시판만 지정할 수 있다(핫딜·직접홍보·소모임은 409 {@code BOARD_STAFF_KIND}).</li>
 *   <li>인원: 매니저 = 게시판 값 ?? 기본값(managerMax), 스텝 = 기본값(staffMax). 넘으면 409 {@code BOARD_STAFF_LIMIT}.</li>
 *   <li>같은 게시판의 동시 지정은 게시판 행 잠금, 같은 회원의 동시 지정(다른 게시판)은 회원 행 잠금으로 줄 세운다(5개 한도가 새지 않게).</li>
 * </ul>
 */
@Service
public class BoardStaffService {

    private static final Logger log = LoggerFactory.getLogger(BoardStaffService.class);
    private static final int CANDIDATE_LIMIT = 20;

    private final BoardRepository boards;
    private final BoardStaffRepository staff;
    private final BoardStaffQueries queries;
    private final UserRepository users;
    private final BoardViews views;
    private final AuditLogService audit;

    public BoardStaffService(BoardRepository boards, BoardStaffRepository staff, BoardStaffQueries queries, UserRepository users, BoardViews views,
                             AuditLogService audit) {
        this.boards = boards;
        this.staff = staff;
        this.queries = queries;
        this.users = users;
        this.views = views;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<BoardStaffMemberView> list(String boardId) {
        return viewsOf(find(boardId).getId());
    }

    @Transactional
    public List<BoardStaffMemberView> appoint(AdminActor actor, String boardId, BoardStaffAppointRequest request) {
        BoardEntity board = lock(boardId);
        String role = request == null ? null : request.role();
        if (role == null || !BoardFields.STAFF_ROLES.contains(role)) {
            throw ValidationException.of("role", new MessageRef("board.staff.role.invalid"));
        }
        Long userId = BoardRules.parseId(request.memberId());
        if (userId == null || users.findWithLockById(userId).isEmpty()) {
            throw new MemberNotFoundException();
        }
        BoardStaffQueries.MemberFacts member = queries.member(userId);
        if (member == null) {
            throw new MemberNotFoundException();
        }
        if (!BoardFields.KIND_GENERAL.equals(board.getKind())) {
            throw new BoardStaffKindException();
        }
        boolean assigned = staff.existsByBoardIdAndUserId(board.getId(), userId);
        if (ineligible(assigned, member.status(), member.emailVerified(), member.gradeLevel(), staff.countByUserId(userId)) != null) {
            throw new BoardStaffIneligibleException();
        }
        boolean manager = BoardStaffEntity.MANAGER.equals(role);
        int max = manager ? (board.getManagerMax() != null ? board.getManagerMax() : views.defaultsRow().values().managerMax())
                : views.defaultsRow().values().staffMax();
        if (staff.countByBoardIdAndRole(board.getId(), role) >= max) {
            throw new BoardStaffLimitException(manager, max);
        }
        staff.persist(BoardStaffEntity.byOperator(board.getId(), userId, role, actor.operatorId(), Instant.now()));
        staff.flush();
        audit.record(event(actor, AuditActions.BOARD_STAFF_APPOINT, board.getId()).with("added", List.of(role + " " + userId)));
        log.info("게시판 담당을 지정했다 boardId={} userId={} role={} by operatorId={}", board.getId(), userId, role, actor.operatorId());
        return viewsOf(board.getId());
    }

    @Transactional
    public List<BoardStaffMemberView> remove(AdminActor actor, String boardId, String memberId) {
        BoardEntity board = lock(boardId);
        Long userId = BoardRules.parseId(memberId);
        BoardStaffEntity row = userId == null ? null : staff.findById(new BoardStaffId(board.getId(), userId)).orElse(null);
        if (row != null) {
            String role = row.getRole();
            staff.delete(row);
            staff.flush();
            audit.record(event(actor, AuditActions.BOARD_STAFF_REMOVE, board.getId()).with("deleted", List.of(role + " " + userId)));
            log.info("게시판 담당을 해제했다 boardId={} userId={} by operatorId={}", board.getId(), userId, actor.operatorId());
        }
        return viewsOf(board.getId());
    }

    /** 후보 회원 — 닉네임 일부·이메일 전체·회원 ID 로 찾는다. 자격 미달은 사유와 함께 */
    @Transactional(readOnly = true)
    public List<BoardStaffCandidateView> candidates(String boardId, String keyword) {
        BoardEntity board = find(boardId);
        List<BoardStaffQueries.CandidateRow> rows = queries.candidates(keyword, CANDIDATE_LIMIT);
        Map<Long, Long> counts = queries.boardCountsOf(rows.stream().map(BoardStaffQueries.CandidateRow::userId).toList());
        Set<Long> assigned = staff.findByBoardId(board.getId()).stream().map(BoardStaffEntity::getUserId).collect(Collectors.toSet());
        return rows.stream().map(row -> {
            long count = counts.getOrDefault(row.userId(), 0L);
            return new BoardStaffCandidateView(Long.toString(row.userId()), row.nickname(), MemberMasking.email(row.email()), row.gradeKey(), count,
                    ineligible(assigned.contains(row.userId()), row.status(), row.emailVerified(), row.gradeLevel(), count));
        }).toList();
    }

    /** 자격 미달 사유(목업과 같은 차례) — null 이면 지정할 수 있다. 사업자(BUSINESS) 판정은 칸이 없어 하지 않는다 */
    private static String ineligible(boolean assigned, UserStatus status, boolean emailVerified, int gradeLevel, long boardCount) {
        if (assigned) {
            return "ALREADY_ASSIGNED";
        }
        if (status != UserStatus.ACTIVE) {
            return "SUSPENDED";
        }
        if (!emailVerified) {
            return "EMAIL_UNVERIFIED";
        }
        if (gradeLevel <= 1) {
            return "GRADE_TOO_LOW";
        }
        if (boardCount >= BoardFields.MEMBER_BOARD_MAX) {
            return "BOARD_LIMIT";
        }
        return null;
    }

    private List<BoardStaffMemberView> viewsOf(long boardId) {
        return queries.staffOf(boardId).stream()
                .map(row -> new BoardStaffMemberView(Long.toString(row.userId()), row.nickname(), row.gradeKey(), row.role(), row.appointedAt(), row.appointedBy()))
                .toList();
    }

    private BoardEntity find(String boardId) {
        Long id = BoardRules.parseId(boardId);
        BoardEntity board = id == null ? null : boards.findById(id).orElse(null);
        if (board == null) {
            throw new BoardNotFoundException();
        }
        return board;
    }

    private BoardEntity lock(String boardId) {
        Long id = BoardRules.parseId(boardId);
        BoardEntity board = id == null ? null : boards.findWithLockById(id).orElse(null);
        if (board == null) {
            throw new BoardNotFoundException();
        }
        return board;
    }

    private static AuditEvent event(AdminActor actor, String action, long boardId) {
        return AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target("BOARD", Long.toString(boardId));
    }
}
