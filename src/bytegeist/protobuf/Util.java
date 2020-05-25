package bytegeist.protobuf;

import io.netty.buffer.ByteBuf;

public class Util {
    // Mostly copied from com.google.protobuf.CodedInputStream.readRawVarint32
    public static int readUnsignedVarint32(ByteBuf buf) {
        int firstByte = buf.readByte();
        if ((firstByte & 0x80) == 0) {
            return firstByte;
        }
        int result = firstByte & 0x7f;
        int offset = 7;
        for (; offset < 32; offset += 7) {
            final int b = buf.readByte();
            result |= (b & 0x7f) << offset;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        // Keep reading up to 64 bits.
        for (; offset < 64; offset += 7) {
            final int b = buf.readByte();
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new RuntimeException("Varint32 too long");
    }

    // Mostly copied from com.google.protobuf.CodedOutputStream.SafeDirectNioEncoder.writeUInt32NoTag
    public static void writeUnsignedVarint32(ByteBuf buf, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                buf.writeByte((byte) value);
                return;
            } else {
                buf.writeByte((byte) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }

    // Copied from com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag
    public static int sizeUnsignedVarint32(final int value) {
        if ((value & (~0 << 7)) == 0) {
            return 1;
        }
        if ((value & (~0 << 14)) == 0) {
            return 2;
        }
        if ((value & (~0 << 21)) == 0) {
            return 3;
        }
        if ((value & (~0 << 28)) == 0) {
            return 4;
        }
        return 5;
    }
}
