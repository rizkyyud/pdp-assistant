package id.rizky.ramadhan.pdp_assistant.ingestion.service;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PasalSplitter {

    private static final Pattern PASAL_KETAT = Pattern.compile(
            "^\\s*Pasal\\s+(\\d+)\\s*$"
    );

    Pattern PASAL_LONGGAR = Pattern.compile("(?m)^.{0,15}Pasal\\s+(\\d+).{0,15}$");

    public List<Document> split(String text, Map<String, Object> metadataDasar) {
        List<Document> hasil = new ArrayList<>();
        Matcher m = PASAL_LONGGAR.matcher(text);

        Matcher penjelasan = Pattern.compile("(?m)^\\s*PENJE[LI][,.]?ASAN\\s*$").matcher(text);
        if (penjelasan.find()) {
            text = text.substring(0, penjelasan.start());
        }

        // Tahap 1: kumpulkan semua kandidat
        List<int[]> posisi = new ArrayList<>();
        List<String> nomor = new ArrayList<>();

        while (m.find()) {
            posisi.add(new int[]{m.start(), m.end()});
            nomor.add(m.group(1));
        }

        // Tahap 2: saring — hanya terima nomor yang berurutan menaik
        List<int[]> posisiValid = new ArrayList<>();
        List<String> nomorValid = new ArrayList<>();
        int terakhir = 0;

        for (int i = 0; i < posisi.size(); i++) {
            int n = Integer.parseInt(nomor.get(i));
            if (n != terakhir + 1) continue;
            posisiValid.add(posisi.get(i));
            nomorValid.add(nomor.get(i));
            terakhir = n;
        }

        // Tahap 3: bangun Document dari kandidat yang lolos
        for (int i = 0; i < posisiValid.size(); i++) {
            int awal = posisiValid.get(i)[0];
            int akhir = (i + 1 < posisiValid.size())
                    ? posisiValid.get(i + 1)[0]
                    : text.length();

            String isi = text.substring(awal, akhir).trim();
            if (isi.length() < 30) continue;

            var metadata = new HashMap<>(metadataDasar);
            metadata.put("pasal", nomorValid.get(i));
            metadata.put("urutan", i);

            hasil.add(new Document(isi, metadata));
        }

        return hasil;
    }
}
