package com.community.site.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notification.service.AdminNotices;
import com.community.permission.dto.AdminActor;
import com.community.site.dto.SiteThemeMenu;
import com.community.site.dto.request.SiteThemeMenuRequest;
import com.community.site.dto.request.SiteThemeRequest;
import com.community.site.dto.response.SiteThemeResponse;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.entity.SiteThemeMenuEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.site.repository.master.SiteThemeMenuRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 사이트 테마({@code site_settings.theme})와 테마별 메뉴 설정({@code site_theme_menus}) 조회·저장. 허용 값은 {@link SiteThemes}·{@link SiteThemeMenus}(서버 코드)다.
 * 바뀐 때만 감사(테마 {@code SITE_THEME_UPDATE} · 메뉴 설정 {@code SITE_THEME_MENU_UPDATE}, 바뀐 칸의 전후)와 관리자 알림(사이트 설정 변경)을 남긴다.
 * 공개 사이트(api)는 자기 사이트 설정 사본({@code PublicSiteService}, 20초)을 다시 읽을 때 따라온다 — 다른 사이트 설정과 같은 전파 방식이다.
 */
@Service
public class SiteThemeService {

    private static final Logger log = LoggerFactory.getLogger(SiteThemeService.class);

    private final SiteSettingRepository settings;
    private final SiteThemeMenuRepository menus;
    private final AuditLogService audit;
    private final AdminNotices notices;

    public SiteThemeService(SiteSettingRepository settings, SiteThemeMenuRepository menus, AuditLogService audit, AdminNotices notices) {
        this.settings = settings;
        this.menus = menus;
        this.audit = audit;
        this.notices = notices;
    }

    /** @param forTheme 메뉴 설정을 볼 테마 — null·빈 값이면 지금 테마, 목록 밖이면 {@code 400 fields.forTheme} */
    @Transactional(readOnly = true)
    public SiteThemeResponse load(String forTheme) {
        String wanted = forTheme == null || forTheme.isBlank() ? null : forTheme.strip();
        if (wanted != null && !SiteThemes.isKnown(wanted)) {
            throw ValidationException.of("forTheme", new MessageRef("siteSetting.theme"));
        }
        SiteSettingEntity row = settings.findById(SiteSettingEntity.ROW_ID).orElseThrow(() -> new CommonException(ErrorCode.NOT_FOUND));
        String current = SiteThemes.normalize(row.getTheme());
        String menuTheme = wanted == null ? current : wanted;
        return view(current, menuTheme, menuOf(menuTheme));
    }

