package com.community.installation.service;

import com.community.board.service.BoardFields;
import com.community.installation.dto.BoardPreset;
import com.community.installation.dto.response.BoardPresetBoardItem;
import com.community.installation.dto.response.BoardPresetItem;
import com.community.installation.dto.response.BoardPresetsResponse;
import com.community.site.service.SitePolicy;
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
 * 사이트 슬로건({@code siteTagline})은 사이트 설정의 슬로건 규칙을 지킴, 영문 이름·설명({@code nameEn}·{@code descriptionEn})이 있음.
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
