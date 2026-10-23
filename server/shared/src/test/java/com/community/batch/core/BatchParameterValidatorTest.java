package com.community.batch.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 실행 요청의 파라미터·단계 검사(docs/features/batch-contract.md 「0-2 보강」 — 넣을 때 400 {@code fields.params.<이름>}/{@code fields.steps}). DB 없이.
 */
class BatchParameterValidatorTest {

    /** 대사 기준일(필수 날짜, 어제까지) · 회원 번호(정수) · 메모(글), 단계 fetch(고를 수 없음) · compare · report */
    private static final BatchJob JOB = new BatchJob() {
        @Override
        public BatchDefinition definition() {
            return new BatchTestKit.TestDefinition("zz-validate");
        }

        @Override
        public List<BatchStepDefinition> steps() {
            return List.of(new BatchStepDefinition("fetch", false), new BatchStepDefinition("compare", true), new BatchStepDefinition("report", true));
        }

        @Override
        public List<BatchParamDefinition> params() {
            return List.of(new BatchParamDefinition("reconcileDate", BatchParamType.DATE, true), new BatchParamDefinition("memberId", BatchParamType.LONG, false),
                    new BatchParamDefinition("note", BatchParamType.STRING, false));
        }

        @Override
        public Map<String, MessageRef> validate(BatchParameters parameters) {
            return parameters.date("reconcileDate").filter(date -> !date.isBefore(LocalDate.of(2026, 10, 23)))
                    .map(date -> Map.of("reconcileDate", new MessageRef("zz.test.yesterday"))).orElse(Map.of());
        }

        @Override
        public BatchResult run(BatchContext context) {
            throw new UnsupportedOperationException();
        }
    };

    private static Map<String, MessageRef> fieldsOf(Map<String, String> params, List<String> steps) {
        try {
            BatchParameterValidator.validate(JOB, params, steps);
        } catch (ValidationException e) {
            return e.getFields();
        }
        throw new AssertionError("검사를 통과하면 안 된다");
    }

    @Test
    void 맞는_값은_통과하고_앞뒤_공백과_빈_값은_정리한다() {
        BatchParameterValidator.Validated validated = BatchParameterValidator.validate(JOB,
                Map.of("reconcileDate", " 2026-10-20 ", "memberId", "42", "note", "  "), List.of("report", "compare"));

        assertThat(validated.parameters().asMap()).containsExactly(Map.entry("memberId", "42"), Map.entry("reconcileDate", "2026-10-20"));
        assertThat(validated.parameters().longValue("memberId")).contains(42L);
        assertThat(validated.steps()).as("정의 순서로").containsExactly("compare", "report");
        assertThat(BatchParameterValidator.validate(JOB, Map.of("reconcileDate", "2026-10-20"), null).steps()).as("안 주면 모든 단계").isNull();
    }

    @Test
    void 필수_파라미터가_없으면_거절한다() {
        assertThat(fieldsOf(Map.of(), null)).containsOnlyKeys("params.reconcileDate");
        assertThat(fieldsOf(Map.of("reconcileDate", "   "), null).get("params.reconcileDate").key()).isEqualTo("batch.param.required");
    }

    @Test
    void 꼴이_틀리면_거절한다() {
        Map<String, MessageRef> fields = fieldsOf(Map.of("reconcileDate", "2026-02-30", "memberId", "0"), null);
        assertThat(fields.get("params.reconcileDate").key()).isEqualTo("batch.param.date");
        assertThat(fields.get("params.memberId").key()).isEqualTo("batch.param.long");
        assertThat(fieldsOf(Map.of("reconcileDate", "20261020"), null).get("params.reconcileDate").key()).isEqualTo("batch.param.date");
        assertThat(fieldsOf(Map.of("reconcileDate", "2026-10-20", "memberId", "-5"), null).get("params.memberId").key()).isEqualTo("batch.param.long");
        assertThat(fieldsOf(Map.of("reconcileDate", "2026-10-20", "note", "가".repeat(201)), null).get("params.note").key()).isEqualTo("batch.param.tooLong");
    }

    @Test
    void 모르는_파라미터_이름은_거절한다() {
        Map<String, MessageRef> fields = fieldsOf(Map.of("reconcileDate", "2026-10-20", "memberld", "1"), null);
        assertThat(fields).containsOnlyKeys("params");
        assertThat(fields.get("params").key()).isEqualTo("batch.param.unknown");
    }

    @Test
    void 모르는_단계_고를_수_없는_단계_빈_단계_목록은_거절한다() {
        Map<String, String> ok = Map.of("reconcileDate", "2026-10-20");
        assertThat(fieldsOf(ok, List.of("compare", "nope")).get("steps").key()).isEqualTo("batch.steps.unknown");
        assertThat(fieldsOf(ok, List.of("fetch")).get("steps").key()).as("고를 수 없는 단계는 고를 수 없다(늘 돈다)").isEqualTo("batch.steps.unknown");
        assertThat(fieldsOf(ok, List.of()).get("steps").key()).isEqualTo("batch.steps.empty");
    }

    @Test
    void 형식이_맞으면_작업별_추가_검사를_한다() {
        assertThat(fieldsOf(Map.of("reconcileDate", "2026-10-23"), null).get("params.reconcileDate").key()).isEqualTo("zz.test.yesterday");
    }

    @Test
    void DB_배치는_파라미터도_고를_단계도_없다() {
        assertThat(BatchParameterValidator.validate(null, null, null).parameters().isEmpty()).isTrue();
        Map<String, String> params = new HashMap<>();
        params.put("x", "1");
        assertThatThrownBy(() -> BatchParameterValidator.validate(null, params, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> BatchParameterValidator.validate(null, null, List.of("main"))).isInstanceOf(ValidationException.class);
    }
}
