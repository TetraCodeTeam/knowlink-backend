package com.knowlink.api.community.data.projections;

import java.util.UUID;

public interface ReplyHelpfulCount {

    UUID getPostId();

    UUID getReplyId();

    long getTotal();
}
