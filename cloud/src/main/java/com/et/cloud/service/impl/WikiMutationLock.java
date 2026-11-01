package com.et.cloud.service.impl;

import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.mapper.UserMapper;
import com.et.cloud.mapper.WikiSpaceMapper;
import com.et.cloud.model.entity.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Serializes changes within a space; callers must re-read all target state after locking. */
@Component
public class WikiMutationLock {
    private final WikiSpaceMapper spaces;
    private final UserMapper users;
    private final org.mybatis.spring.SqlSessionTemplate sqlSession;

    public WikiMutationLock(WikiSpaceMapper spaces, UserMapper users, org.mybatis.spring.SqlSessionTemplate sqlSession) {
        this.spaces = spaces;
        this.users = users;
        this.sqlSession = sqlSession;
    }

    public User acquire(long spaceId, long userId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Wiki mutation requires a transaction");
        if (spaces.lockActiveById(spaceId) == null)
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间不存在或已删除");
        User user = users.lockActiveById(userId);
        if (user == null) throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        sqlSession.clearCache();
        return user;
    }
}
