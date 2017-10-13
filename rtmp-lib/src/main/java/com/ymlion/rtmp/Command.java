package com.ymlion.rtmp;

import com.ymlion.rtmp.bean.CommandObject;
import com.ymlion.rtmp.bean.RNull;
import com.ymlion.rtmp.bean.RNumber;
import com.ymlion.rtmp.bean.RObject;
import com.ymlion.rtmp.bean.RString;
import com.ymlion.rtmp.bean.RtmpHeader;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by YMlion on 2017/10/13.
 */

public class Command {

    private OutputStream out;

    public Command(OutputStream outputStream) {
        out = outputStream;
    }

    public void connect(String app, String tcUrl, String streamName) throws IOException {
        System.out.println("rtmp start connecting...");
        RString name = new RString("connect", false);
        byte[] nameBytes = name.getBytes();
        byte[] number = new RNumber(1.0).getBytes();
        CommandObject object = new CommandObject();
        object.put("app", app);
        object.put("type", "nonprivate");
        object.put("tcUrl", tcUrl);
        byte[] objBytes = object.getBytes();
        RtmpHeader header = new RtmpHeader();
        header.fmt = 0;
        header.CSID = 3;
        header.timestamp = 0;
        header.msgType = 20;
        header.msgSID = 0;
        header.msgLength = nameBytes.length + number.length + objBytes.length;
        header.write(out);
        out.write(nameBytes);
        out.write(number);
        out.write(objBytes);
        out.flush();
        System.out.println("rtmp connected.");
        //createStream(streamName, "releaseStream");
        //createStream(streamName, "FCPublish");
        //createStream(null, "createStream");
    }

    private void createStream(String streamName, String command) throws IOException {
        int total = 0;
        RObject name = new RString(command, false);
        total += name.getSize();
        RObject num = new RNumber(2.0);
        total += num.getSize();
        RObject rNull = new RNull();
        total += rNull.getSize();
        RObject s = null;
        if (streamName != null) {
            s = new RString(streamName, false);
            total += s.getSize();
        }
        RtmpHeader header = new RtmpHeader();
        header.fmt = 1;
        header.CSID = 3;
        header.timestamp = 0;
        header.msgType = 20;
        header.msgLength = total;
        header.write(out);
        name.write(out);
        num.write(out);
        rNull.write(out);
        if (s != null) {
            s.write(out);
        }
        out.flush();
    }
}
