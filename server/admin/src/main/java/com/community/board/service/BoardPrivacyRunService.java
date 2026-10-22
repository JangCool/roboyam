package com.community.board.service;

import com.community.board.dto.request.BoardPrivacyRunRequest;
import com.community.board.dto.response.BoardPrivacyRunView;
import com.community.board.exception.BoardPrivacyDisabledException;
import com.community.board.exception.BoardPrivacyRunningException;
import com.community.board.repository.master.BoardPrivacyGateway;
import com.community.permission.dto.AdminActor;
import org.springframework.stereotype.Service;

/**
 * 개인정보 보유기간 「지금 실행」 — 저장된 설정으로 축 하나를 한 번 처리한다. 처리는 DB 배치 {@code board-privacy-retention} 과 같은 함수
 * ({@code board_privacy_process})를 덩어리({@value #CHUNK}행)마다 <b>따로 커밋</b>하며 부른다(긴 트랜잭션을 만들지 않게) — 그래서 이 서비스에는 트랜잭션이 없다.
 * 한 번 실행 상한은 {@value #MAX_PER_RUN}행(넘는 몫은 다음 실행·배치가 이어서 한다).
 *
 * <ul>
 *   <li>전체 스위치가 꺼져 있으면 409 {@code BOARD_PRIVACY_DISABLED}. 배치(또는 다른 지금 실행)가 도는 중이면 409 {@code BOARD_PRIVACY_RUNNING}.</li>
 *   <li>신고 기록·처리 기록 축은 표가 아직 없어 0건이다.</li>
 *   <li>마친 뒤 마지막 실행 시각과 감사 기록({@code BOARD_PRIVACY_RUN}, 축·처리 수)을 남긴다.</li>
 * </ul>
 */
@Service
public class BoardPrivacyRunService {

    static final int CHUNK = 5000;
    static final int MAX_PER_RUN = 100_000;

    private final BoardPrivacyGateway gateway;
    private final BoardDefaultsService settings;

    public BoardPrivacyRunService(BoardPrivacyGateway gateway, BoardDefaultsService settings) {
        this.gateway = gateway;
        this.settings = settings;
    }

    public BoardPrivacyRunView run(AdminActor actor, BoardPrivacyRunRequest request) {
        String axis = BoardDefaultsService.axis(request == null ? null : request.axis());
        if (!settings.privacyEnabled()) {
            throw new BoardPrivacyDisabledException();
        }
        long total = 0;
        while (total < MAX_PER_RUN) {
            int want = (int) Math.min(CHUNK, MAX_PER_RUN - total);
            long processed = gateway.process(axis, want);
            if (processed < 0) {
                if (total == 0) {
                    throw new BoardPrivacyRunningException();
                }
                break; // 도중에 배치가 시작됐다 — 나머지는 배치가 한다
            }
            total += processed;
            if (processed < want) {
                break;
            }
        }
        settings.recordPrivacyRun(actor, axis, total);
        return new BoardPrivacyRunView(axis, total);
    }
}
