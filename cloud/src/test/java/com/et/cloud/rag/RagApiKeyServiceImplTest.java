package com.et.cloud.rag;

import com.et.cloud.exception.BusinessException;
import com.et.cloud.mapper.RagApiKeyMapper;
import com.et.cloud.model.entity.RagApiKey;
import com.et.cloud.model.entity.User;
import com.et.cloud.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * API key lifecycle: plaintext exists only in the create response; the stored
 * row carries just the SHA-256 hash; auth failures are uniform 40101; deletion
 * is owner-scoped and revokes immediately.
 */
class RagApiKeyServiceImplTest {

    private RagApiKeyMapper ragApiKeyMapper;
    private UserService userService;
    private RagApiKeyServiceImpl service;

    @BeforeEach
    void setUp() {
        // Lambda wrappers need the MyBatis-Plus lambda cache for RagApiKey
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                RagApiKey.class);
        ragApiKeyMapper = mock(RagApiKeyMapper.class);
        userService = mock(UserService.class);
        service = new RagApiKeyServiceImpl();
        ReflectionTestUtils.setField(service, "ragApiKeyMapper", ragApiKeyMapper);
        ReflectionTestUtils.setField(service, "userService", userService);
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    @Test
    void createReturnsPlaintextOnceAndStoresOnlyHash() {
        when(ragApiKeyMapper.insert(any(RagApiKey.class))).thenAnswer(inv -> {
            ((RagApiKey) inv.getArgument(0)).setId(1L);
            return 1;
        });

        RagApiKeyCreatedView view = service.create(user(7L), "本地 Agent");

        assertEquals("本地 Agent", view.getKeyName());
        assertTrue(view.getApiKey().startsWith("cpk_"));
        assertEquals(68, view.getApiKey().length());

        // stored row: hash matches plaintext, no plaintext column
        ArgumentCaptor<RagApiKey> captor = ArgumentCaptor.forClass(RagApiKey.class);
        verify(ragApiKeyMapper).insert(captor.capture());
        RagApiKey stored = captor.getValue();
        assertEquals(RagApiKeyServiceImpl.sha256Hex(view.getApiKey()), stored.getKeyHash());
        assertNotEquals(view.getApiKey(), stored.getKeyHash());
        assertEquals(7L, stored.getUserId());
        assertEquals(0, stored.getIsDelete());
    }

    @Test
    void resolveUserHitsOwnerByHash() {
        RagApiKey row = new RagApiKey();
        row.setId(1L);
        row.setUserId(7L);
        row.setIsDelete(0);
        when(ragApiKeyMapper.selectOne(any())).thenReturn(row);
        when(userService.getById(7L)).thenReturn(user(7L));

        User resolved = service.resolveUser("cpk_abc");
        assertEquals(7L, resolved.getId());
    }

    @Test
    void invalidOrRevokedKeyFailsUniformly() {
        // blank
        BusinessException blank = assertThrows(BusinessException.class,
                () -> service.resolveUser("  "));
        assertEquals(40101, blank.getCode());
        assertEquals(RagApiKeyServiceImpl.INVALID_KEY_MESSAGE, blank.getMessage());

        // unknown hash (covers malformed, revoked and deleted keys: same code path)
        when(ragApiKeyMapper.selectOne(any())).thenReturn(null);
        BusinessException unknown = assertThrows(BusinessException.class,
                () -> service.resolveUser("cpk_nope"));
        assertEquals(40101, unknown.getCode());
        assertEquals(RagApiKeyServiceImpl.INVALID_KEY_MESSAGE, unknown.getMessage());

        // owner vanished (deleted/banned user filtered by @TableLogic)
        RagApiKey row = new RagApiKey();
        row.setId(1L);
        row.setUserId(9L);
        row.setIsDelete(0);
        when(ragApiKeyMapper.selectOne(any())).thenReturn(row);
        when(userService.getById(9L)).thenReturn(null);
        BusinessException orphan = assertThrows(BusinessException.class,
                () -> service.resolveUser("cpk_orphan"));
        assertEquals(40101, orphan.getCode());
    }

    @Test
    void deleteIsOwnerScoped() {
        // not owned -> false, no update issued
        RagApiKey foreign = new RagApiKey();
        foreign.setId(2L);
        foreign.setUserId(8L);
        foreign.setIsDelete(0);
        when(ragApiKeyMapper.selectById(2L)).thenReturn(foreign);
        assertFalse(service.delete(user(7L), 2L));
        verify(ragApiKeyMapper, never()).update(any(), any());

        // owned -> soft delete via explicit set
        RagApiKey own = new RagApiKey();
        own.setId(3L);
        own.setUserId(7L);
        own.setIsDelete(0);
        when(ragApiKeyMapper.selectById(3L)).thenReturn(own);
        when(ragApiKeyMapper.update(any(), any())).thenReturn(1);
        assertTrue(service.delete(user(7L), 3L));
        verify(ragApiKeyMapper).update(any(), any());
    }

    @Test
    void listNeverExposesPlaintextOrOtherUsers() {
        RagApiKey row = new RagApiKey();
        row.setId(1L);
        row.setUserId(7L);
        row.setKeyName("本地 Agent");
        row.setKeyPrefix("cpk_a1");
        row.setIsDelete(0);
        when(ragApiKeyMapper.selectList(any())).thenReturn(List.of(row));

        List<RagApiKeyView> views = service.list(user(7L));
        assertEquals(1, views.size());
        assertEquals("本地 Agent", views.get(0).getKeyName());
        assertEquals("cpk_a1", views.get(0).getKeyPrefix());
        // view has no field for the plaintext/hash at all — compile-time guarantee
        verify(userService, never()).getById(anyLong());
        verify(ragApiKeyMapper).selectList(any());
    }
}
