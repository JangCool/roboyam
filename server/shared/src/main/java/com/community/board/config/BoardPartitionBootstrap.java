package com.community.board.config;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 서버가 뜰 때(api·admin 모두) 글 표({@code posts})의 월 조각을 한 번 맞춘다 — 매일 도는 DB 배치 {@code board-post-partitions} 와 같은 함수
 * {@code board_ensure_post_partitions}(R__batch_17)를 부른다. pg_cron 이 없어 배치가 돌지 않는 설치도 서버를 다시 띄울 때마다 6개월 앞까지 채워진다.
 *
 * <p>native SQL 인 이유: DB 함수 호출이라 JPQL/QueryDSL 에 없다. 실패해도 서버는 뜬다(조각이 이미 있으면 글은 쓸 수 있다) — 원인은 WARN 으로 남긴다.
 */
@Component
public class BoardPartitionBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BoardPartitionBootstrap.class);

    private final JdbcTemplate jdbc;

    public BoardPartitionBootstrap(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer created = jdbc.queryForObject("SELECT board_ensure_post_partitions(batch_param('board-post-partitions', 'months_ahead', 6, 1, 24)::int)",
                    Integer.class);
            if (created != null && created > 0) {
                log.info("글 월 조각 {}개를 새로 만들었다", created);
            }
        } catch (RuntimeException e) {
            log.warn("글 월 조각을 맞추지 못했다(조각이 없는 달의 글은 저장에 실패한다) 원인={}", e.getMessage());
        }
    }
}
