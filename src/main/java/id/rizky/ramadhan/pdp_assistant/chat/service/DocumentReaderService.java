package id.rizky.ramadhan.pdp_assistant.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
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
}
