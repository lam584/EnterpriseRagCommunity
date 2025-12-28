package com.example.EnterpriseRagCommunity;

import com.example.EnterpriseRagCommunity.repository.content.PostViewsDailyRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 说明：本项目测试环境可能未配置可用的 MySQL/Flyway（CI/本机差异较大）。
 * 该用例仅作为本地联调时的快速回归校验，默认禁用。
 */
@SpringBootTest
@Disabled("Enable manually when a MySQL datasource is available")
class PostViewsDailyRepositoryTest {

    @Autowired
    private PostViewsDailyRepository postViewsDailyRepository;

    @Test
    void increment_should_upsert_and_increase_count() {
        long postId = 1L;
        LocalDate day = LocalDate.now();

        long before = postViewsDailyRepository.sumViewsByPostId(postId);
        postViewsDailyRepository.increment(postId, day);
        long after = postViewsDailyRepository.sumViewsByPostId(postId);

        assertThat(after).isGreaterThanOrEqualTo(before + 1);
    }
}

