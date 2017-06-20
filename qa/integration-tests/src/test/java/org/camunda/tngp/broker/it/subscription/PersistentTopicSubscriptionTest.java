package org.camunda.tngp.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.regex.Pattern;

import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.broker.it.startup.BrokerRestartTest;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.event.TopicSubscription;
import org.camunda.tngp.test.util.TestFileUtil;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class PersistentTopicSubscriptionTest
{

    protected static InputStream persistentBrokerConfig(String path)
    {
        final String canonicallySeparatedPath = path.replaceAll(Pattern.quote(File.separator), "/");

        return TestFileUtil.readAsTextFileAndReplace(
                BrokerRestartTest.class.getClassLoader().getResourceAsStream("persistent-broker.cfg.toml"),
                StandardCharsets.UTF_8,
                Collections.singletonMap("\\$\\{brokerFolder\\}", canonicallySeparatedPath));
    }


    public TemporaryFolder tempFolder = new TemporaryFolder();

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(() -> persistentBrokerConfig(tempFolder.getRoot().getAbsolutePath()));

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(tempFolder)
        .around(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected TngpClient client;
    protected RecordingEventHandler recordingHandler;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
        this.recordingHandler = new RecordingEventHandler();
    }

    @Test
    public void shouldResumeSubscriptionOnRestart()
    {
        // given a first task
        clientRule.taskTopic().create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        final String subscriptionName = "foo";

        final TopicSubscription subscription = clientRule.topic()
            .newSubscription()
            .handler(recordingHandler)
            .name(subscriptionName)
            .startAtHeadOfTopic()
            .open();

        // that was received by the subscription
        TestUtil.waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);

        subscription.close();

        final long lastEventPosition = recordingHandler.getRecordedEvents()
                .get(recordingHandler.numRecordedEvents() - 1)
                .getMetadata()
                .getEventPosition();

        recordingHandler.reset();

        // and a second not-yet-received task
        clientRule.taskTopic().create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("foo")
            .execute();

        // when
        restartBroker();

        clientRule.topic()
                .newSubscription()
                .handler(recordingHandler)
                .name(subscriptionName)
                .startAtHeadOfTopic()
                .open();

        // then
        TestUtil.waitUntil(() -> recordingHandler.numRecordedEvents() > 0);

        final long firstEventPositionAfterReopen = recordingHandler.getRecordedEvents()
                .get(0)
                .getMetadata()
                .getEventPosition();

        assertThat(firstEventPositionAfterReopen).isGreaterThan(lastEventPosition);
    }


    protected void restartBroker()
    {
        client.disconnect();
        brokerRule.restartBroker();
        client.connect();
    }

}
