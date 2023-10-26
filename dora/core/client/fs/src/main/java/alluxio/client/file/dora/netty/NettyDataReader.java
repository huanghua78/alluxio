/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.client.file.dora.netty;

import alluxio.PositionReader;
import alluxio.client.file.FileSystemContext;
import alluxio.file.ReadTargetBuffer;
import alluxio.metrics.MetricKey;
import alluxio.metrics.MetricsSystem;
import alluxio.proto.dataserver.Protocol;
import alluxio.wire.WorkerNetAddress;

import com.codahale.metrics.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Positioned Netty data reader.
 */
public class NettyDataReader implements PositionReader {
  private static final Logger LOG = LoggerFactory.getLogger(NettyDataReader.class);
  private final FileSystemContext mContext;
  private final WorkerNetAddress mAddress;
  private final Supplier<Protocol.ReadRequest.Builder> mRequestBuilder;

  /**
   * Constructor.
   *
   * @param context
   * @param address
   * @param requestBuilder
   */
  public NettyDataReader(FileSystemContext context, WorkerNetAddress address,
      Protocol.ReadRequest.Builder requestBuilder) {
    mContext = context;
    mAddress = address;
    // clone the builder so that the initial values does not get overridden
    mRequestBuilder = requestBuilder::clone;
  }

  @Override
  public int readInternal(long position, ReadTargetBuffer buffer, int length) throws IOException {
    Protocol.ReadRequest.Builder builder = mRequestBuilder.get()
        .setLength(length)
        .setOffset(position)
        .clearCancel();
    LOG.error("Client-side Netty reading pos = {} len = {}", position, length);
//    if (length > 0) {
//      return length;
//    }

    NettyDataReaderStateMachine clientStateMachine =
        new NettyDataReaderStateMachine(mContext, mAddress, builder, buffer);
    clientStateMachine.run();
    int bytesRead = clientStateMachine.getBytesRead();
    PartialReadException exception = clientStateMachine.getException();
    if (exception != null) {
      throw exception;
    } else {
      if (bytesRead == 0) {
        return -1;
      }
      Metrics.BYTES_READ_FROM_WORKERS.inc(bytesRead);
      return bytesRead;
    }
  }

  /**
   * Class that contains metrics about FileOutStream.
   */
  private static final class Metrics {
    private static final Counter BYTES_READ_FROM_WORKERS =
        MetricsSystem.counter(MetricKey.CLIENT_BYTES_READ_FROM_WORKERS.getName());

    private Metrics() {
    } // prevent instantiation
  }
}
