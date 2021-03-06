/*
 * Copyright 2002-2006 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Created on 26-Jan-2006 by Adrian Colyer
 */
package org.springframework.osgi.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.easymock.MockControl;
import org.easymock.internal.AlwaysMatcher;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.springframework.beans.factory.BeanFactory;

import junit.framework.TestCase;

/**
 * @author Adrian Colyer
 * @since 2.0
 */
public class OsgiServiceExporterTests extends TestCase {

	private OsgiServiceExporter exporter = new OsgiServiceExporter();
	private BeanFactory beanFactory;
	private MockControl beanFactoryControl;
	private BundleContext bundleContext;
	private MockControl bundleContextControl;
	private MockControl mockServiceRegistrationControl;
	
	protected void setUp() throws Exception {
		this.beanFactoryControl = MockControl.createControl(BeanFactory.class);
		this.beanFactory = (BeanFactory) this.beanFactoryControl.getMock();
		this.bundleContextControl = MockControl.createControl(BundleContext.class);
		this.bundleContext = (BundleContext) this.bundleContextControl.getMock();
	}
	
	public void testSetExportBeansBadContent() {
		List notStrings = new ArrayList();
		notStrings.add(new Object());
		try {
			this.exporter.setExportBeans(notStrings);
			fail("Expecting IllegalArgumentException");
		} 
		catch (IllegalArgumentException ex) {
			assertTrue(ex.getMessage().startsWith("The exportBeans property requires a list of bean names as strings"));
		}		
	}
	
	public void testAfterPropertiesSetNoBeans() throws Exception {
		this.exporter.setBeanFactory(this.beanFactory);
		this.exporter.setBundleContext(this.bundleContext);
		this.bundleContextControl.replay();
		this.beanFactoryControl.replay();
		this.exporter.afterPropertiesSet();
		this.bundleContextControl.verify();
		this.beanFactoryControl.verify();
	}
	
	public void testAfterPropertiesSetNoBundleContext() throws Exception {
		this.exporter.setBeanFactory(this.beanFactory);
		try {
			this.exporter.afterPropertiesSet();
			fail("Expecting IllegalArgumentException");
		}
		catch(IllegalArgumentException ex) {
			assertEquals("Required property bundleContext has not been set",
					ex.getMessage());
		}
	}
	
	public void testAfterPropertiesSetNoResolver() throws Exception {
		this.exporter.setBeanFactory(this.beanFactory);
		this.exporter.setBundleContext(this.bundleContext);
		this.exporter.setResolver(null);
		try {
			this.exporter.afterPropertiesSet();
			fail("Expecting IllegalArgumentException");
		}
		catch(IllegalArgumentException ex) {
			assertEquals("Required property resolver was set to a null value",
					ex.getMessage());
		}
	}
	
	public void testAfterPropertiesSetNoBeanFactory() throws Exception {
		try {
			this.exporter.afterPropertiesSet();
			fail("Expecting IllegalArgumentException");
		}
		catch(IllegalArgumentException ex) {
			assertEquals("Required property beanFactory has not been set",
					ex.getMessage());
		}		
	}
	
	public void testPublish() throws Exception {
		this.exporter.setBeanFactory(this.beanFactory);
		this.exporter.setBundleContext(this.bundleContext);
		MockControl mc = MockControl.createControl(OsgiServicePropertiesResolver.class);
		OsgiServicePropertiesResolver resolver = (OsgiServicePropertiesResolver) mc.getMock();
		this.exporter.setResolver(resolver);
		List beanNames = new ArrayList();
		beanNames.add("thisBean");
		beanNames.add("thatBean");
		this.exporter.setExportBeans(beanNames);
		
		// set expectations on afterProperties
		this.beanFactory.getBean("thisBean");
		Object thisBean = new Object();
		this.beanFactoryControl.setReturnValue(thisBean);
		this.beanFactory.getBean("thatBean");
		Object thatBean = new Object();
		this.beanFactoryControl.setReturnValue(thatBean);
		
		resolver.getServiceProperties(thisBean, "thisBean");
		mc.setReturnValue(new Properties());
		resolver.getServiceProperties(thatBean, "thatBean");
		mc.setReturnValue(new Properties());
		
		this.bundleContext.registerService((String)null, null, null);
		this.bundleContextControl.setMatcher(new AlwaysMatcher());
		this.bundleContextControl.setReturnValue(getServiceRegistration());
		this.bundleContext.registerService((String)null, null, null);
		this.bundleContextControl.setReturnValue(getServiceRegistration());
		
		this.bundleContextControl.replay();
		this.beanFactoryControl.replay();
		mc.replay();
		
		// do the work
		this.exporter.afterPropertiesSet();
		
		// verify
		this.bundleContextControl.verify();
		this.beanFactoryControl.verify();
		mc.verify();
	}
	
	public void testDestroy() throws Exception {
		testPublish();
		this.mockServiceRegistrationControl.replay();
		this.exporter.destroy();
		this.mockServiceRegistrationControl.verify();
	}

	private ServiceRegistration getServiceRegistration() {
		this.mockServiceRegistrationControl = MockControl.createControl(ServiceRegistration.class);
		ServiceRegistration ret = (ServiceRegistration) this.mockServiceRegistrationControl.getMock();
		ret.unregister(); // for destroy test..
		return ret;
	}
	
}
