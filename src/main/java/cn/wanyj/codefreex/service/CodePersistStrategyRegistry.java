package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.enums.CodeGenType;
import cn.wanyj.codefreex.service.strategy.CodePersistStrategy;

/**
 * 代码持久化策略管理器
 *
 * @author BanXia
 */
public interface CodePersistStrategyRegistry {

    CodePersistStrategy getStrategy(CodeGenType codeGenType);
}
