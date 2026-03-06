package com.smartknowledgehub.service;

import com.smartknowledgehub.model.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AnswerGroundingValidator {
    private static final int MIN_OVERLAP_TOKENS = 2;
    private static final int MIN_SENTENCE_LENGTH = 6;
    private static final String NOT_FOUND_REPLY = "Not found in the uploaded documents.";

    public boolean hasGroundedClaim(String answer, List<RetrievedChunk> context) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        if (answer.contains(NOT_FOUND_REPLY)) {
            return true;
        }
        if (context == null || context.isEmpty()) {
            return false;
        }

        List<Set<String>> contextTokens = new ArrayList<>();
        for (RetrievedChunk chunk : context) {
            contextTokens.add(tokenize(chunk.getText()));
        }

        String[] sentences = answer.split("[。！？.!?;\\n]+");
        for (String sentence : sentences) {
            String normalized = sentence == null ? "" : sentence.trim();
            if (normalized.length() < MIN_SENTENCE_LENGTH) {
                continue;
            }
            Set<String> sentenceTokens = tokenize(normalized);
            if (sentenceTokens.isEmpty()) {
                continue;
            }
            for (Set<String> chunkTokens : contextTokens) {
                if (overlapCount(sentenceTokens, chunkTokens) >= MIN_OVERLAP_TOKENS) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        String normalized = value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}a-z0-9]+", " ")
                .trim();
        if (normalized.isBlank()) {
            return Set.of();
        }
        String[] parts = normalized.split("\\s+");
        Set<String> tokens = new HashSet<>();
        for (String part : parts) {
            if (part.length() > 1) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private int overlapCount(Set<String> left, Set<String> right) {
        int count = 0;
        for (String token : left) {
            if (right.contains(token)) {
                count++;
            }
        }
        return count;
    }
}
