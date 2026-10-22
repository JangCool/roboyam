package com.community.post.dto.response;

/** 글이 가리키는 말머리(user-web {@code PostPrefixRef}) */
public record PostPrefixRefView(String id, String name, boolean staffOnly, String color) {
}
