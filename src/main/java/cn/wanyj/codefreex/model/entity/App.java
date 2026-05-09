package cn.wanyj.codefreex.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.Fastjson2TypeHandler;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用实体
 *
 * @author wanyj
 */
@Data
@Table("app")
public class App {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private String appName;

    private String description;

    private String cover;

    private String initPrompt;

    private String codeGenType;

    private String status;

    private String deployKey;

    private LocalDateTime deployedTime;

    private Integer isPublic;

    private Integer isFeatured;

    private Integer priority;

    private Integer viewCount;

    private Integer likeCount;

    @Column(typeHandler = Fastjson2TypeHandler.class)
    private List<String> tags;

    private Long userId;

    @Column(onInsertValue = "now()")
    private LocalDateTime editTime;

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    @Column(onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;

    private Integer isDelete;
}
