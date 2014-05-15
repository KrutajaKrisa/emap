/*******************************************************************************
 * Copyright (c) 2014 BestSolution.at and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tom Schindl <tom.schindl@bestsolution.at> - initial API and implementation
 *     Christoph Caks <ccaks@bestsolution.at> - finished implementation
 *******************************************************************************/
package at.bestsolution.persistence.java.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.sql.Blob;
import java.sql.SQLException;

public class LocalBlob implements Blob {
	private File storageFile;
	private RandomAccessFile storage;
	
	private boolean blobInvalid = false;
	
	private void checkInvalid() throws SQLException {
		if (blobInvalid) {
			throw new SQLException("BLOB invalid (already freed)");
		}
	}
	
	private File createStorageFile() throws IOException {
		final File storageFile = File.createTempFile("emap", "blob");
		storageFile.deleteOnExit();
		return storageFile;
	}
	
	private RandomAccessFile createStorage(File storageFile) throws IOException {
		return  new RandomAccessFile(storageFile, "rw");
	}
	
	private RandomAccessFile getStorage() throws IOException {
		if (storage == null) {
			storageFile = createStorageFile();
			storage = createStorage(storageFile);
		}
		return storage;
	}
	
	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		try {
			if (storage != null) {
				storage.close();
				storage = null;
			}
		}
		catch (IOException e) {
		}
		if (storageFile != null) {
			if (storageFile.delete()) {
				storageFile = null;
			}
		}
	}
	
	@Override
	public void free() throws SQLException {
		try {
			if (storage != null) {
				storage.close();
				storage = null;
			}
			if (storageFile != null) {
				storageFile.delete();
				storageFile = null;
			}
		} catch (IOException e) {
			throw new SQLException(e);
		}
		finally {
			blobInvalid = true;
		}
	}

	@Override
	public InputStream getBinaryStream() throws SQLException {
		checkInvalid();
		try {
			return new RAF_InputStream(getStorage());
		}
		catch (IOException e) {
			throw new SQLException(e);
		}
	}

	@Override
	public InputStream getBinaryStream(long pos, long length) throws SQLException {
		checkInvalid();
		throw new UnsupportedOperationException();
	}

	@Override
	public byte[] getBytes(long pos, int length) throws SQLException {
		checkInvalid();
		try {
			byte[] buf = new byte[length];
			getStorage().read(buf, (int)pos-1, length);
			return buf;
		} catch (IOException e) {
			throw new SQLException(e);
		}
	}

	@Override
	public long length() throws SQLException {
		checkInvalid();
		try {
			return getStorage().length();
		} catch (IOException e) {
			throw new SQLException(e);
		}
	}

	@Override
	public long position(byte[] pattern, long start) throws SQLException {
		checkInvalid();
		throw new UnsupportedOperationException();
	}

	@Override
	public long position(Blob pattern, long start) throws SQLException {
		checkInvalid();
		throw new UnsupportedOperationException();
	}

	@Override
	public OutputStream setBinaryStream(long pos) throws SQLException {
		checkInvalid();
		try {
			return new RAF_OutputStream(getStorage(), (int) pos);
		}
		catch (IOException e) {
			throw new SQLException(e);
		}
	}

	@Override
	public int setBytes(long pos, byte[] bytes) throws SQLException {
		checkInvalid();
		try {
			getStorage().write(bytes, (int) pos-1, bytes.length);
			return bytes.length;
		} catch (IOException e) {
			throw new SQLException(e);
		}
	}

	@Override
	public int setBytes(long pos, byte[] bytes, int offset, int len) throws SQLException {
		checkInvalid();
		try {
			getStorage().write(bytes, (int)pos-1, len);
			return len;
		} catch (IOException e) {
			throw new SQLException(e);
		}
	}

	@Override
	public void truncate(long len) throws SQLException {
		checkInvalid();
		try {
			getStorage().setLength(len);
		} catch (IOException e) {
			throw new SQLException(e);
		}
	}
	
	static class RAF_OutputStream extends OutputStream {
		private final RandomAccessFile raf;
		private final byte[] b1 = new byte[1];
		private int offset;
		
		public RAF_OutputStream(RandomAccessFile raf) {
			this.raf = raf;
		}
		
		public RAF_OutputStream(RandomAccessFile raf, int offset) {
			this.raf = raf;
			this.offset = offset;
		}
		
		@Override
		public void write(byte[] b) throws IOException {
			write(b,offset,b.length);
		}
		
		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			raf.write(b, off, len);
			offset  = off + len;
		}
		
		@Override
		public void write(int arg0) throws IOException {
			b1[0] = (byte) arg0;
			write(b1);
		}
		
	}
	
	static class RAF_InputStream extends InputStream {
		private final RandomAccessFile raf;
		private final byte[] b1 = new byte[1];
		private int offset;
		
		public RAF_InputStream(RandomAccessFile raf) {
			this.raf = raf;
		}
		
		@Override
		public int read(byte[] b) throws IOException {
			return read(b, offset, b.length);
		}
		
		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int rv = raf.read(b, off, len);
			offset = off+len;
			return rv;
		}
		
		
		@Override
		public int read() throws IOException {
			read(b1);
			return b1[0];
		}
	}
}