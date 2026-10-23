package com.community.menu.service;

import com.community.menu.dto.AdminMenuNode;
import com.community.permission.entity.Permission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 관리자 화면 레지스트리 — admin-web {@code UtilAdminMenu.ITEMS}(메뉴 정의)를 서버에 옮긴 것이다. 잎 화면의 키 → 주소·권한, 그리고 저장한 적 없을 때 돌려줄 <b>기본 구성</b>
 * (모드·묶음·부모 항목·아이콘·차례)이 여기서 나온다. 화면을 더하면 admin-web 메뉴 정의와 함께 한 줄 더한다.
 *
 * <p>화면 항목의 권한은 이 표가 정하고 저장 요청으로 바꿀 수 없다. 이번 단계에서 관리자 셸은 여전히 admin-web 의 메뉴 정의를 읽는다(구성은 저장만 된다).
 */
public final class AdminScreenRegistry {

    public static final String NORMAL = "normal";
    public static final String SETTINGS = "settings";
    public static final List<String> MODES = List.of(NORMAL, SETTINGS);

    /** 잎 화면 하나 */
    public record Screen(String key, String path, String labelKey, String icon, Permission permission) {
    }

    /** 기본 구성의 노드 — 묶음·부모 항목·화면 */
    private record Def(String kind, String mode, String labelKey, String icon, Screen screen, List<Def> children) {
    }

    private static final List<Def> ITEMS = List.of(
            leaf(NORMAL, s("dashboard", "/", "menu.dashboard", "pi pi-home", null)),
            section(NORMAL, "menu.member", "pi pi-users",
                    leaf(s("members", "/members", "menu.members", "pi pi-user", Permission.MEMBER_READ)),
                    leaf(s("grades", "/grades", "menu.grades", "pi pi-trophy", Permission.GRADE_READ))),
            section(NORMAL, "menu.siteMenu", "pi pi-bars",
                    leaf(s("menusPublic", "/menus/public", "menu.menusPublic", "pi pi-globe", Permission.LAYOUT_READ)),
                    leaf(s("menusAdmin", "/menus/admin", "menu.menusAdmin", "pi pi-desktop", Permission.LAYOUT_READ))),
            section(NORMAL, "menu.board", "pi pi-list",
                    leaf(s("boards", "/boards", "menu.boards", "pi pi-th-large", Permission.BOARD_READ)),
                    folder("menu.boardDefaults", "pi pi-sliders-v",
                            s("boardDefaultsGeneral", "/boards/defaults/general", "menu.boardDefaultsGeneral", null, Permission.BOARD_READ),
                            s("boardDefaultsReport", "/boards/defaults/report", "menu.boardDefaultsReport", null, Permission.BOARD_READ),
                            s("boardDefaultsSecurity", "/boards/defaults/security", "menu.boardDefaultsSecurity", null, Permission.BOARD_READ),
                            s("boardDefaultsNotifications", "/boards/defaults/notifications", "menu.boardDefaultsNotifications", null, Permission.BOARD_READ),
                            s("boardDefaultsPrivacy", "/boards/defaults/privacy", "menu.boardDefaultsPrivacy", null, Permission.BOARD_READ))),
            section(NORMAL, "menu.point", "pi pi-wallet",
                    leaf(s("points", "/points", "menu.points", "pi pi-receipt", Permission.POINT_READ)),
                    leaf(s("pointPolicy", "/points/policy", "menu.pointPolicy", "pi pi-percentage", Permission.POINT_READ))),
            section(SETTINGS, "menu.system", "pi pi-cog",
                    leaf(s("siteGeneral", "/settings/general", "menu.general", "pi pi-sliders-h", Permission.SITE_READ)),
                    leaf(s("socialLogin", "/settings/social-login", "menu.socialLogin", "pi pi-key", Permission.SITE_READ)),
                    leaf(s("teams", "/settings/teams", "menu.teams", "pi pi-sitemap", Permission.TEAM_READ))),
            // 메일 묶음 — 메일 서버만(message-template-contract 「개정 3」·「개정 4」 — 메일 종류·템플릿·레이아웃은 알림·이벤트 묶음의 템플릿·문구로 옮겼다)
            section(SETTINGS, "menu.mail", "pi pi-envelope",
                    leaf(s("mailSettings", "/settings/mail", "menu.mailSettings", "pi pi-server", Permission.SITE_READ))),
            // 알림·이벤트 묶음(개정 4) — 알림 도구 · 템플릿(메일·Slack·Mattermost·카카오톡) · 문구(운영자·회원·필수 안내 메일) · 이벤트(운영자·회원). admin-web UtilAdminMenu 와 같은 키·주소·권한·차례
            section(SETTINGS, "menu.notifications", "pi pi-bell",
                    leaf(s("notificationChannels", "/settings/notification-channels", "menu.notificationChannels", "pi pi-send", Permission.NOTIFICATION_READ)),
                    folder("menu.notificationTemplates", "pi pi-palette",
                            s("mailTemplates", "/settings/notification-templates/mail", "menu.mailTemplates", null, Permission.TEMPLATE_READ),
                            s("slackTemplates", "/settings/notification-templates/slack", "menu.slackTemplates", null, Permission.TEMPLATE_READ),
                            s("mattermostTemplates", "/settings/notification-templates/mattermost", "menu.mattermostTemplates", null, Permission.TEMPLATE_READ),
                            s("kakaoTemplates", "/settings/notification-templates/kakao", "menu.kakaoTemplates", null, Permission.TEMPLATE_READ)),
                    folder("menu.notificationMessages", "pi pi-file-edit",
                            s("operatorMessages", "/settings/notification-messages/operator", "menu.operatorMessages", null, Permission.TEMPLATE_READ),
                            s("memberMessages", "/settings/notification-messages/member", "menu.memberMessages", null, Permission.TEMPLATE_READ),
                            s("requiredMails", "/settings/notification-messages/required-mails", "menu.requiredMails", null, Permission.TEMPLATE_READ)),
                    folder("menu.events", "pi pi-bolt",
                            s("operatorEvents", "/settings/notifications/operator", "menu.eventsOperator", null, Permission.NOTIFICATION_READ),
                            s("memberEvents", "/settings/notifications/member", "menu.eventsMember", null, Permission.NOTIFICATION_READ))),
            section(SETTINGS, "menu.operatorsAndPermissions", "pi pi-users",
                    leaf(s("operators", "/settings/operators", "menu.operators", "pi pi-user-edit", Permission.OPERATOR_READ)),
                    folder("menu.roles", "pi pi-id-card",
                            s("rolesMember", "/settings/roles/member", "menu.rolesMember", null, Permission.ROLE_READ),
                            s("rolesOperator", "/settings/roles/operator", "menu.rolesOperator", null, Permission.ROLE_READ),
                            s("rolesGrade", "/settings/roles/grade", "menu.rolesGrade", null, Permission.ROLE_READ))),
            section(SETTINGS, "menu.batchAndLogs", "pi pi-history",
                    leaf(s("batches", "/settings/batches", "menu.batches", "pi pi-clock", Permission.BATCH_READ)),
                    leaf(s("batchLogs", "/settings/batch-logs", "menu.batchLogs", "pi pi-book", Permission.BATCH_READ)),
                    leaf(s("audit", "/settings/audit", "menu.audit", "pi pi-history", Permission.AUDIT_READ))));

