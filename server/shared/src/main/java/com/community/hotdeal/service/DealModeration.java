package com.community.hotdeal.service;

import com.community.board.entity.BoardStaffEntity;
import com.community.hotdeal.entity.PostDealEntity;
import com.community.hotdeal.service.DealEndService.Outcome;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진의 핫딜 종료·재개 — 누가 할 수 있는지(hotdeal-contract 「사용자 결정」 담당 · 「권한·정책」)를 판정한 뒤 {@link DealEndService} 의 운영진 길을 부른다.
 *
 * <ul>
 *   <li><b>담당</b>: 운영자 {@code hotdeal.manage} 는 모든 핫딜 게시판, <b>게시판 매니저·스텝</b>(회원 — {@code board_staff})은 <b>그 게시판</b> 글만. 다른 게시판의 매니저·스텝은 안 된다.
 *       정지·영구 정지·탈퇴 회원({@code users.status} 가 ACTIVE 가 아님)도 안 된다. 담당 범위 기능을 새로 만들지 않는다(계약). 권한 코드 판정은 부르는 쪽(관리자 서버
 *       {@code AdminActor})이 넘긴다. 판정은 부를 때마다 다시 한다(지정이 풀리면 바로 막힌다).</li>
 *   <li><b>대상</b>: 지우지 않은 글이고 지금 핫딜 게시판({@code boards.kind = DEAL})에 있으며 딜 줄이 있어야 한다(아니면 {@link Result#NOT_FOUND}).</li>
 *   <li><b>상태</b>: 종료는 진행·의심·기간 지남 → ENDED MANAGER(이미 종료면 {@link Result#STATE_CHANGED}), 재개는 ENDED 만. 보상·작성자 알림은 {@link DealEndService}.</li>
 *   <li>{@code ended_by_id} 는 운영자 ID 또는 매니저·스텝 회원 ID, {@code ended_by_role} 이 어느 쪽인지(OPERATOR · BOARD_MANAGER · BOARD_STAFF) 남긴다 — 관리자 상세
 *       {@code endedByRole}·{@code endedByName}.</li>
 * </ul>
 * 감사 기록·오류 코드는 부르는 쪽의 몫이다 — 운영자 길은 관리자 서버({@code HotdealModerationService}), 매니저·스텝 길은 공개 사이트 서버({@code DealStateService#moderateEnd}).
 */
@Service
public class DealModeration {

    private final DealEndService ends;
    private final JdbcTemplate jdbc;

    public DealModeration(DealEndService ends, DataSource dataSource) {
        this.ends = ends;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 누가 하는가 — 운영자({@link #operator}) 또는 게시판 매니저·스텝 회원({@link #boardMember}).
     *
     * @param hotdealManage 운영자가 {@code hotdeal.manage} 를 가졌다(루트 포함)
     */
    public record Actor(Long operatorId, boolean hotdealManage, Long memberId) {

        public static Actor operator(long operatorId, boolean hotdealManage) {
            return new Actor(operatorId, hotdealManage, null);
        }

        public static Actor boardMember(long memberId) {
            return new Actor(null, false, memberId);
        }

        long actorId() {
            return operatorId != null ? operatorId : memberId;
        }
    }

    /** 결과 */
    public enum Result {
        CHANGED, NOT_FOUND, FORBIDDEN, STATE_CHANGED
    }

    /** 판정 — 통과면 {@code result} null, {@code role} = 딜 줄에 남길 종류 */
    private record Gate(Result result, String role) {
    }

    /** 운영진 종료 — 사유(≤200자, 검사는 부르는 쪽)는 작성자 알림 {@code DEAL_MANAGER_ENDED} 로 간다 */
    @Transactional
    public Result end(long postId, Actor actor, String reason) {
        Gate gate = gate(postId, actor);
        if (gate.result() != null) {
            return gate.result();
        }
        return of(ends.endByManager(postId, actor.actorId(), gate.role(), reason));
    }

    /** 운영진 재개 — 의심·keep·종료 요청 리셋, 기간이 지났으면 작성자에게 「마감을 고쳐 주세요」 */
    @Transactional
    public Result reopen(long postId, Actor actor) {
        Gate gate = gate(postId, actor);
        if (gate.result() != null) {
            return gate.result();
        }
        return of(ends.reopenByManager(postId));
    }

    /** 이 사람이 이 글의 딜을 종료·재개할 수 있는가(대상이 없으면 false) */
    public boolean allowed(long postId, Actor actor) {
        return gate(postId, actor).result() == null;
    }

    /**
     * 그 게시판에서 이 회원이 맡은 자리 — {@link BoardStaffEntity#MANAGER} · {@link BoardStaffEntity#STAFF}, 매니저·스텝이 아니거나 활성 회원이 아니면(정지·영구 정지·탈퇴) null.
     * 글 상세 {@code dealModeration}·매니저·스텝 API 의 감사 {@code role} 이 쓴다.
     */
    public String boardRole(long boardId, long memberId) {
        List<String> roles = jdbc.queryForList("SELECT s.role FROM board_staff s JOIN users u ON u.id = s.user_id WHERE s.board_id = ? AND s.user_id = ? AND u.status = 'ACTIVE'",
                String.class, boardId, memberId);
        return roles.isEmpty() ? null : roles.get(0);
    }

    /** 대상 → 담당 */
    private Gate gate(long postId, Actor actor) {
        List<Long> boards = jdbc.queryForList("SELECT p.board_id FROM posts p JOIN post_deals d ON d.post_id = p.id JOIN boards b ON b.id = p.board_id "
                + "WHERE p.id = ? AND b.kind = 'DEAL' AND p.deleted_at IS NULL AND p.status = 'PUBLISHED' AND (p.delete_scheduled_at IS NULL OR p.delete_scheduled_at > now())",
                Long.class, postId);
        if (boards.isEmpty()) {
            return new Gate(Result.NOT_FOUND, null);
        }
        if (actor.operatorId() != null) {
            return actor.hotdealManage() ? new Gate(null, PostDealEntity.ROLE_OPERATOR) : new Gate(Result.FORBIDDEN, null);
        }
        if (actor.memberId() == null) {
            return new Gate(Result.FORBIDDEN, null);
        }
        String role = boardRole(boards.get(0), actor.memberId());
        if (role == null) {
            return new Gate(Result.FORBIDDEN, null);
        }
        return new Gate(null, BoardStaffEntity.MANAGER.equals(role) ? PostDealEntity.ROLE_BOARD_MANAGER : PostDealEntity.ROLE_BOARD_STAFF);
    }

    private static Result of(Outcome outcome) {
        return switch (outcome) {
            case CHANGED -> Result.CHANGED;
            case NOT_FOUND -> Result.NOT_FOUND;
            case STATE_CHANGED -> Result.STATE_CHANGED;
        };
    }
}
