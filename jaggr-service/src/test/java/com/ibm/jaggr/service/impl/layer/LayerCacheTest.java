/*
 * (C) Copyright 2012, IBM Corporation
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
 */

package com.ibm.jaggr.service.impl.layer;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.io.Files;
import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.InitParams;
import com.ibm.jaggr.service.NotFoundException;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.deps.IDependencies;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.layer.ILayer;
import com.ibm.jaggr.service.test.TestCacheManager;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.test.TestUtils.Ref;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.util.CopyUtil;
import com.ibm.jaggr.service.util.Features;

public class LayerCacheTest {
	
	static File tmpdir = null;
	IAggregator mockAggregator;
	Ref<IConfig> configRef = new Ref<IConfig>(null);
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	HttpServletRequest mockRequest;
	HttpServletResponse mockResponse;
	IDependencies mockDependencies;
	FilenameFilter layerFileFilter = new FilenameFilter() {
		@Override 
		public boolean accept(File dir, String name) {
			return name.startsWith("layer.");	
		}
	};

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tmpdir = Files.createTempDir();
		TestUtils.createTestFiles(tmpdir);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if (tmpdir != null) {
			TestUtils.deleteRecursively(tmpdir);
			tmpdir = null;
		}
	}

	@Test
	public void test() throws Exception {
		List<InitParams.InitParam> initParams = new LinkedList<InitParams.InitParam>();
		initParams.add(new InitParams.InitParam(InitParams.MAXLAYERCACHEENTRIES_INITPARAM, "10"));
		createMockObjects(initParams);
		
		LayerCacheImpl layerCache = (LayerCacheImpl)mockAggregator.getCacheManager().getCache().getLayers();
		Assert.assertEquals(0, layerCache.getLayerBuildMap().size());
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, Arrays.asList(new String[]{"layer1"}));
		ILayer layer = layerCache.getLayer(mockRequest);
		Assert.assertEquals("[layer1]", layer.getKey());
		Assert.assertEquals(1, layerCache.size());
		
		Assert.assertEquals(0,  layerCache.getLayerBuildMap().size());
		
		boolean exceptionThrown = false;
		try {
			layer.getInputStream(mockRequest, mockResponse);
		} catch (NotFoundException e) {
			// ensure that layer was removed from cache
			exceptionThrown = true;
			Assert.assertEquals(0, layerCache.size());
		}
		Assert.assertTrue(exceptionThrown);
		
		populateCache(layerCache);
		
		// Add another layer that will cause the cache to overflow
		requestAttributes.clear();
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, Arrays.asList(new String[] {"p1/b", "p1/c", "p1/a"}));
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		layer = layerCache.getLayer(mockRequest);
		Assert.assertEquals(6, layerCache.size());
		InputStream in = layer.getInputStream(mockRequest, mockResponse);
		in.close();
		Assert.assertEquals(1, layerCache.getNumEvictions());
		Assert.assertEquals(6, layerCache.size());
		
		Assert.assertEquals(10, layerCache.getLayerBuildMap().size());
		Assert.assertNotNull(layerCache.get("[p1/a]"));
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		in = layer.getInputStream(mockRequest, mockResponse);
		in.close();
		Assert.assertEquals(2, layerCache.getNumEvictions());
		Assert.assertEquals(5, layerCache.size());
		Assert.assertEquals(10, layerCache.getLayerBuildMap().size());
		Assert.assertNull(layerCache.get("[p1/a]"));
		
		// Serialize the cache to disk
		List<String> serializedKeys = new LinkedList<String>(layerCache.getLayerBuildKeys());
		((TestCacheManager)mockAggregator.getCacheManager()).serializeCache();
		createMockObjects(initParams);
		layerCache = (LayerCacheImpl)mockAggregator.getCacheManager().getCache().getLayers();
		Assert.assertEquals(new LinkedList<String>(layerCache.getLayerBuildKeys()), new LinkedList<String>(serializedKeys));
		Assert.assertEquals(5, layerCache.size());
		Assert.assertEquals(10, layerCache.getLayerBuildMap().size());
		
		// test LRU ordering of keys
		List<String> keys = new LinkedList<String>(layerCache.getLayerBuildKeys());
		Assert.assertEquals(10, keys.size());
		String head = keys.get(0);
		String second = keys.get(1);
		// Get a layer that's already in the cache, causing the cache entry to be 
		// promoted to the tail of the LRU cache
		requestAttributes.clear();
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, Arrays.asList(new String[] {"p1/b"}));
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		layer = layerCache.getLayer(mockRequest);
		Assert.assertEquals(5, layerCache.size());
		in = layer.getInputStream(mockRequest, mockResponse);
		in.close();
		Assert.assertEquals(10, keys.size());
		keys = new LinkedList<String>(layerCache.getLayerBuildKeys());
		Assert.assertEquals(second, keys.get(0));
		Assert.assertEquals(head, keys.get(9));
		
		// Make sure the de-serialized cache works as expected
		// Add another layer that will cause the cache to overflow
		requestAttributes.clear();
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, Arrays.asList(new String[] {"p1/b", "p1/c", "p2/a"}));
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		layer = layerCache.getLayer(mockRequest);
		Assert.assertEquals(6, layerCache.size());
		in = layer.getInputStream(mockRequest, mockResponse);
		in.close();
		Assert.assertEquals(3, layerCache.getNumEvictions());
		Assert.assertEquals(6, layerCache.size());
		Assert.assertEquals(10, layerCache.getLayerBuildMap().size());
		// test to make sure the expected number of layer cache files are in the cache directory
		Assert.assertEquals(10 * 2, mockAggregator.getCacheManager().getCacheDir().listFiles(layerFileFilter).length);

		// Serialize the cache to disk
		serializedKeys = new LinkedList<String>(layerCache.getLayerBuildKeys());
		((TestCacheManager)mockAggregator.getCacheManager()).serializeCache();
		
		String filename1 = layerCache.getLayerBuildMap().get(serializedKeys.get(0)).filename;
		String filename2 = layerCache.getLayerBuildMap().get(serializedKeys.get(1)).filename;
		Assert.assertTrue(new File(mockAggregator.getCacheManager().getCacheDir(), filename1).exists());
		Assert.assertTrue(new File(mockAggregator.getCacheManager().getCacheDir(), filename1 + ".zip").exists());
		Assert.assertTrue(new File(mockAggregator.getCacheManager().getCacheDir(), filename2).exists());
		Assert.assertTrue(new File(mockAggregator.getCacheManager().getCacheDir(), filename2 + ".zip").exists());
		
		// Make sure that if the cache is de-serialized using a smaller max size, then
		// it is adjusted accordingly.
		initParams.clear();
		initParams.add(new InitParams.InitParam(InitParams.MAXLAYERCACHEENTRIES_INITPARAM, "8"));
		createMockObjects(initParams);
		layerCache = (LayerCacheImpl)mockAggregator.getCacheManager().getCache().getLayers();
		Assert.assertEquals(5, layerCache.getNumEvictions());
		Assert.assertEquals(5, layerCache.size());
		Assert.assertEquals(8, layerCache.getLayerBuildMap().size());
		// test to make sure the expected number of layer cache files are in the cache directory
		Assert.assertEquals(8 * 2, mockAggregator.getCacheManager().getCacheDir().listFiles(layerFileFilter).length);
		// Make sure the LRU entries were removed
		Assert.assertNull(layerCache.getLayerBuildMap().get(serializedKeys.get(0)));
		Assert.assertNull(layerCache.getLayerBuildMap().get(serializedKeys.get(1)));
		Assert.assertNotNull(layerCache.getLayerBuildMap().get(serializedKeys.get(2)));
		Assert.assertEquals(layerCache.getLayerBuildMap(), ((LayerImpl)layerCache.get("[p1/b]")).getLayerBuildMap());

		Assert.assertFalse(new File(mockAggregator.getCacheManager().getCacheDir(), filename1).exists());
		Assert.assertFalse(new File(mockAggregator.getCacheManager().getCacheDir(), filename1 + ".gzip").exists());
		Assert.assertFalse(new File(mockAggregator.getCacheManager().getCacheDir(), filename2).exists());
		Assert.assertFalse(new File(mockAggregator.getCacheManager().getCacheDir(), filename2 + ".gzip").exists());
		
		mockAggregator.getCacheManager().clearCache();
		Assert.assertEquals(0, layerCache.size());
		Assert.assertEquals(0, layerCache.getLayerBuildMap().size());
		Assert.assertEquals(0, mockAggregator.getCacheManager().getCacheDir().listFiles(layerFileFilter).length);

		// Test modified file updating when development mode is enabled
		initParams.clear();
		initParams.add(new InitParams.InitParam(InitParams.MAXLAYERCACHEENTRIES_INITPARAM, "10"));
		createMockObjects(initParams);
		layerCache = (LayerCacheImpl)mockAggregator.getCacheManager().getCache().getLayers();
		populateCache(layerCache);
		
		mockAggregator.getOptions().setOption("developmentMode", "true");
		
		TestUtils.createTestFile(tmpdir, "p1/a.js", TestUtils.a.replace("hello", "Hello"));
		requestAttributes.clear();
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, Arrays.asList(new String[] {"p1/a"}));
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		String key = layerCache.getLayerBuildKeys().iterator().next();
		String filename = layerCache.getLayerBuildMap().get(key).filename;
		layer = layerCache.getLayer(mockRequest);
		Assert.assertEquals(5, layerCache.size());
		in = layer.getInputStream(mockRequest, mockResponse);
		Writer writer = new StringWriter();
		CopyUtil.copy(in, writer);
		String result = writer.toString();
		Assert.assertTrue(result.contains("Hello from a.js"));
		String newFilename = layerCache.getLayerBuildMap().get(key).filename;
		Assert.assertNotSame(filename, newFilename);
		Reader reader = new FileReader(new File(mockAggregator.getCacheManager().getCacheDir(), newFilename));
		writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		Assert.assertEquals(result, writer.toString());		
		
		Assert.assertEquals(0, layerCache.getNumEvictions());
		Assert.assertEquals(5, layerCache.size());
		Assert.assertEquals(10, layerCache.getLayerBuildMap().size());
		Assert.assertEquals(20, mockAggregator.getCacheManager().getCacheDir().listFiles(layerFileFilter).length);
	
		// Make sure replace entry was moved to end tail of LRU queue
		Assert.assertEquals(key, new LinkedList<String>(layerCache.getLayerBuildKeys()).getLast());
		
		// Test recovery from deleted cache entry data
		layerCache.getLayerBuildMap().get(key).delete(mockAggregator.getCacheManager());
		Assert.assertEquals(18, mockAggregator.getCacheManager().getCacheDir().listFiles(layerFileFilter).length);
		in = layer.getInputStream(mockRequest, mockResponse);
		in.close();
		Assert.assertEquals(0, layerCache.getNumEvictions());
		Assert.assertEquals(5, layerCache.size());
		Assert.assertEquals(10, layerCache.getLayerBuildMap().size());
		Assert.assertEquals(20, mockAggregator.getCacheManager().getCacheDir().listFiles(layerFileFilter).length);
	}
	
	@SuppressWarnings("unchecked")
	private void createMockObjects(List<InitParams.InitParam> initParams) throws Exception {
		mockAggregator = TestUtils.createMockAggregator(configRef, tmpdir, initParams);
		mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes);
		mockResponse = EasyMock.createNiceMock(HttpServletResponse.class);
		mockDependencies = EasyMock.createMock(IDependencies.class);
		EasyMock.expect(mockAggregator.getDependencies()).andAnswer(new IAnswer<IDependencies>() {
			public IDependencies answer() throws Throwable {
				return mockDependencies;
			}
		}).anyTimes();
		
		EasyMock.expect(mockDependencies.getDelcaredDependencies(EasyMock.eq("p1/p1"))).andReturn(Arrays.asList(new String[]{"p1/a", "p2/p1/b", "p2/p1/p1/c", "p2/noexist"})).anyTimes();
		EasyMock.expect(mockDependencies.getExpandedDependencies((String)EasyMock.anyObject(), (Features)EasyMock.anyObject(), (Set<String>)EasyMock.anyObject(), EasyMock.anyBoolean())).andAnswer(new IAnswer<Map<String, String>>() {
			public Map<String, String> answer() throws Throwable {
				String name = (String)EasyMock.getCurrentArguments()[0];
				Map<String, String> result = TestUtils.testDepMap.get(name);
				if (result == null) {
					result = TestUtils.emptyDepMap;
				}
				return result;
			}
		}).anyTimes();
		
		URI p1Path = new File(tmpdir, "p1").toURI();
		URI p2Path = new File(tmpdir, "p2").toURI();
		final Map<String, URI> map = new HashMap<String, URI>();
		map.put("p1", p1Path);
		map.put("p2", p2Path);
		
		EasyMock.replay(mockAggregator);
		EasyMock.replay(mockRequest);
		EasyMock.replay(mockResponse);
		EasyMock.replay(mockDependencies);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		String configJson = "{paths:{p1:'p1',p2:'p2'}}";
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));
	}
	
	void populateCache(LayerCacheImpl layerCache) throws IOException {
		String[][] layers = new String[][] {
				new String[] {"p1/a"},
				new String[] {"p1/b"},
				new String[] {"p1/c"},
				new String[] {"p2/a"},
				new String[] {"p2/b"}, 
			};
		int i = 1;
		for (String[] l : layers) {
			requestAttributes.clear();
			requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, Arrays.asList(l));
			requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
			ILayer layer = layerCache.getLayer(mockRequest);
			Assert.assertEquals(i, layerCache.size());
			InputStream in = layer.getInputStream(mockRequest, mockResponse);
			in.close();
			System.out.println(layerCache.getLayerBuildKeys());
			Assert.assertEquals(i*2-1, layerCache.getLayerBuildMap().size());
			requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
			in = layer.getInputStream(mockRequest, mockResponse);
			in.close();
			Assert.assertEquals(i*2, layerCache.getLayerBuildMap().size());
			i++;
		}	
		Assert.assertEquals(0, layerCache.getNumEvictions());
		Assert.assertNotNull(layerCache.get("[p1/a]"));
	}
}