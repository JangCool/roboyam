package com.community.member.service;

import com.community.member.dto.MemberSearchCriteria;
import com.community.member.dto.MemberSearchCriteria.AuthMethod;
import com.community.member.dto.MemberSearchCriteria.BoardRole;
import com.community.member.dto.MemberSearchCriteria.MemberType;
import com.community.member.dto.MemberSearchCriteria.SearchField;
import com.community.member.dto.MemberSearchCriteria.SortField;
import com.community.member.dto.MemberSearchCriteria.StatusChangeKind;
import com.community.member.dto.request.MemberSearchRequest;
import com.community.member.exception.MemberPeriodReversedException;
import com.community.member.exception.MemberPointRangeReversedException;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 회원 목록 조회 조건 해석 — 글자로 받은 쿼리 값을 형식에 맞는 조건({@link MemberSearchCriteria})으로 바꾸고 틀린 값은 칸 이름과 함께 400 으로 알린다
 * ({@code VALIDATION}: 알 수 없는 값·쪽 번호나 크기·정렬 기준, {@code MEMBER_PERIOD_REVERSED}, {@code MEMBER_POINT_RANGE_REVERSED}).
 * 틀린 값은 문구에 싣지 않는다(칸 이름만). 쪽 크기는 1~100, 기본 20. 정렬 기본은 {@code joinedAt,desc}.
 */
@Component
public class MemberSearchParser {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /** 숫자 조건(포인트·쪽 번호)이 받는 절댓값의 한도 */
    private static final long NUMBER_LIMIT = 1_000_000_000_000L;

    public MemberSearchCriteria parse(MemberSearchRequest request) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        MessageRef invalid = new MessageRef("member.query.invalid");

        SearchField searchField = oneOf("searchField", request.searchField(), SearchField::valueOf, SearchField.ALL, fields, invalid);
        String keyword = request.keyword() == null || request.keyword().isBlank() ? null : request.keyword().strip().toLowerCase(Locale.ROOT);

        List<UserStatus> statuses = null;
        List<String> statusNames = split(request.statuses());
        if (!statusNames.isEmpty()) {
            statuses = new ArrayList<>();
            for (String name : statusNames) {
                if ("DORMANT".equals(name)) {
                    continue; // 휴면 상태는 아직 없다 — 그것만 고르면 결과가 비도록 빈 목록으로 남는다
                }
                UserStatus status = parseOrNull(name, UserStatus::valueOf);
                if (status == null) {
                    fields.put("statuses", invalid);
                } else {
                    statuses.add(status);
                }
            }
        }

        List<String> roleIds = emptyToNull(split(request.roleIds()));
        List<String> grades = emptyToNull(split(request.grades()));

        Instant joinedFrom = instant("joinedFrom", request.joinedFrom(), fields, invalid);
        Instant joinedTo = instant("joinedTo", request.joinedTo(), fields, invalid);
        Instant lastLoginFrom = instant("lastLoginFrom", request.lastLoginFrom(), fields, invalid);
        Instant lastLoginTo = instant("lastLoginTo", request.lastLoginTo(), fields, invalid);
        Boolean noLoginHistory = bool("noLoginHistory", request.noLoginHistory(), fields, invalid);
        MemberType memberType = oneOf("memberType", request.memberType(), MemberType::valueOf, null, fields, invalid);
        Boolean emailVerified = bool("emailVerified", request.emailVerified(), fields, invalid);

        List<AuthMethod> authMethods = null;
        List<String> methodNames = split(request.authMethods());
        if (!methodNames.isEmpty()) {
            authMethods = new ArrayList<>();
            for (String name : methodNames) {
                AuthMethod method = parseOrNull(name, AuthMethod::valueOf);
                if (method == null) {
                    fields.put("authMethods", invalid);
                } else {
                    authMethods.add(method);
                }
            }
        }

        Instant dormantFrom = instant("dormantFrom", request.dormantFrom(), fields, invalid);
        Instant dormantTo = instant("dormantTo", request.dormantTo(), fields, invalid);
        StatusChangeKind statusChangedKind = oneOf("statusChangedKind", request.statusChangedKind(), StatusChangeKind::valueOf, null, fields, invalid);
        Instant statusChangedFrom = instant("statusChangedFrom", request.statusChangedFrom(), fields, invalid);
        Instant statusChangedTo = instant("statusChangedTo", request.statusChangedTo(), fields, invalid);
        Long pointMin = number("pointMin", request.pointMin(), fields, invalid);
        Long pointMax = number("pointMax", request.pointMax(), fields, invalid);
        BoardRole boardRole = oneOf("boardRole", request.boardRole(), BoardRole::valueOf, null, fields, invalid);
        Boolean hasSanction = bool("hasSanction", request.hasSanction(), fields, invalid);
        Boolean businessVerified = bool("businessVerified", request.businessVerified(), fields, invalid);

