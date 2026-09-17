package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.service.CommandExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 进程命令执行器
 *
 * @author BanXia
 */
@Slf4j
@Service
public class ProcessCommandExecutor implements CommandExecutor {

    /** 失败异常中保留的输出末尾行数 */
    private static final int OUTPUT_TAIL_LINES = 40;

    private final long timeoutMinutes;

    public ProcessCommandExecutor(@Value("${app.command.timeout-minutes:15}") long timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    @Override
    public void execute(List<String> command, Path workingDirectory) {
        List<String> effectiveCommand = command;
        boolean windows = isWindows();
        if (windows) {
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

        Process process = null;
        try {
            process = processBuilder.start();
            final Process started = process;
            // 独立线程消费输出：留存日志与失败原因尾部，同时避免管道积压导致子进程写阻塞
            ConcurrentLinkedDeque<String> outputTail = new ConcurrentLinkedDeque<>();
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new java.io.InputStreamReader(started.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("[命令输出] {}", line);
                        outputTail.addLast(line);
                        while (outputTail.size() > OUTPUT_TAIL_LINES) {
                            outputTail.pollFirst();
                        }
                    }
                } catch (Exception ignored) {
                    // 进程被击杀时输出流会中断，属预期
                }
            }, "cmd-output-reader");
            outputReader.setDaemon(true);
            outputReader.start();

            // AI 生成的构建脚本存在挂起可能（引用不存在的脚本、误启 dev server 等），
            // waitFor 必须带超时，否则工作流线程会被永久阻塞
            if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
                killProcessTree(process, windows);
                throw new RuntimeException("命令执行超时(>" + timeoutMinutes + "分钟): "
                        + String.join(" ", command) + "\n--- 输出末尾 ---\n" + String.join("\n", outputTail));
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new RuntimeException("命令执行失败: " + String.join(" ", command) + ", exitCode=" + exitCode
                        + "\n--- 输出末尾 ---\n" + String.join("\n", outputTail));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                killProcessTree(process, windows);
            }
            throw new RuntimeException("命令执行被中断", e);
        } catch (java.io.IOException e) {
            throw new RuntimeException("命令执行失败: " + String.join(" ", command), e);
        }
    }

    /**
     * 超时/中断时彻底结束进程：npm→node 多层派生进程必须整树击杀，
     * 仅 destroyForcibly 直接子进程会留下仍在运行的孙进程（如 vite）。
     */
    private void killProcessTree(Process process, boolean windows) {
        try {
            if (windows) {
                new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(process.pid()))
                        .start().waitFor(10, TimeUnit.SECONDS);
            }
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        } catch (Exception e) {
            process.destroyForcibly();
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
