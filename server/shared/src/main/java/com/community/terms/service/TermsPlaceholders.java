package com.community.terms.service;

import com.community.site.dto.SiteOperatorValues;
import com.community.site.entity.SiteOperatorStage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 약관 본문의 자리표시({@code {{…}}})를 운영 주체 값으로 바꾸는 순수 함수 — 게시할 때 부른다(docs/features/terms-operator-contract.md 「규칙」).
 * <b>규칙의 정본은 admin-web {@code apps/admin-web/app/utils/util-terms-placeholder.ts} 머리 주석(규칙 1~5)</b>이고, 관리자 화면의 [미리보기]와 같은 결과를 낸다.
 *
 * <ol>
 *   <li>자리표시는 {@code {{이름}}}(안쪽 앞뒤 공백 허용). 아는 이름은 {@link #NAMES} 열 개뿐이다.</li>
 *   <li>모르는 이름은 바꾸지 않고 두고 {@link Result#unknown()}(처음 나온 차례, 중복 없음)으로 돌려준다 — 게시는 이것이 있으면 거절한다.
 *       아는 이름인데 바꿀 값이 비었으면(운영 주체 설정에 그 칸을 적지 않음) {@link Result#empty()}(같은 차례) — 게시는 이것도 거절한다(빈칸 게시본 방지).</li>
 *   <li>{@code {{운영자정보}}} 는 단계에 맞는 목록(빈 값 줄은 뺀다). 문단 하나를 통째로 차지하면 {@code <ul><li>이름표: 값</li>…</ul>}, 글 중간이면 「이름표 값, 이름표 값」 한 줄.</li>
 *   <li>바꿔 넣는 값은 HTML 이스케이프한다({@code & < > " '}). 이름표·호칭은 고정 한국어(약관 본문이 한국어다).</li>
 *   <li>서식 경계로 쪼개진 것({@code {{운영<strong>자</strong>}}})은 자리표시로 보지 않는다.</li>
 * </ol>
 * 공백은 자바스크립트 {@code \s}·{@code trim()} 과 같은 글자(유니코드 공백 + BOM)로 본다.
 *
 * <p>저장된 초안은 {@link TermsHtml} 정화를 거쳐 {@code {{} 가 {@code {<!-- -->{} 로 쪼개져 있다(정화기가 클라이언트 템플릿 주입을 막으려고 넣는다).
 * 바꾸기 전에 {@link #unsplitBraces} 로 되돌린다 — 정화된 HTML 에는 다른 주석이 남지 않으므로 이 조각은 정화기가 넣은 것뿐이다.
 */
public final class TermsPlaceholders {

    /** 아는 자리표시 이름 — 화면 칩 차례 */
    public static final List<String> NAMES = List.of("운영자", "운영자이름", "서비스이름", "운영자정보", "보호책임자", "보호책임자연락처", "연락이메일", "호스팅사업자",
            "메일발송사업자", "시행일");

    /** 자바스크립트 {@code \s} 와 같은 공백 */
    private static final String WS = "[\\t\\n\\x0B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]";
    private static final Pattern TOKEN = Pattern.compile("\\{\\{" + WS + "*([^{}<>]+?)" + WS + "*\\}\\}");
    private static final Pattern INFO_PARAGRAPH = Pattern.compile("<p(?:" + WS + "[^>]*)?>" + WS + "*\\{\\{" + WS + "*운영자정보" + WS + "*\\}\\}" + WS + "*</p>");
    private static final Pattern TRIM = Pattern.compile("^" + WS + "+|" + WS + "+$");
    private static final String SPLIT_BRACE = "{<!-- -->";

    /**
     * 바꿀 값.
     *
     * @param operatorAlias   「운영자」·「회사」
     * @param operatorName    {@code {{운영자이름}}} — 개인은 운영자 이름, 개인사업자는 상호, 법인은 회사명
     * @param operatorInfo    {@code {{운영자정보}}} 목록(빈 값 줄은 이미 뺐다)
     * @param hostingProvider {@code {{호스팅사업자}}}(없으면 빈 글자)
     * @param mailProvider    {@code {{메일발송사업자}}}(없으면 빈 글자)
     * @param effectiveDate   「YYYY년 M월 D일」로 바꾼 글자
     */
    public record Values(String operatorAlias, String operatorName, String serviceName, List<Row> operatorInfo, String privacyOfficer, String privacyOfficerContact,
                         String contactEmail, String hostingProvider, String mailProvider, String effectiveDate) {

        public Values {
            operatorInfo = List.copyOf(operatorInfo);
        }
    }

    /** 운영자 정보 목록 한 줄 */
    public record Row(String label, String value) {
    }

    /**
     * 바꾼 결과.
     *
     * @param unknown 모르는 자리표시 이름(처음 나온 차례, 중복 없음)
     * @param empty   아는 이름인데 바꿀 값이 빈 자리표시 이름(처음 나온 차례, 중복 없음) — 빈 글자로 바뀌었다
     */
    public record Result(String html, List<String> unknown, List<String> empty) {

        public Result {
            unknown = List.copyOf(unknown);
            empty = List.copyOf(empty);
        }
    }

    private TermsPlaceholders() {
    }

    /** {@link LocalDate} → 「YYYY년 M월 D일」(앞 0 없음) */
    public static String koreanDate(LocalDate date) {
        return date.getYear() + "년 " + date.getMonthValue() + "월 " + date.getDayOfMonth() + "일";
    }

    /** 운영 주체 값 → 바꿀 값({@code util-terms-placeholder.ts} 의 {@code valuesOf} 와 같다) */
    public static Values valuesOf(SiteOperatorValues op, String serviceName, LocalDate effectiveDate) {
        SiteOperatorValues.Business b = op.business();
        boolean corp = op.stage() == SiteOperatorStage.CORPORATION;
        List<String[]> rows = new ArrayList<>();
        if (op.stage() == SiteOperatorStage.INDIVIDUAL || b == null) {
            rows.add(new String[] {"운영자", op.operatorName()});
            rows.add(new String[] {"연락 이메일", op.contactEmail()});
            rows.add(new String[] {"전화", op.phone()});
        } else {
            rows.add(new String[] {corp ? "회사명" : "상호", b.businessName()});
            rows.add(new String[] {"대표자", b.representative()});
            rows.add(new String[] {"사업자등록번호", b.registrationNumber()});
            rows.add(new String[] {"법인등록번호", corp ? b.corporateNumber() : null});
            rows.add(new String[] {"사업장 주소", b.address()});
            rows.add(new String[] {"전화", op.phone()});
            rows.add(new String[] {"이메일", op.contactEmail()});
            String mailOrder = present(b.mailOrderNumber())
                    ? (present(b.mailOrderAuthority()) ? b.mailOrderNumber() + " (" + b.mailOrderAuthority() + ")" : b.mailOrderNumber())
                    : null;
            rows.add(new String[] {"통신판매업 신고번호", mailOrder});
        }
        List<Row> info = new ArrayList<>();
        for (String[] row : rows) {
            if (row[1] != null && !trim(row[1]).isEmpty()) {
                info.add(new Row(row[0], trim(row[1])));
            }
        }
        boolean same = op.privacyOfficerSameAsOperator();
        String officerEmail = same ? op.contactEmail() : nz(op.privacyOfficerEmail());
        String officerPhone = same ? op.phone() : null;
        String name = op.stage() == SiteOperatorStage.INDIVIDUAL || b == null ? nz(op.operatorName()) : nz(b.businessName());
        return new Values(corp ? "회사" : "운영자", trim(name), nz(serviceName), info, same ? op.operatorName() : nz(op.privacyOfficerName()),
                present(officerPhone) ? officerEmail + " · " + officerPhone : officerEmail, nz(op.contactEmail()), trim(nz(op.hostingProvider())),
                trim(nz(op.mailProvider())), koreanDate(effectiveDate));
    }

    /** 본문의 자리표시를 바꾼다(규칙은 클래스 주석) */
    public static Result apply(String html, Values values) {
        String list = values.operatorInfo().isEmpty() ? ""
                : "<ul>" + String.join("", values.operatorInfo().stream().map(r -> "<li>" + escape(r.label()) + ": " + escape(r.value()) + "</li>").toList()) + "</ul>";
        String inline = String.join(", ", values.operatorInfo().stream().map(r -> escape(r.label()) + " " + escape(r.value())).toList());
        Map<String, String> known = new LinkedHashMap<>();
        known.put("운영자", escape(values.operatorAlias()));
        known.put("운영자이름", escape(values.operatorName()));
        known.put("서비스이름", escape(values.serviceName()));
        known.put("운영자정보", inline);
        known.put("보호책임자", escape(values.privacyOfficer()));
        known.put("보호책임자연락처", escape(values.privacyOfficerContact()));
        known.put("연락이메일", escape(values.contactEmail()));
        known.put("호스팅사업자", escape(values.hostingProvider()));
        known.put("메일발송사업자", escape(values.mailProvider()));
        known.put("시행일", escape(values.effectiveDate()));

        // 값이 빈 자리표시 — 목록으로 바꾸기 전 본문에서 찾는다(문단 하나짜리 {{운영자정보}} 도 같은 차례에 든다)
        String source = html == null ? "" : html;
        List<String> empty = new ArrayList<>();
        Matcher scan = TOKEN.matcher(source);
        while (scan.find()) {
            String name = trim(scan.group(1));
            String value = known.get(name);
            if (value != null && trim(value).isEmpty() && !empty.contains(name)) {
                empty.add(name);
            }
        }

        String withList = INFO_PARAGRAPH.matcher(source).replaceAll(Matcher.quoteReplacement(list));
        List<String> unknown = new ArrayList<>();
        Matcher m = TOKEN.matcher(withList);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = trim(m.group(1));
            String replacement = known.get(name);
            if (replacement == null) {
                if (!unknown.contains(name)) {
                    unknown.add(name);
                }
                replacement = m.group();
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return new Result(out.toString(), unknown, empty);
    }

    /** 정화기가 쪼갠 {@code {<!-- -->{} 를 {@code {{} 로 되돌린다(정화된 초안을 바꾸기 전에) */
    public static String unsplitBraces(String sanitizedHtml) {
        return sanitizedHtml == null ? "" : sanitizedHtml.replace(SPLIT_BRACE, "{");
    }

    /** HTML 이스케이프 {@code & < > " '} */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** 자바스크립트 {@code trim()} 과 같은 공백 떼기 */
    static String trim(String text) {
        return TRIM.matcher(text).replaceAll("");
    }

    private static boolean present(String value) {
        return value != null && !value.isEmpty();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
