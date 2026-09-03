package id.rizky.ramadhan.pdp_assistant.retrieval.dto;

import java.util.List;

public record RagReply(
        String jawaban,
        String pertanyaanRetrieval,
        List<Sumber> sumber,
        List<String> pasalDisebutModel,
        List<String> pasalHalusinasi,
        boolean adaSitasi,
        boolean sitasiTerverifikasi,
        long durasiMs
) {}