        SortField sortField = SortField.JOINED_AT;
        boolean ascending = false;
        if (request.sort() != null && !request.sort().isBlank()) {
            String[] parts = request.sort().strip().split(",");
            SortField parsed = parts.length >= 1 ? sortFieldOf(parts[0]) : null;
            boolean directionOk = parts.length == 1 || (parts.length == 2 && ("asc".equals(parts[1]) || "desc".equals(parts[1])));
            if (parsed == null || !directionOk) {
                fields.put("sort", new MessageRef("member.sort.invalid"));
            } else {
                sortField = parsed;
                ascending = parts.length == 2 && "asc".equals(parts[1]);
            }
        }

        Long pageNumber = number("page", request.page(), fields, new MessageRef("member.page.range", String.valueOf(MAX_SIZE)));
        Long pageSize = number("size", request.size(), fields, new MessageRef("member.page.range", String.valueOf(MAX_SIZE)));
        if (pageNumber != null && (pageNumber < 0 || pageNumber > Integer.MAX_VALUE / 100)) {
            fields.put("page", new MessageRef("member.page.range", String.valueOf(MAX_SIZE)));
        }
        if (pageSize != null && (pageSize < 1 || pageSize > MAX_SIZE)) {
            fields.put("size", new MessageRef("member.page.range", String.valueOf(MAX_SIZE)));
        }

        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        if (reversed(joinedFrom, joinedTo)) {
            throw new MemberPeriodReversedException("joinedFrom");
        }
        if (reversed(lastLoginFrom, lastLoginTo)) {
            throw new MemberPeriodReversedException("lastLoginFrom");
        }
        if (reversed(dormantFrom, dormantTo)) {
            throw new MemberPeriodReversedException("dormantFrom");
        }
        if (reversed(statusChangedFrom, statusChangedTo)) {
            throw new MemberPeriodReversedException("statusChangedFrom");
        }
        if (pointMin != null && pointMax != null && pointMin > pointMax) {
            throw new MemberPointRangeReversedException();
        }

        return new MemberSearchCriteria(searchField, keyword, statuses, roleIds, joinedFrom, joinedTo, lastLoginFrom, lastLoginTo, Boolean.TRUE.equals(noLoginHistory),
                grades, memberType, emailVerified, authMethods, dormantFrom != null || dormantTo != null, statusChangedKind, statusChangedFrom, statusChangedTo,
                pointMin, pointMax, boardRole, hasSanction, businessVerified, sortField, ascending, pageNumber == null ? 0 : pageNumber.intValue(),
                pageSize == null ? DEFAULT_SIZE : pageSize.intValue());
    }

    private static SortField sortFieldOf(String name) {
        return switch (name.strip()) {
            case "memberNo" -> SortField.MEMBER_NO;
            case "email" -> SortField.EMAIL;
            case "nickname" -> SortField.NICKNAME;
            case "grade" -> SortField.GRADE;
            case "status" -> SortField.STATUS;
            case "joinedAt" -> SortField.JOINED_AT;
            case "lastLoginAt" -> SortField.LAST_LOGIN_AT;
            case "point" -> SortField.POINT;
            case "dormantAt" -> SortField.DORMANT_AT;
            default -> null;
        };
    }

    private static boolean reversed(Instant from, Instant to) {
        return from != null && to != null && from.isAfter(to);
    }

    /** 반복한 값과 쉼표로 이은 값을 모두 낱개로 편다. 빈 조각은 버린다 */
    private static List<String> split(List<String> values) {
        List<String> out = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                for (String part : value.split(",")) {
                    if (!part.isBlank()) {
                        out.add(part.strip());
                    }
                }
            }
        }
        return out;
    }

    private static <T> List<T> emptyToNull(List<T> list) {
        return list.isEmpty() ? null : list;
    }

    private static <E> E oneOf(String field, String value, Function<String, E> parser, E defaultValue, Map<String, MessageRef> errors, MessageRef message) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        E parsed = parseOrNull(value.strip(), parser);
        if (parsed == null) {
            errors.put(field, message);
            return defaultValue;
        }
        return parsed;
    }

    private static <E> E parseOrNull(String value, Function<String, E> parser) {
        try {
            return parser.apply(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant instant(String field, String value, Map<String, MessageRef> errors, MessageRef message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException e) {
            errors.put(field, message);
            return null;
        }
    }

    private static Boolean bool(String field, String value, Map<String, MessageRef> errors, MessageRef message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if ("true".equals(value.strip())) {
            return Boolean.TRUE;
        }
        if ("false".equals(value.strip())) {
            return Boolean.FALSE;
        }
        errors.put(field, message);
        return null;
    }

    private static Long number(String field, String value, Map<String, MessageRef> errors, MessageRef message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.strip());
            if (Math.abs(parsed) > NUMBER_LIMIT) {
                errors.put(field, message);
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            errors.put(field, message);
            return null;
        }
    }
}
