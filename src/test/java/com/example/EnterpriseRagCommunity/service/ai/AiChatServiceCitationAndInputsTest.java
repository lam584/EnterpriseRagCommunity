package com.example.EnterpriseRagCommunity.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;

class AiChatServiceCitationAndInputsTest {
    @Test
    void extractCitationIndexes_should_skip_code_blocks_and_inline_code() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("extractCitationIndexes", String.class, int.class);
        m.setAccessible(true);

        String text = ""
                + "normal [1] ok\n"
                + "```java\n"
                + "code [2] ignored\n"
                + "```\n"
                + "inline `[3]` ignored\n"
                + "link [4](https://x) ignored\n"
                + "tooBig [1234] ignored\n"
                + "normal [5] ok\n";

        @SuppressWarnings("unchecked")
        Set<Integer> out = (Set<Integer>) m.invoke(null, text, 10);
        assertEquals(Set.of(1, 5), out);
    }

    @Test
    void filterSourcesByCitations_should_only_keep_cited_sources() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("filterSourcesByCitations", List.class, String.class);
        m.setAccessible(true);

        List<RagContextPromptService.CitationSource> sources = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            RagContextPromptService.CitationSource s = new RagContextPromptService.CitationSource();
            s.setIndex(i);
            s.setTitle("t" + i);
            sources.add(s);
        }

        String answer = "hello [1] and [3]";
        @SuppressWarnings("unchecked")
        List<RagContextPromptService.CitationSource> out = (List<RagContextPromptService.CitationSource>) m.invoke(null, sources, answer);
        assertEquals(2, out.size());
        assertEquals(1, out.get(0).getIndex());
        assertEquals(3, out.get(1).getIndex());
    }

    @Test
    void resolveImages_should_dedup_filter_and_cap_to_five() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("resolveImages", AiChatStreamRequest.class);
        m.setAccessible(true);

        AiChatStreamRequest req = new AiChatStreamRequest();
        List<AiChatStreamRequest.ImageInput> imgs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            AiChatStreamRequest.ImageInput in = new AiChatStreamRequest.ImageInput();
            in.setUrl("/uploads/" + (i % 3) + ".png");
            if (i % 2 == 0) in.setMimeType("image/png");
            imgs.add(in);
        }
        AiChatStreamRequest.ImageInput bad = new AiChatStreamRequest.ImageInput();
        bad.setUrl("not-an-image.txt");
        bad.setMimeType("text/plain");
        imgs.add(bad);
        req.setImages(imgs);

        @SuppressWarnings("unchecked")
        List<AiChatStreamRequest.ImageInput> out = (List<AiChatStreamRequest.ImageInput>) m.invoke(null, req);
        assertTrue(out.size() <= 5);
        assertEquals(3, out.size());
    }

    @Test
    void resolveFiles_should_dedup_by_id_or_url_and_cap_to_twenty() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("resolveFiles", AiChatStreamRequest.class);
        m.setAccessible(true);

        AiChatStreamRequest req = new AiChatStreamRequest();
        List<AiChatStreamRequest.FileInput> files = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            AiChatStreamRequest.FileInput f = new AiChatStreamRequest.FileInput();
            if (i % 2 == 0) {
                f.setFileAssetId((long) (i % 5 + 1));
            } else {
                f.setUrl("https://x/" + (i % 7));
            }
            files.add(f);
        }
        req.setFiles(files);

        @SuppressWarnings("unchecked")
        List<AiChatStreamRequest.FileInput> out = (List<AiChatStreamRequest.FileInput>) m.invoke(null, req);
        assertTrue(out.size() <= 20);
        assertTrue(out.size() >= 12);
    }

    @Test
    void extractFilesFromHistoryText_should_extract_unique_positive_ids_and_cap_to_twenty() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("extractFilesFromHistoryText", String.class);
        m.setAccessible(true);

        StringBuilder sb = new StringBuilder();
        sb.append("file_asset_id=1 file_asset_id = 1 FILE_ASSET_ID=2 file_asset_id=0 file_asset_id=-3 file_asset_id=abc ");
        for (int i = 3; i <= 50; i++) {
            sb.append("file_asset_id=").append(i).append(' ');
        }

        @SuppressWarnings("unchecked")
        List<AiChatStreamRequest.FileInput> out = (List<AiChatStreamRequest.FileInput>) m.invoke(null, sb.toString());
        assertEquals(20, out.size());
        assertEquals(1L, out.get(0).getFileAssetId());
        assertEquals(2L, out.get(1).getFileAssetId());
    }
}

