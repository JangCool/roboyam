package com.community.notification.entity;

import static com.community.notification.entity.NotificationArea.BIZ_AD;
import static com.community.notification.entity.NotificationArea.CLUB;
import static com.community.notification.entity.NotificationArea.GROUPBUY;
import static com.community.notification.entity.NotificationArea.HOTDEAL;
import static com.community.notification.entity.NotificationArea.MEMBER;
import static com.community.notification.entity.NotificationArea.SAFETY;
import static com.community.notification.entity.NotificationArea.SECURITY;
import static com.community.notification.entity.NotificationArea.SYSTEM;
import static com.community.notification.entity.NotificationSeverity.CRITICAL;
import static com.community.notification.entity.NotificationSeverity.INFO;
import static com.community.notification.entity.NotificationSeverity.WARNING;

import com.community.permission.entity.Permission;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 관리자 알림 이벤트 카탈로그 — <b>코드가 원천</b>이다({@link Permission} 과 같은 방식: 서버가 뜰 때 {@code notification_events} 표와 맞춘다 — {@code NotificationCatalogSynchronizer}).
 * 이벤트의 코드({@link #code()} = 상수 이름)는 한 번 정하면 바꾸지 않는다(알림 행·개인 끄기·이벤트 설정이 이 값으로 가리킨다 — 감사 {@code AuditActions} 와 같은 규칙).
 *
 * <p>한 이벤트가 정하는 것(운영자가 화면에서 바꾸는 것은 <b>사용 여부와 이메일 전달뿐</b>이고 나머지는 모두 코드에 고정이다):
 * <ul>
 *   <li>{@link #area()} · {@link #severity()} — 서랍·이벤트 관리 화면의 분류와 아이콘/색</li>
 *   <li>수신 규칙 {@link #recipient()}(+ {@link #recipientPermissions()}) · {@link #excludeActor()} — 누가 받는가. <b>규칙을 화면에서 바꾸지 않는다</b>(보면 안 될 알림을 받을 수 있다)</li>
 *   <li>{@link #requiredPermission()} — 알림을 보는 데 필요한 권한. 없으면 누구나. 권한을 잃은 운영자는 <b>조회 시점에</b> 이 이벤트의 알림이 안 보인다(행은 보관 정책이 지운다)</li>
 *   <li>{@link #mandatory()} — 끌 수 없는 이벤트(보안: 내 계정의 상태·권한·비밀번호가 바뀜). 이벤트 관리에서도 개인 설정에서도 끄지 못한다</li>
 *   <li>{@link #defaultEnabled()} · {@link #emailable()} · {@link #defaultEmailEnabled()} — 처음 사용 여부, 이메일로 보낼 수 있는가(준비 중이 아니면 모두), 처음 이메일 전달 값({@code email()} 로 정한 것만)</li>
 *   <li>{@link #linkPath()} — 이동 경로 템플릿({@code {이름}} 자리는 발행 때 {@code NotificationContext#link} 로 채운다)</li>
 *   <li>{@link #collapseSeconds()} — 같은 이벤트·같은 묶음 키를 N초 안에 한 건으로 묶기(0 이면 안 묶음)</li>
 *   <li>{@link #featureReady()} — 발행처가 이미 있는가. false 인 예약 이벤트는 관리 화면에 「준비 중」으로만 보이고 발행되지 않는다</li>
 * </ul>
 *
 * <p><b>이벤트 추가 방법</b>(4단계): ① 여기에 한 줄 ② 메시지 번들(ko·en)에 {@code notification.event.<코드>.name|description|recipients|title|body} ③ 사건이 일어나는 서비스에서
 * {@code publisher.publish(EVENT, 맥락)} 한 줄 ④ 끝. 새 화면·표·권한 변경은 없다. 규약은 {@code .claude/guides/backend/notification.md}.
 */
public enum AdminNotificationEvent {

    // ── 발행처가 있는 이벤트 (featureReady = true) ──
    /** 배치(Java·DB 모두) 실패 — 알림 감시자가 {@code batch_runs} 를 보고 발행한다. 같은 배치의 연속 실패는 30분에 한 건 */
    BATCH_RUN_FAILED(spec(SYSTEM, WARNING).holders(Permission.BATCH_READ).email().link("/settings/batch-logs?jobName={jobName}").collapse(1800)),
    /** 초대 수락 — 초대한 운영자(맥락의 추가 수신자)와 운영자 관리 권한 보유자 */
    OPERATOR_INVITATION_ACCEPTED(spec(SYSTEM, INFO).holders(Permission.OPERATOR_MANAGE).excludeActor().link("/settings/operators")),
    /** 운영자 직접 추가 */
    OPERATOR_ACCOUNT_CREATED(spec(SYSTEM, INFO).holders(Permission.OPERATOR_MANAGE).excludeActor().link("/settings/operators")),
    /** 내 계정이 잠기거나 사용 중지·승인·계정 기간이 바뀜 */
    OPERATOR_STATUS_CHANGED_ME(spec(SECURITY, WARNING).self().mandatory().email().link("/me")),
    /** 로그인 실패 5회로 잠금이 <b>시작되는 순간</b> 한 번 */
    OPERATOR_LOCKED_BY_FAILURES(spec(SECURITY, CRITICAL).holders(Permission.OPERATOR_LOCK).email().link("/settings/operators")),
    /** 내 역할이 더해지거나 회수됨, 내 역할의 권한이 바뀜(회수는 발행 때 심각도를 warning 으로 올린다) */
    ROLES_CHANGED_ME(spec(SECURITY, INFO).self().mandatory().link("/me")),
    PASSWORD_CHANGED_ME(spec(SECURITY, WARNING).self().mandatory().email().link("/me?tab=password")),
    PASSWORD_RESET_DONE_ME(spec(SECURITY, WARNING).self().mandatory().email().link("/me?tab=password")),
    /** 사이트 설정(기본 정보·로고·아이콘·링크 미리보기 이미지·표시 기본값) 변경 — 60초 안의 연속 변경은 한 건 */
    SITE_SETTINGS_CHANGED(spec(SYSTEM, INFO).holders(Permission.SITE_READ).excludeActor().link("/settings/general").collapse(60)),
    GRADE_SETTINGS_SAVED(spec(MEMBER, INFO).holders(Permission.GRADE_READ).excludeActor().link("/grades")),
    /** 등급 일수 승급 배치 종료(처리 건수 &gt; 0) — 알림 감시자가 발행한다 */
    GRADE_PROMOTION_RUN(spec(MEMBER, INFO).holders(Permission.GRADE_READ).link("/settings/batch-logs?jobName=grade-promotion")),
    /** 공개 사이트 글·댓글 신고 접수 — 신고 정책 notifyOperatorScope 가 PER_CASE 면 대상마다 첫 신고에만, PER_REPORT 면 신고마다(api ReportService). 처리 화면이 아직 없어 링크가 없다 */
    REPORT_RECEIVED(spec(SAFETY, INFO).holders(Permission.REPORT_MANAGE)),
    /** 최고 관리자가 관리자 2차 인증을 켬 — 운영자 전원에게 OTP 등록 안내(켠 사람 제외). 문구에 인자가 없다 */
    MFA_ENABLED(spec(SECURITY, INFO).everyone().excludeActor().link("/me?tab=security")),
    /** 최고 관리자가 관리자 2차 인증을 끔 — 운영자 전원에게 심각도 높음(끈 사람 포함 — 루트 세션을 남이 쓴 경우 알아채게). 이메일 전달이 처음부터 켜짐 */
    MFA_DISABLED(spec(SECURITY, CRITICAL).everyone().email().link("/me?tab=security")),

    // ── 예약 이벤트 (featureReady = false) — 기능이 만들어질 때 reserved() 를 지우고 발행 한 줄만 더한다 ──
    SANCTION_APPEAL_FILED(spec(SAFETY, INFO).holders(Permission.APPEAL_MANAGE).reserved()),
    BOARD_SANCTION_REPEAT_REVIEW(spec(SAFETY, WARNING).holders(Permission.SANCTION_MANAGE).reserved()),
    NICKNAME_IMAGE_PENDING(spec(MEMBER, INFO).holders(Permission.NICKNAME_IMAGE_MANAGE).reserved()),
    CLUB_OPEN_REQUEST_PENDING(spec(CLUB, INFO).holders(Permission.GROUP_MANAGE).reserved()),
    HOTDEAL_SUSPECTED_PROMO(spec(HOTDEAL, WARNING).holders(Permission.HOTDEAL_MANAGE).reserved()),
    COMMERCE_ORDER_ANOMALY(spec(GROUPBUY, CRITICAL).holders(Permission.COMMERCE_ORDER_MANAGE).reserved()),
    COMMERCE_REFUND_REQUESTED(spec(GROUPBUY, WARNING).holders(Permission.COMMERCE_REFUND_APPROVE).reserved()),
    BUSINESS_APPLICATION_PENDING(spec(BIZ_AD, INFO).holders(Permission.BUSINESS_MANAGE).reserved()),
    AD_REVIEW_PENDING(spec(BIZ_AD, INFO).holders(Permission.AD_MANAGE).reserved()),
    /** 운영자 신청 기능이 구현된 뒤 */
    OPERATOR_APPLICATION_PENDING(spec(SYSTEM, INFO).holders(Permission.OPERATOR_MANAGE).reserved());

    /** 이벤트 한 줄을 읽기 좋게 쓰는 도우미 — 상수 정의에서만 쓴다 */
    private static final class Spec {

        private final NotificationArea area;
        private final NotificationSeverity severity;
        private NotificationRecipientRule recipient = NotificationRecipientRule.SELF;
        private List<Permission> holders = List.of();
        private Permission requires;
        private boolean excludeActor;
        private boolean mandatory;
        private boolean emailByDefault;
        private String link;
        private int collapse;
        private boolean ready = true;

        private Spec(NotificationArea area, NotificationSeverity severity) {
            this.area = area;
            this.severity = severity;
        }

        /** 이 권한 중 하나라도 가진 운영자(+ 최고 관리자)가 받는다. 보는 데 필요한 권한은 첫 권한이다 */
        private Spec holders(Permission... permissions) {
            this.recipient = NotificationRecipientRule.PERMISSION_HOLDERS;
            this.holders = List.of(permissions);
            this.requires = permissions[0];
            return this;
        }

        private Spec self() {
            this.recipient = NotificationRecipientRule.SELF;
            return this;
        }

        /** 활성 운영자 전원이 받는다. 보는 데 필요한 권한이 없다 */
        private Spec everyone() {
            this.recipient = NotificationRecipientRule.ALL_OPERATORS;
            this.holders = List.of();
            this.requires = null;
            return this;
        }

        private Spec excludeActor() {
            this.excludeActor = true;
            return this;
        }

        private Spec mandatory() {
            this.mandatory = true;
            return this;
        }

        /** 이메일 전달이 <b>처음부터 켜진</b> 이벤트 — 이메일을 보낼 수 있는지는 이것과 상관없다(준비 중이 아니면 모두 보낼 수 있다) */
        private Spec email() {
            this.emailByDefault = true;
            return this;
        }

        private Spec link(String template) {
            this.link = template;
            return this;
        }

        private Spec collapse(int seconds) {
            this.collapse = seconds;
            return this;
        }

        private Spec reserved() {
            this.ready = false;
            return this;
        }
    }

    private static Spec spec(NotificationArea area, NotificationSeverity severity) {
        return new Spec(area, severity);
    }

    private static final Map<String, AdminNotificationEvent> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(AdminNotificationEvent::code, Function.identity()));

    private final NotificationArea area;
    private final NotificationSeverity severity;
    private final NotificationRecipientRule recipient;
    private final List<Permission> recipientPermissions;
    private final Permission requiredPermission;
    private final boolean excludeActor;
    private final boolean mandatory;
    private final boolean emailable;
    private final boolean emailByDefault;
    private final String linkPath;
    private final int collapseSeconds;
    private final boolean featureReady;
    private final boolean broadcastAllowed;

    AdminNotificationEvent(Spec spec) {
        // 본인에게만 가는 이벤트(SELF)와 필수(보안) 이벤트는 다른 사람·공유 채널로 퍼뜨리면 안 된다 — 코드가 정한다(화면·DB 에서 바꾸지 않는다)
        this.broadcastAllowed = spec.recipient != NotificationRecipientRule.SELF && !spec.mandatory;
        this.area = spec.area;
        this.severity = spec.severity;
        this.recipient = spec.recipient;
        this.recipientPermissions = spec.holders;
        this.requiredPermission = spec.requires;
        this.excludeActor = spec.excludeActor;
        this.mandatory = spec.mandatory;
        this.emailable = spec.ready;
        this.emailByDefault = spec.emailByDefault;
        this.linkPath = spec.link;
        this.collapseSeconds = spec.collapse;
        this.featureReady = spec.ready;
    }

    /** 화면·DB 와 주고받는 이벤트 코드 — 상수 이름. 한 번 정하면 바꾸지 않는다 */
    public String code() {
        return name();
    }

    public NotificationArea area() {
        return area;
    }

    /** 기본 심각도 — 발행 때 상황에 따라 올릴 수 있다(맥락의 severity) */
    public NotificationSeverity severity() {
        return severity;
    }

    public NotificationRecipientRule recipient() {
        return recipient;
    }

    /** {@link NotificationRecipientRule#PERMISSION_HOLDERS} 일 때 받을 권한들(이 중 하나라도 가지면 받는다). 그 밖은 빈 목록 */
    public List<Permission> recipientPermissions() {
        return recipientPermissions;
    }

    public boolean excludeActor() {
        return excludeActor;
    }

    /** 알림을 보는 데 필요한 권한. 없으면(null) 누구나 */
    public Permission requiredPermission() {
        return requiredPermission;
    }

    public boolean mandatory() {
        return mandatory;
    }

    /** 처음 사용 여부 — 모든 이벤트가 켜진 채 시작한다 */
    public boolean defaultEnabled() {
        return true;
    }

    /**
     * 이메일로 보낼 수 있는가 — <b>준비 중이 아닌 이벤트는 모두</b> 보낼 수 있다(개정 3, 2026-10-23). 본인 전용·보안 이벤트도 받는 사람이 본인이라 이메일은 막지 않는다
     * (공유 채널·팀은 {@link #broadcastAllowed()} 가 따로 막는다). 메일 양식은 지금 한 가지({@code NotificationMailTemplate}).
     */
    public boolean emailable() {
        return emailable;
    }

    /** 처음 이메일 전달 값 — 코드가 {@code email()} 로 정한 이벤트만 켜진 채 시작한다(예전에 이메일을 보내던 5개). 나머지는 운영자가 켠다 */
    public boolean defaultEmailEnabled() {
        return emailable && emailByDefault;
    }

    /** 이동 경로 템플릿(없으면 null) */
    public String linkPath() {
        return linkPath;
    }

    public int collapseSeconds() {
        return collapseSeconds;
    }

    public boolean featureReady() {
        return featureReady;
    }

    /**
     * 추가 수신 팀과 공유 채널(Slack · Mattermost)로 보낼 수 있는 이벤트인가. 본인에게만 가는 이벤트({@code *_ME} — 수신 규칙 {@code SELF})와 필수(보안) 이벤트는 false 다 —
     * 다른 사람이나 여럿이 보는 채널에 나가면 안 된다. 이메일·브라우저 알림은 본인 몫이라 이 값과 상관없다.
     */
    public boolean broadcastAllowed() {
        return broadcastAllowed;
    }

    /** 이름의 메시지 번들 키 */
    public String nameKey() {
        return key("name");
    }

    public String descriptionKey() {
        return key("description");
    }

    /** 「누가 받는가」 문구의 키 — 수신 규칙은 코드에 고정이라 이벤트마다 한 줄로 적는다(권한 이름은 화면 쪽에 있어 서버가 지어내지 않는다) */
    public String recipientsKey() {
        return key("recipients");
    }

    public String titleKey() {
        return key("title");
    }

    public String bodyKey() {
        return key("body");
    }

    /** 이 이벤트가 번들에 가져야 하는 문구 키 다섯 개 */
    public List<String> bundleKeys() {
        return List.of(nameKey(), descriptionKey(), recipientsKey(), titleKey(), bodyKey());
    }

    private String key(String part) {
        return "notification.event." + name() + "." + part;
    }

    /** 코드로 찾는다. 모르는 코드면 비어 있다 */
    public static Optional<AdminNotificationEvent> ofCode(String code) {
        return Optional.ofNullable(code == null ? null : BY_CODE.get(code));
    }

    /** 운영자가 이 권한 코드를 가졌는지 {@code can} 으로 판정했을 때 그 운영자에게 보이는 이벤트(발행처가 있는 것만) */
    public static List<AdminNotificationEvent> visibleTo(Predicate<String> can) {
        return Arrays.stream(values()).filter(AdminNotificationEvent::featureReady).filter(event -> event.isVisibleTo(can)).toList();
    }

    /** 알림을 보는 데 필요한 권한이 없거나 {@code can} 이 그 권한을 인정하는가 */
    public boolean isVisibleTo(Predicate<String> can) {
        return requiredPermission == null || can.test(requiredPermission.code());
    }
}
