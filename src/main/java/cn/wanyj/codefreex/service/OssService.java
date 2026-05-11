package cn.wanyj.codefreex.service;

import java.nio.file.Path;

/**
 * OSS 存储服务
 *
 * @author BanXia
 */
public interface OssService {

    /**
     * 上传文件到 OSS
     *
     * @param objectKey OSS 对象键
     * @param filePath  本地文件路径
     * @return 文件的公网访问 URL
     */
    String upload(String objectKey, Path filePath);

    /**
     * 上传字节数据到 OSS
     *
     * @param objectKey   OSS 对象键
     * @param data        文件数据
     * @param contentType MIME 类型
     * @return 文件的公网访问 URL
     */
    String upload(String objectKey, byte[] data, String contentType);
}
