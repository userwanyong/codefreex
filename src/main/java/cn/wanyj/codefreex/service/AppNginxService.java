package cn.wanyj.codefreex.service;

import java.nio.file.Path;

/**
 * Nginx 发布服务
 *
 * @author BanXia
 */
public interface AppNginxService {

    Path publish(String deployKey);

    void remove(String deployKey);
}
