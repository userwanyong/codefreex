package cn.wanyj.codefreex.service;

import java.nio.file.Path;
import java.util.List;

/**
 * 外部命令执行器
 *
 * @author BanXia
 */
public interface CommandExecutor {

    void execute(List<String> command, Path workingDirectory);
}
