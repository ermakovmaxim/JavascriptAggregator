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
package com.ibm.jaggr.service.impl.transport;

import com.ibm.jaggr.core.impl.transport.AbstractDojoHttpTransport;

import java.net.URI;

/**
 * Extends {@link AbstractDojoHttpTransport} for OSGi platform
 *
 */
public class DojoHttpTransport extends AbstractDojoHttpTransport {

	private URI comboUri = URI.create("namedbundleresource://com.ibm.jaggr.core/WebContent/"); //$NON-NLS-1$


	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.impl.transport.AbstractHttpTransport#getComboUri()
	 */
	@Override
	protected URI getComboUri() {
		return comboUri;
	}

}
