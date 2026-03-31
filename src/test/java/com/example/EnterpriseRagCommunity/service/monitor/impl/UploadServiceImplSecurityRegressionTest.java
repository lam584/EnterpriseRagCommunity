package com.example.EnterpriseRagCommunity.service.monitor.impl;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadFormatsConfigDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.UploadResultDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.monitor.FileAssetExtractionService;
import com.example.EnterpriseRagCommunity.service.monitor.UploadFormatsConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadServiceImplSecurityRegressionTest {

	@AfterEach
	void cleanup() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void upload_shouldSanitizeOriginalFileNameAndPersistServerGeneratedPath(@TempDir Path tempDir) {
		FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
		AdministratorService administratorService = mock(AdministratorService.class);
		UploadFormatsConfigService uploadFormatsConfigService = mock(UploadFormatsConfigService.class);
		FileAssetExtractionService fileAssetExtractionService = mock(FileAssetExtractionService.class);

		UsersEntity me = new UsersEntity();
		me.setId(100L);
		when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
		when(administratorService.findById(eq(100L))).thenReturn(Optional.of(me));

		UploadFormatsConfigDTO cfg = UploadFormatsConfigDTO.empty();
		cfg.setEnabled(true);
		cfg.setMaxFilesPerRequest(10);
		cfg.setMaxFileSizeBytes(50L * 1024 * 1024);
		cfg.setMaxTotalSizeBytes(200L * 1024 * 1024);
		when(uploadFormatsConfigService.getConfig()).thenReturn(cfg);

		UploadFormatsConfigDTO.UploadFormatRuleDTO rule = new UploadFormatsConfigDTO.UploadFormatRuleDTO();
		rule.setFormat("TXT");
		rule.setEnabled(true);
		rule.setExtensions(java.util.List.of("txt"));
		rule.setParseEnabled(true);
		when(uploadFormatsConfigService.enabledExtensionToRule()).thenReturn(Map.of("txt", rule));

		when(fileAssetsRepository.findBySha256(any())).thenReturn(Optional.empty());
		when(fileAssetsRepository.save(any())).thenAnswer(invocation -> {
			FileAssetsEntity fa = invocation.getArgument(0);
			fa.setId(77L);
			return fa;
		});

		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", java.util.List.of()));

		MockMultipartFile file = new MockMultipartFile(
				"file",
				"../../../../evil<script>.txt",
				"text/plain",
				"hello".getBytes(StandardCharsets.UTF_8)
		);

		UploadServiceImpl svc = new UploadServiceImpl();
		ReflectionTestUtils.setField(svc, "fileAssetsRepository", fileAssetsRepository);
		ReflectionTestUtils.setField(svc, "administratorService", administratorService);
		ReflectionTestUtils.setField(svc, "uploadFormatsConfigService", uploadFormatsConfigService);
		ReflectionTestUtils.setField(svc, "fileAssetExtractionService", fileAssetExtractionService);
		ReflectionTestUtils.setField(svc, "uploadRoot", tempDir.toString());
		ReflectionTestUtils.setField(svc, "urlPrefix", "/uploads");

		UploadResultDTO out = svc.upload(file);
		assertEquals(77L, out.getId());
		assertEquals("evil_script_.txt", out.getFileName());

		ArgumentCaptor<FileAssetsEntity> captor = ArgumentCaptor.forClass(FileAssetsEntity.class);
		verify(fileAssetsRepository).save(captor.capture());
		FileAssetsEntity saved = captor.getValue();

		Path savedPath = Paths.get(saved.getPath()).toAbsolutePath().normalize();
		assertTrue(savedPath.startsWith(tempDir.toAbsolutePath().normalize()));
		String storedFileName = savedPath.getFileName().toString();
		assertEquals(32, storedFileName.length());
		assertFalse(storedFileName.contains("."));
		assertFalse(storedFileName.contains("/"));
		assertFalse(storedFileName.contains("\\"));
	}
}

