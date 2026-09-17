package cn.wanyj.codefreex.common;

/**
 * 项目通用常量
 *
 * @author wanyj
 */
public interface AppConstant {

    /**
     * 项目名称
     */
    String APP_NAME = "cfx";

    /**
     * Redis Key 前缀
     */
    String REDIS_KEY_PREFIX = "cfx:";

    /**
     * 代码输出根目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 代码部署根目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    String CODE_DOWNLOAD_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_download";

    String CODE_NGINX_ROOT_DIR = System.getProperty("user.dir") + "/tmp/nginx";
}
