package cn.wanyj.codefreex.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用-标签关联
 *
 * @author wanyj
 */
@Data
@Table("app_tag")
public class AppTag {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long appId;

    private Long tagId;

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    private Integer isDelete;
}
