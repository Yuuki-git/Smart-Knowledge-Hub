package com.smartknowledgehub.service;

import com.smartknowledgehub.model.Citation;
import com.smartknowledgehub.model.ChunkSource;
import com.smartknowledgehub.model.RetrievedChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class CitationMapper {
    // 将检索命中的 chunk 元数据映射为引用信息
    public List<Citation> toCitations(List<RetrievedChunk> chunks) {
        List<Citation> citations = new ArrayList<>();
        for (RetrievedChunk chunk : chunks) {
            citations.add(toCitation(chunk));
        }
        return citations;
    }

    private Citation toCitation(RetrievedChunk chunk) {
        ChunkSource source = chunk.getSource();
        if (source == null) {
            return new Citation("unknown", "unknown", null);
        }
        StringJoiner ref = new StringJoiner(" | ");
        appendIfPresent(ref, "file", source.getFileName());
        appendIfPresent(ref, "page", source.getPageNumber());
        appendIfPresent(ref, "class", source.getClassName());
        appendIfPresent(ref, "method", source.getMethodName());
        return new Citation("chunk", ref.toString(), null);
    }

    // 仅在字段存在时追加，减少重复判断
    private void appendIfPresent(StringJoiner ref, String key, Object value) {
        if (value == null) {
            return;
        }
        ref.add(key + "=" + value);
    }
}
