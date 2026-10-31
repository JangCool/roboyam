package com.community.audit.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.response.AuditOption;
import com.community.zconfiguration.message.Messages;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * 감사 로그가 보여 주는 이름의 요청 언어 문구 — 작업({@code audit.action.<코드>}) · 대상 종류({@code audit.targetType.<종류>}) · 결과({@code audit.result.<값>}) · 사유({@code audit.reason.<사유>}).
 * <b>번들에 없는 코드는 코드 그대로</b> 보인다(과거에 쌓인 기록의 옛 작업 이름·새로 더한 작업이 조회를 깨뜨리지 않는다). 기동 때 {@link AuditActions} 의 모든 작업에 이름이 있는지 확인해 빠진 것을 경고한다.
 */
@Component
public class AuditLabels {

    private static final Logger log = LoggerFactory.getLogger(AuditLabels.class);

    /**
     * 서버가 기록하는 대상 종류 — 거르기 선택지의 원천. 새 대상 종류를 기록하면 여기와 번들({@code audit.targetType.*})에 더한다.
     * (OPERATOR_ROLES 는 운영자에게 역할을 줄 권한이 없을 때의 거부 기록 대상이다)
     */
    public static final List<String> TARGET_TYPES = List.of("OPERATOR", "OPERATOR_ROLES", "INVITATION", "ROLE", "GRADE", "MEMBER", "BATCH", "SITE", "ENDPOINT", "NOTIFICATION_EVENT", "MEMBER_EVENT", "TEAM", "NOTIFICATION_CHANNEL", "BOARD", "BOARD_DEFAULTS", "MENU_SET", "ADMIN_MENU", "POINT_SETTINGS", "SOCIAL_PROVIDER", "NOTIFICATION_MESSAGE", "NOTIFICATION_MESSAGE_TARGET", "NOTIFICATION_TEMPLATE", "LOGIN_SECURITY", "TERMS", "NICKNAME_IMAGE", "NICKNAME_IMAGE_SETTINGS", "POPULAR_SETTINGS", "POST", "HIGHLIGHT", "COMMENT", "REPORT_CASE",
            "COMMERCE_SETTINGS", "AUDIT_RETENTION", "PORTONE_SETTINGS", "POINT_SHOP_ITEM", "POINT_SHOP_AVATARS", "POINT_SHOP_SETTINGS", "POINT_SHOP_PURCHASE", "GROUP_CATEGORY", "GROUP",
            "GROUP_SETTINGS", "PAYMENT_ORDER", "PAYMENT_CANCEL", "RECONCILIATION_ISSUE", "PAYMENT_RECONCILE_DAY", "GROUP_BUY", "SHIPPING_POLICY", "ADDRESS_SEARCH", "BUSINESS", "BIZ_APPLICATION");

    /** 결과 3종 — 화면 값. 서버 {@code AuditResult} 는 SUCCESS→OK · FAILURE→FAIL · DENIED→DENIED */
    public static final List<String> RESULTS = List.of("OK", "FAIL", "DENIED");

    private final MessageSource source;
    private final Messages messages;

    public AuditLabels(MessageSource source, Messages messages) {
        this.source = source;
        this.messages = messages;
        warnOnMissing();
    }

    public String actionName(String action) {
        return find("audit.action.", action);
    }

    public String targetTypeName(String targetType) {
        return targetType == null ? null : find("audit.targetType.", targetType);
    }

    public String resultName(String result) {
        return find("audit.result.", result);
    }

    /** 사유 문구. 모르는 사유는 null(문구를 지어내지 않는다) */
    public String reasonText(String reason) {
        return reason == null ? null : lookup("audit.reason." + reason);
    }

    /** 그 키의 문구(요청 언어, 없으면 null) */
    public String text(String key) {
        return lookup(key);
    }

    public List<AuditOption> actionOptions() {
        return AuditActions.all().stream().map(action -> new AuditOption(action, actionName(action))).toList();
    }

    public List<AuditOption> targetTypeOptions() {
        return TARGET_TYPES.stream().map(type -> new AuditOption(type, targetTypeName(type))).toList();
    }

    public List<AuditOption> resultOptions() {
        return RESULTS.stream().map(result -> new AuditOption(result, resultName(result))).toList();
    }

    private String find(String prefix, String code) {
        String found = lookup(prefix + code);
        return found == null ? code : found;
    }

    private String lookup(String key) {
        Locale locale = messages.locale();
        return source.getMessage(key, null, null, locale);
    }

    private void warnOnMissing() {
        for (String action : AuditActions.all()) {
            if (lookup("audit.action." + action) == null) {
                log.warn("감사 작업 이름이 번들에 없다: audit.action.{}", action);
            }
        }
        for (String type : TARGET_TYPES) {
            if (lookup("audit.targetType." + type) == null) {
                log.warn("감사 대상 종류 이름이 번들에 없다: audit.targetType.{}", type);
            }
        }
    }
}
