package com.community.batch.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 카탈로그의 <b>Java 배치</b>와 구현({@link BatchJob} 빈)을 짝짓고, 배치마다 단계·파라미터·재시작 지원 여부를 알려 준다. 카탈로그 한 줄에 구현이 없거나 둘 이상이면 서버가 뜨지 않는다 —
 * 정의만 있고 도는 일이 없는 배치(또는 그 반대)가 생기지 않게 한다. DB 배치(저장 프로시저)는 구현 빈이 없고 고를 수 없는 한 단계 {@code main}·파라미터 없음·재시작 불가로 본다.
 * 작업의 단계·파라미터 정의가 규칙에 맞지 않아도(이름 중복, 주 단계가 목록에 없음) 서버가 뜨지 않는다.
 */
@Component
public class BatchRegistry {

    /** DB 배치의 단계 — 프로시저 한 번 */
    public static final List<BatchStepDefinition> DATABASE_STEPS = List.of(BatchStepDefinition.main());

    private final Map<String, BatchJob> jobs = new LinkedHashMap<>();

    /** 운영 구성 — 카탈로그의 모든 Java 배치에 구현이 있어야 한다 */
    @Autowired
    public BatchRegistry(List<BatchJob> beans) {
        this(beans, true);
    }

    private BatchRegistry(List<BatchJob> beans, boolean requireCatalog) {
        for (BatchJob job : beans) {
            BatchDefinition definition = job.definition();
            if (!definition.isJava()) {
                throw new IllegalStateException("DB 배치는 BatchJob 구현을 두지 않는다(저장 프로시저가 일한다): " + definition.key());
            }
            if (!BatchDefinition.KEY.matcher(definition.key()).matches()) {
                throw new IllegalStateException("배치 키 규칙에 맞지 않는다: " + definition.key());
            }
            checkShape(job);
            BatchJob previous = jobs.put(definition.key(), job);
            if (previous != null) {
                throw new IllegalStateException("배치 구현이 둘 이상이다: " + definition.key());
            }
        }
        if (requireCatalog) {
            for (BatchCatalog definition : BatchCatalog.javaJobs()) {
                if (!jobs.containsKey(definition.key())) {
                    throw new IllegalStateException("카탈로그에 있는 Java 배치의 구현(BatchJob 빈)이 없다: " + definition);
                }
            }
        }
    }

    /** 카탈로그와 맞춰 보지 않는 구성 — 시험이나 일부 작업만 쓰는 곳 */
    public static BatchRegistry forJobs(List<BatchJob> jobs) {
        return new BatchRegistry(jobs, false);
    }

    private static void checkShape(BatchJob job) {
        String key = job.definition().key();
        List<BatchStepDefinition> steps = job.steps();
        if (steps == null || steps.isEmpty()) {
            throw new IllegalStateException("단계가 하나도 없다: " + key);
        }
        Set<String> names = new HashSet<>();
        steps.forEach(step -> {
            if (!names.add(step.name())) {
                throw new IllegalStateException("단계 이름이 겹친다: " + key + "." + step.name());
            }
        });
        if (!names.contains(job.mainStep())) {
            throw new IllegalStateException("주 단계가 단계 목록에 없다: " + key + "." + job.mainStep());
        }
        Set<String> params = new HashSet<>();
        job.params().forEach(param -> {
            if (!params.add(param.name())) {
                throw new IllegalStateException("파라미터 이름이 겹친다: " + key + "." + param.name());
            }
        });
    }

    /** Java 배치의 구현. 없으면(DB 배치·모르는 키) null */
    public BatchJob jobOf(String key) {
        return jobs.get(key);
    }

    public BatchJob jobOf(BatchDefinition definition) {
        return jobOf(definition.key());
    }

    /** 이 구성이 아는 Java 배치 키 */
    public Collection<String> javaKeys() {
        return jobs.keySet();
    }

    /** 단계 목록 — DB 배치는 {@link #DATABASE_STEPS} */
    public List<BatchStepDefinition> stepsOf(BatchDefinition definition) {
        BatchJob job = jobOf(definition);
        return job == null ? DATABASE_STEPS : job.steps();
    }

    /** 주 단계 */
    public String mainStepOf(BatchDefinition definition) {
        BatchJob job = jobOf(definition);
        return job == null ? BatchStepDefinition.MAIN : job.mainStep();
    }

    /** 파라미터 — DB 배치는 없음 */
    public List<BatchParamDefinition> paramsOf(BatchDefinition definition) {
        BatchJob job = jobOf(definition);
        return job == null ? List.of() : job.params();
    }

    /** 「실패 지점부터 다시」를 지원하나 — DB 배치는 아니다 */
    public boolean resumable(BatchDefinition definition) {
        BatchJob job = jobOf(definition);
        return job != null && job.resumable();
    }
}
