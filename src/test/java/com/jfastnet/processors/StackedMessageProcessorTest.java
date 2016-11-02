package com.jfastnet.processors;

import com.jfastnet.AbstractTest;
import com.jfastnet.Config;
import com.jfastnet.MessageKey;
import com.jfastnet.MessageLog;
import com.jfastnet.idprovider.ReliableModeIdProvider;
import com.jfastnet.messages.Message;
import com.jfastnet.util.NullsafeHashMap;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class StackedMessageProcessorTest extends AbstractTest {

	static AtomicInteger stackableReceived = new AtomicInteger();
	static AtomicInteger unstackableReceived = new AtomicInteger();
	static AtomicInteger closeMsgReceived = new AtomicInteger();
	static Map<Integer, List<Message>> receivedMessages = new HashMap<>();

	static Map<Integer, Set<Long>> stackableIds = new NullsafeHashMap<Integer, Set<Long>>() {
		@Override protected Set<Long> newInstance() {return new HashSet<>();}
	};

	static Map<Integer, Set<Long>> unstackableIds = new NullsafeHashMap<Integer, Set<Long>>() {
		@Override protected Set<Long> newInstance() {return new HashSet<>();}
	};

	static boolean fail = false;

	public static class UnStackableMsg1 extends Message {
		@Override
		public void process(Object context) {
			log.info("########### UNSTACKABLE ### ClientID: {} ### MsgID: {} ### Number: {}",
					new Object[]{getConfig().senderId, getMsgId(), unstackableReceived.incrementAndGet()});
			addReceived(this);
			printMsg(this);
			if (getConfig() != null && unstackableIds.containsKey(getConfig().senderId)) {
				if (unstackableIds.get(getConfig().senderId).contains(getMsgId())) {
					log.error("Stackables already contained {}, {}", getConfig().senderId, getMsgId());
					fail = true;
				}
			}
		}
	}

	private synchronized static void addReceived(Message message) {
		List<Message> messages = receivedMessages.getOrDefault(message.getConfig().senderId, new ArrayList<>());
		messages.add(message);
		receivedMessages.put(message.getConfig().senderId, messages);
	}

	private static void printMsg(Message msg) {
//		log.info("+++++++++++++ msg-id: " + msg.getMsgId());
//		try {
//			throw new Exception();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
	}

	public static class StackableMsg1 extends Message {
		@Override
		public boolean stackable() {
			return true;
		}

		@Override
		public void process(Object context) {
//			stackableReceived.incrementAndGet();
			log.info("########### STACKABLE ### ClientID: {} ### MsgID: {} ### Number: {}",
					new Object[]{getConfig().senderId, getMsgId(), stackableReceived.incrementAndGet()});
			addReceived(this);
			printMsg(this);
			if (stackableReceived.get() <= unstackableReceived.get()) {
				log.error("Stackable must have a greater id!");
				fail = true;
			}
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
		public void process(Object context) {
			addReceived(this);
			closeMsgReceived.incrementAndGet();
			log.info("Close msg #" + closeMsgReceived.get());
		}
	}

	@Test
	public void testStacking() {
		reset();
		start(8,
				() -> {
					Config config = newClientConfig().setStackKeepAliveMessages(true);
					config.debug = true;
					config.debugLostPackagePercentage = 5;
					config.setIdProviderClass(ReliableModeIdProvider.class);
					return config;
				});
		logBig("Send broadcast messages to clients");

		int messageCount = 40;
		for (int i = 0; i < messageCount; i++) {
			server.send(new StackableMsg1());
		}
		server.send(new StackableMsg2());

		int timeoutInSeconds = 15;
		waitForCondition("Not all messages received.", timeoutInSeconds,
				() -> closeMsgReceived.get() == clients.size(),
				() -> "Received close messages: " + closeMsgReceived);

		assertThat(stackableReceived.get(), is(messageCount * clients.size()));

		assertThat(fail, is(false));
	}

	@Test
	public void testStackingWithUnstackables() {
		reset();
		start(4,
				() -> {
					Config config = newClientConfig();
					config.debug = true;
					config.debugLostPackagePercentage = 5;
					config.setIdProviderClass(ReliableModeIdProvider.class);
					return config;
				});
		logBig("Send broadcast messages to clients");

		int messageCount = 100;
		for (int i = 0; i < messageCount; i++) {
			server.send(new StackableMsg1());
			server.send(new UnStackableMsg1());
		}
		server.send(new StackableMsg2());

		int timeoutInSeconds = 15;
		waitForCondition("Not all messages received.", timeoutInSeconds,
				() -> closeMsgReceived.get() == clients.size(),
				() -> "Received close messages: " + closeMsgReceived);

		assertThat(stackableReceived.get(), is(messageCount * clients.size()));
		assertThat(unstackableReceived.get(), is(messageCount * clients.size()));
		assertThat(fail, is(false));

		log.info("Check order of received messages");
		for (int i = 1; i <= clients.size(); i++) {
			List<Message> messages = receivedMessages.get(i);
			assertThat(messages, is(notNullValue()));
			long lastId = 2L;
			for (Message message : messages) {
				assertThat(message.getMsgId(), is(lastId));
				lastId++;
			}
		}

		log.info("Check ids in message log");
		MessageLog messageLog = server.getState().getProcessorOf(MessageLogProcessor.class).getMessageLog();
		for (long i = 1; i <= messageCount * 2; i++) {
			Message message = messageLog.getSent(MessageKey.newKey(Message.ReliableMode.SEQUENCE_NUMBER, 0, i));
			assertThat("Message was null, id=" + i, message, is(notNullValue()));
			assertThat(message.getMsgId(), is(i));
		}
	}


	public void reset() {
		stackableReceived.set(0);
		unstackableReceived.set(0);
		closeMsgReceived.set(0);
		stackableIds.clear();
		unstackableIds.clear();
		receivedMessages.clear();
		fail = false;
	}
}