package id.rizky.ramadhan.pdp_assistant.retrieval.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalService {

    private final VectorStore vectorStore;
    public RetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }
    public List<Document> cari(String pertanyaan, int topK, double ambang){
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(pertanyaan)
                        .topK(topK)
                        .similarityThreshold(ambang)
                        .build()
        );
    }
}
