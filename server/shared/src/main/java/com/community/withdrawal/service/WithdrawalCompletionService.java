package com.community.withdrawal.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.business.service.BusinessWithdrawal;
import com.community.hotdeal.service.DealWithdrawal;
import com.community.media.service.ImageStorage;
import com.community.point.service.PointService;
import com.community.withdrawal.config.WithdrawalProperties;
import com.community.withdrawal.repository.master.MemberWithdrawalStore;
import com.community.withdrawal.repository.master.WithdrawalEraseStore;
import com.community.withdrawal.repository.master.WithdrawalQueries;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 탈퇴 <b>완료(익명화)</b> — 한 트랜잭션(commerce.md 17-2, 분석 문서 4절 「완료 때」). 신청 즉시 완료(남은 일 없음 — 회원 신청·운영자 강제 탈퇴)와 배치 자동 완료가
 * <b>같은 코드</b>를 쓴다. 감사 {@code MEMBER_WITHDRAW} 의 행위자는 {@link Actor}(회원 · SYSTEM · 운영자) 가 정한다.
 *
 * <ol>
 *   <li>내가 모임장인 소모임은 신청 때 저장한 선택(넘기기·해산·자동 — 대상이 멤버가 아니거나 신청 뒤 새로 모임장이 됐으면 자동)으로 처리 → 모든 소모임에서 나가기(글 가림·멤버 수) + 줄 삭제</li>
 *   <li>포인트: 남은 묶음 전부 {@code WITHDRAW_FORFEIT}(원장 보존) · 아이템 회수 · 사업자 신원 탈퇴(기다리기/포기) · 핫딜 작성자 종료</li>
 *   <li>재가입 제한 해시(이메일·소셜·본인인증, {@link WithdrawalRejoinPolicy} 의 끝까지) · 결제 이력이 있으면 연락용 이메일 분리 보관</li>
 *   <li>설정·이력·관계·역할·토큰·알림·내 쪽지함 삭제, 대기 요청·대기 메일 취소, 주소록 삭제, 배송지 암호문 즉시 마스킹, 작성 90일 지난 글·댓글 IP 삭제</li>
 *   <li>프로필·계정 익명화(닉네임·이메일·비밀번호 NULL, WITHDRAWN, deleted_at) · 세션 모두 종료 · 감사 {@code MEMBER_WITHDRAW}(돈 관련) · 신청 줄 COMPLETED</li>
 *   <li>커밋 뒤: 완료 메일(익명화 전 주소 — 인증한 주소만) · 닉네임 이미지 디스크 파일 삭제</li>
 * </ol>
 */
