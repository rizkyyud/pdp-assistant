package id.rizky.ramadhan.pdp_assistant.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentReaderService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentReaderService.class);

    public List<Document> read(Resource resource){
        var reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();
        logger.info("Dokumen Terbaca: {}", documents.size());
        documents.forEach(document -> {
            logger.info("Panjang teks: {} karakter", document.getText().length());
            logger.info("Metadata: {}", document.getMetadata());
        });
        return documents;
    }

    public List<Document> chunk(List<Document> documents, int chunkSize) {
        var splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(350)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();

        List<Document> chunks = splitter.apply(documents);
        logger.info("Chunk dihasilkan: {}", chunks.size());
        return chunks;
    }
}
