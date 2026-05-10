package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.model.enums.CodeGenType;
import cn.wanyj.codefreex.service.CodePersistStrategyRegistry;
import cn.wanyj.codefreex.service.strategy.CodePersistStrategy;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 浠ｇ爜鎸佷箙鍖栫瓥鐣ョ鐞嗗櫒瀹炵幇
 *
 * @author BanXia
 */
@Service
public class CodePersistStrategyRegistryImpl implements CodePersistStrategyRegistry {

    private final Map<CodeGenType, CodePersistStrategy> strategyMap = new EnumMap<>(CodeGenType.class);

    public CodePersistStrategyRegistryImpl(List<CodePersistStrategy> strategies) {
        for (CodePersistStrategy strategy : strategies) {
            strategyMap.put(strategy.getCodeGenType(), strategy);
        }
    }

    @Override
    public CodePersistStrategy getStrategy(CodeGenType codeGenType) {
        return strategyMap.getOrDefault(codeGenType, strategyMap.get(CodeGenType.HTML));
    }
}
