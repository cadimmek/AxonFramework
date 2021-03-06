/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling;

import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
class AsynchronousCommandBusTest {

    private MessageHandlerInterceptor<CommandMessage<?>> handlerInterceptor;
    private MessageDispatchInterceptor<CommandMessage<?>> dispatchInterceptor;
    private MessageHandler<CommandMessage<?>> commandHandler;
    private ExecutorService executorService;
    private AsynchronousCommandBus testSubject;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        commandHandler = mock(MessageHandler.class);
        executorService = mock(ExecutorService.class);
        dispatchInterceptor = mock(MessageDispatchInterceptor.class);
        handlerInterceptor = mock(MessageHandlerInterceptor.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(executorService).execute(isA(Runnable.class));
        testSubject = AsynchronousCommandBus.builder().executor(executorService).build();
        testSubject.registerDispatchInterceptor(dispatchInterceptor);
        testSubject.registerHandlerInterceptor(handlerInterceptor);
        when(dispatchInterceptor.handle(isA(CommandMessage.class)))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        when(handlerInterceptor.handle(isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> ((InterceptorChain) invocation.getArguments()[1]).proceed());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDispatchWithCallback() throws Exception {
        testSubject.subscribe(Object.class.getName(), commandHandler);
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        CommandMessage<Object> command = asCommandMessage(new Object());
        testSubject.dispatch(command, mockCallback);

        InOrder inOrder = inOrder(mockCallback,
                                  executorService,
                                  commandHandler,
                                  dispatchInterceptor,
                                  handlerInterceptor);
        inOrder.verify(dispatchInterceptor).handle(isA(CommandMessage.class));
        inOrder.verify(executorService).execute(isA(Runnable.class));
        inOrder.verify(handlerInterceptor).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(commandHandler).handle(isA(CommandMessage.class));
        ArgumentCaptor<CommandMessage<Object>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        ArgumentCaptor<CommandResultMessage<Object>> responseCaptor = ArgumentCaptor
                .forClass(CommandResultMessage.class);
        inOrder.verify(mockCallback).onResult(commandCaptor.capture(), responseCaptor.capture());
        assertEquals(command, commandCaptor.getValue());
        assertNull(responseCaptor.getValue().getPayload());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDispatchWithoutCallback() throws Exception {
        MessageHandler<CommandMessage<?>> commandHandler = mock(MessageHandler.class);
        testSubject.subscribe(Object.class.getName(), commandHandler);
        testSubject.dispatch(asCommandMessage(new Object()), NoOpCallback.INSTANCE);

        InOrder inOrder = inOrder(executorService, commandHandler, dispatchInterceptor, handlerInterceptor);
        inOrder.verify(dispatchInterceptor).handle(isA(CommandMessage.class));
        inOrder.verify(executorService).execute(isA(Runnable.class));
        inOrder.verify(handlerInterceptor).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(commandHandler).handle(isA(CommandMessage.class));
    }

    @Test
    void testShutdown_ExecutorServiceUsed() {
        testSubject.shutdown();

        verify(executorService).shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testExceptionIsThrownWhenNoHandlerIsRegistered() {
        CommandCallback<Object, Object> callback = mock(CommandCallback.class);
        CommandMessage<Object> command = asCommandMessage("test");
        testSubject.dispatch(command, callback);
        //noinspection rawtypes
        ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(callback).onResult(eq(command), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(NoHandlerForCommandException.class,
                     commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test
    void testShutdown_ExecutorUsed() {
        Executor executor = mock(Executor.class);
        AsynchronousCommandBus.builder().executor(executor).build().shutdown();

        verify(executor, never()).execute(any(Runnable.class));
    }
}
