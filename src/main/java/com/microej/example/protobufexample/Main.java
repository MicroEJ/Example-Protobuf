/*
 * Java
 *
 * Copyright 2020-2026 MicroEJ Corp. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be found with this software.
 */
package com.microej.example.protobufexample;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Logger;

import com.google.protobuf.Timestamp;
import com.microej.example.protobufexample.AppProto.AppMessage;
import com.microej.example.protobufexample.AppProto.Encryption;

/**
 * Serializes a message to a byte array using Protobuf.
 * <p>
 * The message is then deserialized from the byte array and its content printed on the standard output.
 */
public class Main {

	private static final char NEW_LINE = '\n';

	/**
	 * Entry point of the example.
	 *
	 * @param args
	 *            not used.
	 * @throws IOException
	 *             if an error occurs during the serialization or deserialization.
	 */
	public static void main(String[] args) throws IOException {
		byte[] array = serialize();
		deserialize(array);
	}

	private static byte[] serialize() throws IOException {
		Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
		logger.info("Serializing the message...");

		AppMessage.Builder messageBuilder = AppMessage.newBuilder();
		Timestamp.Builder timestampBuilder = Timestamp.newBuilder();
		timestampBuilder.setSeconds(System.currentTimeMillis() / 1000);
		Timestamp date = timestampBuilder.build();
		messageBuilder.setId(42).setSubject("Hello").setMessageContent("Hello World!").setDate(date)
				.setEncryption(Encryption.PGP);
		AppMessage message = messageBuilder.build();

		byte[] output = message.toByteArray();
		logger.info("Serialized into " + output.length + " bytes");
		return output;
	}

	private static void deserialize(byte[] array) throws IOException {
		ByteArrayInputStream input = new ByteArrayInputStream(array);
		AppMessage message = AppMessage.parseFrom(input);
		logMessage(message);
	}

	private static void logMessage(AppMessage message) {
		Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
		StringBuilder builder = new StringBuilder();
		builder.append("Deserializing the message:").append(NEW_LINE);
		builder.append("> id: ").append(message.getId()).append(NEW_LINE);
		builder.append("> subject: ").append(message.getSubject()).append(NEW_LINE);
		builder.append("> message: ").append(message.getMessageContent()).append(NEW_LINE);
		builder.append("> date: ").append(message.getDate().getSeconds()).append(NEW_LINE);
		builder.append("> encryption: ").append(message.getEncryption().name());
		String logMessage = builder.toString();
		logger.info(logMessage);
	}
}