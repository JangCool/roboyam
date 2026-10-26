package com.community.point.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.member.exception.MemberNotFoundException;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.point.dto.request.PointAdjustRequest;
import com.community.point.dto.response.PointAdjustResultView;
import com.community.point.entity.PointReason;
import com.community.point.exception.PointAdjustLargeRequiredException;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자의 수동 지급·차감 — {@code POST /api/admin/members/{id}/points}(권한 {@code point.adjust}, 컨트롤러가 선언). commerce-stage1-contract 「1-1」 API, commerce.md 5-4·13-2.
 *
 * <ul>
 *   <li><b>검사 순서</b>: ① 칸(모아 400 {@code VALIDATION}) — {@code delta} 는 0 이 아닌 정수 −{@value #AMOUNT_MAX}~{@value #AMOUNT_MAX}, {@code reason} 은 앞뒤 공백을 떼고
 *       연속 공백을 한 칸으로 줄인 뒤 1~{@value #REASON_MAX}자(회원의 포인트 내역에 공개된다). ② 회원 — 없는 회원·탈퇴한 회원·형식이 틀린 ID 는 404 {@code MEMBER_NOT_FOUND}.
 *       ③ 큰 금액 — |delta| 가 문턱({@code point_settings.adjust_large_threshold}, 0 이면 모든 조정)을 넘는데 {@code point.adjust.large} 가 없으면 403
 *       {@code POINT_ADJUST_LARGE_REQUIRED} + 거부 감사({@code PERMISSION_DENIED}, 따로 커밋 — 권한 거부 관례). 회원 확인을 먼저 하는 까닭: 거부 감사의 대상이 실제 회원이어야
 *       해서다(입구 권한 {@code point.adjust} 를 가진 운영자에게 회원 유무는 감출 정보가 아니다). 어느 단계에서 막혀도 잔액·원장·묶음은 그대로다.</li>
 *   <li>정지 회원에게도 조정할 수 있다(운영자의 명시적 조치).</li>
 *   <li><b>반영</b>({@link PointService#adjust}): <b>무상만</b>(유상을 고르는 칸이 없다 — 운영자가 유상 포인트를 만들 길은 없다). +는 무상 묶음, −는 무상 잔액까지(0P 바닥, 유상 불변,
 *       빚을 만들지 않는다) — {@code applied} 가 요청보다 작을 수 있고 0 이면 원장 줄·알림이 없다. 응답 {@code balance} 는 합계(무상 + 유상).</li>
 *   <li>감사 {@code POINT_ADJUST}(대상 MEMBER + 회원 ID, 요청값·반영값·잔액·사유·{@code large}) — 반영 0 이어도 남긴다. 보관 분류는 돈 관련(5년, {@code AuditRetention}).
 *       차감이 반영되면 회원 알림 {@code POINTS_REVOKED}(운영 조정, 사유 포함). 지급에는 알림이 없다. 모두 한 트랜잭션이다.</li>
 * </ul>
 */
@Service
public class PointAdjustService {

    private static final Logger log = LoggerFactory.getLogger(PointAdjustService.class);

    static final int AMOUNT_MAX = 100_000;
    static final int REASON_MAX = 100;

    private final UserRepository users;
    private final PointService points;
    private final PointPolicy policy;
    private final AuditLogService audit;
    private final AccessDeniedRecorder denied;
    private final MemberNotificationPublisher memberNotices;

    public PointAdjustService(UserRepository users, PointService points, PointPolicy policy, AuditLogService audit, AccessDeniedRecorder denied,
                              MemberNotificationPublisher memberNotices) {
        this.users = users;
        this.points = points;
        this.policy = policy;
        this.audit = audit;
        this.denied = denied;
        this.memberNotices = memberNotices;
    }

    @Transactional
    public PointAdjustResultView adjust(AdminActor actor, String memberId, PointAdjustRequest body) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Integer delta = deltaOf(body == null ? null : body.delta());
        if (delta == null) {
            fields.put("delta", new MessageRef("point.adjust.delta.range", String.format("%,d", AMOUNT_MAX)));
        }
        String reason = normalize(body == null ? null : body.reason());
        int length = reason.codePointCount(0, reason.length());
        if (length == 0) {
            fields.put("reason", new MessageRef("point.adjust.reason.required"));
        } else if (length > REASON_MAX) {
            fields.put("reason", new MessageRef("point.adjust.reason.max", String.valueOf(REASON_MAX)));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        long userId = parseId(memberId);
        UserEntity user = users.findById(userId).filter(u -> !u.isGone()).orElseThrow(MemberNotFoundException::new);

        int threshold = policy.lots().adjustLargeThreshold();
        boolean large = isLarge(delta, threshold);
        if (large && !actor.can(Permission.POINT_ADJUST_LARGE)) {
            denied.record(actor, "MISSING_PERMISSION", "MEMBER", Long.toString(userId), List.of(Permission.POINT_ADJUST_LARGE.code()));
            throw new PointAdjustLargeRequiredException(threshold);
        }

        PointService.Adjusted result = points.adjust(user.getId(), delta, reason, actor.operatorId());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.POINT_ADJUST, AuditResult.SUCCESS).target("MEMBER", Long.toString(userId))
                .with("requested", delta).with("applied", result.applied()).with("balance", result.balance()).with("reason", reason).with("large", large));
        if (result.applied() < 0) {
            memberNotices.pointsRevoked(userId, PointReason.ADMIN_ADJUST.name(), -result.applied(), reason);
        }
        log.info("포인트를 조정했다 memberId={} 요청={} 반영={} 고액={} by operatorId={}", userId, delta, result.applied(), large, actor.operatorId());
        return new PointAdjustResultView(result.applied(), result.balance());
    }

    /** 큰 금액 조정인가 — |delta| 가 문턱을 넘으면(문턱 0 이면 모든 조정) */
    static boolean isLarge(int delta, int threshold) {
        return Math.abs((long) delta) > threshold;
    }

    /** 0 이 아닌 정수이고 범위 안이면 그 값, 아니면 null(소수·빈 값 포함) */
    static Integer deltaOf(Number raw) {
        if (raw == null) {
            return null;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
        if (value.stripTrailingZeros().scale() > 0) {
            return null;
        }
        if (value.signum() == 0 || value.abs().compareTo(BigDecimal.valueOf(AMOUNT_MAX)) > 0) {
            return null;
        }
        return value.intValueExact();
    }

    /** 앞뒤 공백을 떼고 연속 공백을 한 칸으로 */
    static String normalize(String text) {
        return text == null ? "" : text.strip().replaceAll("\s+", " ");
    }

    private static long parseId(String memberId) {
        if (memberId == null || memberId.isEmpty() || memberId.length() > 19 || !memberId.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            throw new MemberNotFoundException();
        }
        try {
            return Long.parseLong(memberId);
        } catch (NumberFormatException e) {
            throw new MemberNotFoundException();
        }
    }
}
