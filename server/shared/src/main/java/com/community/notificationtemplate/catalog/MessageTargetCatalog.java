package com.community.notificationtemplate.catalog;

import com.community.membernotification.entity.MemberEvent;
import static com.community.notificationtemplate.catalog.MessageVariable.Sample;

import com.community.notification.entity.AdminNotificationEvent;
import com.community.notificationtemplate.catalog.MessageTarget.InAppText;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 문구 대상 카탈로그 — <b>코드가 원천</b>이다({@code AdminNotificationEvent} · {@code Permission} 과 같은 방식). 대상마다 받는 사람 · 채널 · 변수 · 조건 선택지 ·
 * 기본 앱 알림 문구의 번들 출처를 정한다. 운영자가 화면에서 바꾸는 것은 문구·선택·규칙뿐이다.
 *
 * <ul>
 *   <li>운영자 이벤트 = {@link AdminNotificationEvent} 전부(준비 중 포함). 채널 앱 알림 · 이메일 · Slack · Mattermost · 카카오톡(준비 중) —
 *       공유 채널로 퍼뜨리면 안 되는 이벤트({@code broadcastAllowed=false} — 본인에게만 가는 {@code *_ME})는 Slack · Mattermost 가 없다.</li>
 *   <li>회원 이벤트 = {@link MemberEvent} 중 실제로 일어나는 것. 채널 앱 알림 · 이메일 · 카카오톡(준비 중).</li>
 *   <li>필수 안내 메일 = {@link RequiredMail}. 채널 이메일뿐.</li>
 * </ul>
 * 공통 변수는 받는 사람이 정한다 — 회원 {@code siteName}·{@code siteUrl}·{@code recipientName}, 운영자 {@code siteName}·{@code adminUrl}·{@code recipientName}.
 * 이메일 · Slack · Mattermost · 카카오톡은 이벤트의 「앱 알림 문구로 만든 글」 {@code title}·{@code body} 와 {@code link}·{@code occurredAt} 을 더 쓴다.
 * 대상 코드는 종류(이벤트 · 필수 안내 메일)를 넘어 겹치지 않는다(기동 때 확인).
 */
public final class MessageTargetCatalog {

    // 운영자 이벤트 — 번들 notification.event.<코드>.title|body 의 인자 자리 이름(쓰지 않는 자리는 null). 아래 TARGETS 보다 먼저 초기화돼야 한다
    private static final Map<AdminNotificationEvent, List<String>> OPERATOR_ARGS = Map.of(
            AdminNotificationEvent.BATCH_RUN_FAILED, Arrays.asList("jobName", null, "reason"),
            AdminNotificationEvent.OPERATOR_INVITATION_ACCEPTED, List.of("operatorName"),
            AdminNotificationEvent.OPERATOR_ACCOUNT_CREATED, List.of("operatorName"),
            AdminNotificationEvent.OPERATOR_STATUS_CHANGED_ME, List.of("change"),
            AdminNotificationEvent.OPERATOR_LOCKED_BY_FAILURES, List.of("operatorName"),
            AdminNotificationEvent.ROLES_CHANGED_ME, List.of("change"),
            AdminNotificationEvent.SITE_SETTINGS_CHANGED, List.of("changedItems"),
            AdminNotificationEvent.GRADE_PROMOTION_RUN, List.of("count"));

