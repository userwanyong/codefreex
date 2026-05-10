package cn.wanyj.codefreex.service.strategy.impl;

import cn.wanyj.codefreex.service.ProjectBuildService;
import cn.wanyj.codefreex.service.WorkflowFileToolService;
import cn.wanyj.codefreex.service.impl.LocalWorkflowFileToolService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VueProjectStrategyTest {

    @Test
    void persist_writesSourceFilesAndTriggersBuild() {
        WorkflowFileToolService fileToolService = new LocalWorkflowFileToolService();
        ProjectBuildService buildService = mock(ProjectBuildService.class);
        doNothing().when(buildService).buildVueProject(any(), any());
        VueProjectStrategy strategy = new VueProjectStrategy(fileToolService, buildService);

        strategy.persist("vue_test", """
                ```file:package.json
                {"name":"demo"}
                ```
                ```file:src/App.vue
                <template><div>demo</div></template>
                ```
                """);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(buildService).buildVueProject(pathCaptor.capture(), any());
        assertThat(pathCaptor.getValue().toString()).contains("vue_test");
    }
}
