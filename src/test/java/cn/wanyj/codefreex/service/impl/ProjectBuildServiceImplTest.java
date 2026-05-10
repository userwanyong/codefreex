package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.AppRuntimeConfig;
import cn.wanyj.codefreex.service.CommandExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ProjectBuildServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void buildVueProject_whenBuildDisabled_writesFallbackPreview() throws Exception {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        AppRuntimeConfig.WorkflowProperties properties = new AppRuntimeConfig.WorkflowProperties();
        properties.setVueBuildEnabled(false);
        ProjectBuildServiceImpl service = new ProjectBuildServiceImpl(commandExecutor, properties);

        Path generatedRoot = tempDir.resolve("generated");
        Files.createDirectories(generatedRoot.resolve("source"));

        service.buildVueProject(generatedRoot, progress -> {
        });

        assertThat(generatedRoot.resolve("index.html")).exists();
    }

    @Test
    void buildVueProject_whenDistExists_copiesArtifacts() throws Exception {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        doNothing().when(commandExecutor).execute(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
        AppRuntimeConfig.WorkflowProperties properties = new AppRuntimeConfig.WorkflowProperties();
        properties.setVueBuildEnabled(true);
        ProjectBuildServiceImpl service = new ProjectBuildServiceImpl(commandExecutor, properties);

        Path generatedRoot = tempDir.resolve("generated2");
        Path distDir = generatedRoot.resolve("source").resolve("dist");
        Files.createDirectories(distDir);
        Files.writeString(distDir.resolve("index.html"), "<html>dist</html>");

        service.buildVueProject(generatedRoot, progress -> {
        });

        assertThat(Files.readString(generatedRoot.resolve("index.html"))).contains("dist");
        verify(commandExecutor, times(2))
                .execute(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(generatedRoot.resolve("source")));
    }
}
