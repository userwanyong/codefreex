package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.enums.CodeGenType;
import cn.wanyj.codefreex.service.strategy.CodePersistStrategy;

/**
 * 浠ｇ爜鎸佷箙鍖栫瓥鐣ョ鐞嗗櫒
 *
 * @author BanXia
 */
public interface CodePersistStrategyRegistry {

    CodePersistStrategy getStrategy(CodeGenType codeGenType);
}
