package cn.wanyj.codefreex.service.strategy;

/**
 * 代码持久化策略接口
 * <p>
 * 策略模式：不同生成模式（HTML/多文件/Vue）的代码持久化策略
 *
 * @author wanyj
 */
public interface CodePersistStrategy {

    /**
     * 将 AI 生成的代码持久化到文件系统
     *
     * @param deployKey 部署标识
     * @param code      生成的代码
     * @return 文件保存路径
     */
    String persist(String deployKey, String code);
}
