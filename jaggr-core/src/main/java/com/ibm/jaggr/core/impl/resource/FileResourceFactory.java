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

package com.ibm.jaggr.core.impl.resource;

import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactory;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation for {@link IResourceFactory} that currently supports
 * only file resources.
 */
public class FileResourceFactory implements IResourceFactory {
	static final Logger log = Logger.getLogger(FileResourceFactory.class.getName());

	private boolean tryNIO = true;
	private Class<?> nioFileResourceClass = null; // access only through the getter
	private Constructor<?> nioFileResourceConstructor = null; // access only through the getter

	@Override
	public IResource newResource(URI uri) {
		IResource result = null;
		String scheme = uri.getScheme();
		if ("file".equals(scheme) || scheme == null) { //$NON-NLS-1$
			Constructor<?> constructor = getNIOFileResourceConstructor(URI.class);
			if (constructor != null) {
				try {
					result = (IResource)constructor.newInstance(uri);
				} catch (Throwable t) {
					if (log.isLoggable(Level.SEVERE)) {
						log.log(Level.SEVERE, t.getMessage(), t);
					}
					result = new FileResource(uri);
				}
			} else {
				result = new FileResource(uri);
			}
		} else {
			throw new UnsupportedOperationException(uri.getScheme());
		}
		return result;
	}

	@Override
	public boolean handles(URI uri) {
		return "file".equals(uri.getScheme()); //$NON-NLS-1$
	}

	/**
	 * Utility method for acquiring a reference to the NIOFileResource class without
	 * asking the class loader every single time after we know it's not there.
	 */
	protected Class<?> getNIOFileResourceClass() {
		if (tryNIO && nioFileResourceClass == null) {
			try {
				nioFileResourceClass = FileResourceFactory.class.getClassLoader().loadClass("com.ibm.jaggr.core.impl.resource.NIOFileResource");
			} catch (ClassNotFoundException e) {
				tryNIO = false; // Don't try this again.
				if (log.isLoggable(Level.WARNING)) {
					log.log(Level.WARNING, e.getMessage(), e);
				}
			}
		}
		return nioFileResourceClass;
	}

	/**
	 * Utility method for acquiring a reference to the NIOFileResource class constructor
	 * without asking the class loader every single time after we know it's not there.
	 */
	protected Constructor<?> getNIOFileResourceConstructor(Class<?>... args) {
		if (tryNIO && nioFileResourceConstructor == null) {
			try {
				Class<?> clazz = getNIOFileResourceClass();
				if (clazz != null)
					nioFileResourceConstructor = clazz.getConstructor(args);
			} catch (NoSuchMethodException e) {
				tryNIO = false; // Don't try this again.
				if (log.isLoggable(Level.SEVERE)) {
					log.log(Level.SEVERE, e.getMessage(), e);
				}
			} catch (SecurityException e) {
				tryNIO = false; // Don't try this again.
				if (log.isLoggable(Level.SEVERE)) {
					log.log(Level.SEVERE, e.getMessage(), e);
				}
			}
		}
		return nioFileResourceConstructor;
	}

}
