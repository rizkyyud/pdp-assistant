package id.rizky.ramadhan.pdp_assistant.retrieval.dto;

import java.util.List;

public record RagReply(
        String jawaban,
        String pertanyaanRetrieval,
        List<Sumber> sumber,
        List<String> pasalDisebutModel,
        boolean sitasiTerverifikasi,
        long durasiMs
) {}
