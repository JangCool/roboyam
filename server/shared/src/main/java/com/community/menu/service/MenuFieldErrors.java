package com.community.menu.service;

import com.community.zconfiguration.message.MessageRef;
import java.util.Map;

/**
 * 노드 하나의 칸 오류를 모으는 자리 — 칸 이름만 주면 노드 경로({@code nodes[1].children[2]})를 앞에 붙여 {@code fields} 에 넣는다. 칸마다 첫 문구만 남긴다.
 */
public final class MenuFieldErrors {

    private final String path;
    private final Map<String, MessageRef> sink;

    public MenuFieldErrors(String path, Map<String, MessageRef> sink) {
        this.path = path;
        this.sink = sink;
    }

    /** 문구가 null 이면 아무것도 하지 않는다 */
    public void add(String field, MessageRef message) {
        if (message != null) {
            sink.putIfAbsent(path + "." + field, message);
        }
    }
}
