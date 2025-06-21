// java/com/example/hellospringboot/service/ViteManifestService.java
package com.example.NewsPublishingSystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ViteManifestService {

    private static final Logger logger = LoggerFactory.getLogger(ViteManifestService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    // 解析后的 manifest 数据，key：入口文件；value：构建后文件信息（包括 file、css 等）
    private Map<String, JsonNode> manifest = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        logger.info("开始初始化ViteManifestService，尝试加载manifest.json文件...");
        try (InputStream is = getClass().getResourceAsStream("/static/.vite/manifest.json")) {
            if (is == null) {
                logger.error("未找到manifest.json，在classpath:/static/.vite/manifest.json");
                return;
            }
            logger.debug("找到manifest.json文件，开始解析...");
            JsonNode root = objectMapper.readTree(is);
            logger.debug("manifest.json解析完成，开始提取入口信息");
            
            int count = 0;
            for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                manifest.put(key, value);
                count++;
                logger.debug("加载入口项 [{}]: {}", key, value.has("file") ? value.get("file").asText() : "无file字段");
            }
            logger.info("Vite manifest 加载成功：{}项", count);
            logger.debug("manifest内容概览: {}", manifest.keySet());
        } catch (Exception e) {
            logger.error("加载Vite manifest出错: {}", e.getMessage(), e);
        }
    }

    // 根据入口文件路径获取构建后文件 URL
    public String getAssetUrl(String entry) {
        logger.debug("请求获取资源URL, 入口: {}", entry);
        JsonNode assetNode = manifest.get(entry);
        if (assetNode != null && assetNode.has("file")) {
            String url = "/" + assetNode.get("file").asText();
            logger.debug("找到资源, 返回URL: {}", url);
            return url;
        }
        logger.warn("未找到入口[{}]对应的资源或无file字段", entry);
        return "";
    }

    // 检查资源是否存在
    public boolean hasAsset(String entry) {
        boolean exists = manifest.containsKey(entry);
        logger.debug("检查资源[{}]是否存在: {}", entry, exists);
        return exists;
    }
    
    // 获取CSS资源列表
    public String[] getCssFiles(String entry) {
        logger.debug("获取入口[{}]的CSS文件列表", entry);
        JsonNode node = manifest.get(entry);
        if (node != null && node.has("css") && node.get("css").isArray()) {
            JsonNode cssArray = node.get("css");
            String[] cssFiles = new String[cssArray.size()];
            for (int i = 0; i < cssArray.size(); i++) {
                cssFiles[i] = "/" + cssArray.get(i).asText();
                logger.debug("CSS文件 #{}: {}", i, cssFiles[i]);
            }
            logger.debug("找到{}个CSS文件", cssFiles.length);
            return cssFiles;
        }
        logger.debug("入口[{}]没有关联的CSS文件", entry);
        return new String[0];
    }

    public JsonNode getAssetInfo(String entry) {
        logger.debug("获取资源[{}]的完整信息", entry);
        JsonNode info = manifest.get(entry);
        if (info != null) {
            logger.debug("资源[{}]信息: {}", entry, info);
        } else {
            logger.warn("未找到资源[{}]的信息", entry);
        }
        return info;
    }
}
