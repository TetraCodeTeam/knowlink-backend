package com.knowlink.api.community.data.projections;

import java.util.UUID;

public interface PostReplyCount {

    UUID getPostId();

    long getTotal();
}
