package com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.query.cmd;

import com.genersoft.iot.vmp.conf.SipConfig;
import com.genersoft.iot.vmp.gb28181.bean.*;
import com.genersoft.iot.vmp.gb28181.event.EventPublisher;
import com.genersoft.iot.vmp.gb28181.event.record.RecordEndEventListener;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.impl.SIPCommander;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.impl.SIPCommanderFroPlatform;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.SIPRequestProcessorParent;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.query.QueryMessageHandler;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import com.genersoft.iot.vmp.service.ICloudRecordService;
import com.genersoft.iot.vmp.service.bean.CloudRecordItem;
import com.genersoft.iot.vmp.storager.dao.CloudRecordServiceMapper;
import com.genersoft.iot.vmp.utils.DateUtil;
import com.genersoft.iot.vmp.storager.IVideoManagerStorage;
import com.genersoft.iot.vmp.storager.dao.dto.ChannelSourceInfo;
import com.github.pagehelper.PageInfo;
import gov.nist.javax.sip.message.SIPRequest;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.SipException;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecordInfoQueryMessageHandler extends SIPRequestProcessorParent implements InitializingBean, IMessageHandler {

    private Logger logger = LoggerFactory.getLogger(RecordInfoQueryMessageHandler.class);
    private final String cmdType = "RecordInfo";

    @Autowired
    private QueryMessageHandler queryMessageHandler;

    @Autowired
    private IVideoManagerStorage storager;

    @Autowired
    private SIPCommanderFroPlatform cmderFroPlatform;

    @Autowired
    private SIPCommander commander;

    @Autowired
    private RecordEndEventListener recordEndEventListener;

    @Autowired
    private SipConfig config;

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private IMediaServerService mediaServerService;

    @Autowired
    private ICloudRecordService cloudRecordService;

    @Override
    public void afterPropertiesSet() throws Exception {
        queryMessageHandler.addHandler(cmdType, this);
    }

    @Override
    public void handForDevice(RequestEvent evt, Device device, Element element) {

    }

    @Override
    public void handForPlatform(RequestEvent evt, ParentPlatform parentPlatform, Element rootElement) {

        SIPRequest request = (SIPRequest) evt.getRequest();
        Element snElement = rootElement.element("SN");
        int sn = Integer.parseInt(snElement.getText());
        Element deviceIDElement = rootElement.element("DeviceID");
        String channelId = deviceIDElement.getText();
        Element startTimeElement = rootElement.element("StartTime");
        String startTime = null;
        if (startTimeElement != null) {
            startTime = startTimeElement.getText();
        }
        Element endTimeElement = rootElement.element("EndTime");
        String endTime = null;
        if (endTimeElement != null) {
            endTime = endTimeElement.getText();
        }
        Element secrecyElement = rootElement.element("Secrecy");
        int secrecy = 0;
        if (secrecyElement != null) {
            secrecy = Integer.parseInt(secrecyElement.getText().trim());
        }
        String type = "all";
        Element typeElement = rootElement.element("Type");
        if (typeElement != null) {
            type =  typeElement.getText();
        }
        // 确认是直播还是国标， 国标直接请求下级，直播请求录像管理服务
        List<ChannelSourceInfo> channelSources = storager.getChannelSource(parentPlatform.getServerGBId(), channelId);

        if (channelSources.get(0).getCount() > 0 && 1==1) {
            //cyf
            try {
                //从数据库里获取存储的文件内容
                List<MediaServer>  mediaServerItems = mediaServerService.getAllOnlineList();
                DeviceChannel deviceChannel = storager.queryChannelInParentPlatform(parentPlatform.getServerGBId(), channelId);
                String stream = deviceChannel.getDeviceId()+"_"+deviceChannel.getChannelId();
                PageInfo<CloudRecordItem> cloudList = cloudRecordService.getList(
                        1,
                        1000,
                        null,
                        "rtp",
                        stream,
                        startTime.replaceAll("T"," "),
                        endTime.replaceAll("T"," "),
                        mediaServerItems);
                responseAck(request, Response.OK);

               // for(CloudRecordItem cloudRecordItem : cloudList.getList()){
                //每两条记录合并发一个message
                int fregment = 2;
                RecordInfo recordInfo = new RecordInfo();
                recordInfo.setChannelId(deviceChannel.getChannelId());
                recordInfo.setDeviceId(deviceChannel.getDeviceId());
                recordInfo.setName(deviceChannel.getName());
                recordInfo.setSn(String.valueOf(sn));
                recordInfo.setSumNum(cloudList.getList().size());
                if (cloudList.getList().size() == 0) {
                    //recordInfo.setSumNum(0);
                    cmderFroPlatform.recordInfo(deviceChannel, parentPlatform, request.getFromTag(), recordInfo);
                } else {

                    for (int i=0; i<cloudList.getList().size(); i+=fregment) {
                        List<RecordItem> recordList = new ArrayList<>();
                        for (int j=0; j<fregment&&((i+j)<cloudList.getList().size()); j++) {
                            CloudRecordItem cloudRecord = cloudList.getList().get(i + j);
                            RecordItem record = new RecordItem();
                            //record.setAddress(cloudRecord.getFilePath());
                            //record.setRecorderId("1");
                            record.setDeviceId(deviceChannel.getChannelId());
                            record.setName(deviceChannel.getName());
                            record.setStartTime(DateUtil.timestampMsTo_yyyy_MM_dd_HH_mm_ss(cloudRecord.getStartTime()));
                            record.setEndTime(DateUtil.timestampMsTo_yyyy_MM_dd_HH_mm_ss(cloudRecord.getEndTime()));
                            record.setFileSize(String.valueOf(cloudRecord.getFileSize()));
                            record.setType("2");
                            recordList.add(record);
                           // recordInfo.setSumNum(j+1);
                        }

                        recordInfo.setRecordList(recordList);
                        cmderFroPlatform.recordInfo(deviceChannel, parentPlatform, request.getFromTag(), recordInfo);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                logger.error("[命令发送失败] 录像查询回复: {}", e.getMessage());
            }

        }
        else if (channelSources.get(0).getCount() > 0) { // 国标

            // 向国标设备请求录像数据
            Device device = storager.queryVideoDeviceByPlatformIdAndChannelId(parentPlatform.getServerGBId(), channelId);
            DeviceChannel deviceChannel = storager.queryChannelInParentPlatform(parentPlatform.getServerGBId(), channelId);
            // 接收录像数据
            recordEndEventListener.addEndEventHandler(deviceChannel.getDeviceId(), channelId, (recordInfo)->{
                try {
                    logger.info("[国标级联] 录像查询收到数据， 通道： {}，准备转发===", channelId);
                    cmderFroPlatform.recordInfo(deviceChannel, parentPlatform, request.getFromTag(), recordInfo);
                } catch (SipException | InvalidArgumentException | ParseException e) {
                    logger.error("[命令发送失败] 国标级联 回复录像数据: {}", e.getMessage());
                }
            });
            try {
                commander.recordInfoQuery(device, channelId, DateUtil.ISO8601Toyyyy_MM_dd_HH_mm_ss(startTime),
                        DateUtil.ISO8601Toyyyy_MM_dd_HH_mm_ss(endTime), sn, secrecy, type, (eventResult -> {
                            // 回复200 OK
                            try {
                                responseAck(request, Response.OK);
                            } catch (SipException | InvalidArgumentException | ParseException e) {
                                logger.error("[命令发送失败] 录像查询回复: {}", e.getMessage());
                            }
                        }),(eventResult -> {
                            // 查询失败
                            try {
                                responseAck(request, eventResult.statusCode, eventResult.msg);
                            } catch (SipException | InvalidArgumentException | ParseException e) {
                                logger.error("[命令发送失败] 录像查询回复: {}", e.getMessage());
                            }
                        }));
            } catch (InvalidArgumentException | ParseException | SipException e) {
                logger.error("[命令发送失败] 录像查询: {}", e.getMessage());
            }

        }else if (channelSources.get(1).getCount() > 0) { // 直播流
            // TODO
            try {
                responseAck(request, Response.NOT_IMPLEMENTED); // 回复未实现
            } catch (SipException | InvalidArgumentException | ParseException e) {
                logger.error("[命令发送失败] 录像查询: {}", e.getMessage());
            }
        }else { // 错误的请求
            try {
                responseAck(request, Response.BAD_REQUEST);
            } catch (SipException | InvalidArgumentException | ParseException e) {
                logger.error("[命令发送失败] 录像查询: {}", e.getMessage());
            }
        }
    }
}
