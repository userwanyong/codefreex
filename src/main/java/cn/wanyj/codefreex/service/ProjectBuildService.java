package cn.wanyj.codefreex.service;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 椤圭洰鏋勫缓鏈嶅姟
 *
 * @author BanXia
 */
public interface ProjectBuildService {

    void buildVueProject(Path generatedRootDir, Consumer<String> progressConsumer);
}
