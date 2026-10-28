package com.community.groupbuy.repository.master;

import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 공구가 보는 회원 쪽 값 — 참여 자격(상태·이메일 인증)과 회원별 어드바이저리 잠금. 회원 엔티티는 다른 업무의 것이라 고치지 않는다(포인트 상점 {@code PointShopMemberStore} 와 같은 모양).
 */
@Repository
public class GroupBuyMemberStore {

    /** 자격 판정 재료 — {@code status} 는 users.status, {@code gone} 은 탈퇴·삭제 */
    public record MemberState(String status, boolean emailVerified, boolean gone) {

        public boolean suspended() {
            return "SUSPENDED".equals(status) || "BANNED".equals(status);
        }
    }

    private final JdbcTemplate jdbc;

    public GroupBuyMemberStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<MemberState> state(long userId) {
        return jdbc.query("SELECT status, email_verified_at IS NOT NULL, status = 'WITHDRAWN' OR deleted_at IS NOT NULL FROM users WHERE id = ?",
                (rs, i) -> new MemberState(rs.getString(1), rs.getBoolean(2), rs.getBoolean(3)), userId).stream().findFirst();
    }

    /**
     * (공구, 회원) 어드바이저리 잠금 — 트랜잭션 범위, <b>모든 행 잠금보다 먼저</b>(결정 37: A → G → O → X). 1인 한도·중복 주문 검사를 줄 세운다(10-4).
     * 키 = {@code hashtext('gb:<공구>:<회원>')}.
     */
    public void lockMemberInGroupBuy(long groupBuyId, long userId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", "gb:" + groupBuyId + ":" + userId);
    }
}
