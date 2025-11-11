package com.example.EnterpriseRagCommunity.entity.semantic.enums;

public enum ContextWindowPolicy {
    FIXED,        // fixed-size token budget
    ADAPTIVE,     // dynamically adjust based on query/answer length
    SLIDING,      // sliding window across retrieved chunks
    TOPK,
    IMPORTANCE,
    DEDUP,
    HYBRID
}
