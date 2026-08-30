package id.rizky.ramadhan.pdp_assistant.ingestion.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class IngestionService {

    private final DocumentReaderService readerService;
    private final TextCleaner cleaner;
    private final PasalSplitter splitter;
    private final VectorStore vectorStore;

    public IngestionService(DocumentReaderService readerService,
                            TextCleaner cleaner, PasalSplitter splitter, VectorStore vectorStore) {
        this.readerService = readerService;
        this.cleaner = cleaner;
        this.splitter = splitter;
        this.vectorStore = vectorStore;
    }

    public int ingest(Resource resource, String namaFile) {
        String teks = cleaner.clean(readerService.read(resource).getFirst().getText());
        List<Document> chunks = splitter.split(teks, Map.of(
                "sumber", namaFile,
                "peraturan", "UU 27/2022"
        ));

        vectorStore.add(chunks);
        return chunks.size();
    }
}
