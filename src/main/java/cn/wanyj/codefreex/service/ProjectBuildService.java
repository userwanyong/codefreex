package cn.wanyj.codefreex.service;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 项目构建服务
 *
 * @author BanXia
 */
public interface ProjectBuildService {

    void buildVueProject(Path generatedRootDir, Consumer<String> progressConsumer);
}
