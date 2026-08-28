package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.dto.LoginUserContext;
import cn.wanyj.codefreex.model.dto.OAuthLoginOutcome;
import cn.wanyj.codefreex.model.dto.request.LoginCodeRequest;
import cn.wanyj.codefreex.model.dto.request.OAuthCompleteRequest;
import cn.wanyj.codefreex.model.dto.request.RegisterRequest;
import cn.wanyj.codefreex.model.dto.response.TokenResponse;

import java.util.List;

/**
 * 认证服务：全部认证授权能力由 auth-service 微服务通过 RPC 提供，
 * 本服务仅做业务编排（邀请码消费、本地用户信息初始化、令牌下发）。
 *
 * @author wanyj
 */
public interface AuthService {

    /**
     * 查询当前租户开放的登录方式（管理端开闭后实时同步），如 password / email:smtp / oauth:gitee
     */
    List<String> listLoginMethods();

    /**
     * 账号密码登录（用户名或邮箱）
     */
    TokenResponse loginByPassword(String username, String password);

    /**
     * 发送登录验证码（邮箱/短信，方式须已启用）
     */
    void sendCode(String method, String target);

    /**
     * 验证码登录；新用户（目标不存在将自动注册）须携带有效邀请码
     */
    TokenResponse loginByCode(LoginCodeRequest request);

    /**
     * 邮箱注册（密码 + 邀请码）
     */
    TokenResponse register(RegisterRequest request);

    /**
     * 构建 OAuth 授权页 URL（gitee / github），前端跳转该 URL 发起授权
     */
    String buildOAuthAuthorizeUrl(String provider);

    /**
     * 处理 OAuth 提供方回调：老用户直接登录，新用户返回待补邀请码的临时令牌
     */
    OAuthLoginOutcome handleOAuthCallback(String provider, String code, String state);

    /**
     * OAuth 新用户完成注册（提交邀请码）
     */
    TokenResponse completeOAuthRegistration(OAuthCompleteRequest request);

    /**
     * 刷新令牌（轮换返回新令牌对）
     */
    TokenResponse refresh(String refreshToken);

    /**
     * 登出（拉黑 access + 删除 refresh）
     */
    void logout(String accessToken, String refreshToken);

    /**
     * 获取当前登录用户完整信息（含本地昵称头像回填）
     */
    LoginUserContext getLoginUser();
}
