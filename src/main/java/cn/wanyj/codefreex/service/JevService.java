package cn.wanyj.codefreex.service;

import java.util.Optional;

/**
 * jev 结构化决策服务（TypeSafe AI "System One" 模型）：
 * 从有限候选项中选择一个，用于替代生成流程中"轻量判断"类 LLM 调用（毫秒级返回，降低首响延迟）。
 *
 * 服务自身不抛业务异常：未启用、调用失败、结果缺失时返回空，由调用方回退原有判断链路。
 *
 * @author wanyj
 */
public interface JevService {

    /**
     * 单问题选择决策（choice）
     *
     * @param content        待判断正文（用户消息、PRD+需求等），将与决策定义中的 statePrefix 拼接为 state
     * @param definitionFile 决策定义文件名（prompts 目录下的 JSON，含 question/instructions/criteria/statePrefix）
     * @return 选中项（criteria 的某个键）；服务不可用或结果缺失时返回 Optional.empty()
     */
    Optional<String> classify(String content, String definitionFile);
}
