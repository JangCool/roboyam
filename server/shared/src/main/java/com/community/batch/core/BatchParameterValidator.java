package com.community.batch.core;

import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 「지금 실행」·「파라미터 지정 실행」 요청의 파라미터·단계 검사 — 요청 표에 넣을 때 한다(docs/features/batch-contract.md 「0-2 보강」). 틀리면 400 {@code VALIDATION} 이고
 * 칸은 {@code params.<이름>} · {@code steps} 이다(틀린 값은 문구에 싣지 않는다).
 *
 * <ul>
 *   <li>값은 앞뒤 공백을 떼고 빈 값은 보내지 않은 것으로 본다. 필수 파라미터가 없으면 거절.</li>
 *   <li>꼴: {@code DATE} = {@code YYYY-MM-DD}(있는 날짜) · {@code LONG} = 1 이상의 정수(18자리까지) · {@code STRING} = {@value #MAX_STRING}자까지.</li>
 *   <li>작업이 정의하지 않은 이름은 거절(오타로 조용히 무시되지 않게).</li>
 *   <li>단계: 보내지 않으면(null) 모든 단계. 보냈으면 하나 이상이고 모두 <b>고를 수 있는</b> 단계여야 한다 — 고를 수 없는 단계는 고르지 않아도 늘 돈다(확정 규칙 2).</li>
 *   <li>형식이 맞으면 작업별 추가 검사({@link BatchJob#validate})를 한다.</li>
 * </ul>
 */
public final class BatchParameterValidator {

    public static final int MAX_STRING = 200;

    private static final Pattern LONG = Pattern.compile("[1-9][0-9]{0,17}");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    /** 검사를 마친 요청 — 파라미터와 고른 단계(모두면 null, 아니면 정의 순서) */
    public record Validated(BatchParameters parameters, List<String> steps) {
    }

    private BatchParameterValidator() {
    }

    /**
     * @param job    Java 배치의 구현. DB 배치는 null(파라미터 없음 · 고를 수 없는 한 단계)
     * @param params 요청의 파라미터(없으면 null)
     * @param steps  요청의 단계(모두면 null)
     */
    public static Validated validate(BatchJob job, Map<String, String> params, List<String> steps) {
        List<BatchParamDefinition> definitions = job == null ? List.of() : job.params();
        List<BatchStepDefinition> stepDefinitions = job == null ? BatchRegistry.DATABASE_STEPS : job.steps();
        Map<String, MessageRef> fields = new LinkedHashMap<>();

        Map<String, String> given = new LinkedHashMap<>();
        if (params != null) {
            params.forEach((name, value) -> {
                String stripped = value == null ? "" : value.strip();
                if (!stripped.isEmpty()) {
                    given.put(name, stripped);
                }
            });
        }
        Map<String, String> accepted = new LinkedHashMap<>();
        for (BatchParamDefinition definition : definitions) {
            String value = given.remove(definition.name());
            String field = "params." + definition.name();
            if (value == null) {
                if (definition.required()) {
                    fields.put(field, new MessageRef("batch.param.required"));
                }
                continue;
            }
            MessageRef problem = checkValue(definition.type(), value);
            if (problem != null) {
                fields.put(field, problem);
            } else {
                accepted.put(definition.name(), value);
            }
        }
        // 정의에 없는 이름 — 키 자체를 칸 경로에 쓰지 않는다(입력값이 경로가 되지 않게)
        if (!given.isEmpty()) {
            fields.put("params", new MessageRef("batch.param.unknown"));
        }

        List<String> chosen = null;
        if (steps != null) {
            Set<String> selectable = new LinkedHashSet<>();
            stepDefinitions.stream().filter(BatchStepDefinition::selectable).forEach(step -> selectable.add(step.name()));
            Set<String> wanted = new LinkedHashSet<>(steps);
            if (wanted.isEmpty()) {
                fields.put("steps", new MessageRef("batch.steps.empty"));
            } else if (!selectable.containsAll(wanted)) {
                fields.put("steps", new MessageRef("batch.steps.unknown"));
            } else {
                chosen = new ArrayList<>();
                for (BatchStepDefinition step : stepDefinitions) {
                    if (wanted.contains(step.name())) {
                        chosen.add(step.name());
                    }
                }
            }
        }

        BatchParameters parameters = BatchParameters.of(accepted);
        if (fields.isEmpty() && job != null) {
            job.validate(parameters).forEach((name, message) -> fields.put("params." + name, message));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new Validated(parameters, chosen == null ? null : List.copyOf(chosen));
    }

    private static MessageRef checkValue(BatchParamType type, String value) {
        return switch (type) {
            case DATE -> {
                if (!DATE.matcher(value).matches()) {
                    yield new MessageRef("batch.param.date");
                }
                try {
                    LocalDate.parse(value);
                    yield null;
                } catch (DateTimeParseException e) {
                    yield new MessageRef("batch.param.date");
                }
            }
            case LONG -> LONG.matcher(value).matches() ? null : new MessageRef("batch.param.long");
            case STRING -> value.length() <= MAX_STRING ? null : new MessageRef("batch.param.tooLong", String.valueOf(MAX_STRING));
        };
    }
}
