package com.smartknowledgehub.service;

import com.smartknowledgehub.model.Citation;
import com.smartknowledgehub.model.ChunkSource;
import com.smartknowledgehub.model.RetrievedChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class CitationMapper {
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
        if (source.getFileName() != null) {
            ref.add("file=" + source.getFileName());
        }
        if (source.getPageNumber() != null) {
            ref.add("page=" + source.getPageNumber());
        }
        if (source.getClassName() != null) {
            ref.add("class=" + source.getClassName());
        }
        if (source.getMethodName() != null) {
            ref.add("method=" + source.getMethodName());
        }
        return new Citation("chunk", ref.toString(), null);
    }
}