    /** 사이트 테마만 바꾼다(메뉴 설정은 테마마다 따로 — {@link #saveMenu}) → 지금 테마의 메뉴 설정을 담은 모양 */
    @Transactional
    public SiteThemeResponse save(AdminActor actor, SiteThemeRequest request) {
        String theme = request == null || request.theme() == null ? null : request.theme().strip();
        if (!SiteThemes.isKnown(theme)) {
            throw ValidationException.of("theme", new MessageRef("siteSetting.theme"));
        }
        SiteSettingEntity row = lockSettings();
        String before = row.getTheme();
        if (!theme.equals(before)) {
            row.changeTheme(theme, actor.operatorId(), Instant.now());
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_THEME_UPDATE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("before", Map.of("theme", before)).with("after", Map.of("theme", theme)));
            notices.siteSettingsChanged(actor, "siteTheme");
            log.info("사이트 테마를 바꿨다 operatorId={} theme={}", actor.operatorId(), theme);
        }
        return view(theme, theme, menuOf(theme));
    }

    /**
     * 테마 하나의 메뉴 설정을 통째로 저장한다 → 그 테마의 메뉴 설정을 담은 모양. 모르는 테마는 {@code 404}, 칸 오류는 {@code 400 VALIDATION}.
     * 같은 테마의 저장은 사이트 설정 행 잠금으로 줄 세운다(처음 저장의 행 넣기가 겹치지 않게).
     */
    @Transactional
    public SiteThemeResponse saveMenu(AdminActor actor, String themeId, SiteThemeMenuRequest request) {
        if (!SiteThemes.isKnown(themeId)) {
            throw new CommonException(ErrorCode.NOT_FOUND);
        }
        SiteThemeMenu next = SiteThemeMenus.check(request == null ? null : request.toMenu());
        SiteSettingEntity site = lockSettings();
        Instant now = Instant.now();
        SiteThemeMenuEntity row = menus.findWithLockByTheme(themeId).orElse(null);
        SiteThemeMenu before = row == null ? SiteThemeMenus.INITIAL : row.toMenu();
        boolean changed;
        if (row == null) {
            changed = !next.equals(before);
            if (changed) {
                menus.persist(SiteThemeMenuEntity.create(themeId, next, actor.operatorId(), now));
            }
        } else {
            changed = row.change(next, actor.operatorId(), now);
        }
        if (changed) {
            Map<String, Object> from = new LinkedHashMap<>();
            Map<String, Object> to = new LinkedHashMap<>();
            diff(before, next, from, to);
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.SITE_THEME_MENU_UPDATE, AuditResult.SUCCESS).target("SITE", "1")
                    .with("theme", themeId).with("before", from).with("after", to));
            notices.siteSettingsChanged(actor, "siteThemeMenu");
            log.info("테마 메뉴 설정을 바꿨다 operatorId={} theme={} fields={}", actor.operatorId(), themeId, to.keySet());
        }
        return view(SiteThemes.normalize(site.getTheme()), themeId, next);
    }

    private SiteSettingEntity lockSettings() {
        return settings.findWithLockById(SiteSettingEntity.ROW_ID)
                .orElseThrow(() -> ValidationException.of("site", new MessageRef("siteSetting.missing")));
    }

    private SiteThemeMenu menuOf(String theme) {
        return menus.findById(theme).map(SiteThemeMenuEntity::toMenu).orElse(SiteThemeMenus.INITIAL);
    }

    /** 바뀐 칸만 전후 맵에 — 감사 「바뀐 내용」 */
    private static void diff(SiteThemeMenu a, SiteThemeMenu b, Map<String, Object> from, Map<String, Object> to) {
        Map<String, Object> left = flat(a);
        Map<String, Object> right = flat(b);
        left.forEach((key, value) -> {
            if (!value.equals(right.get(key))) {
                from.put(key, value);
                to.put(key, right.get(key));
            }
        });
    }

    private static Map<String, Object> flat(SiteThemeMenu m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("menuPosition", m.menuPosition());
        out.put("topItems", m.topItems());
        out.put("barGroupIds", String.join(",", m.barGroupIds()));
        out.put("menuWidth", m.menuWidth());
        out.put("menuOverflow", m.menuOverflow());
        SiteThemeMenu.Style s = m.style();
        out.put("menuGroupStyle", s.menuGroupStyle());
        out.put("sideGroupStyle", s.sideGroupStyle());
        out.put("menuSize", s.menuSize());
        out.put("mobileMenu", s.mobileMenu());
        out.put("menuDescription", s.menuDescription());
        out.put("stickyMenu", s.stickyMenu());
        out.put("mobileHeader", s.mobileHeader());
        return out;
    }

    private static SiteThemeResponse view(String current, String menuTheme, SiteThemeMenu menu) {
        Map<String, SiteThemeMenu> defaults = SiteThemeMenus.allDefaults();
        return new SiteThemeResponse(current, SiteThemes.IDS, menuTheme, menu.menuPosition(), menu.topItems(), menu.barGroupIds(), menu.menuWidth(),
                menu.menuOverflow(), menu.style(), pick(defaults, SiteThemeMenu::menuPosition), pick(defaults, SiteThemeMenu::topItems),
                pick(defaults, SiteThemeMenu::menuWidth), pick(defaults, SiteThemeMenu::menuOverflow), pick(defaults, SiteThemeMenu::style));
    }

    private static <T> Map<String, T> pick(Map<String, SiteThemeMenu> defaults, Function<SiteThemeMenu, T> field) {
        Map<String, T> out = new LinkedHashMap<>();
        defaults.forEach((id, menu) -> out.put(id, field.apply(menu)));
        return out;
    }
}
