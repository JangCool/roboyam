package com.community.operator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 운영자 계정(관리자 앱에 로그인하는 사람) — V7__operators_roles.sql 의 {@code operators}. 회원({@code users})과 <b>별도 테이블</b>이다.
 *
 * <p>상태는 네 개다 — 승인됨({@code approved}) · 잠금({@code locked}) · 만료 표시({@code expired}) · 사용 중({@code enabled}) — 와 계정 기간.
 * 로그인은 <b>승인됨 · 잠기지 않음 · 만료 표시 없음 · 사용 중 · 기간 안</b>일 때만 된다({@link #canLogin}).
 * 루트(최고 관리자)는 설치 마법사가 만든 계정 하나({@code is_root}, 유일 인덱스)로 어떤 변경도 거절된다 — 모든 권한을 가진다.
 *
 * <p>{@link DynamicUpdate}: 고친 열만 UPDATE 한다 — 로그인 시각 갱신이 다른 요청이 바꾼 상태 열을 낡은 값으로 덮어쓰지 않게 한다.
 * {@link #toString()} 은 값을 싣지 않는다(기본 {@code Object.toString}) — 이메일·비밀번호 해시가 로그에 새지 않게.
 */
@Entity
@Table(name = "operators")
@DynamicUpdate
public class OperatorEntity {

    @Id
    private Long id;

    /** 소문자로 정규화해 저장. 로그인 아이디라 바꿀 수 없다 */
    private String email;

    private String operatorName;

    private String passwordHash;

    @Column(name = "is_root")
    private boolean root;

    private boolean approved;
    private boolean locked;
    private boolean expired;
    private boolean enabled;
    private Instant accountStartAt;
    private Instant accountEndAt;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected OperatorEntity() {
    }

    /** 설치 마법사가 만드는 <b>루트 계정</b>(최고 관리자) — 승인됨·사용 중, 기간 제한 없음 */
    public static OperatorEntity createRoot(long id, String email, String operatorName, String passwordHash, Instant now) {
        OperatorEntity operator = create(id, email, operatorName, passwordHash, true, now);
        operator.root = true;
        return operator;
    }

    /** 최고 관리자(또는 권한 있는 운영자)가 바로 만드는 계정 — 승인된 상태로 만들어진다 */
    public static OperatorEntity createApproved(long id, String email, String operatorName, String passwordHash, Instant now) {
        return create(id, email, operatorName, passwordHash, true, now);
    }

    private static OperatorEntity create(long id, String email, String operatorName, String passwordHash, boolean approved, Instant now) {
        OperatorEntity operator = new OperatorEntity();
        operator.id = id;
        operator.email = email;
        operator.operatorName = operatorName;
        operator.passwordHash = passwordHash;
        operator.approved = approved;
        operator.enabled = true;
        operator.createdAt = now;
        operator.updatedAt = now;
        return operator;
    }

    /** 이름·계정 기간을 고친다 */
    public void updateProfile(String operatorName, Instant accountStartAt, Instant accountEndAt, Instant now) {
        this.operatorName = operatorName;
        this.accountStartAt = accountStartAt;
        this.accountEndAt = accountEndAt;
        this.updatedAt = now;
    }

    /** 운영자 이름(닉네임)만 바꾼다 — 계정 기간은 그대로 둔다(내 정보에서 본인이 바꿀 때) */
    public void rename(String operatorName, Instant now) {
        this.operatorName = operatorName;
        this.updatedAt = now;
    }

    /** 상태를 바꾼다 — 값 그대로다(예: {@code locked=true} 는 잠금). 보내지 않은(null) 칸은 그대로 둔다 */
    public void changeStatus(Boolean approved, Boolean locked, Boolean expired, Boolean enabled, Instant now) {
        if (approved != null) {
            this.approved = approved;
        }
        if (locked != null) {
            this.locked = locked;
        }
        if (expired != null) {
            this.expired = expired;
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
        this.updatedAt = now;
    }

    /** 비밀번호 해시를 바꾼다(비밀번호 재설정·변경) — 새 해시는 호출하는 쪽이 만든다. 평문을 받지 않는다 */
    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    /** 로그인했다 — 마지막 로그인 시각만 고친다 */
    public void markLoggedIn(Instant now) {
        this.lastLoginAt = now;
    }

    /** 계정 기간이 지금 시각 기준으로 끝났거나 아직 시작 전인가 — 만료 표시({@code expired})와 별개로 판정한 값 */
    public boolean isPeriodOver(Instant now) {
        return (accountStartAt != null && accountStartAt.isAfter(now)) || (accountEndAt != null && accountEndAt.isBefore(now));
    }

    /** 막혀 있는가 — 승인된 계정이 잠김·만료·사용 중지·기간 밖인 경우(운영자 목록의 「막힘」 탭) */
    public boolean isBlocked(Instant now) {
        return approved && (locked || expired || !enabled || isPeriodOver(now));
    }

    /** 로그인할 수 있는가 — 승인됨 · 잠기지 않음 · 만료 표시 없음 · 사용 중 · 기간 안 */
    public boolean canLogin(Instant now) {
        return approved && !locked && !expired && enabled && !isPeriodOver(now);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getOperatorName() {
        return operatorName;
    }

    /** 비밀번호 해시. 응답·로그에 싣지 않는다 */
    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isRoot() {
        return root;
    }

    public boolean isApproved() {
        return approved;
    }

    public boolean isLocked() {
        return locked;
    }

    public boolean isExpired() {
        return expired;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getAccountStartAt() {
        return accountStartAt;
    }

    public Instant getAccountEndAt() {
        return accountEndAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