@Service
public class WithdrawalCompletionService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalCompletionService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 글·댓글 접속 IP 보관(통비법 3개월) — 이보다 오래된 것은 완료 때 지우고 나머지는 배치가 지운다 */
    public static final int AUTHOR_IP_DAYS = 90;

    /** 완료 결과 — 완료 화면에 보일 것 */
    public record Completed(long withdrawalId, Instant completedAt, Instant rejoinUntil, boolean hasPaymentHistory, int retentionYears, String supportEmail) {
    }

    /**
     * 완료를 일으킨 행위자(감사 {@code MEMBER_WITHDRAW} 의 행위자) — 회원 본인의 즉시 완료 · 배치(SYSTEM) · 운영자 강제 탈퇴의 즉시 완료.
     *
     * @param ip 요청의 IP(배치면 null)
     */
    public record Actor(AuditActorType type, Long id, String ip) {

        public static final Actor SYSTEM = new Actor(AuditActorType.SYSTEM, null, null);

        public static Actor member(long userId, String ip) {
            return new Actor(AuditActorType.MEMBER, userId, ip);
        }

        public static Actor operator(long operatorId, String ip) {
            return new Actor(AuditActorType.OPERATOR, operatorId, ip);
        }

        AuditEvent event(long userId) {
            return new AuditEvent(type, id, AuditActions.MEMBER_WITHDRAW, AuditResult.SUCCESS, "MEMBER", Long.toString(userId), ip, Map.of())
                    .with("forced", type == AuditActorType.OPERATOR);
        }
    }

    private final WithdrawalQueries queries;
    private final WithdrawalEraseStore erase;
    private final MemberWithdrawalStore withdrawals;
    private final WithdrawalGroupHandler groupHandler;
    private final WithdrawalRejoinPolicy rejoinPolicy;
    private final PointService points;
    private final BusinessWithdrawal business;
    private final DealWithdrawal deals;
    private final IdentityWithdrawalRecorder identities;
    private final WithdrawnContactStore contacts;
    private final AuditLogService audit;
    private final WithdrawalMailSender mail;
    private final ImageStorage storage;
    private final WithdrawalProperties properties;
    private final Clock clock;

    public WithdrawalCompletionService(WithdrawalQueries queries, WithdrawalEraseStore erase, MemberWithdrawalStore withdrawals, WithdrawalGroupHandler groupHandler,
                                       WithdrawalRejoinPolicy rejoinPolicy, PointService points, BusinessWithdrawal business, DealWithdrawal deals,
                                       IdentityWithdrawalRecorder identities, WithdrawnContactStore contacts, AuditLogService audit, WithdrawalMailSender mail,
                                       ImageStorage storage, WithdrawalProperties properties, ObjectProvider<Clock> clocks) {
        this.queries = queries;
        this.erase = erase;
        this.withdrawals = withdrawals;
        this.groupHandler = groupHandler;
        this.rejoinPolicy = rejoinPolicy;
        this.points = points;
        this.business = business;
        this.deals = deals;
        this.identities = identities;
        this.contacts = contacts;
        this.audit = audit;
        this.mail = mail;
        this.storage = storage;
        this.properties = properties;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 진행 중 신청이 있는 회원을 완료 처리한다 — 회원 본인(즉시 완료) 또는 배치(SYSTEM).
     *
     * @param byMember 회원 자신의 요청(즉시 완료)이면 true — 감사 행위자가 MEMBER, 배치면 SYSTEM
     * @param ip       회원 요청의 IP(배치면 null)
     */
    @Transactional
    public Optional<Completed> complete(long userId, boolean byMember, String ip) {
        return complete(userId, byMember ? Actor.member(userId, ip) : Actor.SYSTEM);
    }

    /**
     * 진행 중 신청이 있는 회원을 완료 처리한다. 신청 줄과 회원 줄을 잠근다 — 이미 완료·취소됐거나 회원이 없으면 비어 있다.
     *
     * @param actor 완료를 일으킨 행위자(감사) — 회원 본인 · 배치 · 운영자 강제 탈퇴
     */
    @Transactional
    public Optional<Completed> complete(long userId, Actor actor) {
        Optional<String> status = queries.lockStatus(userId);
        if (status.isEmpty() || "WITHDRAWN".equals(status.get())) {
            return Optional.empty();
        }
        MemberWithdrawalStore.Row request = withdrawals.lockScheduled(userId).orElse(null);
        WithdrawalQueries.Member member = queries.member(userId).orElse(null);
        if (request == null || member == null) {
            return Optional.empty();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        // 1. 소모임 — 신청 때 저장한 선택(TRANSFER 대상이 멤버가 아니면 AUTO, 신청 뒤 새로 모임장이 된 모임은 AUTO)대로 넘기기·해산, 그다음 모든 소모임에서 나가기
        Map<Long, String[]> plans = groupPlans(request.groupChoicesJson());
        for (Long groupId : erase.ledGroupIds(userId)) {
            String[] plan = plans.get(groupId);
            groupHandler.complete(groupId, userId, plan == null ? "AUTO" : plan[0], plan == null || plan[1] == null ? null : Long.valueOf(plan[1]), now);
        }
        int leftGroups = erase.leaveAllGroups(userId, now);

        // 2. 돈·아이템·사업자·핫딜
        PointService.Forfeited forfeited = points.forfeitAll(userId);
        int revokedItems = erase.revokeItems(userId, now);
        business.withdraw(userId, request.choices().forfeitBiz());
        DealWithdrawal.Result dealResult = deals.withdraw(userId);

        // 3. 재가입 제한·연락용 이메일(지우기 전에 읽는다)
        WithdrawalRejoinPolicy.Rejoin rejoin = rejoinPolicy.of(userId, request.priorStatus(), now);
        if (member.email() != null) {
            identities.recordEmail(member.email(), now, rejoin.until());
        }
        for (WithdrawalQueries.SocialIdentity social : queries.socialIdentities(userId)) {
            identities.recordSocial(social.provider(), social.providerUserId(), now, rejoin.until());
        }
        identities.recordIdentity(userId, now, rejoin.until());
        Optional<Instant> lastTransaction = queries.lastTransactionAt(userId);
        boolean hasPaymentHistory = lastTransaction.isPresent();
        if (hasPaymentHistory && member.email() != null) {
            contacts.store(userId, member.email(), now, lastTransaction.get());
        }

        // 4. 정리
        List<String> imageKeys = queries.nicknameImageKeys(userId);
        erase.cancelPendingRequests(userId);
        int canceledMail = erase.cancelPendingMail(userId, now);
        erase.deleteOwnRows(userId);
        int clearedIps = erase.clearOldAuthorIps(userId, AUTHOR_IP_DAYS, now);
        int masked = erase.maskParticipations(userId, now);

        // 5. 익명화·세션·기록
        erase.anonymizeProfile(userId, now);
        erase.anonymizeUser(userId, now);
        int sessions = erase.deleteSessions(userId);
        withdrawals.complete(request.id(), now, rejoin.until(), hasPaymentHistory, null);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("withdrawalId", Long.toString(request.id()));
        detail.put("forfeitedFree", forfeited.free());
        detail.put("forfeitedPaid", forfeited.paid());
        detail.put("forfeitPaidPoints", request.forfeitPaidPoints() == null ? 0L : request.forfeitPaidPoints());
        detail.put("forfeitBizAmount", request.forfeitBizAmount() == null ? 0L : request.forfeitBizAmount());
        detail.put("forfeitBiz", request.choices().forfeitBiz());
        detail.put("hasPaymentHistory", hasPaymentHistory);
        detail.put("rejoinUntil", rejoin.until().toString());
        detail.put("waitedPending", request.pendingCount());
        detail.put("revokedItems", revokedItems);
        detail.put("leftGroups", leftGroups);
        detail.put("endedDeals", dealResult.endedDeals());
        detail.put("source", request.source().name());
        AuditEvent event = actor.event(userId);
        for (Map.Entry<String, Object> e : detail.entrySet()) {
            event = event.with(e.getKey(), e.getValue());
        }
        audit.record(event);

        // 6. 커밋 뒤 — 완료 메일(인증한 주소만), 닉네임 이미지 파일
        if (member.emailVerified()) {
            mail.completedAfterCommit(userId, member.email(), member.language(), member.nickname(), rejoin.until(), hasPaymentHistory);
        }
        afterCommit(() -> imageKeys.forEach(this::deleteQuietly));

        log.info("회원 탈퇴를 완료했다 userId={} withdrawalId={} 무상포기={} 유상포기={} 결제이력={} 세션={} 마스킹={} IP삭제={} 대기메일취소={} 소모임={}", userId, request.id(),
                forfeited.free(), forfeited.paid(), hasPaymentHistory, sessions, masked, clearedIps, canceledMail, leftGroups);
        return Optional.of(new Completed(request.id(), now, rejoin.until(), hasPaymentHistory, properties.contactRetentionYears(), mail.supportEmail()));
    }

    /** 신청 때 저장한 소모임 선택({@code [{groupId, action, toMemberId}]}) → 모임 ID → {동작, 대상}. 없거나 깨졌으면 빈 맵(모두 AUTO) */
    static Map<Long, String[]> groupPlans(String json) {
        Map<Long, String[]> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            for (Map<String, Object> plan : JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() { })) {
                Object groupId = plan.get("groupId");
                if (groupId == null) {
                    continue;
                }
                Object to = plan.get("toMemberId");
                out.put(Long.valueOf(groupId.toString()), new String[] {String.valueOf(plan.get("action")), to == null ? null : to.toString()});
            }
        } catch (RuntimeException e) {
            log.warn("탈퇴 신청의 소모임 선택을 읽지 못해 모두 자동 처리한다 원인={}", e.getClass().getSimpleName());
        }
        return out;
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("탈퇴 회원의 닉네임 이미지 파일을 지우지 못했다 key={} 원인={}", key, e.getClass().getSimpleName());
        }
    }

    private static void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
