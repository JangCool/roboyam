package com.community.menu.target;

import com.community.menu.dto.MenuNode;
import com.community.menu.service.MenuFieldErrors;
import com.community.menu.service.MenuLimits;
import com.community.menu.service.MenuRules;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.message.RequestLocale;
import org.springframework.stereotype.Component;

/**
 * {@code BUILTIN} — {@code targetKey} = 앱 화면 키({@link MenuBuiltin}). 기본 이름 = 번들의 화면 이름(한국어 → {@code label}, 영어 → {@code nameEn}).
 * 한 그룹 안에서 한 번. 로그인 필요 화면은 보이는 대상이 로그인(MEMBER·GRADE_MIN·STAFF)이어야 저장되고, 비로그인에게는 빠진다. 판매 스위치가 필요한 화면은 스위치가 꺼지면 빠진다.
 */
@Component
public class BuiltinMenuTargetResolver implements MenuTargetResolver {

    public static final String TYPE = "BUILTIN";

    private final Messages messages;

    public BuiltinMenuTargetResolver(Messages messages) {
        this.messages = messages;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String picker() {
        return "BUILTIN_SELECT";
    }

    @Override
    public boolean labelRequired() {
        return false;
    }

    @Override
    public String targetField() {
        return "targetKey";
    }

    @Override
    public String linkId(MenuNode item) {
        return item.targetKey() == null ? null : TYPE + ":" + item.targetKey();
    }

    @Override
    public MenuNode normalize(MenuNode item) {
        return new MenuNode(item.id(), item.kind(), item.label(), item.nameEn(), item.visible(), item.audience(), item.minGrade(), false, false, false,
                TYPE, null, MenuRules.stripToNull(item.targetKey()), null, item.mobileTab(), item.children());
    }

    @Override
    public void validate(MenuNode item, MenuValidationContext context, MenuFieldErrors errors) {
        if (item.targetKey() == null) {
            errors.add(targetField(), new MessageRef("menu.target.required"));
            return;
        }
        MenuBuiltin builtin = MenuBuiltin.of(item.targetKey()).orElse(null);
        if (builtin == null) {
            errors.add(targetField(), new MessageRef("menu.target.missing"));
        } else if (builtin.loginRequired() && !MenuLimits.isLoginAudience(item.audience())) {
            errors.add("audience", new MessageRef("menu.audience.login"));
        }
    }

    @Override
    public MenuTargetStatus status(MenuNode item, MenuResolveContext context) {
        MenuBuiltin builtin = MenuBuiltin.of(item.targetKey()).orElse(null);
        if (builtin == null) {
            return MenuTargetStatus.TARGET_MISSING;
        }
        return builtin.salesGated() && !context.salesEnabled() ? MenuTargetStatus.SALES_OFF : MenuTargetStatus.OK;
    }

    @Override
    public ResolvedTarget resolve(MenuNode item, MenuResolveContext context) {
        if (status(item, context) != MenuTargetStatus.OK) {
            return null;
        }
        MenuBuiltin builtin = MenuBuiltin.of(item.targetKey()).orElseThrow();
        if (builtin.loginRequired() && (context.viewer() == null || context.viewer().isGuest())) {
            return null;
        }
        MessageRef label = new MessageRef(builtin.labelKey());
        return new ResolvedTarget(builtin.path(), builtin.matchPrefix(), messages.get(label, RequestLocale.KO), messages.get(label, RequestLocale.EN), false,
                null, null);
    }

    /** 앱 화면의 기본 이름(요청 언어) — 관리자 대상 고르기 등 */
    public String labelOf(MenuBuiltin builtin) {
        return messages.get(new MessageRef(builtin.labelKey()));
    }
}
