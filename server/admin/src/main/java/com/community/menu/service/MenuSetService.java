package com.community.menu.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.menu.dto.request.MenuSetCreateRequest;
import com.community.menu.dto.request.MenuSetUpdateRequest;
import com.community.menu.dto.response.MenuSetView;
import com.community.menu.entity.MenuSetEntity;
import com.community.menu.exception.MenuSetKeyDuplicateException;
import com.community.menu.exception.MenuSetNotFoundException;
import com.community.menu.exception.MenuSetSystemException;
import com.community.menu.repository.master.MenuNodeRepository;
import com.community.menu.repository.master.MenuNodeRepositoryQDSL;
import com.community.menu.repository.master.MenuSetRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메뉴 그룹(메뉴 세트) 목록·만들기·수정·삭제 (docs/features/menu-contract.md 「공개 사이트 메뉴」, admin-web {@code useMenuApi.ts} 머리말).
 *
 * <ul>
 *   <li>입력 오류는 400 {@code VALIDATION} + {@code fields.key|name|nameEn|placement|description}. 코드는 만든 뒤 바꿀 수 없다.</li>
 *   <li>코드 중복 409 {@code MENU_SET_KEY_DUPLICATE}(fields.key) · 기본 그룹 삭제 409 {@code MENU_SET_SYSTEM} · 없는 그룹 404 {@code MENU_SET_NOT_FOUND}.</li>
 *   <li>기본 그룹의 위치는 보낸 값을 무시하고 그대로 둔다(화면 목업과 같다). 위치가 {@code SIDE} 에서 다른 것으로 바뀌면 트리의 모바일 탭·자동 추가를 끄고 트리 버전을 올린다.</li>
 *   <li>삭제는 트리도 함께 지운다(표의 연쇄 삭제). 그룹 행을 잠그고 바꾼다 — 같은 그룹의 트리 저장과 줄 선다.</li>
 *   <li>감사 기록 {@code MENU_SET_CREATE}·{@code MENU_SET_UPDATE}(바뀐 칸만)·{@code MENU_SET_DELETE}.</li>
 * </ul>
 */
@Service
public class MenuSetService {

    private static final Logger log = LoggerFactory.getLogger(MenuSetService.class);

    private final MenuSetRepository sets;
    private final MenuNodeRepository nodes;
    private final MenuNodeRepositoryQDSL nodeQueries;
    private final AuditLogService audit;

