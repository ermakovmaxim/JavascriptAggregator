/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.ibm.jaggr.core.impl.resource;

import com.ibm.jaggr.core.resource.IResourceFactory;
import com.ibm.jaggr.core.resource.IResourceVisitor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class NIOFileResource extends FileResource {
	static final Logger log = Logger.getLogger(NIOFileResource.class.getName());

	public NIOFileResource(URI uri) {
		super(uri);
	}

	public NIOFileResource(URI ref, IResourceFactory factory, URI uri) {
		super(ref, factory, uri);
	}

	@Override
	public void walkTree(final IResourceVisitor visitor) throws IOException {
		if (!exists()) {
			throw new FileNotFoundException(file.getAbsolutePath());
		}
		if (!file.isDirectory()) {
			return;
		}

		java.nio.file.Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(final Path pathfile, final BasicFileAttributes attrs) throws IOException {
				visitor.visitResource(new IResourceVisitor.Resource() {
					@Override
					public long lastModified() {
						return attrs.lastModifiedTime().to(TimeUnit.MICROSECONDS);
					}
					@Override
					public boolean isFolder() {
						return attrs.isDirectory();
					}
					@Override
					public URI getURI() {
						return FileResource.getURI(pathfile.toFile());
					}
					@Override
					public Reader getReader() throws IOException {
						return new InputStreamReader(new FileInputStream(pathfile.toFile()), "UTF-8"); //$NON-NLS-1$
					}
					@Override
					public InputStream getInputStream() throws IOException {
						return new FileInputStream(pathfile.toFile());
					}
				}, file.toPath().relativize(pathfile).toString());
				return FileVisitResult.CONTINUE;
			}
		});
	}

}