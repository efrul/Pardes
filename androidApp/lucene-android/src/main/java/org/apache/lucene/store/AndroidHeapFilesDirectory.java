/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.index.IndexFileNames;

/**
 * Android: a directory that reads some of the wrapped directory's files from the Java heap, each read whole when it is
 * opened, for an index that is only read.
 *
 * <p>Not part of Lucene. Pardes for Android 4.4 wraps its NIOFSDirectory in it, for the files a search seeks in again
 * and again: the terms index (.tip), looked up once for every term of every word's forms, and the small files the
 * readers keep open beside it (the points index .kdi, the stored fields index .fdx, the metadata). Each lookup there
 * would otherwise be a pread through the desugared library's FileChannel on a FUSE memory card, a few thousand of
 * them for a search of three words. Its other files (postings, norms, stored fields) stay on disk. The main app does
 * not use it.
 */
public final class AndroidHeapFilesDirectory extends FilterDirectory {
  private final Set<String> extensions;
  private final long maxFileBytes;

  /**
   * @param in the directory to read from
   * @param extensions the extensions (without the dot) of the files to read into the heap
   * @param maxFileBytes a larger file of those is read from {@code in} as usual
   */
  public AndroidHeapFilesDirectory(Directory in, Set<String> extensions, long maxFileBytes) {
    super(in);
    this.extensions = Collections.unmodifiableSet(new HashSet<>(extensions));
    this.maxFileBytes = maxFileBytes;
  }

  @Override
  public IndexInput openInput(String name, IOContext context) throws IOException {
    String extension = IndexFileNames.getExtension(name);
    if (extension == null || !extensions.contains(extension)) {
      return in.openInput(name, context);
    }
    long length = in.fileLength(name);
    if (length > maxFileBytes) {
      return in.openInput(name, context);
    }
    byte[] bytes = new byte[(int) length];
    try (IndexInput input = in.openInput(name, IOContext.READONCE)) {
      input.readBytes(bytes, 0, bytes.length);
    }
    // One buffer: its clones and slices (the readers take one per search) share it.
    ByteBuffersDataInput data = new ByteBuffersDataInput(Collections.singletonList(ByteBuffer.wrap(bytes)));
    return new ByteBuffersIndexInput(data, "AndroidHeapFilesDirectory(" + name + ")");
  }
}