    private static final Map<String, Screen> SCREENS = collectScreens();
    private static final Set<String> LABEL_KEYS = collectLabelKeys();

    private AdminScreenRegistry() {
    }

    /** 화면 키로 찾는다 */
    public static Optional<Screen> screen(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(SCREENS.get(key));
    }

    /** 묶음·부모 항목의 기본 이름 키인가 */
    public static boolean isGroupLabelKey(String labelKey) {
        return LABEL_KEYS.contains(labelKey);
    }

    /** 저장한 적 없을 때의 구성(모드별, ID 없음) — admin-web {@code UtilAdminMenuConfig.seed()} 와 같은 모양 */
    public static Map<String, List<AdminMenuNode>> defaults() {
        Map<String, List<AdminMenuNode>> out = new LinkedHashMap<>();
        MODES.forEach(mode -> out.put(mode, new ArrayList<>()));
        for (Def def : ITEMS) {
            out.get(def.mode()).add(toNode(def, false));
        }
        return out;
    }

    private static AdminMenuNode toNode(Def def, boolean underFolder) {
        if (def.screen() != null) {
            return new AdminMenuNode(null, AdminMenuNode.SCREEN, null, null, null, underFolder ? null : def.screen().icon(), true, AdminMenuNode.ADMIN_SCREEN,
                    def.screen().key(), null, true, null, List.of());
        }
        boolean folder = AdminMenuNode.FOLDER.equals(def.kind());
        List<AdminMenuNode> children = def.children().stream().map(child -> toNode(child, folder)).toList();
        return new AdminMenuNode(null, def.kind(), def.labelKey(), null, null, def.icon(), true, null, null, null, true, null, children);
    }

    private static Screen s(String key, String path, String labelKey, String icon, Permission permission) {
        return new Screen(key, path, labelKey, icon, permission);
    }

    private static Def leaf(Screen screen) {
        return new Def(AdminMenuNode.SCREEN, null, null, null, screen, List.of());
    }

    private static Def leaf(String mode, Screen screen) {
        return new Def(AdminMenuNode.SCREEN, mode, null, null, screen, List.of());
    }

    private static Def section(String mode, String labelKey, String icon, Def... children) {
        return new Def(AdminMenuNode.SECTION, mode, labelKey, icon, null, List.of(children));
    }

    private static Def folder(String labelKey, String icon, Screen... screens) {
        return new Def(AdminMenuNode.FOLDER, null, labelKey, icon, null, java.util.Arrays.stream(screens).map(AdminScreenRegistry::leaf).toList());
    }

    private static Map<String, Screen> collectScreens() {
        Map<String, Screen> out = new LinkedHashMap<>();
        collect(ITEMS, out, new HashSet<>());
        return Map.copyOf(out);
    }

    private static Set<String> collectLabelKeys() {
        Set<String> keys = new HashSet<>();
        collect(ITEMS, new LinkedHashMap<>(), keys);
        return Set.copyOf(keys);
    }

    private static void collect(List<Def> defs, Map<String, Screen> screens, Set<String> labelKeys) {
        for (Def def : defs) {
            if (def.screen() != null) {
                if (screens.put(def.screen().key(), def.screen()) != null) {
                    throw new IllegalStateException("관리자 화면 키가 겹친다: " + def.screen().key());
                }
            } else {
                labelKeys.add(def.labelKey());
                collect(def.children(), screens, labelKeys);
            }
        }
    }
}
