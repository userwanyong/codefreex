package cn.wanyj.codefreex.service;

/**
 * 应用封面服务
 *
 * @author BanXia
 */
public interface AppCoverService {

    void generateCoverAsync(Long appId, String deployKey, String appName, String description);
}
