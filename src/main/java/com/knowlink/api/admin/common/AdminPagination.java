package com.knowlink.api.admin.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class AdminPagination {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 50;

    private AdminPagination() {
    }

    public static Pageable of(Integer page, Integer size, Sort sort) {
        int safePage = page == null || page < 0 ? DEFAULT_PAGE : page;
        int requestedSize = size == null || size <= 0 ? DEFAULT_SIZE : size;
        int safeSize = Math.min(requestedSize, MAX_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }
}