package com.example.EnterpriseRagCommunity.service.ai;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateHistoryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslatePublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateHistoryEntity;
import com.example.EnterpriseRagCommunity.repository.ai.SemanticTranslateConfigRepository;
import com.example.EnterpriseRagCommunity.repository.ai.SemanticTranslateHistoryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SemanticTranslateConfigService {

    public static final int DEFAULT_MAX_CONTENT_CHARS = 8000;

    public static final String DEFAULT_SYSTEM_PROMPT = """
你是一个专业的翻译助手。
要求：
1. 把用户提供的标题与正文翻译成目标语言；
2. 正文输出必须为 Markdown，尽量保留原文的结构（标题层级/列表/引用/代码块/表格等）；
3. 不要添加与原文无关的内容，不要进行总结，不要输出额外解释；
4. 输出严格为 JSON（不要包裹 ```），字段如下：
   - title: 翻译后的标题（纯文本）
   - markdown: 翻译后的正文 Markdown
""";

    public static final String DEFAULT_PROMPT_TEMPLATE = """
目标语言：{{targetLang}}

标题：
{{title}}

正文（Markdown）：
{{content}}
""";

    private final SemanticTranslateConfigRepository configRepository;
    private final SemanticTranslateHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private static final String DEFAULT_ALLOWED_TARGET_LANGUAGES_TEXT = """
英语（English）
简体中文（Simplified Chinese）
繁体中文（Traditional Chinese）
法语（French）
西班牙语（Spanish）
阿拉伯语（Arabic）
俄语（Russian）
葡萄牙语（Portuguese）
德语（German）
意大利语（Italian）
荷兰语（Dutch）
丹麦语（Danish）
爱尔兰语（Irish）
威尔士语（Welsh）
芬兰语（Finnish）
冰岛语（Icelandic）
瑞典语（Swedish）
新挪威语（Norwegian Nynorsk）
书面挪威语（Norwegian Bokmål）
日语（Japanese）
朝鲜语/韩语（Korean）
越南语（Vietnamese）
泰语（Thai）
印度尼西亚语（Indonesian）
马来语（Malay）
缅甸语（Burmese）
他加禄语（Tagalog）
高棉语（Khmer）
老挝语（Lao）
印地语（Hindi）
孟加拉语（Bengali）
乌尔都语（Urdu）
尼泊尔语（Nepali）
希伯来语（Hebrew）
土耳其语（Turkish）
波斯语（Persian）
波兰语（Polish）
乌克兰语（Ukrainian）
捷克语（Czech）
罗马尼亚语（Romanian）
保加利亚语（Bulgarian）
斯洛伐克语（Slovak）
匈牙利语（Hungarian）
斯洛文尼亚语（Slovenian）
拉脱维亚语（Latvian）
爱沙尼亚语（Estonian）
立陶宛语（Lithuanian）
白俄罗斯语（Belarusian）
希腊语（Greek）
克罗地亚语（Croatian）
马其顿语（Macedonian）
马耳他语（Maltese）
塞尔维亚语（Serbian）
波斯尼亚语（Bosnian）
格鲁吉亚语（Georgian）
亚美尼亚语（Armenian）
北阿塞拜疆语（North Azerbaijani）
哈萨克语（Kazakh）
北乌兹别克语（Northern Uzbek）
塔吉克语（Tajik）
斯瓦西里语（Swahili）
南非语（Afrikaans）
粤语（Cantonese）
卢森堡语（Luxembourgish）
林堡语（Limburgish）
加泰罗尼亚语（Catalan）
加利西亚语（Galician）
阿斯图里亚斯语（Asturian）
巴斯克语（Basque）
奥克语（Occitan）
威尼斯语（Venetian）
撒丁语（Sardinian）
西西里语（Sicilian）
弗留利语（Friulian）
隆巴底语（Lombard）
利古里亚语（Ligurian）
法罗语（Faroese）
托斯克阿尔巴尼亚语（Tosk Albanian）
西里西亚语（Silesian）
巴什基尔语（Bashkir）
鞑靼语（Tatar）
美索不达米亚阿拉伯语（Mesopotamian Arabic）
内志阿拉伯语（Najdi Arabic）
埃及阿拉伯语（Egyptian Arabic）
黎凡特阿拉伯语（Levantine Arabic）
闪米特阿拉伯语（Ta'izzi-Adeni Arabic）
达里语（Dari）
突尼斯阿拉伯语（Tunisian Arabic）
摩洛哥阿拉伯语（Moroccan Arabic）
克里奥尔语（Kabuverdianu）
托克皮辛语（Tok Pisin）
意第绪（Eastern Yiddish）
信德阿拉伯语（Sindhi）
僧伽罗语（Sinhala）
泰卢固语（Telugu）
旁遮普语（Punjabi）
泰米尔语（Tamil）
古吉拉特语（Gujarati）
马拉雅拉姆语（Malayalam）
马拉地语（Marathi）
卡纳达语（Kannada）
马加拉语（Magahi）
奥里亚语（Oriya）
阿瓦德语（Awadhi）
迈蒂利语（Maithili）
阿萨姆语（Assamese）
切蒂斯格尔语（Chhattisgarhi）
比哈尔语（Bhojpuri）
米南加保语（Minangkabau）
巴厘语（Balinese）
爪哇语（Javanese）
班章语（Banjar）
巽他语（Sundanese）
宿务语（Cebuano）
邦阿西楠语（Pangasinan）
伊洛卡诺语（Iloko）
瓦莱语（Waray (Philippines)）
海地语（Haitian）
帕皮阿门托语（Papiamento）
""";

    private static final List<String> DEFAULT_ALLOWED_TARGET_LANGUAGES = normalizeStringList(splitLines(DEFAULT_ALLOWED_TARGET_LANGUAGES_TEXT));

    @Transactional(readOnly = true)
    public SemanticTranslateConfigDTO getAdminConfig() {
        SemanticTranslateConfigEntity cfg = configRepository.findTopByOrderByUpdatedAtDesc().orElse(null);
        if (cfg == null) return toDto(defaultEntity(), null);
        return toDto(cfg, null);
    }

    @Transactional(readOnly = true)
    public SemanticTranslatePublicConfigDTO getPublicConfig() {
        SemanticTranslateConfigEntity cfg = configRepository.findTopByOrderByUpdatedAtDesc().orElse(null);
        if (cfg == null) cfg = defaultEntity();

        SemanticTranslatePublicConfigDTO dto = new SemanticTranslatePublicConfigDTO();
        dto.setEnabled(Boolean.TRUE.equals(cfg.getEnabled()));
        List<String> allowed = parseAllowedTargetLanguages(cfg.getAllowedTargetLangs());
        if (allowed == null || allowed.isEmpty()) allowed = DEFAULT_ALLOWED_TARGET_LANGUAGES;
        dto.setAllowedTargetLanguages(allowed);
        return dto;
    }

    @Transactional
    public SemanticTranslateConfigDTO upsertAdminConfig(SemanticTranslateConfigDTO payload, Long actorUserId, String actorUsername) {
        SemanticTranslateConfigEntity cfg = configRepository.findAll().stream().findFirst().orElseGet(this::defaultEntity);

        SemanticTranslateConfigEntity merged = mergeAndValidate(cfg, payload);
        merged.setUpdatedAt(LocalDateTime.now());
        merged.setUpdatedBy(actorUserId);

        merged = configRepository.save(merged);
        return toDto(merged, actorUsername);
    }

    @Transactional(readOnly = true)
    public Page<SemanticTranslateHistoryDTO> listHistory(Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<SemanticTranslateHistoryEntity> rows = (userId == null)
                ? historyRepository.findAllByOrderByCreatedAtDesc(pageable)
                : historyRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        return rows.map(this::toHistoryDto);
    }

    @Transactional
    public void recordHistory(SemanticTranslateHistoryEntity e) {
        if (e == null) return;
        historyRepository.save(e);
    }

    @Transactional(readOnly = true)
    public SemanticTranslateConfigEntity getConfigEntityOrDefault() {
        return configRepository.findTopByOrderByUpdatedAtDesc().orElseGet(this::defaultEntity);
    }

    private SemanticTranslateConfigEntity defaultEntity() {
        SemanticTranslateConfigEntity e = new SemanticTranslateConfigEntity();
        e.setEnabled(Boolean.TRUE);
        e.setSystemPrompt(DEFAULT_SYSTEM_PROMPT);
        e.setPromptTemplate(DEFAULT_PROMPT_TEMPLATE);
        e.setModel(null);
        e.setTemperature(0.2);
        e.setMaxContentChars(DEFAULT_MAX_CONTENT_CHARS);
        e.setHistoryEnabled(Boolean.TRUE);
        e.setHistoryKeepDays(30);
        e.setHistoryKeepRows(5000);
        e.setAllowedTargetLangs(serializeAllowedTargetLanguages(DEFAULT_ALLOWED_TARGET_LANGUAGES));
        e.setVersion(0);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(null);
        return e;
    }

    private SemanticTranslateConfigEntity mergeAndValidate(SemanticTranslateConfigEntity base, SemanticTranslateConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload 不能为空");

        String systemPrompt = payload.getSystemPrompt() == null ? "" : payload.getSystemPrompt().trim();
        String promptTemplate = payload.getPromptTemplate() == null ? "" : payload.getPromptTemplate().trim();
        if (systemPrompt.isBlank()) throw new IllegalArgumentException("systemPrompt 不能为空");
        if (promptTemplate.isBlank()) throw new IllegalArgumentException("promptTemplate 不能为空");
        if (promptTemplate.length() > 20000) throw new IllegalArgumentException("promptTemplate 过长（>20000），请精简");

        Integer maxContentChars = payload.getMaxContentChars();
        if (maxContentChars == null) maxContentChars = DEFAULT_MAX_CONTENT_CHARS;
        if (maxContentChars < 200 || maxContentChars > 100000) throw new IllegalArgumentException("maxContentChars 需在 [200,100000] 范围内");

        Double temperature = payload.getTemperature();
        if (temperature != null && (temperature < 0 || temperature > 2)) {
            throw new IllegalArgumentException("temperature 需在 [0,2] 范围内");
        }

        Integer historyKeepDays = payload.getHistoryKeepDays();
        if (historyKeepDays != null && historyKeepDays < 1) throw new IllegalArgumentException("historyKeepDays 必须为正数");
        Integer historyKeepRows = payload.getHistoryKeepRows();
        if (historyKeepRows != null && historyKeepRows < 1) throw new IllegalArgumentException("historyKeepRows 必须为正数");

        List<String> allowedTargetLanguages = normalizeStringList(payload.getAllowedTargetLanguages());
        if (allowedTargetLanguages.size() > 500) throw new IllegalArgumentException("allowedTargetLanguages 过多（>500），请精简");
        for (String s : allowedTargetLanguages) {
            if (s != null && s.length() > 64) {
                throw new IllegalArgumentException("allowedTargetLanguages 单项过长（>64），请精简");
            }
        }

        base.setEnabled(Boolean.TRUE.equals(payload.getEnabled()));
        base.setSystemPrompt(systemPrompt);
        base.setPromptTemplate(promptTemplate);

        String model = payload.getModel();
        base.setModel(model == null || model.isBlank() ? null : model.trim());
        base.setTemperature(temperature);
        base.setMaxContentChars(maxContentChars);

        base.setHistoryEnabled(Boolean.TRUE.equals(payload.getHistoryEnabled()));
        base.setHistoryKeepDays(historyKeepDays);
        base.setHistoryKeepRows(historyKeepRows);
        base.setAllowedTargetLangs(
                serializeAllowedTargetLanguages(allowedTargetLanguages.isEmpty() ? DEFAULT_ALLOWED_TARGET_LANGUAGES : allowedTargetLanguages)
        );
        return base;
    }

    private SemanticTranslateConfigDTO toDto(SemanticTranslateConfigEntity e, String updatedByName) {
        SemanticTranslateConfigDTO dto = new SemanticTranslateConfigDTO();
        dto.setId(e.getId());
        dto.setVersion(e.getVersion());
        dto.setEnabled(e.getEnabled());
        dto.setSystemPrompt(e.getSystemPrompt());
        dto.setPromptTemplate(e.getPromptTemplate());
        dto.setModel(e.getModel());
        dto.setTemperature(e.getTemperature());
        dto.setMaxContentChars(e.getMaxContentChars());
        dto.setHistoryEnabled(e.getHistoryEnabled());
        dto.setHistoryKeepDays(e.getHistoryKeepDays());
        dto.setHistoryKeepRows(e.getHistoryKeepRows());
        List<String> allowed = parseAllowedTargetLanguages(e.getAllowedTargetLangs());
        dto.setAllowedTargetLanguages((allowed == null || allowed.isEmpty()) ? DEFAULT_ALLOWED_TARGET_LANGUAGES : allowed);
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setUpdatedBy(updatedByName);
        return dto;
    }

    private SemanticTranslateHistoryDTO toHistoryDto(SemanticTranslateHistoryEntity e) {
        SemanticTranslateHistoryDTO dto = new SemanticTranslateHistoryDTO();
        dto.setId(e.getId());
        dto.setUserId(e.getUserId());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setSourceType(e.getSourceType());
        dto.setSourceId(e.getSourceId());
        dto.setTargetLang(e.getTargetLang());
        dto.setSourceTitleExcerpt(e.getSourceTitleExcerpt());
        dto.setSourceContentExcerpt(e.getSourceContentExcerpt());
        dto.setTranslatedTitle(e.getTranslatedTitle());
        dto.setTranslatedMarkdown(e.getTranslatedMarkdown());
        dto.setModel(e.getModel());
        dto.setTemperature(e.getTemperature());
        dto.setLatencyMs(e.getLatencyMs());
        dto.setPromptVersion(e.getPromptVersion());
        return dto;
    }

    private List<String> parseAllowedTargetLanguages(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            List<String> list = objectMapper.readValue(json, STRING_LIST_TYPE);
            return normalizeStringList(list);
        } catch (Exception ignore) {
            return normalizeStringList(splitLines(json));
        }
    }

    private String serializeAllowedTargetLanguages(List<String> list) {
        List<String> normalized = normalizeStringList(list);
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new IllegalArgumentException("allowedTargetLanguages 序列化失败", e);
        }
    }

    private static List<String> splitLines(String text) {
        if (text == null) return List.of();
        String t = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] parts = t.split("\n");
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) out.add(p);
        return out;
    }

    private static List<String> normalizeStringList(List<String> list) {
        if (list == null || list.isEmpty()) return List.of();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String raw : list) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isBlank()) continue;
            set.add(s);
        }
        return new ArrayList<>(set);
    }
}
