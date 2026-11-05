package com.community.operator.service;

import com.community.operator.repository.master.OperatorMemberAccountStore;
import com.community.operator.repository.master.OperatorMemberAccountStore.Link;
import com.community.permission.entity.Permission;
import com.community.role.repository.master.RoleRepositoryQDSL;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 회원 계정의 힘 판정(docs/features/operator-member-account-contract.md 정책 6·7) — api(게시판 관리 · 운영자 표시 · 로그인 정보 · 탈퇴 막기 · 차단 보호)와
 * admin(연결 화면 · 운영자 목록 딱지 · 회원 상세 · 게시판 담당 자격 · 강제 탈퇴 막기), 로보티콘({@code EmoticonGuard} — 다른 브랜치)이 같이 쓴다.
 *
 * <ul>
 *   <li><b>힘이 살아 있는 조건</b>(정책 6): 연결 있음 + 운영자 {@code canLogin(now)}(승인 · 안 잠김 · 만료 표시 없음 · 사용 중 · 기간 안) + 회원 상태 정상(정지 · 영구 정지 ·
 *       탈퇴 진행이 아님). <b>요청마다 DB 에서 판정</b>한다(캐시 없음 — 관리자 서버 {@code AdminActorService#load} 와 같은 규칙).</li>
 *   <li><b>권한 거울</b>(정책 7): 최고 관리자는 모든 권한, 아니면 역할 권한의 합집합({@code RoleRepositoryQDSL#permissionCodesOfOperator}) — 역할이 없으면 게시판 관리 힘이 없다
 *       (표시 · 팩은 그대로).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class OperatorMemberAccountService {

    /** 이어져 있지만 힘이 멈춘 까닭 — 운영자 계정을 쓸 수 없음 */
    public static final String PAUSED_OPERATOR = "OPERATOR_UNAVAILABLE";
    /** 이어져 있지만 힘이 멈춘 까닭 — 회원 계정 정지 중(정지 · 영구 정지 · 탈퇴 진행) */
    public static final String PAUSED_MEMBER = "MEMBER_SUSPENDED";

    /**
     * 힘이 살아 있는 운영자 회원 계정.
     *
     * @param permissions 그 운영자의 관리자 앱 권한 코드(최고 관리자는 전부)
     */
    public record ActiveOperatorAccount(long operatorId, String operatorName, long userId, boolean root, Set<String> permissions) {

        public ActiveOperatorAccount {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }

        /** 그 관리자 권한이 있나(최고 관리자는 늘) */
        public boolean can(Permission permission) {
            return root || permissions.contains(permission.code());
        }
    }

    private final OperatorMemberAccountStore store;
    private final RoleRepositoryQDSL roles;
    private final Clock clock;

    public OperatorMemberAccountService(OperatorMemberAccountStore store, RoleRepositoryQDSL roles, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.roles = roles;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 지금(DB 시각 정밀도) */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 힘이 살아 있으면(정책 6) 그 운영자와 권한 — 아니면 빈 값 */
    public Optional<ActiveOperatorAccount> activeOperator(long userId) {
        return store.linkOfUser(userId, now()).filter(Link::active).map(this::active);
    }

    /** 힘이 살아 있는 운영자 회원 계정인가(로보티콘 모든 팩 · 운영자 표시) — 권한은 읽지 않는다 */
    public boolean isActiveOperatorAccount(long userId) {
        return store.linkOfUser(userId, now()).map(Link::active).orElse(false);
    }

    /** 이 회원들 중 힘이 살아 있는 운영자 회원 계정 — 목록 작성자 표시용(한 질의) */
    public Set<Long> activeOperatorUserIds(Collection<Long> userIds) {
        return store.activeUserIds(userIds == null ? Set.of() : userIds, now());
    }

    /** 힘이 살아 있는 운영자 회원 계정 전부(운영자 수만큼 — 처리 대상 보호) */
    public Set<Long> allActiveOperatorUserIds() {
        return store.activeUserIds(null, now());
    }

    /** 이어져 있나(힘과 상관없이 — 탈퇴 막기 정책 12 · 게시판 담당 자격 정책 4) */
    public boolean isLinked(long userId) {
        return store.linked(userId);
    }

    /** 이 회원들 중 이어진 회원(힘과 상관없이) */
    public Set<Long> linkedUserIds(Collection<Long> userIds) {
        return store.linkedUserIds(userIds);
    }

    /** 그 회원의 연결 + 지금 판정(관리자 회원 상세) */
    public Optional<Link> linkOfUser(long userId) {
        return store.linkOfUser(userId, now());
    }

    /** 그 운영자의 연결 + 지금 판정(관리자 연결 화면) */
    public Optional<Link> linkOfOperator(long operatorId) {
        return store.linkOfOperator(operatorId, now());
    }

    /** 운영자 여럿의 연결(관리자 운영자 목록 딱지) */
    public Map<Long, Link> linksOfOperators(Collection<Long> operatorIds) {
        return store.linksOfOperators(operatorIds, now());
    }

    /** 멈춘 까닭(살아 있으면 빈 목록) — 운영자 쪽이 먼저 */
    public static java.util.List<String> pausedReasons(Link link) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (!link.operatorOk()) {
            out.add(PAUSED_OPERATOR);
        }
        if (!link.memberOk()) {
            out.add(PAUSED_MEMBER);
        }
        return java.util.List.copyOf(out);
    }

    /** 그 운영자의 관리자 앱 권한(최고 관리자는 전부) — 연결 · 멈춤과 상관없는 값 */
    public Set<String> permissionsOf(long operatorId, boolean root) {
        return root ? Set.copyOf(Permission.allCodes()) : roles.permissionCodesOfOperator(operatorId);
    }

    private ActiveOperatorAccount active(Link link) {
        return new ActiveOperatorAccount(link.operatorId(), link.operatorName(), link.userId(), link.root(), permissionsOf(link.operatorId(), link.root()));
    }
}
