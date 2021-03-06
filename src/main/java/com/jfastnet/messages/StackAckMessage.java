/*******************************************************************************
 * Copyright 2018 Klaus Pfeiffer - klaus@allpiper.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jfastnet.messages;

import lombok.Getter;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
public class StackAckMessage extends Message {

	@Getter
	private long lastReceivedId;

	/** no-arg constructor required for serialization. */
	private StackAckMessage() {}

	public StackAckMessage(long lastReceivedId) {
		this.lastReceivedId = lastReceivedId;
	}

	@Override
	public ReliableMode getReliableMode() {
		return ReliableMode.UNRELIABLE;
	}
}
