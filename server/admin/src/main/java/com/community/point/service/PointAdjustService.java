package com.community.point.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.member.exception.MemberNotFoundException;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.permission.dto.AdminActor;
import com.community.point.dto.request.PointAdjustRequest;
import com.community.point.dto.response.PointAdjustResultView;
import com.community.point.entity.PointReason;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자의 수동 지급·차감 — {@code POST /api/admin/members/{id}/points}(권한 {@code point.adjust}, 컨트롤러가 선언).
 *
 * <ul>
 *   <li><b>검사</b>(칸을 모아 400 {@code VALIDATION}): {@code delta} 는 0 이 아닌 정수 −{@value #AMOUNT_MAX}~{@value #AMOUNT_MAX}, {@code reason} 은 앞뒤 공백을 떼고 연속 공백을
 *       한 칸으로 줄인 뒤 1~{@value #REASON_MAX}자(회원의 포인트 내역에 공개된다). 틀리면 아무것도 저장하지 않고 감사 기록도 없다.</li>
 *   <li>없는 회원·탈퇴한 회원(포인트가 소멸했다)·형식이 틀린 ID 는 404 {@code MEMBER_NOT_FOUND}. 정지 회원에게도 조정할 수 있다(운영자의 명시적 조치).</li>
 *   <li><b>반영</b>({@link PointService#adjust}): 잔액 행을 잠그고 차감은 0P 까지 — {@code applied} 가 요청보다 작을 수 있고, 0 이면 원장 줄·알림이 없다. 동시 차감도 잔액이 음수가 되지 않는다.</li>
 *   <li>감사 {@code POINT_ADJUST}(대상 MEMBER + 회원 ID, 요청값·반영값·사유) — 반영 0 이어도 남긴다. 차감이 반영되면 회원 알림 {@code POINTS_REVOKED}(운영 조정, 사유 포함).
 *       지급에는 알림이 없다. 모두 한 트랜잭션이다.</li>
 * </ul>
 */
@Service
public class PointAdjustService {

    private static final Logger log = LoggerFactory.getLogger(PointAdjustService.class);

    static final int AMOUNT_MAX = 10000;
    static final int REASON_MAX = 100;

    private final UserRepository users;
    private final PointService points;
    private final AuditLogService audit;
    private final MemberNotificationPublisher memberNotices;

    public PointAdjustService(UserRepository users, PointService points, AuditLogService audit, MemberNotificationPublisher memberNotices) {
        this.users = users;
        this.points = points;
        this.audit = audit;
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

        PointService.Adjusted result = points.adjust(user.getId(), delta, reason, actor.operatorId());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.POINT_ADJUST, AuditResult.SUCCESS).target("MEMBER", Long.toString(userId))
                .with("requested", delta).with("applied", result.applied()).with("balance", result.balance()).with("reason", reason));
        if (result.applied() < 0) {
            memberNotices.pointsRevoked(userId, PointReason.ADMIN_ADJUST.name(), -result.applied(), reason);
        }
        log.info("포인트를 조정했다 memberId={} 요청={} 반영={} by operatorId={}", userId, delta, result.applied(), actor.operatorId());
        return new PointAdjustResultView(result.applied(), result.balance());
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
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
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
