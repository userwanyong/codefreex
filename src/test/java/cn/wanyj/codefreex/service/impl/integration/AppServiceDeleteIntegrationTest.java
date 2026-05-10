package cn.wanyj.codefreex.service.impl.integration;

import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.mapper.AppMapper;
import cn.wanyj.codefreex.mapper.ChatHistoryMapper;
import cn.wanyj.codefreex.service.AppService;
import cn.wanyj.codefreex.service.ChatHistoryService;
import cn.wanyj.codefreex.testutil.IntegrationTestConfig;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static cn.wanyj.codefreex.model.entity.table.ChatHistoryTableDef.CHAT_HISTORY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author BanXia
 */
@SpringBootTest(classes = cn.wanyj.codefreex.TestApplication.class)
@ActiveProfiles("test")
@Transactional
@Import(IntegrationTestConfig.class)
class AppServiceDeleteIntegrationTest {

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AppService appService;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @Autowired
    private ChatHistoryService chatHistoryService;

    private final Long appId = 1001L;
    private final Long userId = 2001L;
    private final String deployKey = "DK_TEST_001";

    @AfterEach
    void tearDown() throws IOException {
        deleteIfExists(Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, deployKey));
        deleteIfExists(Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey));
    }

    @Test
    void deleteApp_cascadesFilesAndChatHistory() throws Exception {
        Files.createDirectories(Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, deployKey));
        Files.createDirectories(Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey));
        Files.writeString(Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, deployKey, "index.html"), "<html>out</html>");
        Files.writeString(Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey, "index.html"), "<html>deploy</html>");
        chatHistoryService.saveUserMessage(appId, userId, "hello");

        appService.deleteApp(userId, appId);

        assertThat(appMapper.selectOneById(appId)).isNull();
        assertThat(chatHistoryMapper.selectListByQuery(QueryWrapper.create().where(CHAT_HISTORY.APP_ID.eq(appId)))).isEmpty();
        assertThat(Files.exists(Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, deployKey))).isFalse();
        assertThat(Files.exists(Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey))).isFalse();
    }

    private void deleteIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
