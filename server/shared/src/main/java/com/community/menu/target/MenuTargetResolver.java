package com.community.menu.target;

import com.community.menu.dto.MenuNode;
import com.community.menu.service.MenuFieldErrors;

/**
 * 메뉴 항목의 <b>연결 유형</b> 하나 — 새 유형은 이 인터페이스를 구현한 컴포넌트 하나({@link MenuTargetRegistry} 가 {@link #type()} 으로 모은다)와
 * 관리자 화면의 대상 고르기, 메시지 번들의 {@code menu.targetType.<유형>} 이면 된다. 표에는 CHECK 가 없다(모르는 유형은 보존된다).
 *
 * <p>리졸버는 DB 를 읽지 않는다 — 부르는 쪽이 읽어 둔 스냅샷({@link MenuValidationContext}·{@link MenuResolveContext})만 본다.
 */
public interface MenuTargetResolver {

    /** 유형 코드 — 대문자·밑줄, 한 번 정하면 바꾸지 않는다(DB 에 그대로 저장된다) */
    String type();

    /** 관리자 대상 고르기 모양 — {@code BOARD_SELECT} · {@code BUILTIN_SELECT} · {@code PATH_INPUT} · {@code URL_INPUT} */
    String picker();

    /** 이름을 비울 수 없는가(따라갈 대상 이름이 없는 유형) */
    boolean labelRequired();

    /** 대상 칸의 이름 — 대상 오류·중복 오류의 경로({@code nodes[0].targetId}) */
    String targetField();

    /** 한 그룹 안에서 한 번만 연결할 수 있는 대상의 이름표({@code BOARD:<id>}) — 중복을 허용하는 유형은 null */
    String linkId(MenuNode item);

    /** 저장 모양으로 정리 — 이 유형이 쓰지 않는 칸을 비우고 값을 다듬는다(검사 전에 부른다) */
    MenuNode normalize(MenuNode item);

    /** 대상·설정 검사 — 틀린 칸을 {@code errors} 에 모은다(던지지 않는다) */
    void validate(MenuNode item, MenuValidationContext context, MenuFieldErrors errors);

    /** 관리자 상태 */
    MenuTargetStatus status(MenuNode item, MenuResolveContext context);

    /** 보는 사람에게 보일 대상 — 상태가 {@code OK} 가 아니거나 이 사람에게 보일 수 없으면(로그인 필요 화면을 비로그인이 봄) null */
    ResolvedTarget resolve(MenuNode item, MenuResolveContext context);
}
