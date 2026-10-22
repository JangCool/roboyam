package com.community.menu.target;

import com.community.menu.dto.MenuBoardRef;
import com.community.menu.dto.MenuNode;
import com.community.menu.service.MenuFieldErrors;
import com.community.menu.service.MenuRules;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.stereotype.Component;

/**
 * {@code BOARD} — {@code targetId} = {@code boards.id}. 기본 이름 = 게시판 이름(+영문명), 주소 {@code /boards/{slug}}(그 아래 주소도 밝다).
 * 꺼진 게시판은 넣을 수 있지만 공개 메뉴에서 빠진다(상태 {@code BOARD_INACTIVE}). 한 그룹 안에서 한 번.
 */
@Component
public class BoardMenuTargetResolver implements MenuTargetResolver {

    public static final String TYPE = "BOARD";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String picker() {
        return "BOARD_SELECT";
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
        if (id == null || !context.boards().containsKey(id)) {
            errors.add(targetField(), new MessageRef("menu.target.missing"));
        }
    }

    @Override
    public MenuTargetStatus status(MenuNode item, MenuResolveContext context) {
        MenuBoardRef board = context.board(item.targetId());
        if (board == null) {
            return MenuTargetStatus.TARGET_MISSING;
        }
        return board.active() ? MenuTargetStatus.OK : MenuTargetStatus.BOARD_INACTIVE;
    }

    @Override
    public ResolvedTarget resolve(MenuNode item, MenuResolveContext context) {
        if (status(item, context) != MenuTargetStatus.OK) {
            return null;
        }
        MenuBoardRef board = context.board(item.targetId());
        return new ResolvedTarget("/boards/" + board.slug(), true, board.name(), board.nameEn(), false, board, null);
    }
}
