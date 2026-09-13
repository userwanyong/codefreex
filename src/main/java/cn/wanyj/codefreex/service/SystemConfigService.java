package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.dto.response.CreditConfigVO;
import cn.wanyj.codefreex.model.dto.response.SystemConfigGroupVO;
import cn.wanyj.codefreex.model.enums.SystemConfigKey;

import java.util.List;
import java.util.Map;

/**
 * 系统配置服务：AI 服务商、码点计费等运行时配置的读取与热更新
 *
 * @author wanyj
 */
public interface SystemConfigService {

    /**
     * 读取字符串配置
     */
    String getString(SystemConfigKey key);

    /**
     * 读取面向用户展示的码点计费配置（价格信息，可公开）
     */
    CreditConfigVO getCreditConfig();

    /**
     * 读取整数配置（解析失败回退默认值）
     */
    int getInt(SystemConfigKey key);

    /**
     * 读取小数配置（解析失败回退默认值）
     */
    double getDouble(SystemConfigKey key);

    /**
     * 读取布尔配置（解析失败回退默认值）
     */
    boolean getBoolean(SystemConfigKey key);

    /**
     * 按分组列出全部配置项（管理端）
     */
    List<SystemConfigGroupVO> listGroupedConfigs();

    /**
     * 批量更新配置（管理端），校验键合法性与值类型
     *
     * @param configs 配置键 -> 配置值
     */
    void updateConfigs(Map<String, String> configs);
}
