/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.talend.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.util.ISO8601Utils;

import com.liferay.talend.configuration.LiferayInputMapperConfiguration;
import com.liferay.talend.data.store.GenericDataStore;
import com.liferay.talend.dataset.InputDataSet;
import com.liferay.talend.service.ConnectionService;
import com.liferay.talend.service.LiferayService;
import com.liferay.talend.util.JsonUtils;

import java.io.IOException;
import java.io.Serializable;

import java.net.URI;
import java.net.URL;

import java.text.ParseException;
import java.text.ParsePosition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import javax.json.JsonObject;

import javax.ws.rs.core.UriBuilder;

import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

/**
 * @author Zoltán Takács
 * @author Igor Beslic
 */
public class LiferayInputEmitter implements Serializable {

	public LiferayInputEmitter(
		final ConnectionService connectionService,
		final LiferayService liferayService,
		@Option("liferayInputMapperConfiguration") final
			LiferayInputMapperConfiguration liferayInputMapperConfiguration,
		final RecordBuilderFactory recordBuilderFactory) {

		_connectionService = connectionService;
		_liferayService = liferayService;
		_liferayInputMapperConfiguration = liferayInputMapperConfiguration;
		_recordBuilderFactory = recordBuilderFactory;
	}

	@PostConstruct
	public void init() {
		_inputDataSet = _liferayInputMapperConfiguration.getInputDataSet();

		_intputSchema = _liferayService.getEndpointTalendSchema(
			_inputDataSet, _recordBuilderFactory);

		GenericDataStore genericDataStore = _inputDataSet.getGenericDataStore();

		URL openAPISpecURL = genericDataStore.getOpenAPISpecURL();

		URL serverURL = ConnectionService.getServerURL(genericDataStore);

		String appBasePathSegment =
			_liferayService.extractJaxRSAppBasePathSegment(openAPISpecURL);

		String endpoint = _inputDataSet.getEndpoint();

		UriBuilder uriBuilder = UriBuilder.fromPath(serverURL.toExternalForm());

		URI resourceURI = uriBuilder.path(
			appBasePathSegment
		).path(
			endpoint
		).build(
			_inputDataSet.getFirstPathParam(),
			_inputDataSet.getSecondPathParam(),
			_inputDataSet.getThirdPathParam()
		);

		JsonObject responseJsonObject =
			_connectionService.getResponseJsonObject(
				_inputDataSet, resourceURI.toString());

		final JsonNode responseJsonNode = JsonUtils.toJsonNode(
			responseJsonObject);

		final JsonNode totalCountJsonNode = responseJsonNode.path("totalCount");

		_totalCount = totalCountJsonNode.asInt();

		JsonNode itemsJsonNode = responseJsonNode.path("items");

		if (!itemsJsonNode.isArray()) {
			throw new RuntimeException(
				"Unexpected response from the Server: " + itemsJsonNode);
		}

		_itemsIterator = itemsJsonNode.elements();
	}

	@Producer
	public Record next() {
		if (_currentItemIndex.incrementAndGet() > _totalCount) {
			return null;
		}

		while (_itemsIterator.hasNext()) {
			JsonNode itemJsonNode = _itemsIterator.next();

			List<Schema.Entry> schemaEntries = _intputSchema.getEntries();

			Record.Builder recordBuilder =
				_recordBuilderFactory.newRecordBuilder();

			return _getRecord(itemJsonNode, schemaEntries, recordBuilder);
		}

		// `null` means we finished the processing of the resource collection

		return null;
	}

	@PreDestroy
	public void release() {
	}

	private Record _getRecord(
		JsonNode itemJsonNode, List<Schema.Entry> schemaEntries,
		Record.Builder recordBuilder) {

		schemaEntries.forEach(
			schemaEntry -> {
				String name = schemaEntry.getName();
				Schema.Type type = schemaEntry.getType();

				JsonNode fieldJsonNode = itemJsonNode.path(name);

				if (Schema.Type.ARRAY == type) {
					if (!fieldJsonNode.isArray()) {
						String arrayJsonNodeAsText = fieldJsonNode.asText();

						recordBuilder.withArray(
							schemaEntry,
							Arrays.asList(
								arrayJsonNodeAsText.split("\\s*,\\s*")));
					}

					List<String> arrayFields = new ArrayList<>();

					for (JsonNode arrayFieldJsonNode : fieldJsonNode) {
						arrayFields.add(arrayFieldJsonNode.asText());
					}

					recordBuilder.withArray(schemaEntry, arrayFields);
				}
				else if (Schema.Type.BOOLEAN == type) {
					recordBuilder.withBoolean(
						schemaEntry, fieldJsonNode.booleanValue());
				}
				else if (Schema.Type.BYTES == type) {
					try {
						recordBuilder.withBytes(
							schemaEntry, fieldJsonNode.binaryValue());
					}
					catch (IOException ioe) {
						String fieldAsText = fieldJsonNode.asText();

						recordBuilder.withBytes(
							schemaEntry, fieldAsText.getBytes());
					}
				}
				else if (Schema.Type.DATETIME == type) {
					Date date = null;

					try {
						date = ISO8601Utils.parse(
							fieldJsonNode.asText(), new ParsePosition(0));
					}
					catch (ParseException pe) {
						_logger.severe(
							"Unable to parse date: " + fieldJsonNode.asText());
					}

					recordBuilder.withDateTime(schemaEntry, date);
				}
				else if (Schema.Type.DOUBLE == type) {
					recordBuilder.withDouble(
						schemaEntry, fieldJsonNode.asDouble());
				}
				else if (Schema.Type.LONG == type) {
					recordBuilder.withLong(schemaEntry, fieldJsonNode.asLong());
				}
				else if (Schema.Type.INT == type) {
					recordBuilder.withInt(schemaEntry, fieldJsonNode.asInt());
				}
				else {
					recordBuilder.withString(
						schemaEntry, fieldJsonNode.toString());
				}
			});

		return recordBuilder.build();
	}

	private final ConnectionService _connectionService;
	private AtomicInteger _currentItemIndex = new AtomicInteger();
	private InputDataSet _inputDataSet;
	private Schema _intputSchema;
	private Iterator<JsonNode> _itemsIterator;
	private final LiferayInputMapperConfiguration
		_liferayInputMapperConfiguration;
	private final LiferayService _liferayService;
	private final Logger _logger = Logger.getLogger("LiferayInputEmitter");
	private final RecordBuilderFactory _recordBuilderFactory;
	private int _totalCount;

}