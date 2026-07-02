package com.devflow.core.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationMemory 单元测试")
class ConversationMemoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOps;

    @InjectMocks
    private ConversationMemory memory;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
    }

    @Nested
    @DisplayName("addMessage — 添加消息")
    class AddMessage {

        @Test
        @DisplayName("添加消息 — 写入 Redis 并设置 TTL")
        void shouldAddMessageAndSetTTL() {
            memory.addMessage(1L, "REQUIREMENTS", "user", "Hello");

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            verify(listOps).rightPush(keyCaptor.capture(), valueCaptor.capture());
            assertTrue(keyCaptor.getValue().contains("1"));
            assertTrue(keyCaptor.getValue().contains("REQUIREMENTS"));
            assertEquals("user:Hello", valueCaptor.getValue());

            verify(redisTemplate).expire(anyString(), eq(4L), eq(TimeUnit.HOURS));
        }

        @Test
        @DisplayName("消息超过 MAX_MESSAGES(20) 时裁剪旧消息")
        void shouldTrimWhenExceedsMax() {
            when(listOps.size(anyString())).thenReturn(25L);

            memory.addMessage(1L, "CODER", "user", "New message");

            verify(listOps).trim(anyString(), eq(5L), eq(-1L));
        }

        @Test
        @DisplayName("消息未超过上限时不裁剪")
        void shouldNotTrimWhenUnderMax() {
            when(listOps.size(anyString())).thenReturn(10L);

            memory.addMessage(1L, "CODER", "user", "Message");

            verify(listOps, never()).trim(anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("不同 agent 的消息存入不同 key")
        void shouldUseDifferentKeyPerAgent() {
            memory.addMessage(1L, "REQUIREMENTS", "user", "Msg1");
            memory.addMessage(1L, "CODER", "user", "Msg2");

            verify(listOps, times(2)).rightPush(anyString(), anyString());
            // 两次调用应使用不同的 key
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(listOps, times(2)).rightPush(keyCaptor.capture(), anyString());
            List<String> keys = keyCaptor.getAllValues();
            assertNotEquals(keys.get(0), keys.get(1));
        }
    }

    @Nested
    @DisplayName("getHistory — 获取历史")
    class GetHistory {

        @Test
        @DisplayName("获取历史 — 返回已有消息")
        void shouldReturnExistingMessages() {
            List<String> messages = Arrays.asList("user:Hello", "assistant:Hi");
            when(listOps.range(anyString(), eq(0L), eq(-1L))).thenReturn(messages);

            List<String> history = memory.getHistory(1L, "REQUIREMENTS");

            assertEquals(2, history.size());
            assertEquals("user:Hello", history.get(0));
            assertEquals("assistant:Hi", history.get(1));
        }

        @Test
        @DisplayName("获取历史 — 无消息时返回空列表")
        void shouldReturnEmptyListWhenNoMessages() {
            when(listOps.range(anyString(), eq(0L), eq(-1L))).thenReturn(null);

            List<String> history = memory.getHistory(1L, "REQUIREMENTS");

            assertNotNull(history);
            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("获取历史 — 空列表时返回空列表")
        void shouldReturnEmptyForEmptyList() {
            when(listOps.range(anyString(), eq(0L), eq(-1L))).thenReturn(Collections.emptyList());

            List<String> history = memory.getHistory(1L, "REQUIREMENTS");

            assertNotNull(history);
            assertTrue(history.isEmpty());
        }
    }

    @Nested
    @DisplayName("clearHistory — 清除历史")
    class ClearHistory {

        @Test
        @DisplayName("清除历史 — 删除 Redis key")
        void shouldDeleteRedisKey() {
            memory.clearHistory(1L, "REQUIREMENTS");

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(redisTemplate).delete(keyCaptor.capture());
            assertTrue(keyCaptor.getValue().contains("REQUIREMENTS"));
        }
    }
}