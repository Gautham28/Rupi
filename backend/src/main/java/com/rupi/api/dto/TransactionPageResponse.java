package com.rupi.api.dto;

import java.util.List;

public record TransactionPageResponse(
        List<TransactionResponse> items, String nextCursor, boolean hasMore) {}
