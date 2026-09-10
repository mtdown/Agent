package com.et.cloud.rag;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.mapper.RagApiKeyMapper;
import com.et.cloud.model.entity.RagApiKey;
import com.et.cloud.model.entity.User;
import com.et.cloud.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Default {@link RagApiKeyService}. Hash lookup is the single authentication
 * path; soft delete revokes immediately because lookups require isDelete = 0.
 */
@Service
@Slf4j
public class RagApiKeyServiceImpl implements RagApiKeyService {

    /** Uniform failure message — never reveals whether a key exists. */
    static final String INVALID_KEY_MESSAGE = "API Key 无效或已失效";

    private static final String KEY_PREFIX = "cpk_";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private RagApiKeyMapper ragApiKeyMapper;

    @Resource
    private UserService userService;

    @Override
    public RagApiKeyCreatedView create(User loginUser, String keyName) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR);
        String name = StrUtil.isBlank(keyName) ? "默认 Key" : keyName.trim();
        ThrowUtils.throwIf(name.length() > 64, ErrorCode.PARAMS_ERROR, "Key 名称过长（≤64 字符）");

        // plaintext: cpk_ + 32 random bytes hex (68 chars total)
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(KEY_PREFIX);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        String plaintext = hex.toString();

        RagApiKey entity = new RagApiKey();
        entity.setUserId(loginUser.getId());
        entity.setKeyName(name);
        entity.setKeyHash(sha256Hex(plaintext));
        entity.setKeyPrefix(plaintext.substring(0, KEY_PREFIX.length() + 4));
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        entity.setIsDelete(0);
        int inserted = ragApiKeyMapper.insert(entity);
        ThrowUtils.throwIf(inserted != 1, ErrorCode.SYSTEM_ERROR, "Key 创建失败");

        RagApiKeyCreatedView view = new RagApiKeyCreatedView();
        view.setId(entity.getId());
        view.setKeyName(name);
        view.setKeyPrefix(entity.getKeyPrefix());
        view.setApiKey(plaintext);
        view.setCreateTime(entity.getCreateTime());
        return view;
    }

    @Override
    public List<RagApiKeyView> list(User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR);
        List<RagApiKey> rows = ragApiKeyMapper.selectList(new LambdaQueryWrapper<RagApiKey>()
                .eq(RagApiKey::getUserId, loginUser.getId())
                .eq(RagApiKey::getIsDelete, 0)
                .orderByDesc(RagApiKey::getCreateTime));
        List<RagApiKeyView> views = new ArrayList<>();
        for (RagApiKey row : rows) {
            RagApiKeyView view = new RagApiKeyView();
            view.setId(row.getId());
            view.setKeyName(row.getKeyName());
            view.setKeyPrefix(row.getKeyPrefix());
            view.setCreateTime(row.getCreateTime());
            views.add(view);
        }
        return views;
    }

    @Override
    public boolean delete(User loginUser, long id) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR);
        RagApiKey row = ragApiKeyMapper.selectById(id);
        if (row == null || row.getIsDelete() != 0 || !loginUser.getId().equals(row.getUserId())) {
            return false;
        }
        // 红线：显式 set 写 isDelete，不用实体 updateById
        LambdaUpdateWrapper<RagApiKey> wrapper = new LambdaUpdateWrapper<RagApiKey>()
                .eq(RagApiKey::getId, id)
                .eq(RagApiKey::getUserId, loginUser.getId())
                .set(RagApiKey::getIsDelete, 1)
                .set(RagApiKey::getUpdateTime, new Date());
        return ragApiKeyMapper.update(null, wrapper) == 1;
    }

    @Override
    public User resolveUser(String apiKey) {
        if (StrUtil.isBlank(apiKey)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, INVALID_KEY_MESSAGE);
        }
        RagApiKey row = ragApiKeyMapper.selectOne(new LambdaQueryWrapper<RagApiKey>()
                .eq(RagApiKey::getKeyHash, sha256Hex(apiKey.trim()))
                .eq(RagApiKey::getIsDelete, 0)
                .last("LIMIT 1"));
        if (row == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, INVALID_KEY_MESSAGE);
        }
        // getById respects @TableLogic so banned/deleted owners fail here too
        User user = userService.getById(row.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, INVALID_KEY_MESSAGE);
        }
        return user;
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JDK spec
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