    // 운영자 이벤트 인자 변수의 번들 id(뜻이 이벤트마다 다르면 id 가 다르다) — 위 이름 중 null 이 아닌 것의 차례
    private static final Map<AdminNotificationEvent, List<String>> OPERATOR_ARG_IDS = Map.of(
            AdminNotificationEvent.BATCH_RUN_FAILED, List.of("jobName", "batchReason"),
            AdminNotificationEvent.OPERATOR_INVITATION_ACCEPTED, List.of("operatorName"),
            AdminNotificationEvent.OPERATOR_ACCOUNT_CREATED, List.of("operatorName"),
            AdminNotificationEvent.OPERATOR_STATUS_CHANGED_ME, List.of("statusChange"),
            AdminNotificationEvent.OPERATOR_LOCKED_BY_FAILURES, List.of("lockedOperatorName"),
            AdminNotificationEvent.ROLES_CHANGED_ME, List.of("roleChange"),
            AdminNotificationEvent.SITE_SETTINGS_CHANGED, List.of("changedItems"),
            AdminNotificationEvent.GRADE_PROMOTION_RUN, List.of("promotedCount"));

    private static final List<MessageTarget> TARGETS = build();
    private static final Map<String, MessageTarget> BY_KEY = index(TARGETS);

    private MessageTargetCatalog() {
    }

    /** 모든 대상 — 회원 이벤트 · 운영자 이벤트 · 필수 안내 메일 차례 */
    public static List<MessageTarget> all() {
        return TARGETS;
    }

    public static Optional<MessageTarget> find(MessageTargetType type, String code) {
        return type == null || code == null ? Optional.empty() : Optional.ofNullable(BY_KEY.get(type.name() + ":" + code));
    }

    public static Optional<MessageTarget> find(String type, String code) {
        return MessageTargetType.of(type).flatMap(found -> find(found, code));
    }

    /** 운영자 이벤트의 대상 */
    public static MessageTarget of(AdminNotificationEvent event) {
        return find(MessageTargetType.EVENT, event.code()).orElseThrow();
    }

    /** 필수 안내 메일의 대상 */
    public static MessageTarget of(RequiredMail mail) {
        return find(MessageTargetType.REQUIRED_MAIL, mail.code()).orElseThrow();
    }

    /** 받는 사람의 공통 변수 */
    public static List<MessageVariable> commonVariables(MailTemplateAudience audience) {
        List<MessageVariable> out = new ArrayList<>();
        out.add(new MessageVariable(MessageVariable.SITE_NAME, "siteName", false, false, Sample.SITE_NAME));
        if (audience == MailTemplateAudience.MEMBER) {
            out.add(new MessageVariable(MessageVariable.SITE_URL, "siteUrl", false, true, Sample.SITE_URL));
        } else if (audience == MailTemplateAudience.OPERATOR) {
            out.add(new MessageVariable(MessageVariable.ADMIN_URL, "adminUrl", false, true, Sample.ADMIN_URL));
        }
        out.add(MessageVariable.of(MessageVariable.RECIPIENT_NAME, "recipientName"));
        return List.copyOf(out);
    }

    public static List<MessageVariable> commonVariables(MessageAudience audience) {
        return commonVariables(MailTemplateAudience.of(audience));
    }

    /** 템플릿 틀의 본문 자리 변수 */
    public static MessageVariable contentVariable() {
        return new MessageVariable(MessageVariable.CONTENT, "content", true, false, Sample.BUNDLE);
    }

    /** 다른 받는 사람에게만 있는 공통 변수 이름(검사의 「이 메일에서는 쓸 수 없는 변수」) — 회원 {@code siteUrl}, 운영자 {@code adminUrl} */
    public static String audienceOnlyVariable(MessageAudience audience) {
        return audience == MessageAudience.MEMBER ? MessageVariable.SITE_URL : MessageVariable.ADMIN_URL;
    }

    // ── 정의 ────────────────────────────────────────────────────────────────

    private static List<MessageTarget> build() {
        List<MessageTarget> out = new ArrayList<>();
        out.addAll(memberEvents());
        for (AdminNotificationEvent event : AdminNotificationEvent.values()) {
            out.add(operatorEvent(event));
        }
        out.addAll(requiredMails());
        return Collections.unmodifiableList(out);
    }

