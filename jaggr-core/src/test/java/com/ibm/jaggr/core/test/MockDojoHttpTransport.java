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

package com.ibm.jaggr.core.test;

import com.ibm.jaggr.core.impl.transport.AbstractDojoHttpTransport;

/**
 * Implements the functionality specific for the Dojo Http Transport (supporting
 * the dojo AMD loader).
 */
public class MockDojoHttpTransport extends AbstractDojoHttpTransport {

	@Override
	protected String getComboUriStr() {		
		return null;
	}

	@Override
	protected String getTextPluginProxyUriStr() {
		return null;
	}

	@Override
	protected String getLoaderExtensionPath() {
		return null;
	}		
}