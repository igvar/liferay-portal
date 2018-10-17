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

package com.liferay.portal.template.freemarker.internal;

import com.liferay.petra.function.UnsafeFunction;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.templateparser.TemplateNode;
import com.liferay.portal.kernel.test.CaptureHandler;
import com.liferay.portal.kernel.test.JDKLoggerTestUtil;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.CodeCoverageAssertor;
import com.liferay.portal.kernel.test.rule.NewEnv;
import com.liferay.portal.kernel.util.ProxyFactory;
import com.liferay.portal.kernel.util.ProxyUtil;
import com.liferay.portal.test.aspects.ReflectionUtilAdvice;
import com.liferay.portal.test.rule.AdviseWith;
import com.liferay.portal.test.rule.AspectJNewEnvTestRule;

import freemarker.ext.beans.EnumerationModel;
import freemarker.ext.beans.MapModel;
import freemarker.ext.beans.ResourceBundleModel;
import freemarker.ext.beans.StringModel;
import freemarker.ext.dom.NodeModel;
import freemarker.ext.util.ModelFactory;

import freemarker.template.SimpleSequence;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.Version;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * @author Xiangyue Cai
 */
public class LiferayObjectWrapperTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			AspectJNewEnvTestRule.INSTANCE, CodeCoverageAssertor.INSTANCE);

	@Test
	public void testCheckClassIsRestricted() throws Exception {
		_testCheckClassIsRestricted(
			new LiferayObjectWrapper(null, null), TestLiferayObject.class,
			null);

		_testCheckClassIsRestricted(
			new LiferayObjectWrapper(
				new String[] {TestLiferayObject.class.getName()},
				new String[] {TestLiferayObject.class.getName()}),
			TestLiferayObject.class, null);

		_testCheckClassIsRestricted(
			new LiferayObjectWrapper(null, new String[] {"java.lang.String"}),
			TestLiferayObject.class, null);

		_testCheckClassIsRestricted(
			new LiferayObjectWrapper(
				null, new String[] {"com.liferay.portal.cache"}),
			TestLiferayObject.class, null);

		_testCheckClassIsRestricted(
			new LiferayObjectWrapper(
				null, new String[] {TestLiferayObject.class.getName()}),
			TestLiferayObject.class,
			StringBundler.concat(
				"Denied resolving class ", TestLiferayObject.class.getName(),
				" by ", TestLiferayObject.class.getName()));

		_testCheckClassIsRestricted(
			new LiferayObjectWrapper(
				null, new String[] {"com.liferay.portal.template.freemarker"}),
			TestLiferayObject.class,
			StringBundler.concat(
				"Denied resolving class ", TestLiferayObject.class.getName(),
				" by com.liferay.portal.template.freemarker"));

		_testCheckClassIsRestricted(
			new LiferayObjectWrapper(
				null, new String[] {"com.liferay.portal.template.freemarker"}),
			byte.class, null);
	}

	@Test
	public void testConstructor() {
		_assertLiferayObjectWrapper(
			new LiferayObjectWrapper(null, null), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), false);

		_assertLiferayObjectWrapper(
			new LiferayObjectWrapper(new String[] {StringPool.STAR}, null),
			Collections.singletonList(StringPool.STAR), Collections.emptyList(),
			Collections.emptyList(), true);

		_assertLiferayObjectWrapper(
			new LiferayObjectWrapper(
				new String[] {StringPool.BLANK},
				new String[] {StringPool.BLANK}),
			Collections.emptyList(), Collections.emptyList(),
			Collections.emptyList(), false);

		_assertLiferayObjectWrapper(
			new LiferayObjectWrapper(
				new String[] {"com.liferay.allowed.Class"}, null),
			Collections.singletonList("com.liferay.allowed.Class"),
			Collections.emptyList(), Collections.emptyList(), false);

		_assertLiferayObjectWrapper(
			new LiferayObjectWrapper(
				null, new String[] {LiferayObjectWrapper.class.getName()}),
			Collections.emptyList(),
			Collections.singletonList(LiferayObjectWrapper.class),
			Collections.emptyList(), false);

		try (CaptureHandler captureHandler =
				JDKLoggerTestUtil.configureJDKLogger(
					LiferayObjectWrapper.class.getName(), Level.INFO)) {

			_assertLiferayObjectWrapper(
				new LiferayObjectWrapper(
					null, new String[] {"com.liferay.package.name"}),
				Collections.emptyList(), Collections.emptyList(),
				Collections.singletonList("com.liferay.package.name"), false);

			List<LogRecord> logRecords = captureHandler.getLogRecords();

			Assert.assertEquals(logRecords.toString(), 1, logRecords.size());

			LogRecord logRecord = logRecords.get(0);

			Assert.assertEquals(
				"Unable to find restricted class com.liferay.package.name. " +
					"Registering as a package.",
				logRecord.getMessage());
		}

		try (CaptureHandler captureHandler =
				JDKLoggerTestUtil.configureJDKLogger(
					LiferayObjectWrapper.class.getName(), Level.OFF)) {

			_assertLiferayObjectWrapper(
				new LiferayObjectWrapper(
					null, new String[] {"com.liferay.package.name"}),
				Collections.emptyList(), Collections.emptyList(),
				Collections.singletonList("com.liferay.package.name"), false);

			List<LogRecord> logRecords = captureHandler.getLogRecords();

			Assert.assertEquals(logRecords.toString(), 0, logRecords.size());
		}

		Field cacheClassNamesField = ReflectionTestUtil.getAndSetFieldValue(
			LiferayObjectWrapper.class, "_cacheClassNamesField", null);

		try {
			new LiferayObjectWrapper(null, null);

			Assert.fail("NullPointerException was not thrown");
		}
		catch (Exception e) {
			Assert.assertEquals(NullPointerException.class, e.getClass());
		}
		finally {
			ReflectionTestUtil.setFieldValue(
				LiferayObjectWrapper.class, "_cacheClassNamesField",
				cacheClassNamesField);
		}
	}

	@Test
	public void testHandleUnknownType() throws Exception {
		LiferayObjectWrapper liferayObjectWrapper = new LiferayObjectWrapper(
			null, null);

		// 1. handle Enumeration

		Enumeration<String> enumeration = Collections.enumeration(
			Collections.singletonList("testElement"));

		_assertTemplateModel(
			liferayObjectWrapper.handleUnknownType(enumeration),
			EnumerationModel.class, "testElement",
			enumerationModel -> enumerationModel.next());

		_assertModelFactoryCache(
			"_ENUMERATION_MODEL_FACTORY", enumeration.getClass());

		// 2. handle Node

		Node node = (Node)ProxyUtil.newProxyInstance(
			LiferayObjectWrapper.class.getClassLoader(),
			new Class<?>[] {Node.class, Element.class},
			(proxy, method, args) -> {
				String methodName = method.getName();

				if (methodName.equals("getNodeType")) {
					return Node.ELEMENT_NODE;
				}

				return null;
			});

		TemplateModel templateModel = liferayObjectWrapper.handleUnknownType(
			node);

		Assert.assertTrue(
			"org.w3c.dom.Node should be handled as NodeModel",
			templateModel instanceof NodeModel);

		NodeModel nodeModel = (NodeModel)templateModel;

		Assert.assertSame(node, nodeModel.getNode());
		Assert.assertEquals("element", nodeModel.getNodeType());

		_assertModelFactoryCache("_NODE_MODEL_FACTORY", node.getClass());

		// 3. handle ResourceBundle

		ResourceBundle resourceBundle = new ResourceBundle() {

			@Override
			public Enumeration<String> getKeys() {
				return null;
			}

			@Override
			protected Object handleGetObject(String key) {
				return key;
			}

		};

		_assertTemplateModel(
			liferayObjectWrapper.handleUnknownType(resourceBundle),
			ResourceBundleModel.class, resourceBundle.toString(),
			resourceBundleModel -> resourceBundleModel.getBundle());

		_assertModelFactoryCache(
			"_RESOURCE_BUNDLE_MODEL_FACTORY", resourceBundle.getClass());

		// 4. handle Version

		_assertTemplateModel(
			liferayObjectWrapper.handleUnknownType(
				liferayObjectWrapper.handleUnknownType(new Version("1.0"))),
			StringModel.class, "1.0", stringModel -> stringModel.getAsString());

		_assertModelFactoryCache("_STRING_MODEL_FACTORY", Version.class);
	}

	@AdviseWith(adviceClasses = ReflectionUtilAdvice.class)
	@NewEnv(type = NewEnv.Type.CLASSLOADER)
	@Test
	public void testInitializationFailure() throws Exception {
		Exception exception = new Exception();

		ReflectionUtilAdvice.setDeclaredFieldThrowable(exception);

		try {
			Class.forName(
				"com.liferay.portal.template.freemarker.internal." +
					"LiferayObjectWrapper");

			Assert.fail("ExceptionInInitializerError was not thrown");
		}
		catch (ExceptionInInitializerError eiie) {
			Assert.assertSame(exception, eiie.getCause());
		}
	}

	@Test
	public void testWrap() throws Exception {
		_testWrap(new LiferayObjectWrapper(null, null));
		_testWrap(
			new LiferayObjectWrapper(new String[] {StringPool.STAR}, null));
		_testWrap(
			new LiferayObjectWrapper(
				new String[] {StringPool.STAR},
				new String[] {LiferayObjectWrapper.class.getName()}));
		_testWrap(
			new LiferayObjectWrapper(new String[] {StringPool.BLANK}, null));
		_testWrap(
			new LiferayObjectWrapper(null, new String[] {StringPool.BLANK}));
		_testWrap(
			new LiferayObjectWrapper(
				new String[] {StringPool.BLANK},
				new String[] {StringPool.BLANK}));
	}

	private void _assertModelFactoryCache(
		String modelFactoryFieldName, Class<?> clazz) {

		Map<Class<?>, ModelFactory> modelFactories =
			ReflectionTestUtil.getFieldValue(
				LiferayObjectWrapper.class, "_modelFactories");

		Assert.assertSame(
			ReflectionTestUtil.getFieldValue(
				LiferayObjectWrapper.class, modelFactoryFieldName),
			modelFactories.get(clazz));
	}

	private void _assertLiferayObjectWrapper(
		LiferayObjectWrapper liferayObjectWrapper,
		List<String> expectedAllowedClassNames,
		List<Class<?>> expectedRestrictedClasses,
		List<String> expectedRestrictedPackageNames,
		boolean expectedAllowAllClasses) {

		Assert.assertEquals(
			expectedAllowedClassNames,
			ReflectionTestUtil.getFieldValue(
				liferayObjectWrapper, "_allowedClassNames"));
		Assert.assertEquals(
			expectedRestrictedClasses,
			ReflectionTestUtil.getFieldValue(
				liferayObjectWrapper, "_restrictedClasses"));
		Assert.assertEquals(
			expectedRestrictedPackageNames,
			ReflectionTestUtil.getFieldValue(
				liferayObjectWrapper, "_restrictedPackageNames"));

		if (expectedAllowAllClasses) {
			Assert.assertTrue(
				"_allowAllClasses should be true since \"*\" is in " +
					"_allowedClassNames",
				ReflectionTestUtil.getFieldValue(
					liferayObjectWrapper, "_allowAllClasses"));
		}
		else {
			Assert.assertFalse(
				"_allowAllClasses should be false since \"*\" is not in " +
					"_allowedClassNames",
				ReflectionTestUtil.getFieldValue(
					liferayObjectWrapper, "_allowAllClasses"));
		}
	}

	private <T, R> void _assertTemplateModel(
			TemplateModel templateModel, Class<T> expectedClass,
			String expectResult, UnsafeFunction<T, R, Exception> unsafeFunction)
		throws Exception {

		Assert.assertSame(expectedClass, templateModel.getClass());

		R result = unsafeFunction.apply((T)templateModel);

		Assert.assertEquals(expectResult, result.toString());
	}

	private void _testCheckClassIsRestricted(
			LiferayObjectWrapper liferayObjectWrapper, Class<?> targetClass,
			String exceptionMessage)
		throws Exception {

		try {
			ReflectionTestUtil.invoke(
				liferayObjectWrapper, "_checkClassIsRestricted",
				new Class<?>[] {Class.class}, targetClass);

			if (exceptionMessage != null) {
				Assert.fail("Should throw TemplateModelException");
			}
		}
		catch (Exception e) {
			Assert.assertSame(TemplateModelException.class, e.getClass());

			TemplateModelException templateModelException =
				(TemplateModelException)e;

			Assert.assertEquals(
				exceptionMessage, templateModelException.getMessage());
		}
	}

	private void _testWrap(LiferayObjectWrapper liferayObjectWrapper)
		throws Exception {

		// 1. wrap null

		Assert.assertNull(liferayObjectWrapper.wrap(null));

		// 2. wrap TemplateModel

		TemplateModel templateModel = ProxyFactory.newDummyInstance(
			TemplateModel.class);

		Assert.assertSame(
			templateModel, liferayObjectWrapper.wrap(templateModel));

		// 3. wrap TemplateNode

		_assertTemplateModel(
			liferayObjectWrapper.wrap(
				new TemplateNode(null, "testName", "", "", null)),
			LiferayTemplateModel.class, "testName",
			liferayTemplateModel -> liferayTemplateModel.get("name"));

		// 4. wrap liferay Collection

		_assertTemplateModel(
			liferayObjectWrapper.wrap(new TestLiferayCollection("testElement")),
			SimpleSequence.class, "testElement",
			simpleSequence -> simpleSequence.get(0));

		// 5. wrap liferay Map

		_assertTemplateModel(
			liferayObjectWrapper.wrap(
				new TestLiferayMap("testKey", "testValue")),
			MapModel.class, "testValue", mapModel -> mapModel.get("testKey"));

		// 6. wrap liferay Object

		_assertTemplateModel(
			liferayObjectWrapper.wrap(
				new TestLiferayObject("TestLiferayObject")),
			StringModel.class, "TestLiferayObject",
			stringModel -> stringModel.getAsString());

		// 7. wrap unknown type

		_assertTemplateModel(
			liferayObjectWrapper.wrap(new Version("1.0")), StringModel.class,
			"1.0", stringModel -> stringModel.getAsString());

		_assertModelFactoryCache("_STRING_MODEL_FACTORY", Version.class);

		// 8. make sure unknown type is using cache

		Map<Class<?>, ModelFactory> modelFactories =
			ReflectionTestUtil.getFieldValue(
				LiferayObjectWrapper.class, "_modelFactories");

		modelFactories.put(Version.class, (object, wrapper) -> null);

		Assert.assertNull(liferayObjectWrapper.wrap(new Version("2.0")));

		modelFactories.clear();
	}

	private class TestLiferayCollection extends ArrayList<String> {

		private TestLiferayCollection(String element) {
			add(element);
		}

	}

	private class TestLiferayMap extends HashMap<String, String> {

		private TestLiferayMap(String key, String value) {
			put(key, value);
		}

	}

	private class TestLiferayObject {

		@Override
		public String toString() {
			return _name;
		}

		private TestLiferayObject(String name) {
			_name = name;
		}

		private final String _name;

	}

}