    private static Map<String, MessageTarget> index(List<MessageTarget> targets) {
        Map<String, MessageTarget> out = new LinkedHashMap<>();
        Map<String, MessageTargetType> codes = new LinkedHashMap<>();
        for (MessageTarget target : targets) {
            out.put(target.type().name() + ":" + target.code(), target);
            MessageTargetType seen = codes.putIfAbsent(target.code(), target.type());
            if (seen != null) {
                throw new IllegalStateException("문구 대상 코드가 겹친다: " + target.code());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static MessageTarget operatorEvent(AdminNotificationEvent event) {
        List<String> argNames = OPERATOR_ARGS.getOrDefault(event, List.of());
        List<String> argIds = OPERATOR_ARG_IDS.getOrDefault(event, List.of());
        List<MessageVariable> params = new ArrayList<>();
        int at = 0;
        for (String name : argNames) {
            if (name != null) {
                params.add(MessageVariable.of(name, argIds.get(at++)));
            }
        }
        List<MessageVariable> common = commonVariables(MessageAudience.OPERATOR);
        List<MessageVariable> sent = sentVariables("linkAdmin");
        boolean batch = event == AdminNotificationEvent.BATCH_RUN_FAILED;
        MessageCondition job = MessageCondition.dynamic(MessageCondition.JOB_NAME, MessageCondition.Source.BATCH_JOBS);
        MessageCondition recipient = MessageCondition.fixed(MessageCondition.RECIPIENT_SOURCE, MessageCondition.DEFAULT, MessageCondition.TEAM);

        List<MessageChannelDef> channels = new ArrayList<>();
        channels.add(new MessageChannelDef(MessageChannel.IN_APP, concat(common, params), List.of()));
        channels.add(new MessageChannelDef(MessageChannel.EMAIL, concat(common, params, sent), batch ? List.of(recipient, job) : List.of(recipient)));
        if (event.broadcastAllowed()) {
            channels.add(new MessageChannelDef(MessageChannel.SLACK, concat(common, params, sent), batch ? List.of(job) : List.of()));
            channels.add(new MessageChannelDef(MessageChannel.MATTERMOST, concat(common, params, sent), batch ? List.of(job) : List.of()));
        }
        channels.add(new MessageChannelDef(MessageChannel.KAKAO, concat(common, params, sent), List.of()));

        String path = event.linkPath() == null ? "/" : event.linkPath().replace("{jobName}", "grade-promotion");
        return new MessageTarget(MessageTargetType.EVENT, event.code(), MessageAudience.OPERATOR, event.area().id(), MessageSeverity.of(event.severity()),
                event.nameKey(), event.descriptionKey(), false, channels, new InAppText(event.titleKey(), event.bodyKey(), null, argNames), path);
    }

    private static List<MessageTarget> memberEvents() {
        MessageCondition grouped = MessageCondition.fixed(MessageCondition.GROUPED, MessageCondition.SINGLE, MessageCondition.GROUPED_VALUE);
        MessageCondition hide = MessageCondition.fixed(MessageCondition.HIDE_REASON, MessageCondition.REPORTS, MessageCondition.RIGHTS);
        MessageCondition postHide = MessageCondition.fixed(MessageCondition.HIDE_REASON, MessageCondition.REPORTS, MessageCondition.RIGHTS, MessageCondition.STAFF);
        List<MessageTarget> out = new ArrayList<>();
        out.add(member(MemberEvent.COMMENT, List.of(MessageVariable.of("actorName", "commentActor"), MessageVariable.of("postTitle", "postTitle"),
                        MessageVariable.of("excerpt", "commentExcerpt"), MessageVariable.of("otherCount", "otherCount")),
                new InAppText("member.notification.COMMENT.title", null, "{{excerpt}}", List.of("actorName")), "/boards/free/123", List.of(grouped)));
        out.add(member(MemberEvent.REPLY, List.of(MessageVariable.of("actorName", "replyActor"), MessageVariable.of("excerpt", "replyExcerpt")),
                new InAppText("member.notification.REPLY.title", null, "{{excerpt}}", List.of("actorName")), "/boards/free/123#c-456", List.of()));
        out.add(member(MemberEvent.POINTS_REVOKED, List.of(MessageVariable.of("amount", "revokedAmount"), MessageVariable.of("reason", "revokeReason")),
                new InAppText("member.notification.POINTS_REVOKED.title", null, "{{reason}}", List.of("amount")), "/me/points", List.of()));
        out.add(member(MemberEvent.POST_HIDDEN, List.of(MessageVariable.of("postTitle", "hiddenPostTitle"), MessageVariable.of("reason", "staffHideReason")),
                new InAppText("member.notification.POST_HIDDEN.title", null, "{{postTitle}}", List.of()), "/me/posts", List.of(postHide)));
        out.add(member(MemberEvent.COMMENT_HIDDEN, List.of(MessageVariable.of("excerpt", "hiddenCommentExcerpt")),
                new InAppText("member.notification.COMMENT_HIDDEN.title", null, "{{excerpt}}", List.of()), "/me/comments", List.of(hide)));
        out.add(member(MemberEvent.DM, List.of(MessageVariable.of("actorName", "dmSender")),
                new InAppText("member.notification.DM.title", null, null, List.of("actorName")), "/messages/123", List.of()));
        out.add(member(MemberEvent.NICKNAME_IMAGE_REVIEWED, List.of(MessageVariable.of("result", "nicknameImageResult"), MessageVariable.of("reason", "nicknameImageReason")),
                new InAppText("member.notification.NICKNAME_IMAGE_REVIEWED.title", null, "{{reason}}", List.of("result")), "/settings/nickname-image", List.of()));
        out.add(member(MemberEvent.OPINION_PIN_REQUESTED, List.of(MessageVariable.of("actorName", "pinRequester"), MessageVariable.of("postTitle", "postTitle"),
                        MessageVariable.of("excerpt", "opinionExcerpt")),
                new InAppText("member.notification.OPINION_PIN_REQUESTED.title", null, "{{excerpt}}", List.of("actorName")), "/posts/123", List.of()));
        out.add(member(MemberEvent.OPINION_PIN_REVIEWED, List.of(MessageVariable.of("result", "pinRequestResult"), MessageVariable.of("postTitle", "postTitle"),
                        MessageVariable.of("reason", "pinRejectReason")),
                new InAppText("member.notification.OPINION_PIN_REVIEWED.title", null, "{{reason}}", List.of("result")), "/posts/123", List.of()));
        out.add(member(MemberEvent.POST_REMOVED, List.of(MessageVariable.of("postTitle", "removedPostTitle"), MessageVariable.of("reason", "removeReason")),
                new InAppText("member.notification.POST_REMOVED.title", null, "{{reason}}", List.of("postTitle")), "/me/posts", List.of()));
        return out;
    }

    private static MessageTarget member(MemberEvent event, List<MessageVariable> params, InAppText inApp, String path, List<MessageCondition> conditions) {
        if (!event.featureReady()) {
            throw new IllegalStateException("준비 중인 회원 이벤트는 문구 대상이 아니다: " + event);
        }
        List<MessageVariable> common = commonVariables(MessageAudience.MEMBER);
        List<MessageVariable> sent = sentVariables("linkSite");
        List<MessageChannelDef> channels = List.of(
                new MessageChannelDef(MessageChannel.IN_APP, concat(common, params), conditions),
                new MessageChannelDef(MessageChannel.EMAIL, concat(common, params, sent), conditions),
                new MessageChannelDef(MessageChannel.KAKAO, concat(common, params, sent), List.of()));
        return new MessageTarget(MessageTargetType.EVENT, event.code(), MessageAudience.MEMBER, event.area().id(), null, event.nameKey(), event.descriptionKey(), false,
                channels, inApp, path);
    }

    private static List<MessageTarget> requiredMails() {
        List<MessageTarget> out = new ArrayList<>();
        out.add(required(RequiredMail.MEMBER_EMAIL_VERIFY, List.of(MessageVariable.of("nickname", "nickname"), MessageVariable.requiredLink("verifyLink", "verifyLink"),
                MessageVariable.of("validHours", "verifyValidHours")), List.of(MessageCondition.fixed(MessageCondition.SIGNUP_METHOD, MessageCondition.EMAIL,
                MessageCondition.SOCIAL))));
        out.add(required(RequiredMail.OPERATOR_INVITE, List.of(MessageVariable.requiredLink("inviteLink", "inviteLink"), MessageVariable.of("validHours", "inviteValidHours")),
                List.of(MessageCondition.dynamic(MessageCondition.INVITE_ROLE, MessageCondition.Source.OPERATOR_ROLES))));
        out.add(required(RequiredMail.OPERATOR_CREDENTIALS, List.of(MessageVariable.of("operatorName", "credentialOperatorName"),
                MessageVariable.required("temporaryPassword", "temporaryPassword")), List.of()));
        out.add(required(RequiredMail.OPERATOR_RESET_CODE, List.of(MessageVariable.required("code", "resetCode"), MessageVariable.of("validMinutes", "resetValidMinutes")),
                List.of()));
        out.add(required(RequiredMail.MAIL_TEST, List.of(), List.of()));
        out.add(required(RequiredMail.MEMBER_PASSWORD_RESET, List.of(MessageVariable.of("nickname", "nickname"), MessageVariable.requiredLink("resetLink", "memberResetLink"),
                MessageVariable.of("validMinutes", "memberResetValidMinutes")), List.of()));
        out.add(required(RequiredMail.MEMBER_PASSWORD_SOCIAL, List.of(MessageVariable.of("nickname", "nickname"), MessageVariable.of("providerName", "socialProviderName"),
                MessageVariable.requiredLink("socialLink", "socialLoginLink")), List.of()));
        out.add(required(RequiredMail.MEMBER_EMAIL_CHANGE, List.of(MessageVariable.of("nickname", "nickname"), MessageVariable.requiredLink("changeLink", "emailChangeLink"),
                MessageVariable.of("validHours", "emailChangeValidHours")), List.of()));
        out.add(required(RequiredMail.MEMBER_EMAIL_CHANGE_NOTICE, List.of(MessageVariable.of("nickname", "nickname"), MessageVariable.of("newEmail", "maskedNewEmail")),
                List.of()));
        return out;
    }

    private static MessageTarget required(RequiredMail mail, List<MessageVariable> variables, List<MessageCondition> conditions) {
        List<MessageChannelDef> channels = List.of(new MessageChannelDef(MessageChannel.EMAIL, concat(commonVariables(mail.audience()), variables), conditions));
        return new MessageTarget(MessageTargetType.REQUIRED_MAIL, mail.code(), mail.audience(), null, null, mail.nameKey(), mail.descriptionKey(), mail.security(),
                channels, null, null);
    }

    /** 이메일 · Slack · Mattermost · 카카오톡이 같이 쓰는 「앱 알림 문구로 만든 글」 변수 */
    private static List<MessageVariable> sentVariables(String linkId) {
        return List.of(new MessageVariable(MessageVariable.TITLE, "title", false, false, Sample.IN_APP_TITLE),
                new MessageVariable(MessageVariable.BODY, "body", false, false, Sample.IN_APP_BODY),
                new MessageVariable(MessageVariable.LINK, linkId, false, true, Sample.LINK),
                MessageVariable.of(MessageVariable.OCCURRED_AT, "occurredAt"));
    }

    @SafeVarargs
    private static List<MessageVariable> concat(List<MessageVariable>... lists) {
        List<MessageVariable> out = new ArrayList<>();
        for (List<MessageVariable> list : lists) {
            out.addAll(list);
        }
        return List.copyOf(out);
    }
}
