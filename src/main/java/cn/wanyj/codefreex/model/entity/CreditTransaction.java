package cn.wanyj.codefreex.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 码点流水实体
 *
 * @author wanyj
 */
@Data
@Table("credit_transaction")
public class CreditTransaction {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long userId;

    private String type;

    private Integer amount;

    private Integer balanceAfter;

    private String sourceType;

    private Long sourceId;

    private String description;

    private Long operatorId;

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;
}
