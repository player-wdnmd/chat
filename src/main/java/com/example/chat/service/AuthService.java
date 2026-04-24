package com.example.chat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.chat.config.ChatAppProperties;
import com.example.chat.entity.ChatUser;
import com.example.chat.entity.ChatUserRedeemCodeUsage;
import com.example.chat.entity.ChatUserSession;
import com.example.chat.mapper.ChatUserMapper;
import com.example.chat.mapper.ChatUserRedeemCodeUsageMapper;
import com.example.chat.mapper.ChatUserSessionMapper;
import com.example.chat.model.AuthResponse;
import com.example.chat.model.AuthUserView;
import com.example.chat.model.AuthenticatedUser;
import com.example.chat.model.LoginRequest;
import com.example.chat.model.RedeemCodeRequest;
import com.example.chat.model.RegisterRequest;
import com.example.chat.model.UserProfileResponse;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 账号注册、登录态解析、积分扣减与返还都在这里集中处理。
 *
 * <p>运行时不再手写 SQL，而是统一通过 MyBatis-Plus / MyBatis-Plus-Join 的 Mapper 和 Wrapper
 * 完成数据库读写。</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String AUTHENTICATED_USER_ATTRIBUTE = "authenticatedUser";

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final ChatAppProperties properties;
    private final ChatUserMapper chatUserMapper;
    private final ChatUserRedeemCodeUsageMapper chatUserRedeemCodeUsageMapper;
    private final ChatUserSessionMapper chatUserSessionMapper;
    private final AuthRateLimitService authRateLimitService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 注册新账号。
     *
     * <p>新用户默认积分为 0。
     * 注册成功后会直接创建一条登录会话，并把 bearer token 返回给前端。</p>
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp) {
        enforceRegisterRateLimit(clientIp);
        ChatUser user = new ChatUser();
        user.setAccountName(request.accountName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPoints(properties.getAuth().getRegisterDefaultPoints());

        try {
            chatUserMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new AccountAlreadyExistsException("账号名已存在，请换一个账号名。");
        }

        String token = createSessionToken(user.getId());
        log.info("用户注册成功：userId={}，accountName={}", user.getId(), user.getAccountName());
        return new AuthResponse(token, user.getAccountName(), user.getPoints());
    }

    /**
     * 账号密码登录，成功后创建新的 bearer token 会话。
     */
    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp) {
        enforceLoginRateLimit(clientIp, request.accountName());
        ChatUser user = chatUserMapper.selectOne(
                Wrappers.<ChatUser>lambdaQuery()
                        .eq(ChatUser::getAccountName, request.accountName().trim())
        );
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("账号或密码错误。");
        }

        String token = createSessionToken(user.getId());
        log.info("用户登录成功：userId={}，accountName={}", user.getId(), user.getAccountName());
        return new AuthResponse(token, user.getAccountName(), user.getPoints());
    }

    /**
     * 返回当前用户的最新账号名和积分。
     */
    public UserProfileResponse getProfile(AuthenticatedUser user) {
        ChatUser refreshedUser = chatUserMapper.selectById(user.id());
        if (refreshedUser == null) {
            throw new AuthenticationFailedException("当前用户不存在。");
        }
        return new UserProfileResponse(refreshedUser.getAccountName(), normalizePointsForResponse(refreshedUser.getPoints()));
    }

    /**
     * 使用兑换码为当前账号充值积分。
     *
     * <p>规则：
     * `VIP111` -> 10 分
     * `VIP222` -> 20 分
     * ...
     * `VIP888` -> 80 分
     * `VIP999` -> 无限积分
     *
     * <p>默认限制为：同一账号对同一个兑换码只能使用一次。</p>
     */
    @Transactional
    public UserProfileResponse redeemCode(AuthenticatedUser user, RedeemCodeRequest request, String clientIp) {
        enforceRedeemRateLimit(user.id(), clientIp);
        String redeemCode = request.redeemCode().trim().toUpperCase();
        int pointsToGrant = resolveRedeemCodePoints(redeemCode);
        if (pointsToGrant < 0) {
            throw new IllegalArgumentException("兑换码无效。");
        }

        boolean alreadyUsed = chatUserRedeemCodeUsageMapper.selectCount(
                Wrappers.<ChatUserRedeemCodeUsage>lambdaQuery()
                        .eq(ChatUserRedeemCodeUsage::getUserId, user.id())
                        .eq(ChatUserRedeemCodeUsage::getRedeemCode, redeemCode)
        ) > 0;
        if (alreadyUsed) {
            throw new IllegalArgumentException("该兑换码已使用。");
        }

        ChatUserRedeemCodeUsage usage = new ChatUserRedeemCodeUsage();
        usage.setUserId(user.id());
        usage.setRedeemCode(redeemCode);
        usage.setPointsGranted(pointsToGrant);
        chatUserRedeemCodeUsageMapper.insert(usage);

        if (pointsToGrant == properties.getLimits().getUnlimitedPoints()) {
            chatUserMapper.update(
                    null,
                    Wrappers.<ChatUser>lambdaUpdate()
                            .eq(ChatUser::getId, user.id())
                            .set(ChatUser::getPoints, properties.getLimits().getUnlimitedPoints())
            );
        } else {
            chatUserMapper.update(
                    null,
                    Wrappers.<ChatUser>lambdaUpdate()
                            .eq(ChatUser::getId, user.id())
                            .setIncrBy(ChatUser::getPoints, pointsToGrant)
            );
        }

        UserProfileResponse profile = getProfile(user);
        log.info(
                "兑换码使用成功：userId={}，redeemCode={}，pointsGranted={}，currentPoints={}",
                user.id(),
                redeemCode,
                pointsToGrant,
                profile.points()
        );
        return profile;
    }

    /**
     * 通过 bearer token 解析当前用户。
     *
     * <p>这里使用 MPJ 做 session -> user 的 join，避免手写 SQL。</p>
     */
    public Optional<AuthenticatedUser> resolveByToken(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }

        String tokenHash = sha256(token);
        ChatUserSession session = chatUserSessionMapper.selectOne(
                Wrappers.<ChatUserSession>lambdaQuery()
                        .eq(ChatUserSession::getTokenHash, tokenHash)
        );
        if (session == null) {
            return Optional.empty();
        }

        if (isSessionExpired(session)) {
            chatUserSessionMapper.delete(
                    Wrappers.<ChatUserSession>lambdaQuery()
                            .eq(ChatUserSession::getTokenHash, tokenHash)
            );
            log.info("检测到过期会话，已移除：sessionId={}，userId={}", session.getId(), session.getUserId());
            return Optional.empty();
        }

        MPJLambdaWrapper<ChatUserSession> wrapper = new MPJLambdaWrapper<ChatUserSession>()
                .selectAs(ChatUser::getId, AuthUserView::getId)
                .selectAs(ChatUser::getAccountName, AuthUserView::getAccountName)
                .selectAs(ChatUser::getPoints, AuthUserView::getPoints)
                .leftJoin(ChatUser.class, ChatUser::getId, ChatUserSession::getUserId)
                .eq(ChatUserSession::getTokenHash, tokenHash);

        AuthUserView userView = chatUserSessionMapper.selectJoinOne(AuthUserView.class, wrapper);
        if (userView == null) {
            return Optional.empty();
        }

        chatUserSessionMapper.update(
                null,
                Wrappers.<ChatUserSession>lambdaUpdate()
                        .eq(ChatUserSession::getTokenHash, tokenHash)
                        .set(ChatUserSession::getLastUsedAt, LocalDateTime.now())
        );
        return Optional.of(new AuthenticatedUser(userView.getId(), userView.getAccountName(), userView.getPoints()));
    }

    /**
     * 从当前请求中取出已经鉴权通过的用户上下文。
     */
    public AuthenticatedUser requireAuthenticatedUser(HttpServletRequest request) {
        Object attribute = request.getAttribute(AUTHENTICATED_USER_ATTRIBUTE);
        if (attribute instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        throw new AuthenticationFailedException("未登录或登录已失效。");
    }

    public void logout(HttpServletRequest request) {
        String token = extractBearerToken(request.getHeader("Authorization"));
        if (!StringUtils.hasText(token)) {
            throw new AuthenticationFailedException("未登录或登录已失效。");
        }
        chatUserSessionMapper.delete(
                Wrappers.<ChatUserSession>lambdaQuery()
                        .eq(ChatUserSession::getTokenHash, sha256(token))
        );
    }

    /**
     * 原子扣减 1 积分。
     *
     * <p>只有 `points > 0` 时才会更新成功，因此不会出现负积分。</p>
     */
    public int consumeOnePoint(Long userId) {
        return consumePoints(userId, 1);
    }

    /**
     * 原子扣减指定积分。
     */
    public int consumePoints(Long userId, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("积分扣减额度必须大于 0。");
        }

        ChatUser currentUser = chatUserMapper.selectById(userId);
        if (currentUser == null) {
            throw new AuthenticationFailedException("当前用户不存在。");
        }
        if (isUnlimitedPoints(currentUser.getPoints())) {
            log.info("检测到无限积分账号，本次操作不扣减积分：userId={}，points={}，amount={}", userId, currentUser.getPoints(), amount);
            return properties.getLimits().getUnlimitedPoints();
        }

        int updatedRows = chatUserMapper.update(
                null,
                Wrappers.<ChatUser>lambdaUpdate()
                        .eq(ChatUser::getId, userId)
                        .gt(ChatUser::getPoints, amount - 1)
                        .setDecrBy(ChatUser::getPoints, amount)
        );
        if (updatedRows == 0) {
            throw new InsufficientPointsException("积分不足，至少需要 " + amount + " 积分。");
        }
        int remainingPoints = getPoints(userId);
        log.info("积分扣减成功：userId={}，amount={}，remainingPoints={}", userId, amount, remainingPoints);
        return remainingPoints;
    }

    /**
     * 当模型调用失败时，把刚刚扣掉的 1 积分退回。
     */
    public int refundOnePoint(Long userId) {
        return refundPoints(userId, 1);
    }

    /**
     * 当下游调用失败时，把刚刚扣掉的积分退回。
     */
    public int refundPoints(Long userId, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("积分退回额度必须大于 0。");
        }

        ChatUser currentUser = chatUserMapper.selectById(userId);
        if (currentUser != null && isUnlimitedPoints(currentUser.getPoints())) {
            return properties.getLimits().getUnlimitedPoints();
        }

        chatUserMapper.update(
                null,
                Wrappers.<ChatUser>lambdaUpdate()
                        .eq(ChatUser::getId, userId)
                        .setIncrBy(ChatUser::getPoints, amount)
        );
        int points = getPoints(userId);
        log.info("积分已退回：userId={}，amount={}，points={}", userId, amount, points);
        return points;
    }

    public int getPoints(Long userId) {
        ChatUser user = chatUserMapper.selectById(userId);
        return user == null || user.getPoints() == null ? 0 : normalizePointsForResponse(user.getPoints());
    }

    private int resolveRedeemCodePoints(String redeemCode) {
        if (!StringUtils.hasText(redeemCode)) {
            return -1;
        }
        return properties.getAuth().getRedeemCodes().getOrDefault(redeemCode, -1);
    }

    private void enforceLoginRateLimit(String clientIp, String accountName) {
        authRateLimitService.checkAllowed(
                "login:" + safeBucketPart(clientIp) + ":" + safeBucketPart(accountName),
                properties.getAuth().getLoginRateLimitMaxRequests(),
                properties.getAuth().getLoginRateLimitWindow(),
                "登录请求过于频繁，请稍后再试。"
        );
    }

    private void enforceRegisterRateLimit(String clientIp) {
        authRateLimitService.checkAllowed(
                "register:" + safeBucketPart(clientIp),
                properties.getAuth().getRegisterRateLimitMaxRequests(),
                properties.getAuth().getRegisterRateLimitWindow(),
                "注册请求过于频繁，请稍后再试。"
        );
    }

    private void enforceRedeemRateLimit(Long userId, String clientIp) {
        authRateLimitService.checkAllowed(
                "redeem:" + userId + ":" + safeBucketPart(clientIp),
                properties.getAuth().getRedeemRateLimitMaxRequests(),
                properties.getAuth().getRedeemRateLimitWindow(),
                "兑换请求过于频繁，请稍后再试。"
        );
    }

    private boolean isSessionExpired(ChatUserSession session) {
        LocalDateTime lastUsedAt = session.getLastUsedAt() != null ? session.getLastUsedAt() : session.getCreatedAt();
        LocalDateTime expiresAt = lastUsedAt.plus(properties.getAuth().getSessionMaxIdle());
        return expiresAt.isBefore(LocalDateTime.now());
    }

    private String safeBucketPart(String value) {
        return StringUtils.hasText(value) ? value.trim() : "unknown";
    }

    private String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    /**
     * 兼容旧版本“无限积分也被扣减过几次”的历史数据。
     *
     * <p>只要积分仍然处于无限积分哨兵值附近，就继续按无限积分处理，
     * 避免已经使用过几次消息的 admin / VIP999 账号突然显示成普通大数字。</p>
     */
    private boolean isUnlimitedPoints(Integer points) {
        Integer unlimitedPoints = properties.getLimits().getUnlimitedPoints();
        Integer tolerance = properties.getLimits().getUnlimitedPointsTolerance();
        return points != null && unlimitedPoints != null && tolerance != null && points >= unlimitedPoints - tolerance;
    }

    private int normalizePointsForResponse(Integer points) {
        return isUnlimitedPoints(points) ? properties.getLimits().getUnlimitedPoints() : (points == null ? 0 : points);
    }

    private String createSessionToken(Long userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        ChatUserSession session = new ChatUserSession();
        session.setUserId(userId);
        session.setTokenHash(sha256(token));
        chatUserSessionMapper.insert(session);
        return token;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256。", exception);
        }
    }
}
