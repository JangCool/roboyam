package com.community.notification.service;

import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.entity.NotificationSeverity;
import com.community.notification.repository.master.NotificationRecipientRepositoryQDSL;
import com.community.operator.entity.OperatorEntity;
import com.community.permission.dto.AdminActor;
import com.community.role.entity.RoleEntity;
import com.community.role.repository.master.RoleRepository;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 업무 서비스(운영자·초대·로그인·비밀번호·역할·사이트·등급)가 알림을 낼 때 부르는 <b>한 줄짜리 입구</b> — 「내 계정」 알림처럼 <b>상황에 따라 문장이 달라지는</b> 발행(상태가 바뀐 칸, 역할이 더해졌는지 회수됐는지)과
 * 받는 사람을 모으는 일을 여기에 모아 업무 서비스에는 호출 한 줄만 남긴다. 어떤 문장인지는 메시지 번들의 {@code notification.variant.*} 문구 참조로 알림 인자에 실어 읽는 사람의 언어로 풀린다.
 * <b>업무 트랜잭션 안에서 부른다</b>(알림 발행기가 같은 트랜잭션에 참여한다 — 업무가 롤백되면 알림도 없고, 알림이 실패해도 업무는 그대로다). 비밀번호·토큰·이메일은 싣지 않는다.
 * 문장이 갈리지 않는 이벤트는 이 클래스를 거치지 않고 업무 서비스가 {@link NotificationPublisher#publish} 를 바로 불러도 된다.
 */
@Component
public class AdminNotices {

    private final NotificationPublisher publisher;
    private final NotificationRecipientRepositoryQDSL recipients;
    private final RoleRepository roles;

    public AdminNotices(NotificationPublisher publisher, NotificationRecipientRepositoryQDSL recipients, RoleRepository roles) {
        this.publisher = publisher;
        this.recipients = recipients;
        this.roles = roles;
    }

    /** 운영자 계정을 직접 만들었다 — 운영자 관리 권한 보유자에게(만든 사람 제외) */
    public void accountCreated(AdminActor actor, String operatorName) {
        publisher.publish(AdminNotificationEvent.OPERATOR_ACCOUNT_CREATED, NotificationContext.empty().actor(actor.operatorId()).args(operatorName));
    }

    /** 초대를 수락해 운영자가 되었다 — 초대한 운영자와 운영자 관리 권한 보유자에게 */
    public void invitationAccepted(long newOperatorId, String operatorName, Long invitedBy) {
        publisher.publish(AdminNotificationEvent.OPERATOR_INVITATION_ACCEPTED, NotificationContext.empty().actor(newOperatorId).alsoTo(invitedBy).args(operatorName));
    }

    /** 로그인 실패 한도에 닿아 계정 잠금이 <b>시작됐다</b> — 운영자 잠금 권한 보유자에게. 이메일 원문이 아니라 운영자 이름을 싣는다 */
    public void lockedByFailures(long operatorId, String operatorName) {
        publisher.publish(AdminNotificationEvent.OPERATOR_LOCKED_BY_FAILURES, NotificationContext.empty().target(operatorId).args(operatorName));
    }

    /**
     * 사이트 설정이 바뀌었다 — 사이트 설정 조회 권한 보유자에게(바꾼 사람 제외, 60초 안의 연속 변경은 한 건).
     *
     * @param what {@code siteBasic} · {@code siteLogo} · {@code siteIcon} · {@code siteOg} · {@code siteDisplay} — 무엇이 바뀌었는지 알리는 번들 문구({@code notification.variant.<what>})
     */
    public void siteSettingsChanged(AdminActor actor, String what) {
        publisher.publish(AdminNotificationEvent.SITE_SETTINGS_CHANGED,
                NotificationContext.empty().actor(actor.operatorId()).args(new MessageRef("notification.variant." + what)));
    }

    /** 등급 설정(등급 목록·승급 조건)을 저장했다 — 등급 조회 권한 보유자에게(저장한 사람 제외) */
    public void gradeSettingsSaved(AdminActor actor) {
        publisher.publish(AdminNotificationEvent.GRADE_SETTINGS_SAVED, NotificationContext.empty().actor(actor.operatorId()));
    }

    /** 내 비밀번호를 바꿨다(본인에게) */
    public void passwordChanged(long operatorId) {
        publisher.publish(AdminNotificationEvent.PASSWORD_CHANGED_ME, NotificationContext.empty().actor(operatorId).target(operatorId));
    }

    /** 승인번호로 비밀번호를 재설정했다(본인에게) */
    public void passwordReset(long operatorId) {
        publisher.publish(AdminNotificationEvent.PASSWORD_RESET_DONE_ME, NotificationContext.empty().actor(operatorId).target(operatorId));
    }

    /**
     * 계정 상태(승인·잠금·만료·사용)가 바뀌었다 — 바뀐 칸마다 한 건. {@code after} 는 바뀐 칸의 새 값이다(칸 이름 {@code approved}·{@code locked}·{@code expired}·{@code enabled}).
     * 잠김·사용 중지·승인 취소·만료는 경고, 풀림·사용·승인은 정보다.
     */
    public void statusChanged(AdminActor actor, long operatorId, Map<String, Object> after) {
        after.forEach((field, value) -> {
            if (!(value instanceof Boolean on)) {
                return;
            }
            switch (field) {
                case "locked" -> status(actor, operatorId, on ? "statusLocked" : "statusUnlocked", on);
                case "enabled" -> status(actor, operatorId, on ? "statusEnabled" : "statusDisabled", !on);
                case "approved" -> status(actor, operatorId, on ? "statusApproved" : "statusUnapproved", !on);
                case "expired" -> status(actor, operatorId, on ? "statusExpired" : "statusUnexpired", on);
                default -> {
                }
            }
        });
    }

    /** 계정 기간이 바뀌면 알린다 — 새 기간으로 고치기 <b>전에</b> 부른다(지금 값과 견준다) */
    public void periodChanging(AdminActor actor, OperatorEntity operator, Instant newStart, Instant newEnd) {
        if (!Objects.equals(operator.getAccountStartAt(), newStart) || !Objects.equals(operator.getAccountEndAt(), newEnd)) {
            status(actor, operator.getId(), "periodChanged", false);
        }
    }

    /** 운영자가 받은 역할이 바뀌었다 — 더해진 역할과 회수된 역할(역할 아이디) */
    public void rolesChanged(AdminActor actor, long operatorId, List<String> grantedRoleIds, List<String> revokedRoleIds) {
        if (!grantedRoleIds.isEmpty()) {
            publisher.publish(AdminNotificationEvent.ROLES_CHANGED_ME, NotificationContext.empty().actor(actor.operatorId()).target(operatorId)
                    .args(new MessageRef("notification.variant.roleGranted", namesOf(grantedRoleIds))));
        }
        if (!revokedRoleIds.isEmpty()) {
            publisher.publish(AdminNotificationEvent.ROLES_CHANGED_ME, NotificationContext.empty().actor(actor.operatorId()).target(operatorId)
                    .severity(NotificationSeverity.WARNING).args(new MessageRef("notification.variant.roleRevoked", namesOf(revokedRoleIds))));
        }
    }

    /** 역할의 권한이 바뀌었다 — 그 역할을 받은 운영자마다 한 건(권한을 거둔 경우는 경고). 받은 운영자가 없으면(회원 역할 등) 아무것도 하지 않는다 */
    public void rolePermissionsChanged(AdminActor actor, String roleId, String roleName, boolean anyRevoked) {
        for (Long operatorId : recipients.operatorsWithRole(roleId)) {
            NotificationContext context = NotificationContext.empty().actor(actor.operatorId()).target(operatorId)
                    .args(new MessageRef("notification.variant.rolePermissionsChanged", roleName));
            publisher.publish(AdminNotificationEvent.ROLES_CHANGED_ME, anyRevoked ? context.severity(NotificationSeverity.WARNING) : context);
        }
    }

    private void status(AdminActor actor, long operatorId, String variant, boolean warning) {
        NotificationContext context = NotificationContext.empty().actor(actor.operatorId()).target(operatorId).args(new MessageRef("notification.variant." + variant));
        publisher.publish(AdminNotificationEvent.OPERATOR_STATUS_CHANGED_ME, warning ? context : context.severity(NotificationSeverity.INFO));
    }

    private String namesOf(List<String> roleIds) {
        return roles.findAllById(roleIds).stream().map(RoleEntity::getRoleName).sorted().collect(Collectors.joining(", "));
    }
}
