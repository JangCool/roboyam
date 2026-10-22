package com.community.menu.target;

import com.community.menu.dto.MenuNode;
import com.community.menu.service.MenuFieldErrors;
import com.community.menu.service.MenuRules;
import com.community.menu.service.MenuSettingsJson;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@code URL} — 외부 링크. {@code settings {href, newTab}}, 이름 필수, 주소 규칙 = {@link MenuRules#hrefProblem}. 같은 주소를 여러 번 넣을 수 있다.
 * 공개 응답에서 {@code https://} 주소면 {@code extra.external = true}(작은 외부 링크 아이콘).
 */
@Component
public class UrlMenuTargetResolver implements MenuTargetResolver {

    public static final String TYPE = "URL";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String picker() {
        return "URL_INPUT";
    }

    @Override
    public boolean labelRequired() {
        return true;
    }

    @Override
    public String targetField() {
        return "settings.href";
    }

    @Override
    public String linkId(MenuNode item) {
        return null;
    }

    @Override
    public MenuNode normalize(MenuNode item) {
        String href = MenuSettingsJson.text(item.settings(), "href");
        boolean newTab = MenuSettingsJson.flag(item.settings(), "newTab");
        return new MenuNode(item.id(), item.kind(), item.label(), item.nameEn(), item.visible(), item.audience(), item.minGrade(), false, false, false,
                TYPE, null, null, MenuSettingsJson.address("href", href == null ? "" : href.strip(), newTab), item.mobileTab(), item.children());
    }

    @Override
    public void validate(MenuNode item, MenuValidationContext context, MenuFieldErrors errors) {
        errors.add(targetField(), MenuRules.hrefProblem(MenuSettingsJson.text(item.settings(), "href")));
    }

    @Override
    public MenuTargetStatus status(MenuNode item, MenuResolveContext context) {
        return MenuTargetStatus.OK;
    }

    @Override
    public ResolvedTarget resolve(MenuNode item, MenuResolveContext context) {
        String href = MenuSettingsJson.text(item.settings(), "href");
        if (href == null || href.isBlank()) {
            return null;
        }
        Map<String, Object> extra = MenuRules.isHttps(href) ? Map.of("external", true) : null;
        return new ResolvedTarget(href, false, null, null, MenuSettingsJson.flag(item.settings(), "newTab"), null, extra);
    }
}
