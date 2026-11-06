package com.community.pointshop.repository.master;

import static com.community.pointshop.repository.master.PointShopItemStore.marks;
import static com.community.pointshop.repository.master.PointShopItemStore.ts;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 포인트 상점이 보는 회원 쪽 값 — 자격(상태·이메일 인증), 지금 잔액, 고른 아바타({@code profiles.profile_image}). {@code com.community.pointshop.service} 만 부른다.
 * JDBC 인 이유는 {@link PointShopItemStore} 와 같다(구매·환불·아바타 고르기와 한 트랜잭션, 시험이 스프링 문맥 없이 조립). 회원·프로필 엔티티는 다른 업무의 것이라 고치지 않는다.
 */
@Repository
public class PointShopMemberStore {

    /** 자격 판정 재료 — {@code status} 는 users.status, {@code gone} 은 탈퇴·삭제 */
    public record MemberState(String status, boolean emailVerified, boolean gone) {

        /** 정지·영구 정지·탈퇴 진행 중(새 구매·아바타 변경을 막는다 — member-withdrawal-contract) */
        public boolean suspended() {
            return "SUSPENDED".equals(status) || "BANNED".equals(status) || "WITHDRAWING".equals(status);
        }
    }

    private final JdbcTemplate jdbc;

    public PointShopMemberStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<MemberState> state(long userId) {
        return jdbc.query("SELECT status, email_verified_at IS NOT NULL, status = 'WITHDRAWN' OR deleted_at IS NOT NULL FROM users WHERE id = ?",
                (rs, i) -> new MemberState(rs.getString(1), rs.getBoolean(2), rs.getBoolean(3)), userId).stream().findFirst();
    }

    /** 지금 포인트 합계(무상 + 유상 — 잔액 행이 없으면 0) */
    public long balance(long userId) {
        List<Long> rows = jdbc.queryForList("SELECT balance FROM point_balances WHERE user_id = ?", Long.class, userId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    /** 고른 프로필 이미지 값(없거나 프로필이 없으면 null) */
    public String profileImage(long userId) {
        List<String> rows = jdbc.query("SELECT profile_image FROM profiles WHERE user_id = ?", (rs, i) -> rs.getString(1), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 여러 회원의 프로필 이미지 값(프로필 행이 없는 회원은 빠진다) */
    public Map<Long, String> profileImages(Collection<Long> userIds) {
        Map<Long, String> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT user_id, profile_image FROM profiles WHERE user_id IN (" + marks(userIds.size()) + ")", rs -> {
            out.put(rs.getLong(1), rs.getString(2));
        }, new ArrayList<>(userIds).toArray());
        return out;
    }

    /** 프로필 이미지를 바꾼다 — 프로필 행이 있으면 true */
    public boolean setProfileImage(long userId, String value, Instant now) {
        return jdbc.update("UPDATE profiles SET profile_image = ?, updated_at = ? WHERE user_id = ?", value, ts(now), userId) == 1;
    }

    /** 프로필 아바타 칸 — 고른 값·마지막 정지 아바타(V157) */
    public record AvatarState(String profileImage, String lastStillAvatar) {
    }

    /** 프로필 행을 잠그고 아바타 칸을 읽는다(올리기 신청·심사·하나만 규칙이 프로필 행 → 신청 행 차례로 잠근다). 프로필 행이 없으면 빈 값 */
    public Optional<AvatarState> lockAvatar(long userId) {
        return jdbc.query("SELECT profile_image, last_still_avatar FROM profiles WHERE user_id = ? FOR UPDATE", (rs, i) -> new AvatarState(rs.getString(1), rs.getString(2)),
                userId).stream().findFirst();
    }

    /** 아바타 칸(잠그지 않는다) — 프로필 행이 없으면 빈 값 */
    public Optional<AvatarState> avatarState(long userId) {
        return jdbc.query("SELECT profile_image, last_still_avatar FROM profiles WHERE user_id = ?", (rs, i) -> new AvatarState(rs.getString(1), rs.getString(2)), userId)
                .stream().findFirst();
    }

    /**
     * 프로필 아바타를 바꾼다 — 정지 아바타면 마지막 정지 아바타도 그것으로(commerce-motion-avatar-contract 규칙 3). 프로필 행이 있으면 true
     *
     * @param lastStill 새 마지막 정지 아바타 ID(움직이는 아바타를 골랐으면 null — 앞 값을 그대로 둔다)
     */
    public boolean setAvatar(long userId, String profileImage, String lastStill, Instant now) {
        return jdbc.update("UPDATE profiles SET profile_image = ?, last_still_avatar = coalesce(?, last_still_avatar), updated_at = ? WHERE user_id = ?", profileImage,
                lastStill, ts(now), userId) == 1;
    }

    /** 그 값을 쓰고 있을 때만 비운다(예외 환불한 아바타 → 기본 아바타) — 비웠으면 true */
    public boolean clearProfileImageIf(long userId, String value, Instant now) {
        return jdbc.update("UPDATE profiles SET profile_image = NULL, updated_at = ? WHERE user_id = ? AND profile_image = ?", ts(now), userId, value) == 1;
    }
}
