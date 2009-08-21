package net.rubyeye.xmemcached.command.binary;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import net.rubyeye.xmemcached.buffer.BufferAllocator;
import net.rubyeye.xmemcached.command.CommandType;
import net.rubyeye.xmemcached.transcoders.CachedData;
import net.rubyeye.xmemcached.utils.ByteUtils;

/**
 * A command for holding getkq commands
 * 
 * @author dennis
 * 
 */
@SuppressWarnings("unchecked")
public class BinaryGetMultiCommand extends BaseBinaryCommand {
	private boolean finished;
	private String responseKey;
	private long responseCAS;
	private int responseFlag;

	public BinaryGetMultiCommand(String key, CommandType cmdType,
			CountDownLatch latch) {
		super(key, null, cmdType, latch, 0, 0, null, false, null);
		this.result = new HashMap<String, CachedData>();
	}

	@Override
	protected void readOpCode(ByteBuffer buffer) {
		byte opCode = buffer.get();
		if (opCode == OpCode.GET_KEY.fieldValue()) {
			this.finished = true;
		}
	}

	@Override
	public void encode(BufferAllocator bufferAllocator) {
		// do nothing
	}

	@Override
	protected boolean finish() {
		if (this.finished) {
			countDownLatch();
		} else {
			this.responseKey = null;
		}
		return this.finished;
	}

	@Override
	protected boolean readKey(ByteBuffer buffer, int keyLength) {
		if (buffer.remaining() < keyLength) {
			return false;
		}
		if (keyLength > 0) {
			byte[] bytes = new byte[keyLength];
			buffer.get(bytes);
			this.responseKey = ByteUtils.getString(bytes);
			CachedData value = new CachedData();
			value.setCas(this.responseCAS);
			value.setFlag(this.responseFlag);
			((Map<String, CachedData>) this.result)
					.put(this.responseKey, value);
		}
		return true;
	}

	@Override
	protected boolean readValue(ByteBuffer buffer, int bodyLength,
			int keyLength, int extrasLength) {
		if (this.responseStatus == ResponseStatus.NO_ERROR) {
			int valueLength = bodyLength - keyLength - extrasLength;
			CachedData responseValue = ((Map<String, CachedData>) this.result)
					.get(this.responseKey);
			if (valueLength > 0 && responseValue.getCapacity() == 0) {
				responseValue.setCapacity(valueLength);
				responseValue.setData(new byte[valueLength]);
			}
			int remainingCapacity = responseValue.remainingCapacity();
			int remaining = buffer.remaining();
			if (remaining < remainingCapacity) {
				int length = remaining > remainingCapacity ? remainingCapacity
						: remaining;
				responseValue.fillData(buffer, length);
				return false;
			} else if (remainingCapacity > 0) {
				responseValue.fillData(buffer, remainingCapacity);
			}
			return true;
		} else {
			((Map<String, CachedData>) this.result).remove(this.responseKey);
			return true;
		}
	}

	@Override
	protected boolean readExtras(ByteBuffer buffer, int extrasLength) {
		if (buffer.remaining() < extrasLength) {
			return false;
		}
		if (extrasLength == 4) {
			// read flag
			this.responseFlag = buffer.getInt();
		}
		return true;
	}

	@Override
	protected long readCAS(ByteBuffer buffer) {
		this.responseCAS = buffer.getLong();
		return this.responseCAS;
	}

}