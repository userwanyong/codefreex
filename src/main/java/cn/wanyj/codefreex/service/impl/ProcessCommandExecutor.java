package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.service.CommandExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * 进程命令执行器
 *
 * @author BanXia
 */
@Service
public class ProcessCommandExecutor implements CommandExecutor {

    @Override
    public void execute(List<String> command, Path workingDirectory) {
        try {
            List<String> effectiveCommand = command;
            if (isWindows()) {
                List<String> windowsCommand = new java.util.ArrayList<>();
                windowsCommand.add("cmd");
                windowsCommand.add("/c");
                windowsCommand.addAll(command);
                effectiveCommand = windowsCommand;
            }
            ProcessBuilder processBuilder = new ProcessBuilder(effectiveCommand);
            if (workingDirectory != null) {
                processBuilder.directory(workingDirectory.toFile());
            }
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("命令执行失败: " + String.join(" ", command) + ", exitCode=" + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("命令执行被中断", e);
        } catch (Exception e) {
            throw new RuntimeException("命令执行失败: " + String.join(" ", command), e);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
