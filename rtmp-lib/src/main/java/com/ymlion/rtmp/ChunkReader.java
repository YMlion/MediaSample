package com.ymlion.rtmp;

import com.ymlion.rtmp.bean.RtmpHeader;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by YMlion on 2017/10/16.
 */

public class ChunkReader {
    public boolean readChunk(InputStream in) throws IOException {
        RtmpHeader header = new RtmpHeader();
        int r = header.read(in);
        if (r <= 0) {
            return false;
        }

        byte[] chunkBody = new byte[header.msgLength];
        int l = in.read(chunkBody);
        System.out.println("receive bytes size is " + l + "; " + header.msgLength);
        return l > 0;
    }
}
