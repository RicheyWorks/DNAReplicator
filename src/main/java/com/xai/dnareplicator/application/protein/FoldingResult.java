package com.xai.dnareplicator.application.protein;

/**
 * Outcome of folding a single protein through the educational pipeline.
 */
public record FoldingResult(boolean success, String foldingState, String statusMessage) {
}
