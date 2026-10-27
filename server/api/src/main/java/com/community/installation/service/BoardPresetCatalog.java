package com.community.installation.service;

import com.community.board.service.BoardFields;
import com.community.group.service.GroupCategoryNames;
import com.community.installation.dto.BoardPreset;
import com.community.installation.dto.response.BoardPresetBoardItem;
import com.community.installation.dto.response.BoardPresetItem;
import com.community.installation.dto.response.BoardPresetsResponse;
import com.community.menu.service.MenuLimits;
import com.community.menu.service.MenuRules;
import com.community.menu.target.MenuBuiltin;
import com.community.site.service.SitePolicy;
import com.community.zconfiguration.message.MessageRef;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 설치 때 고를 수 있는 <b>기본 게시판 구성(프리셋)</b>의 목록.
 *
 * <p>프리셋은 코드에 박지 않고 {@code install-presets/*.json} 데이터다 — 새 구성은 파일 하나를 더 두면 된다. 서버가 뜰 때 한 번 읽어 <b>모양을 검사하고</b>,
 * 틀린 것이 있으면 앱이 뜨지 않는다(설치 마법사가 한 번뿐인 트랜잭션 안에서 틀린 데이터에 걸려 실패하지 않게).
 * 게시판을 실제로 만드는 적용은 {@link BoardPresetApplier} 가 한다(설치 트랜잭션 안, 그리고 이미 설치된 곳은 서버가 뜰 때 한 번).
 *
 * <p>검사하는 것: 아이디 형식·유일, 기본값 정확히 하나, 이름·설명, 게시판 주소 형식·유일, 게시판 이름 유일, 말머리 이름 유일,
 * 사이트 슬로건({@code siteTagline})은 사이트 설정의 슬로건 규칙을 지킴, 영문 이름·설명({@code nameEn}·{@code descriptionEn})이 있음,
 * 소모임 게시판은 소모임 분류 이름({@code groupCategoryName} — {@link GroupCategoryNames} 규칙)이 있고 일반 게시판은 없음,
 * 메뉴({@code menu})는 메뉴 계약의 한도·규칙(노드 유형·폴더 깊이 ≤ 3·항목 깊이 ≤ 4·노드 ≤ 80·모바일 탭 ≤ 10·자동 추가 폴더 하나·앱 화면 키와 로그인 필요 화면의 보이는 대상·
 * 게시판은 이 프리셋의 일반 게시판·같은 대상 한 번·이름 길이)을 지킴. 설치 때 메뉴 검사기({@code MenuTreeValidator})가 한 번 더 본다.
 */
@Component
public class BoardPresetCatalog {

    /** 프리셋 파일 위치 */
    static final String LOCATION = "classpath*:install-presets/*.json";

    private static final Pattern PRESET_ID = Pattern.compile("[A-Z][A-Z0-9_]{1,19}");
    private static final Pattern SLUG = Pattern.compile("[a-z][a-z0-9-]{1,39}");
    private static final int NAME_MAX = 40;
    private static final int DESCRIPTION_MAX = 200;
    private static final int PREFIX_NAME_MAX = 20;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 설치 화면의 순서(order, 아이디) 대로 */
    private final Map<String, BoardPreset> presets = new LinkedHashMap<>();
    private final BoardPreset defaultPreset;

    /** 클래스패스의 {@code install-presets/*.json} 을 읽는다 */
    public BoardPresetCatalog() {
        List<BoardPreset> loaded = loadAll();
        if (loaded.isEmpty()) {
            throw new IllegalStateException("설치 프리셋이 하나도 없다(" + LOCATION + ")");
        }
        List<BoardPreset> sorted = new ArrayList<>(loaded);
        sorted.sort(Comparator.comparingInt(BoardPreset::order).thenComparing(BoardPreset::id));
        BoardPreset defaultFound = null;
        for (BoardPreset preset : sorted) {
            validate(preset);
            if (presets.put(preset.id(), preset) != null) {
                throw invalid(preset.id(), "아이디가 겹친다");
            }
            if (preset.defaultPreset()) {
                if (defaultFound != null) {
                    throw invalid(preset.id(), "기본 프리셋이 둘 이상이다(" + defaultFound.id() + ", " + preset.id() + ")");
                }
                defaultFound = preset;
            }
        }
        if (defaultFound == null) {
            throw new IllegalStateException("설치 프리셋 중 기본값(defaultPreset)이 하나도 없다");
        }
        this.defaultPreset = defaultFound;
    }

    /** 설치 요청이 고르지 않았을 때 쓰는 프리셋 */
    public BoardPreset defaultPreset() {
        return defaultPreset;
    }

    /** 아이디로 찾는다 — 모르는 아이디면 비어 있다 */
    public Optional<BoardPreset> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(presets.get(id));
    }

    /**
     * 설치 화면에 내보내는 목록 — 이름·설명·게시판 이름·말머리 이름만. 이름·설명은 요청 언어({@code locale}, ko|en)의 값이고 응답 모양은 언어와 무관하다. <b>소모임 게시판은 싣지 않는다</b>: 메뉴·게시판 페이지가 없는 소모임의 그릇이라
     * "이 구성이 만드는 게시판" 목록에 나오면 이름만 보고 메뉴에 생기는 게시판으로 오해한다.
     */
    public BoardPresetsResponse toResponse(Locale locale) {
        List<BoardPresetItem> items = new ArrayList<>();
        for (BoardPreset preset : presets.values()) {
            List<BoardPresetBoardItem> boards = preset.boards().stream()
                    .filter(board -> !board.isGroupBoard())
                    .sorted(Comparator.comparingInt(BoardPreset.Board::sortOrder))
                    .map(board -> new BoardPresetBoardItem(BoardPreset.localized(board.name(), board.nameEn(), locale),
                            board.prefixes().stream().sorted(Comparator.comparingInt(BoardPreset.Prefix::sortOrder)).map(prefix -> BoardPreset.localized(prefix.name(), prefix.nameEn(), locale)).toList()))
                    .toList();
            items.add(new BoardPresetItem(preset.id(), BoardPreset.localized(preset.name(), preset.nameEn(), locale),
                    BoardPreset.localized(preset.description(), preset.descriptionEn(), locale), boards));
        }
        return new BoardPresetsResponse(defaultPreset.id(), items);
    }

    // ── 읽기 ──

    private static List<BoardPreset> loadAll() {
        List<BoardPreset> loaded = new ArrayList<>();
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver(BoardPresetCatalog.class.getClassLoader()).getResources(LOCATION)) {
                try (InputStream in = resource.getInputStream()) {
                    loaded.add(MAPPER.readValue(in, BoardPreset.class));
                } catch (RuntimeException e) {
                    throw new IllegalStateException("설치 프리셋 파일을 읽지 못했다: " + resource.getFilename(), e);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("설치 프리셋 파일 목록을 읽지 못했다(" + LOCATION + ")", e);
        }
        return loaded;
    }

    // ── 검사 ──

    private static void validate(BoardPreset preset) {
        String id = preset.id();
        if (id == null || !PRESET_ID.matcher(id).matches()) {
            throw invalid(String.valueOf(id), "아이디는 영문 대문자·숫자·_ 2~20자다");
        }
        requireText(id, "name", preset.name(), NAME_MAX);
        requireText(id, "description", preset.description(), DESCRIPTION_MAX);
        requireText(id, "nameEn", preset.nameEn(), NAME_MAX);
        requireText(id, "descriptionEn", preset.descriptionEn(), DESCRIPTION_MAX);
        if (preset.boards() == null || preset.boards().isEmpty()) {
            throw invalid(id, "게시판이 하나도 없다");
        }
        Set<String> slugs = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (BoardPreset.Board board : preset.boards()) {
            validateBoard(id, board);
            if (!slugs.add(board.slug())) {
                throw invalid(id, "게시판 주소가 겹친다: " + board.slug());
            }
            if (!names.add(board.name().toLowerCase(Locale.ROOT))) {
                throw invalid(id, "게시판 이름이 겹친다: " + board.name());
            }
        }
        validateSiteTagline(id, preset.siteTagline());
        if (preset.menu() != null) {
            validateMenu(preset);
        }
    }

    // ── 메뉴 ──

    /** 메뉴 트리 — 설치 트랜잭션의 메뉴 검사기({@code MenuTreeValidator})에 걸리지 않게 같은 한도·규칙을 미리 본다 */
    private static void validateMenu(BoardPreset preset) {
        String where = preset.id() + "/menu";
        Set<String> generalSlugs = new HashSet<>();
        preset.boards().stream().filter(board -> !board.isGroupBoard()).forEach(board -> generalSlugs.add(board.slug()));
        MenuWalk walk = new MenuWalk(where, generalSlugs);
        walk.nodes(preset.menu(), 1);
        if (walk.nodes > MenuLimits.NODES) {
            throw invalid(where, "노드가 " + MenuLimits.NODES + "개를 넘는다");
        }
        if (walk.tabs > MenuLimits.MOBILE_TABS) {
            throw invalid(where, "모바일 탭이 " + MenuLimits.MOBILE_TABS + "개를 넘는다");
        }
        if (walk.autoAdd > 1) {
            throw invalid(where, "자동 추가 폴더(autoAdd)는 하나까지다");
        }
    }

    private static final class MenuWalk {

        final String where;
        final Set<String> generalSlugs;
        final Set<String> linked = new HashSet<>();
        int nodes;
        int tabs;
        int autoAdd;

        MenuWalk(String where, Set<String> generalSlugs) {
            this.where = where;
            this.generalSlugs = generalSlugs;
        }

        void nodes(List<BoardPreset.MenuEntry> list, int depth) {
            for (BoardPreset.MenuEntry entry : list) {
                if (entry == null) {
                    throw invalid(where, "빈 노드가 있다");
                }
                nodes++;
                String audience = entry.audienceOrAll();
                if (!MenuLimits.AUDIENCES.contains(audience) || "GRADE_MIN".equals(audience)) {
                    throw invalid(where, "audience 는 ALL·GUEST·MEMBER·STAFF 중 하나다: " + audience);
                }
                check("nameEn", MenuRules.nameEnProblem(entry.nameEn()), entry.nameEn());
                if (entry.nameEn() != null && !entry.nameEn().equals(entry.nameEn().strip())) {
                    throw invalid(where, "nameEn 은 앞뒤 공백이 없어야 한다: " + entry.nameEn());
                }
                if (entry.label() != null && !entry.label().equals(entry.label().strip())) {
                    throw invalid(where, "label 은 앞뒤 공백이 없어야 한다: " + entry.label());
                }
                if (entry.isFolder()) {
                    folder(entry, depth);
                } else {
                    item(entry, depth);
                }
            }
        }

        private void folder(BoardPreset.MenuEntry entry, int depth) {
            check("label", MenuRules.labelProblem(entry.label(), true, MenuLimits.LABEL_MAX), entry.label());
            if (depth > MenuLimits.FOLDER_DEPTH) {
                throw invalid(where, "폴더 깊이는 " + MenuLimits.FOLDER_DEPTH + " 이하다: " + entry.label());
            }
            if (entry.tabOn() || entry.key() != null || entry.slug() != null) {
                throw invalid(where, "폴더는 mobileTab·key·slug 를 쓰지 않는다: " + entry.label());
            }
            if (entry.autoAddOn()) {
                autoAdd++;
            }
            nodes(entry.children(), depth + 1);
        }

        private void item(BoardPreset.MenuEntry entry, int depth) {
            check("label", MenuRules.labelProblem(entry.label(), false, MenuLimits.LABEL_MAX), entry.label());
            if (depth > MenuLimits.ITEM_DEPTH) {
                throw invalid(where, "항목 깊이는 " + MenuLimits.ITEM_DEPTH + " 이하다");
            }
            if (!entry.children().isEmpty() || entry.autoAddOn() || entry.collapsibleOn() || entry.collapsedOn()) {
                throw invalid(where, "항목은 children·autoAdd·collapsible·collapsed 를 쓰지 않는다");
            }
            if (entry.tabOn()) {
                tabs++;
            }
            if (BoardPreset.MenuEntry.BUILTIN.equals(entry.type())) {
                MenuBuiltin builtin = MenuBuiltin.of(entry.key()).orElseThrow(() -> invalid(where, "모르는 앱 화면 키다: " + entry.key()));
                if (entry.slug() != null) {
                    throw invalid(where, "앱 화면 항목은 slug 를 쓰지 않는다: " + entry.key());
                }
                if (builtin.loginRequired() && !MenuLimits.isLoginAudience(entry.audienceOrAll())) {
                    throw invalid(where, "로그인 필요 화면은 audience 가 MEMBER·STAFF 여야 한다: " + entry.key());
                }
                if (!linked.add("BUILTIN:" + entry.key())) {
                    throw invalid(where, "같은 앱 화면이 두 번 있다: " + entry.key());
                }
            } else if (BoardPreset.MenuEntry.BOARD.equals(entry.type())) {
                if (entry.key() != null) {
                    throw invalid(where, "게시판 항목은 key 를 쓰지 않는다: " + entry.slug());
                }
                if (entry.slug() == null || !generalSlugs.contains(entry.slug())) {
                    throw invalid(where, "이 프리셋의 일반 게시판 주소가 아니다(소모임 게시판은 메뉴에 둘 수 없다): " + entry.slug());
                }
                if (!linked.add("BOARD:" + entry.slug())) {
                    throw invalid(where, "같은 게시판이 두 번 있다: " + entry.slug());
                }
            } else {
                throw invalid(where, "type 은 FOLDER·BUILTIN·BOARD 중 하나다: " + entry.type());
            }
        }

        private void check(String field, MessageRef problem, String value) {
            if (problem != null) {
                throw invalid(where, field + " 이(가) 메뉴 규칙(" + problem.key() + ")에 맞지 않다: " + value);
            }
        }
    }

    /** 사이트 슬로건 — 없거나(null), 사이트 설정의 슬로건 규칙({@link SitePolicy#taglineViolation})을 지키는 앞뒤 공백 없는 값이어야 한다(설치 트랜잭션의 DB CHECK 에 걸리지 않게) */
    private static void validateSiteTagline(String presetId, String tagline) {
        if (tagline == null) {
            return;
        }
        if (!tagline.equals(SitePolicy.normalizeTagline(tagline))) {
            throw invalid(presetId, "siteTagline 은 앞뒤 공백 없는 1~" + SitePolicy.TAGLINE_MAX_LENGTH + "자여야 한다");
        }
        SitePolicy.taglineViolation(tagline).ifPresent(reason -> {
            throw invalid(presetId, "siteTagline: " + reason.key());
        });
    }

    private static void validateBoard(String presetId, BoardPreset.Board board) {
        String where = presetId + "/" + board.slug();
        if (board.slug() == null || !SLUG.matcher(board.slug()).matches()) {
            throw invalid(presetId, "게시판 주소 형식이 맞지 않다: " + board.slug());
        }
        requireText(where, "name", board.name(), NAME_MAX);
        requireText(where, "nameEn", board.nameEn(), NAME_MAX);
        // 게시판을 만드는 적용(BoardPresetApplier)이 DB CHECK 에 걸리지 않게 값 목록을 미리 본다
        if (board.description() != null && board.description().length() > DESCRIPTION_MAX) {
            throw invalid(where, "description 은 " + DESCRIPTION_MAX + "자 이하여야 한다");
        }
        if (board.layout() != null && !BoardFields.LAYOUTS.contains(board.layout())) {
            throw invalid(where, "layout 은 " + BoardFields.LAYOUTS + " 중 하나여야 한다");
        }
        if (board.color() != null && !BoardFields.COLORS.contains(board.color())) {
            throw invalid(where, "color 는 " + BoardFields.COLORS + " 중 하나여야 한다");
        }
        if (board.kind() != null && !BoardFields.KINDS.contains(board.kind())) {
            throw invalid(where, "kind 는 " + BoardFields.KINDS + " 중 하나여야 한다");
        }
        if (BoardFields.SLUG_RESERVED.contains(board.slug())) {
            throw invalid(where, "쓸 수 없는 게시판 주소다");
        }
        // 소모임 게시판은 설치 때 이 이름의 분류에 잇는다(분류 표의 이름 규칙 — DB CHECK·유일 키에 걸리지 않게)
        String category = board.groupCategoryName();
        if (board.isGroupBoard()) {
            if (GroupCategoryNames.problem(category).isPresent() || !category.equals(GroupCategoryNames.normalize(category))) {
                throw invalid(where, "소모임 게시판은 groupCategoryName 이 앞뒤·겹친 공백 없는 1~" + GroupCategoryNames.NAME_MAX + "자여야 한다");
            }
        } else if (category != null) {
            throw invalid(where, "groupCategoryName 은 소모임 게시판(kind GROUP)만 쓴다");
        }
        if (board.prefixes() == null || board.prefixes().isEmpty()) {
            throw invalid(where, "말머리가 하나도 없다(게시판은 말머리가 하나 이상 있어야 켤 수 있다)");
        }
        Set<String> prefixNames = new HashSet<>();
        for (BoardPreset.Prefix prefix : board.prefixes()) {
            requireText(where, "말머리 이름", prefix.name(), PREFIX_NAME_MAX);
            requireText(where, "말머리 영문 이름", prefix.nameEn(), PREFIX_NAME_MAX);
            if (!prefixNames.add(prefix.name().toLowerCase(Locale.ROOT))) {
                throw invalid(where, "말머리 이름이 겹친다: " + prefix.name());
            }
        }
    }

    private static void requireText(String where, String field, String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.strip())) {
            throw invalid(where, field + " 은(는) 앞뒤 공백 없는 1~" + max + "자여야 한다");
        }
    }

    private static IllegalStateException invalid(String where, String reason) {
        return new IllegalStateException("설치 프리셋이 올바르지 않다 [" + where + "]: " + reason);
    }
}
