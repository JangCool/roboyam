package com.community.menu.target;

import com.community.menu.dto.MenuNode;
import com.community.menu.service.MenuFieldErrors;
import com.community.menu.service.MenuRules;
import com.community.menu.service.MenuSettingsJson;
import org.springframework.stereotype.Component;

/**
 * {@code PATH} — 사이트 안 주소. {@code settings {path, newTab}}, 이름 필수, 주소 규칙 = {@link MenuRules#pathProblem}. 같은 주소를 여러 번 넣을 수 있다.
 */
@Component
public class PathMenuTargetResolver implements MenuTargetResolver {

    public static final String TYPE = "PATH";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String picker() {
        return "PATH_INPUT";
    }

    @Override
    public boolean labelRequired() {
        return true;
    }

    @Override
    public String targetField() {
        return "settings.path";
    }

    @Override
    public String linkId(MenuNode item) {
        return null;
    }

    @Override
    public MenuNode normalize(MenuNode item) {
        String path = MenuSettingsJson.text(item.settings(), "path");
        boolean newTab = MenuSettingsJson.flag(item.settings(), "newTab");
        return new MenuNode(item.id(), item.kind(), item.label(), item.nameEn(), item.visible(), item.audience(), item.minGrade(), false, false, false,
                TYPE, null, null, MenuSettingsJson.address("path", path == null ? "" : path.strip(), newTab), item.mobileTab(), item.children());
    }

    @Override
    public void validate(MenuNode item, MenuValidationContext context, MenuFieldErrors errors) {
        errors.add(targetField(), MenuRules.pathProblem(MenuSettingsJson.text(item.settings(), "path")));
    }

    @Override
    public MenuTargetStatus status(MenuNode item, MenuResolveContext context) {
        return MenuTargetStatus.OK;
    }

    @Override
    public ResolvedTarget resolve(MenuNode item, MenuResolveContext context) {
        String path = MenuSettingsJson.text(item.settings(), "path");
        if (path == null || path.isBlank()) {
            return null;
        }
        return new ResolvedTarget(path, false, null, null, MenuSettingsJson.flag(item.settings(), "newTab"), null, null);
    }
}
