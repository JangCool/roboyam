package com.community.menu.target;

import com.community.menu.dto.MenuNode;
import com.community.menu.dto.MenuPageRef;
import com.community.menu.service.MenuFieldErrors;
import com.community.menu.service.MenuRules;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.stereotype.Component;

/**
 * {@code GADGET_PAGE} — {@code targetId} = {@code gadget_pages.id}(영문 주소가 바뀌어도 따라간다). 기본 이름 = 페이지 이름(+영문 이름), 주소 {@code /p/{key}}(그 아래 주소도 밝다).
 * 한 그룹 안에서 한 번. 로그인·판매 조건은 없다. 페이지가 없어지면 {@code TARGET_MISSING}(공개 메뉴에서 빠진다). menu-contract 「가젯 페이지 연결」.
 */
@Component
public class GadgetPageMenuTargetResolver implements MenuTargetResolver {

    public static final String TYPE = "GADGET_PAGE";

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
        return "targetId";
    }

    @Override
    public String linkId(MenuNode item) {
        return item.targetId() == null ? null : TYPE + ":" + item.targetId();
    }

    @Override
    public MenuNode normalize(MenuNode item) {
        return new MenuNode(item.id(), item.kind(), item.label(), item.nameEn(), item.visible(), item.audience(), item.minGrade(), false, false, false,
                TYPE, MenuRules.stripToNull(item.targetId()), null, null, item.mobileTab(), item.children());
    }

    @Override
    public void validate(MenuNode item, MenuValidationContext context, MenuFieldErrors errors) {
        if (item.targetId() == null) {
            errors.add(targetField(), new MessageRef("menu.target.required"));
            return;
        }
        Long id = MenuResolveContext.parseId(item.targetId());
        if (id == null || !context.pages().containsKey(id)) {
            errors.add(targetField(), new MessageRef("menu.target.missing"));
        }
    }

    @Override
    public MenuTargetStatus status(MenuNode item, MenuResolveContext context) {
        return context.page(item.targetId()) == null ? MenuTargetStatus.TARGET_MISSING : MenuTargetStatus.OK;
    }

    @Override
    public ResolvedTarget resolve(MenuNode item, MenuResolveContext context) {
        MenuPageRef page = context.page(item.targetId());
        if (page == null) {
            return null;
        }
        return new ResolvedTarget("/p/" + page.key(), true, page.name(), page.nameEn(), false, null, null);
    }
}
