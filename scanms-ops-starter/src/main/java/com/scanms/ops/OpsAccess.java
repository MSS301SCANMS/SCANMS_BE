package com.scanms.ops;

/** Quyết định ai được gọi các endpoint giám sát. */
public interface OpsAccess {
    enum Decision {ALLOWED, UNAUTHENTICATED, FORBIDDEN}

    Decision check();
}
