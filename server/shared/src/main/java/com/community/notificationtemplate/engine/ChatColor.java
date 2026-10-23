package com.community.notificationtemplate.engine;

import java.util.Arrays;
import java.util.Optional;

/** Slack · Mattermost 템플릿의 색 막대 팔레트(계약 「미확정」 11 — 8색 가정). 첨부 {@code color} 로 실제 색 값을 보낸다(admin-web {@code UtilChatTemplate} 과 같은 값) */
public enum ChatColor {

    GREEN("#2eb67d"),
    BLUE("#1d9bd1"),
    TEAL("#0f766e"),
    PURPLE("#7c3aed"),
    AMBER("#ecb22e"),
    ORANGE("#f97316"),
    RED("#e01e5a"),
    GRAY("#9ca3af");

    private final String hex;

    ChatColor(String hex) {
        this.hex = hex;
    }

    public String hex() {
        return hex;
    }

    public String nameKey() {
        return "notificationTemplate.chatColor." + name();
    }

    public static Optional<ChatColor> of(String name) {
        return Arrays.stream(values()).filter(color -> color.name().equals(name)).findFirst();
    }
}
