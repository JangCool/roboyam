package com.community.site.service;

import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * 운영자가 붙여 넣은 SVG 소스를 <b>저장·그리기 전에</b> 검사한다(로고·사이트 아이콘). 순수 검사 — 저장소·DB·그리기 라이브러리를 모른다.
 *
 * <p>규칙을 어기면 {@code 400 VALIDATION fields.svgSource}. 위험한 요소는 <b>지우고 받지 않고 거절</b>한다(정화한 결과가 운영자가 본 그림과 달라지는 것을 막는다). 순서:
 * 비었음 → 길이(100,000자) → DOCTYPE·ENTITY → xml-stylesheet → XML 읽기 → 뿌리 {@code svg} → (아이콘만) 정사각({@code viewBox} 또는 {@code width}·{@code height}) →
 * {@code script} → {@code foreignObject} → {@code on*} 속성 → 외부 {@code href} → 외부 {@code url()}·{@code @import}.
 *
 * <p>XML 은 DTD·외부 엔티티를 끈 StAX 로 읽는다. 통과한 소스는 XML 선언을 떼고, 뿌리에 {@code xmlns} 가 없으면 SVG 이름 공간을 넣은 글({@link Inspected#source})로 돌려준다.
 */
final class SvgInspector {

    /** 소스 글자 수 상한(UTF-16 글자 수) */
    static final int MAX_CHARS = 100_000;
    static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";
    /** 오류가 가리키는 입력칸 — 프론트 필드명 */
    static final String FIELD = "svgSource";

    /** 검사를 통과한 소스 — 저장·그리기에 쓸 글(XML 선언을 떼고, 이름 공간이 없으면 {@code xmlns} 를 넣었다) */
    record Inspected(String source) {
    }

    private static final Pattern DOCTYPE_OR_ENTITY = Pattern.compile("<!(?:DOCTYPE|ENTITY)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLESHEET_PI = Pattern.compile("<\\?xml-stylesheet", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_START = Pattern.compile("url\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_FULL = Pattern.compile("url\\s*\\(\\s*(?:\"([^\"]*)\"|'([^']*)'|([^)\"'\\s]*))\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPORT = Pattern.compile("@import", Pattern.CASE_INSENSITIVE);
    private static final Pattern LENGTH = Pattern.compile("^([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*(px|pt|pc|mm|cm|in|em|ex)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_DECLARATION = Pattern.compile("^<\\?xml[\\s\\S]*?\\?>");

    private SvgInspector() {
    }

    /**
     * 소스를 검사한다 — 규칙에 맞지 않으면 {@link ValidationException}({@code fields.svgSource}).
     *
     * @param requireSquare 정사각이어야 하는가(사이트 아이콘 true, 로고 false)
     */
    static Inspected inspect(String raw, boolean requireSquare) {
        if (raw == null || raw.isBlank()) {
            throw invalid("site.svg.empty");
        }
        if (raw.length() > MAX_CHARS) {
            throw invalid("site.svg.length", String.format(Locale.ROOT, "%,d", MAX_CHARS));
        }
        String text = raw.strip();
        if (text.startsWith("﻿")) {
            text = text.substring(1).stripLeading();
        }
        if (DOCTYPE_OR_ENTITY.matcher(text).find()) {
            throw invalid("site.svg.doctype");
        }
        if (STYLESHEET_PI.matcher(text).find()) {
            throw invalid("site.svg.stylesheet");
        }

        Model model = parse(text);
        if (!model.rootIsSvg) {
            throw invalid("site.svg.notSvg");
        }
        if (requireSquare) {
            requireSquare(model);
        }
        requireSafe(model);

        return new Inspected(forRenderer(text, model.rootHasNamespace));
    }

    // ── XML 읽기 → 모형 ──

    private record Attribute(String qualifiedName, String localName, String value) {
    }

    private record Element(String localName, List<Attribute> attributes) {
    }

    private static final class Model {
        final List<Element> elements = new ArrayList<>();
        /** {@code <style>} 요소 안의 글 */
        final List<String> styleTexts = new ArrayList<>();
        boolean rootIsSvg;
        boolean rootHasNamespace;
        Element root;
    }

    private static Model parse(String text) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        trySet(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        trySet(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        Model model = new Model();
        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(new StringReader(text));
            int depth = 0;
            boolean inStyle = false;
            StringBuilder style = null;
            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.DTD -> throw invalid("site.svg.doctype");
                    case XMLStreamConstants.ENTITY_REFERENCE -> throw invalid("site.svg.entity");
                    case XMLStreamConstants.PROCESSING_INSTRUCTION -> {
                        if ("xml-stylesheet".equalsIgnoreCase(reader.getPITarget())) {
                            throw invalid("site.svg.stylesheet");
                        }
                    }
                    case XMLStreamConstants.START_ELEMENT -> {
                        List<Attribute> attributes = new ArrayList<>();
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            String local = reader.getAttributeLocalName(i);
                            String prefix = reader.getAttributePrefix(i);
                            String qualified = prefix == null || prefix.isEmpty() ? local : prefix + ":" + local;
                            attributes.add(new Attribute(qualified, local, reader.getAttributeValue(i)));
                        }
                        Element element = new Element(reader.getLocalName(), attributes);
                        if (depth == 0) {
                            String namespace = reader.getNamespaceURI();
                            model.root = element;
                            model.rootHasNamespace = namespace != null && !namespace.isEmpty();
                            model.rootIsSvg = "svg".equals(element.localName()) && (!model.rootHasNamespace || SVG_NAMESPACE.equals(namespace));
                        }
                        model.elements.add(element);
                        depth++;
                        if ("style".equals(element.localName())) {
                            inStyle = true;
                            style = new StringBuilder();
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        depth--;
                        if (inStyle && "style".equals(reader.getLocalName())) {
                            model.styleTexts.add(style.toString());
                            inStyle = false;
                        }
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                        if (inStyle) {
                            style.append(reader.getText());
                        }
                    }
                    default -> {
                        // 주석·공백·문서 시작/끝은 볼 것이 없다
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw unreadable(text, e);
        } catch (RuntimeException e) {
            if (e instanceof ValidationException) {
                throw e;
            }
            throw invalid("site.svg.unreadable");
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                    // 닫기 실패는 결과에 영향이 없다
                }
            }
        }
        if (model.root == null) {
            throw invalid("site.svg.unreadable");
        }
        return model;
    }

    private static void trySet(XMLInputFactory factory, String name, Object value) {
        try {
            factory.setProperty(name, value);
        } catch (IllegalArgumentException ignored) {
            // 이 구현이 모르는 속성 — DOCTYPE 은 이미 앞에서 거절한다
        }
    }

    // ── 정사각 ──

    private static void requireSquare(Model model) {
        String viewBox = attribute(model.root, "viewBox");
        if (viewBox != null) {
            String[] parts = viewBox.trim().split("[\\s,]+");
            double[] numbers = new double[4];
            boolean readable = parts.length == 4;
            for (int i = 0; readable && i < 4; i++) {
                try {
                    numbers[i] = Double.parseDouble(parts[i]);
                    readable = Double.isFinite(numbers[i]);
                } catch (NumberFormatException e) {
                    readable = false;
                }
            }
            if (!readable || numbers[2] <= 0 || numbers[3] <= 0) {
                throw invalid("site.svg.viewBox");
            }
            if (!sameLength(numbers[2], numbers[3])) {
                throw invalid("site.svg.notSquare", number(numbers[2]), number(numbers[3]));
            }
            return;
        }
        String width = attribute(model.root, "width");
        String height = attribute(model.root, "height");
        if (width == null || height == null) {
            throw invalid("site.svg.sizeUnknown");
        }
        double w = pixels(width);
        double h = pixels(height);
        if (Double.isNaN(w) || Double.isNaN(h) || w <= 0 || h <= 0) {
            throw invalid("site.svg.sizeUnits");
        }
        if (!sameLength(w, h)) {
            throw invalid("site.svg.notSquare", number(w), number(h));
        }
    }

    /** 문구에 싣는 숫자 — 소수 둘째 자리까지, 뒤의 0 은 뗀다(입력한 글자를 그대로 싣지 않는다) */
    private static String number(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** 길이를 px 로 바꾼다. 읽을 수 없거나 상대 단위(%) 면 NaN */
    private static double pixels(String value) {
        Matcher m = LENGTH.matcher(value.trim());
        if (!m.matches()) {
            return Double.NaN;
        }
        double number;
        try {
            number = Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
        if (!Double.isFinite(number)) {
            return Double.NaN;
        }
        String unit = m.group(2) == null ? "px" : m.group(2).toLowerCase(Locale.ROOT);
        return number * switch (unit) {
            case "pt" -> 96.0 / 72.0;
            case "pc" -> 16.0;
            case "mm" -> 96.0 / 25.4;
            case "cm" -> 96.0 / 2.54;
            case "in" -> 96.0;
            case "em" -> 16.0;
            case "ex" -> 8.0;
            default -> 1.0;
        };
    }

    /** 상대 오차 0.01% 안은 같은 길이로 본다 */
    private static boolean sameLength(double a, double b) {
        return Math.abs(a - b) <= 1e-4 * Math.max(Math.abs(a), Math.abs(b));
    }

    // ── 위험 요소 ──

    private static void requireSafe(Model model) {
        for (Element e : model.elements) {
            if ("script".equals(e.localName())) {
                throw invalid("site.svg.script");
            }
        }
        for (Element e : model.elements) {
            if ("foreignObject".equals(e.localName())) {
                throw invalid("site.svg.foreignObject");
            }
        }
        for (Element e : model.elements) {
            for (Attribute a : e.attributes()) {
                if (a.localName().toLowerCase(Locale.ROOT).startsWith("on")) {
                    throw invalid("site.svg.eventAttribute");
                }
            }
        }
        for (Element e : model.elements) {
            for (Attribute a : e.attributes()) {
                if ("href".equalsIgnoreCase(a.localName()) && !isInternalReference(a.value())) {
                    throw invalid("site.svg.externalHref");
                }
            }
        }
        for (Element e : model.elements) {
            for (Attribute a : e.attributes()) {
                requireInternalUrls(a.value());
            }
        }
        for (String style : model.styleTexts) {
            if (IMPORT.matcher(style).find()) {
                throw invalid("site.svg.externalUrl");
            }
            requireInternalUrls(style);
        }
    }

    /** {@code #아이디}(같은 문서 안) 또는 {@code data:image/…}(내장 이미지)만 안쪽 참조다 */
    private static boolean isInternalReference(String value) {
        if (value == null) {
            return true;
        }
        String v = value.trim();
        return v.startsWith("#") || v.regionMatches(true, 0, "data:image/", 0, "data:image/".length());
    }

    /** 글 안의 {@code url(…)} 이 모두 안쪽 참조인지 본다. 닫히지 않았거나 읽을 수 없는 {@code url(} 도 거절한다 */
    private static void requireInternalUrls(String text) {
        if (text == null || text.length() < 4) {
            return;
        }
        Matcher start = URL_START.matcher(text);
        while (start.find()) {
            Matcher full = URL_FULL.matcher(text);
            full.region(start.start(), text.length());
            if (!full.lookingAt()) {
                throw invalid("site.svg.externalUrl");
            }
            String target = full.group(1) != null ? full.group(1) : full.group(2) != null ? full.group(2) : full.group(3);
            if (!isInternalReference(target)) {
                throw invalid("site.svg.externalUrl");
            }
        }
    }

    // ── 저장·그리기에 쓸 글 ──

    private static String forRenderer(String text, boolean rootHasNamespace) {
        String source = text;
        Matcher declaration = XML_DECLARATION.matcher(source);
        if (declaration.find()) {
            source = source.substring(declaration.end()); // 글자 집합 선언이 읽는 쪽과 어긋나지 않게 뗀다
        }
        if (rootHasNamespace) {
            return source;
        }
        int root = rootTagStart(source);
        // 뿌리 이름이 svg 임은 앞에서 확인했다 — 이름 공간이 없으면 SVG 이름 공간을 넣어 준다
        return source.substring(0, root + 4) + " xmlns=\"" + SVG_NAMESPACE + "\"" + source.substring(root + 4);
    }

    /** 앞머리(공백·주석·처리 지시)를 건너뛴 뿌리 시작 태그({@code <svg})의 위치 */
    private static int rootTagStart(String source) {
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (source.startsWith("<!--", i)) {
                int end = source.indexOf("-->", i + 4);
                i = end < 0 ? n : end + 3;
            } else if (source.startsWith("<?", i)) {
                int end = source.indexOf("?>", i + 2);
                i = end < 0 ? n : end + 2;
            } else {
                break;
            }
        }
        return i;
    }

    // ── 오류 ──

    private static String attribute(Element element, String name) {
        for (Attribute a : element.attributes()) {
            if (a.qualifiedName().equals(name)) {
                return a.value();
            }
        }
        return null;
    }

    /**
     * XML 이 깨졌다. 파서 문구는 실행 환경의 언어로 나오므로 그대로 보이지 않고, 가장 흔한 원인(선언 없는 접두사 — {@code xlink:href} 만 쓰고 {@code xmlns:xlink} 를 빼먹음)만
     * 글에서 찾아 알린다. 접두사는 소스에서 {@code [A-Za-z_][\w.-]*} 로 뽑은 이름만 싣는다.
     */
    private static ValidationException unreadable(String text, XMLStreamException e) {
        int line = e.getLocation() == null ? -1 : e.getLocation().getLineNumber();
        String prefix = undeclaredPrefix(text);
        if (prefix != null) {
            return line > 0 ? invalid("site.svg.unreadable.prefix.at", prefix, String.valueOf(line)) : invalid("site.svg.unreadable.prefix", prefix);
        }
        return line > 0 ? invalid("site.svg.unreadable.at", String.valueOf(line)) : invalid("site.svg.unreadable");
    }

    /** 요소·속성에 쓰였는데 {@code xmlns:접두사=} 로 선언되지 않은 첫 접두사. 없으면 null */
    private static String undeclaredPrefix(String text) {
        Set<String> declared = new HashSet<>(List.of("xml", "xmlns"));
        Matcher decl = Pattern.compile("xmlns:([A-Za-z_][\\w.-]*)\\s*=").matcher(text);
        while (decl.find()) {
            declared.add(decl.group(1));
        }
        Matcher used = Pattern.compile("(?:<|\\s)([A-Za-z_][\\w.-]*):[A-Za-z_][\\w.-]*(?=[\\s=/>])").matcher(text);
        while (used.find()) {
            String prefix = used.group(1);
            if (!declared.contains(prefix) && prefix.length() <= 30) {
                return prefix;
            }
        }
        return null;
    }

    private static ValidationException invalid(String key, Object... args) {
        return ValidationException.of(FIELD, new MessageRef(key, args));
    }
}
