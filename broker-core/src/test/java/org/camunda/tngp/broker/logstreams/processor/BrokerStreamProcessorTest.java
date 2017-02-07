package org.camunda.tngp.broker.logstreams.processor;

import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.protocol.clientapi.EventType.*;

import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.test.MockStreamProcessorController;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BrokerStreamProcessorTest
{

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public MockStreamProcessorController<TestEvent> mockController = new MockStreamProcessorController<>(
        TestEvent.class);

    @Test
    public void shouldThrowExceptionOnEventWithNewerVersion()
    {
        // given
        final NoopStreamProcessor streamProcessor = new NoopStreamProcessor();
        final LoggedEvent event = mockController.buildLoggedEvent(
            0,
            e ->
            { },
            m ->
            {
                m.protocolVersion(Constants.PROTOCOL_VERSION + 1);
                m.eventType(TASK_EVENT);
            });

        // then
        exception.expect(RuntimeException.class);

        // when
        streamProcessor.onEvent(event);
    }

    @Test
    public void shouldPassEventWithCurrentVersion()
    {
        // given
        final NoopStreamProcessor streamProcessor = new NoopStreamProcessor();
        final LoggedEvent event = mockController.buildLoggedEvent(
            0,
            e ->
            { },
            m ->
            {
                m.protocolVersion(Constants.PROTOCOL_VERSION);
                m.eventType(TASK_EVENT);
            });

        // when
        streamProcessor.onEvent(event);

        // then
        assertThat(streamProcessor.invoked).isTrue();
    }

    @Test
    public void shouldPassEventWithEventTypeTaskEvent()
    {
        // given
        final NoopStreamProcessor streamProcessor = new NoopStreamProcessor();
        final LoggedEvent event = mockController.buildLoggedEvent(
            0,
            e ->
            { },
            m ->
            {
                m.protocolVersion(Constants.PROTOCOL_VERSION);
                m.eventType(TASK_EVENT);
            });

        // when
        streamProcessor.onEvent(event);

        // then
        assertThat(streamProcessor.invoked).isTrue();
    }

    @Test
    public void shouldNotPassEventWithEventTypeRaftEvent()
    {
        // given
        final NoopStreamProcessor streamProcessor = new NoopStreamProcessor();
        final LoggedEvent event = mockController.buildLoggedEvent(
            0,
            e ->
            { },
            m ->
            {
                m.protocolVersion(Constants.PROTOCOL_VERSION);
                m.eventType(RAFT_EVENT);
            });

        // when
        streamProcessor.onEvent(event);

        // then
        assertThat(streamProcessor.invoked).isFalse();
    }

    @Test
    public void shouldNotPassEventWithEventTypeNullValue()
    {
        // given
        final NoopStreamProcessor streamProcessor = new NoopStreamProcessor();
        final LoggedEvent event = mockController.buildLoggedEvent(
            0,
            e ->
            { },
            m ->
            {
                m.protocolVersion(Constants.PROTOCOL_VERSION);
                m.eventType(NULL_VAL);
            });

        // when
        streamProcessor.onEvent(event);

        // then
        assertThat(streamProcessor.invoked).isFalse();
    }

    public static class TestEvent extends UnpackedObject
    {
    }

    protected static class NoopStreamProcessor extends BrokerStreamProcessor
    {
        protected boolean invoked = false;

        public NoopStreamProcessor()
        {
            super(TASK_EVENT);
        }

        @Override
        public SnapshotSupport getStateResource()
        {
            return new NoopSnapshotSupport();
        }

        @Override
        protected EventProcessor onCheckedEvent(LoggedEvent event)
        {
            this.invoked = true;
            return null;
        }
    }
}