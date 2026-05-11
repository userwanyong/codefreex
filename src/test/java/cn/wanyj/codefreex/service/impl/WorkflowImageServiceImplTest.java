package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.AiConfig;
import cn.wanyj.codefreex.config.AppRuntimeConfig;
import cn.wanyj.codefreex.model.dto.response.WorkflowImageAsset;
import cn.wanyj.codefreex.service.OssService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WorkflowImageServiceImplTest {

    private WorkflowImageServiceImpl createService(boolean fetchEnabled) {
        AppRuntimeConfig.WorkflowProperties properties = new AppRuntimeConfig.WorkflowProperties();
        properties.setImageFetchEnabled(fetchEnabled);
        OssService ossService = mock(OssService.class);
        ChatModel chatModel = mock(ChatModel.class);
        AiConfig.PromptLoader promptLoader = mock(AiConfig.PromptLoader.class);
        ObjectMapper objectMapper = new ObjectMapper();
        return new WorkflowImageServiceImpl(properties, ossService, chatModel, promptLoader, objectMapper);
    }

    @Test
    void collectAssets_returnsFourAssetTypes() {
        WorkflowImageServiceImpl service = createService(false);

        List<WorkflowImageAsset> assets = service.collectAssets("saas dashboard");

        assertThat(assets).hasSize(4);
        assertThat(assets).extracting("type")
                .containsExactlyInAnyOrder("content", "illustration", "diagram", "logo");
        assertThat(assets).allMatch(asset -> asset.getUrl().startsWith("placeholder://"));
    }

    @Test
    void fetchSingleAsset_whenDisabled_returnsPlaceholder() {
        WorkflowImageServiceImpl service = createService(false);

        WorkflowImageAsset asset = service.fetchSingleAsset("content", "office", "", "");

        assertThat(asset.getType()).isEqualTo("content");
        assertThat(asset.getUrl()).startsWith("placeholder://");
    }

    @Test
    void fetchSingleAsset_unknownType_returnsPlaceholder() {
        WorkflowImageServiceImpl service = createService(true);

        WorkflowImageAsset asset = service.fetchSingleAsset("unknown", "test", "", "");

        assertThat(asset.getType()).isEqualTo("unknown");
        assertThat(asset.getUrl()).startsWith("https://assets.codefreex.local/");
    }
}
