package com.community.zconfiguration.id;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** ID 발급기 구성. 서버 번호는 {@code roboyam.node-id}(환경 변수 {@code NODE_ID}, 0~1023)이고 dev · prod 에는 기본값이 없다(빠지면 기동이 실패한다). */
@Configuration
public class IdConfig {

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(@Value("${roboyam.node-id}") int nodeId) {
        return new SnowflakeIdGenerator(nodeId);
    }
}
