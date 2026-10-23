package com.community.batch.core;

import com.community.zconfiguration.message.MessageRef;
import java.util.List;
import java.util.Map;

/**
 * 배치 하나의 실제 일(Java 배치). 구현은 스프링 빈이고 {@link #definition()} 으로 카탈로그의 어느 줄인지 밝힌다(카탈로그 한 줄에 구현 하나 — {@link BatchRegistry}).
 * 구현은 도메인 쪽({@code com.community.<도메인>.batch})에 두고 도메인 서비스만 부른다 — 관리자 화면 코드(컨트롤러·화면 DTO)를 참조하지 않는다(docs/domain/commerce.md 15-2).
 *
 * <p>실행기({@link BatchRunner})가 잠금·실행 로그·단계 기록·실패 처리를 맡으므로 구현은 일만 한다. 일은 {@link BatchContext#step} 으로 단계에 들어가 하고,
 * 많은 대상을 다루면 덩어리 처리 도우미({@link BatchStep#chunks})를 쓴다 — 덩어리마다 저장 확정·체크포인트, 실패한 덩어리만 한 건씩 다시, 실패 건만 기록, 실패 한도를 실행기가 지킨다.
 * 일 전체가 실패하면 예외를 던진다(실행 로그에 가린 사유가 남는다). 예외 문구에 비밀번호·토큰·개인정보를 싣지 않는다.
 *
 * <p>작업을 짜는 규칙(15-3): ① 덩어리 단위로 처리하고 덩어리마다 저장 확정 ② 「그날 몫」이 아니라 「아직 처리 안 한 것 전부」를 대상으로(하루를 놓쳐도 다음 실행이 메꾼다)
 * ③ 멱등(조건부 상태 변경) — 처음부터 다시 돌려도, 체크포인트 다음부터 다시 돌려도 안전하게.
 */
public interface BatchJob {

    /** 카탈로그의 어느 배치인가 */
    BatchDefinition definition();

    /** 단계 목록(차례대로). 기본은 고를 수 없는 한 단계 {@code main} */
    default List<BatchStepDefinition> steps() {
        return List.of(BatchStepDefinition.main());
    }

    /**
     * 주 단계 — 실행 줄의 읽음·처리·건너뜀 건수가 이 단계의 건수다(확정 규칙 1, 실패 건수는 단계 합). 기본은 첫 단계.
     * 예: 「대상 선정 → 소멸 처리 → 알림」이면 「소멸 처리」.
     */
    default String mainStep() {
        return steps().get(0).name();
    }

    /** 받을 수 있는 실행 파라미터. 기본은 없음 */
    default List<BatchParamDefinition> params() {
        return List.of();
    }

    /** 「실패 지점부터 다시」(체크포인트 재시작)를 지원하는가 — 덩어리 처리 도우미를 쓰고 멱등인 작업만 true. 기본 false */
    default boolean resumable() {
        return false;
    }

    /**
     * 작업별 추가 검사(예: 대사 기준일은 어제까지). 형식·필수·모르는 이름은 공통 검사({@link BatchParameterValidator})가 먼저 걸렀다.
     *
     * @return 파라미터 이름 → 문구. 문제가 없으면 빈 맵(오류 칸은 {@code params.<이름>} 이 된다)
     */
    default Map<String, MessageRef> validate(BatchParameters parameters) {
        return Map.of();
    }

    /** 일을 하고 결과 요약을 돌려준다. 건수는 단계가 센 값이 실행 줄에 남는다 */
    BatchResult run(BatchContext context) throws Exception;
}
