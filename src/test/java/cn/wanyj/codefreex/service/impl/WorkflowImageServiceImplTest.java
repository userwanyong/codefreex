package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.AppRuntimeConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowImageServiceImplTest {

    @Test
    void collectAssets_returnsFourAssetTypes() {
        AppRuntimeConfig.WorkflowProperties properties = new AppRuntimeConfig.WorkflowProperties();
        properties.setImageFetchEnabled(false);
        WorkflowImageServiceImpl service = new WorkflowImageServiceImpl(properties);

        var assets = service.collectAssets("saas dashboard");

        assertThat(assets).hasSize(4);
        assertThat(assets).extracting("type")
                .containsExactlyInAnyOrder("content", "illustration", "diagram", "logo");
        assertThat(assets).allMatch(asset -> asset.getUrl().startsWith("placeholder://"));
    }
}
