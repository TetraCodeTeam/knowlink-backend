package com.knowlink.api.community.data.projections;

import java.util.UUID;

/** Cantidad de marcas "Esto me sirvió" de una publicación. */
public interface PostHelpfulCount {

    UUID getPostId();

    long getTotal();
}
