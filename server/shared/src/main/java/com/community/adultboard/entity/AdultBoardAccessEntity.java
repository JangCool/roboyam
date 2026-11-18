package com.community.adultboard.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 회원의 「19금 게시판 접근」 설정·핀번호 — V254 의 {@code member_adult_board_access}(docs/features/adult-board-pin-contract.md). 회원마다 최대 한 행이고
 * <b>행이 없으면 꺼짐</b>이다.
 *
 * <ul>
 *   <li>켜진 상태에는 늘 핀 해시와 정한 시각이 있다(핀 없이 켜진 상태는 없다 — 표의 CHECK). 끄면 해시·정한 시각·틀린 횟수·잠금을 지우고 다시 묻기 기준만 남긴다.</li>
 *   <li>{@code pinSetAt} 은 로그인 세션의 핀 확인에 같이 담긴다 — 값이 달라지면(핀 바꾸기·끄기·다시 켜기) 다른 기기의 확인이 무효가 된다.</li>
 *   <li>틀린 횟수·잠금은 회원 단위다. 상태 변경은 행을 쓰기 잠금으로 읽은 트랜잭션 안에서 이 메서드들로만 한다.</li>
 * </ul>
 * 핀 해시는 {@code toString}·로그·응답에 싣지 않는다.
 */
@Entity
@Table(name = "member_adult_board_access")
@DynamicUpdate
public class AdultBoardAccessEntity {

    /** 마지막으로 성인 전용 게시판을 본 뒤 유지 시간이 지나면 다시 묻는다(보는 동안 연장) — 기본 */
    public static final String RELOCK_IDLE = "IDLE";
    /** 핀번호를 넣은 뒤 유지 시간이 지나면 보는 중이어도 다시 묻는다 */
    public static final String RELOCK_FIXED = "FIXED";

    @Id
    private Long userId;

    private boolean enabled;
    private String pinHash;
    private Instant pinSetAt;
    private String relockMode;
    private int failedCount;
    private Instant lockedUntil;
    private Instant createdAt;
    private Instant updatedAt;

    protected AdultBoardAccessEntity() {
    }

    /** 꺼진 새 행(다시 묻기 기준은 기본 {@code IDLE}) — 켜기({@link #enable})나 기준 바꾸기({@link #changeRelockMode})가 뒤따른다 */
    public static AdultBoardAccessEntity create(long userId, Instant now) {
        AdultBoardAccessEntity access = new AdultBoardAccessEntity();
        access.userId = userId;
        access.enabled = false;
        access.relockMode = RELOCK_IDLE;
        access.failedCount = 0;
        access.createdAt = now;
        access.updatedAt = now;
        return access;
    }

    /** 접근을 켜거나 핀번호를 새로 정한다 — 틀린 횟수·잠금도 푼다. {@code now} 가 새 {@code pinSetAt} 이다(다른 기기의 핀 확인이 무효가 된다) */
    public void enable(String pinHash, Instant now) {
        this.enabled = true;
        this.pinHash = pinHash;
        this.pinSetAt = now;
        this.failedCount = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    /** 접근을 끈다 — 핀 해시·정한 시각·틀린 횟수·잠금을 지운다(다시 묻기 기준은 남긴다) */
    public void disable(Instant now) {
        this.enabled = false;
        this.pinHash = null;
        this.pinSetAt = null;
        this.failedCount = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    /** @param relockMode 검사를 마친 값({@link #RELOCK_IDLE}·{@link #RELOCK_FIXED}) */
    public void changeRelockMode(String relockMode, Instant now) {
        if (relockMode.equals(this.relockMode)) {
            return;
        }
        this.relockMode = relockMode;
        this.updatedAt = now;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** 잠금이 풀렸으면 횟수를 처음부터 다시 센다 */
    public void clearIfLockExpired(Instant now) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            failedCount = 0;
            lockedUntil = null;
            updatedAt = now;
        }
    }

    /** 틀린 시도를 하나 더하고, 한도에 닿으면 그 순간부터 잠근다. 이번 시도로 잠겼으면 true */
    public boolean fail(Instant now, int maxFailures, long lockSeconds) {
        failedCount++;
        updatedAt = now;
        if (failedCount >= maxFailures) {
            lockedUntil = now.plusSeconds(lockSeconds);
            return true;
        }
        return false;
    }

    /** 핀번호를 맞혔다 — 틀린 횟수와 잠금을 지운다 */
    public void resetFailures(Instant now) {
        if (failedCount == 0 && lockedUntil == null) {
            return;
        }
        failedCount = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 핀번호 해시(꺼져 있으면 null) — 견주기에만 쓴다 */
    public String getPinHash() {
        return pinHash;
    }

    /** 핀번호를 정한 때(꺼져 있으면 null) */
    public Instant getPinSetAt() {
        return pinSetAt;
    }

    public String getRelockMode() {
        return relockMode;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
