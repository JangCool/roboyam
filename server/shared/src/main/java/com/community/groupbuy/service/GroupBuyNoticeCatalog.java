package com.community.groupbuy.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 상품정보제공고시 카탈로그(계약 결정 87) — 공정위 「전자상거래 등에서의 상품 등의 정보제공에 관한 고시」 Ⅲ-1 품목별 정보(40개 품목)의 <b>서버 정본</b>.
 * 데이터 파일 {@code classpath:groupbuy/notice-catalog-<시행일>.json} 을 모두 읽고, 가장 늦은 시행일이 지금 버전이다. 고시가 바뀌면 새 버전 파일을 더하고
 * 기존 공구의 양식은 만든 때 버전을 그대로 쓴다(양식의 {@code catalog_version}).
 */
@Component
public class GroupBuyNoticeCatalog {

    public static final String DEFAULT_PATTERN = "classpath*:groupbuy/notice-catalog-*.json";

    /** 항목 종류 — 일반원칙 ④(날짜 대체 표기)·⑤(품질보증기준)·⑥(KC 인증정보)이 붙는 항목 표시 */
    public enum ItemKind {
        TEXT, DATE_LIKE, KC_CERT, QUALITY_WARRANTY
    }

    /** 항목 — 원문 이름(name)·괄호 설명(description)·하위 번호 묶음(group, 없으면 null), conditional = 원문에 「…에 한함」 등이 붙은 항목 */
    public record Item(String no, String key, String group, String name, String description, ItemKind kind, boolean required, boolean conditional) {
    }

    /** 품목 */
    public record Category(int no, String code, String name, String scope, String warrantyDisadvantageDisclosure, List<Item> items) {

        public Optional<Item> item(String key) {
            return items.stream().filter(i -> i.key().equals(key)).findFirst();
        }
    }

    /** 한 버전(고시 하나) */
    public record Version(String version, String title, String noticeNo, String effectiveDate, String transcribedAt, String source, Map<String, String> principles,
                          List<Category> categories) {

        public Optional<Category> category(String code) {
            return categories.stream().filter(c -> c.code().equals(code)).findFirst();
        }
    }

    private final Map<String, Version> versions;
    private final Version current;

    @Autowired
    public GroupBuyNoticeCatalog() {
        this(DEFAULT_PATTERN);
    }

    /** 위치 패턴들(시험이 옛 버전 파일을 더한다) */
    public GroupBuyNoticeCatalog(String... patterns) {
        JsonMapper mapper = JsonMapper.builder().build();
        Map<String, Version> loaded = new TreeMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            for (String pattern : patterns) {
                for (Resource resource : resolver.getResources(pattern)) {
                    try (InputStream in = resource.getInputStream()) {
                        Version version = parse(mapper.readTree(in));
                        if (loaded.put(version.version(), version) != null) {
                            throw new IllegalStateException("상품정보제공고시 카탈로그 버전이 겹친다: " + version.version());
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("상품정보제공고시 카탈로그를 읽지 못했다", e);
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException("상품정보제공고시 카탈로그 파일이 없다: " + String.join(", ", patterns));
        }
        this.versions = Collections.unmodifiableMap(loaded);
        this.current = loaded.values().stream().max(Comparator.comparing(Version::effectiveDate)).orElseThrow();
    }

    /** 지금 버전(새 양식이 쓴다) */
    public Version current() {
        return current;
    }

    public Optional<Version> version(String version) {
        return Optional.ofNullable(versions.get(version));
    }

    public List<String> versions() {
        return List.copyOf(versions.keySet());
    }

    private static Version parse(JsonNode root) {
        List<Category> categories = new ArrayList<>();
        for (JsonNode c : root.get("categories")) {
            List<Item> items = new ArrayList<>();
            for (JsonNode i : c.get("items")) {
                items.add(new Item(text(i, "no"), text(i, "key"), text(i, "group"), text(i, "name"), text(i, "description"), ItemKind.valueOf(text(i, "kind")),
                        i.path("required").asBoolean(true), i.path("conditional").asBoolean(false)));
            }
            categories.add(new Category(c.get("no").asInt(), text(c, "code"), text(c, "name"), text(c, "scope"), text(c, "warrantyDisadvantageDisclosure"),
                    List.copyOf(items)));
        }
        Map<String, String> principles = new LinkedHashMap<>();
        JsonNode p = root.path("principles");
        p.propertyNames().forEach(name -> principles.put(name, p.get(name).asString()));
        return new Version(text(root, "version"), text(root, "title"), text(root, "noticeNo"), text(root, "effectiveDate"), text(root, "transcribedAt"),
                text(root, "source"), Collections.unmodifiableMap(principles), List.copyOf(categories));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
