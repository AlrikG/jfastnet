package com.jfastnet.processors;

import com.jfastnet.AbstractTest;
import com.jfastnet.Config;
import com.jfastnet.messages.Message;
import com.jfastnet.util.NullsafeHashMap;
import lombok.extern.slf4j.Slf4j;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class StackedMessageProcessorTest extends AbstractTest {

	static AtomicInteger stackableReceived = new AtomicInteger();
	static AtomicInteger unstackableReceived = new AtomicInteger();
	static AtomicInteger closeMsgReceived = new AtomicInteger();

	static Map<Integer, Set<Long>> stackableIds = new NullsafeHashMap<Integer, Set<Long>>() {
		@Override protected Set<Long> newInstance() {return new HashSet<>();}
	};

	static Map<Integer, Set<Long>> unstackableIds = new NullsafeHashMap<Integer, Set<Long>>() {
		@Override protected Set<Long> newInstance() {return new HashSet<>();}
	};

	static boolean fail = false;

	public static class UnStackableMsg1 extends Message {
		@Override
		public void process() {
			unstackableReceived.incrementAndGet();
			if (getConfig() != null && unstackableIds.containsKey(getConfig().senderId)) {
				if (unstackableIds.get(getConfig().senderId).contains(getMsgId())) {
					log.error("Stackables already contained {}, {}", getConfig().senderId, getMsgId());
					fail = true;
				}
			}
		}
	}

	public static class StackableMsg1 extends Message {
		@Override
		public boolean stackable() {
			return true;
		}

		@Override
		public void process() {
			stackableReceived.incrementAndGet();
			if (getConfig() != null && stackableIds.containsKey(getConfig().senderId)) {
				if (stackableIds.get(getConfig().senderId).contains(getMsgId())) {
					log.error("Stackables already contained {}, {}", getConfig().senderId, getMsgId());
					fail = true;
				}
			}
		}
	}

	public static class StackableMsg2 extends StackableMsg1 {
		@Override
		public void process() {
			closeMsgReceived.incrementAndGet();
			log.info("Close msg #" + closeMsgReceived.get());
		}
	}

	@Test
	public void testStacking() {

		reset();
		start(8,
				newServerConfig().setStackKeepAliveMessages(true),
				() -> {
					Config config = newClientConfig().setStackKeepAliveMessages(true);
					config.debug = true;
					config.debugLostPackagePercentage = 50;
					return config;
				});
		logBig("Send broadcast messages to clients");

		int messageCount = 140;
		for (int i = 0; i < messageCount; i++) {
			server.send(new StackableMsg1());
		}
		server.send(new StackableMsg2());

		int timeoutInSeconds = 15;
		waitForCondition("Not all messages received.", timeoutInSeconds, () -> closeMsgReceived.get() == clients.size(), () -> "Received close messages: " + closeMsgReceived);

		assertThat(stackableReceived.get(), is(messageCount * clients.size()));

		assertThat(fail, is(false));
	}

	@Test
	public void testStackingWithUnstackables() {
		reset();
		start(8,
				newServerConfig(),
				() -> {
					Config config = newClientConfig();
					config.debug = true;
					config.debugLostPackagePercentage = 20;
					return config;
				});
		logBig("Send broadcast messages to clients");

		int messageCount = 40;
		for (int i = 0; i < messageCount; i++) {
			server.send(new StackableMsg1());
			server.send(new UnStackableMsg1());
		}
		server.send(new StackableMsg2());

		int timeoutInSeconds = 15;
		waitForCondition("Not all messages received.", timeoutInSeconds, () -> closeMsgReceived.get() == clients.size(), () -> "Received close messages: " + closeMsgReceived);

		assertThat(stackableReceived.get(), is(messageCount * clients.size()));
		assertThat(unstackableReceived.get(), is(messageCount * clients.size()));

		assertThat(fail, is(false));
	}


	public void reset() {
//		waitFor(1000);
		stackableReceived.set(0);
		unstackableReceived.set(0);
		closeMsgReceived.set(0);
		stackableIds.clear();
		unstackableIds.clear();
		fail = false;

	}
}