package id.rizky.ramadhan.pdp_assistant.ingestion.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class TextCleaner {

    private static final List<Pattern> ARTEFACT = List.of(
            Pattern.compile("SK\\s+No\\s+\\d+\\s*A?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("PRESIDEN\\s*\\n?\\s*REPUBLIK\\s+INDONESIA", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*[-.]\\s*\\d+\\s*[-.]\\s*$", Pattern.MULTILINE),
            Pattern.compile("\\n\\s*\\w+\\s*\\.\\s*\\.\\s*\\.\\s*\\n")  // "Dengan . . ."
    );

    public String clean(String text) {
        String hasil = text;
        for (Pattern p : ARTEFACT) {
            hasil = p.matcher(hasil).replaceAll("\n");
        }
        return hasil
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n")     // rapikan spasi di sekitar newline
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