    public MenuSetService(MenuSetRepository sets, MenuNodeRepository nodes, MenuNodeRepositoryQDSL nodeQueries, AuditLogService audit) {
        this.sets = sets;
        this.nodes = nodes;
        this.nodeQueries = nodeQueries;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<MenuSetView> list() {
        Map<String, Long> counts = nodeQueries.countsBySet();
        return sets.findAllByOrderByCreatedAtAscSetKeyAsc().stream().map(set -> MenuViews.setView(set, counts.getOrDefault(set.getSetKey(), 0L))).toList();
    }

    @Transactional
    public MenuSetView create(AdminActor actor, MenuSetCreateRequest request) {
        String key = request == null || request.key() == null ? "" : request.key().strip();
        Input in = Input.of(request == null ? null : request.name(), request == null ? null : request.nameEn(), request == null ? null : request.placement(),
                request == null || !Boolean.FALSE.equals(request.enabled()), request == null ? null : request.description());
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        if (key.isEmpty()) {
            invalid.put("key", new MessageRef("menu.set.key.required"));
        } else if (key.length() < MenuLimits.SET_KEY_MIN || key.length() > MenuLimits.SET_KEY_MAX || !MenuLimits.SET_KEY.matcher(key).matches()) {
            invalid.put("key", new MessageRef("menu.set.key.format", String.valueOf(MenuLimits.SET_KEY_MIN), String.valueOf(MenuLimits.SET_KEY_MAX)));
        }
        in.check(invalid);
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        if (sets.existsById(key)) {
            throw new MenuSetKeyDuplicateException();
        }
        Instant now = Instant.now();
        MenuSetEntity set = MenuSetEntity.create(key, in.name, in.nameEn, in.placement, in.enabled, in.description, actor.operatorId(), now);
        sets.persist(set);
        try {
            sets.flush();
        } catch (DataIntegrityViolationException e) {
            // 같은 코드를 동시에 만들었다 — PK 위반
            throw new MenuSetKeyDuplicateException();
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("key", key);
        after.put("name", in.name);
        after.put("placement", in.placement);
        after.put("enabled", in.enabled);
        audit.record(event(actor, AuditActions.MENU_SET_CREATE, key).with("before", Map.of()).with("after", after));
        log.info("메뉴 그룹을 만들었다 key={} by operatorId={}", key, actor.operatorId());
        return MenuViews.setView(set, 0);
    }

    @Transactional
    public MenuSetView update(AdminActor actor, String key, MenuSetUpdateRequest request) {
        MenuSetEntity set = lock(key);
        String placementWanted = request == null ? null : request.placement();
        Input in = Input.of(request == null ? null : request.name(), request == null ? null : request.nameEn(), placementWanted,
                request == null || request.enabled() == null ? set.isEnabled() : request.enabled(), request == null ? null : request.description());
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        in.check(invalid);
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        String placement = set.isSystem() ? set.getPlacement() : in.placement;
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        diff(before, after, "name", set.getName(), in.name);
        diff(before, after, "nameEn", set.getNameEn(), in.nameEn);
        boolean placementChanged = diff(before, after, "placement", set.getPlacement(), placement);
        diff(before, after, "enabled", set.isEnabled(), in.enabled);
        diff(before, after, "description", set.getDescription(), in.description);
        if (after.isEmpty()) {
            return MenuViews.setView(set, nodes.countBySetKey(set.getSetKey()));
        }
        Instant now = Instant.now();
        boolean leavingSide = placementChanged && MenuLimits.isSide(set.getPlacement());
        set.change(in.name, in.nameEn, placement, in.enabled, in.description, actor.operatorId(), now);
        if (leavingSide && nodeQueries.clearSideOnly(set.getSetKey()) > 0) {
            // 모바일 탭·자동 추가는 SIDE 그룹에서만 — 트리가 바뀌었으니 버전을 올린다
            set.treeChanged(actor.operatorId(), now);
        }
        audit.record(event(actor, AuditActions.MENU_SET_UPDATE, set.getSetKey()).with("before", before).with("after", after));
        log.info("메뉴 그룹을 고쳤다 key={} 칸={} by operatorId={}", set.getSetKey(), after.keySet(), actor.operatorId());
        return MenuViews.setView(set, nodes.countBySetKey(set.getSetKey()));
    }

    @Transactional
    public void delete(AdminActor actor, String key) {
        MenuSetEntity set = lock(key);
        if (set.isSystem()) {
            throw new MenuSetSystemException();
        }
        long count = nodes.countBySetKey(set.getSetKey());
        String name = set.getName();
        sets.delete(set);
        sets.flush();
        audit.record(event(actor, AuditActions.MENU_SET_DELETE, key).with("deleted", List.of(key + " (" + name + ")")).with("nodeCount", count));
        log.info("메뉴 그룹을 지웠다 key={} by operatorId={}", key, actor.operatorId());
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** 정리한 그룹 정보 입력 */
    private static final class Input {
        String name;
        String nameEn;
        String placement;
        boolean enabled;
        String description;
        String rawName;
        String rawNameEn;
        String rawDescription;

        static Input of(String name, String nameEn, String placement, boolean enabled, String description) {
            Input in = new Input();
            in.rawName = name;
            in.rawNameEn = nameEn;
            in.rawDescription = description;
            in.name = name == null ? "" : name.strip();
            in.nameEn = MenuRules.stripToNull(nameEn);
            in.placement = placement;
            in.enabled = enabled;
            in.description = MenuRules.stripToNull(description);
            return in;
        }

        void check(Map<String, MessageRef> invalid) {
            MessageRef name = MenuRules.labelProblem(rawName, true, MenuLimits.SET_NAME_MAX);
            if (name != null) {
                invalid.put("name", name);
            }
            MessageRef nameEn = MenuRules.nameEnProblem(rawNameEn);
            if (nameEn != null) {
                invalid.put("nameEn", nameEn);
            }
            if (placement == null || !MenuLimits.PLACEMENTS.contains(placement)) {
                invalid.put("placement", new MessageRef("menu.set.placement"));
            }
            if (description != null && (description.codePointCount(0, description.length()) > MenuLimits.SET_DESCRIPTION_MAX || hasControl(rawDescription))) {
                invalid.put("description", new MessageRef("menu.set.description.length", String.valueOf(MenuLimits.SET_DESCRIPTION_MAX)));
            }
        }

        private static boolean hasControl(String value) {
            return value != null && value.strip().chars().anyMatch(c -> c < 0x20 || c == 0x7f);
        }
    }

    private MenuSetEntity lock(String key) {
        return key == null ? notFound() : sets.findWithLockBySetKey(key).orElseGet(MenuSetService::notFound);
    }

    private static MenuSetEntity notFound() {
        throw new MenuSetNotFoundException();
    }

    /** 다르면 before/after 에 담고 true */
    private static boolean diff(Map<String, Object> before, Map<String, Object> after, String key, Object oldValue, Object newValue) {
        if (Objects.equals(oldValue, newValue)) {
            return false;
        }
        before.put(key, oldValue == null ? "" : oldValue);
        after.put(key, newValue == null ? "" : newValue);
        return true;
    }

    private static AuditEvent event(AdminActor actor, String action, String key) {
        return AuditEvent.byOperator(actor.operatorId(), actor.ip(), action, AuditResult.SUCCESS).target("MENU_SET", key);
    }
}
