package com.qaprosoft.zafira.dbaccess.dao;

import com.qaprosoft.zafira.dbaccess.dao.mysql.MonitorMapper;
import com.qaprosoft.zafira.dbaccess.utils.KeyGenerator;
import com.qaprosoft.zafira.models.db.Monitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.List;
import static org.testng.Assert.*;

/**
 * @author Kirill Bugrim
 *
 * @version 1.0
 */

@Test
@ContextConfiguration("classpath:com/qaprosoft/zafira/dbaccess/dbaccess-test.xml")
public class MonitorMapperTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MonitorMapper monitorMapper;

    /**
     * Turn this on to enable this test
     */
    private static final boolean ENABLED = false;

    private static final Monitor MONITOR = new Monitor() {
        {
            setName("n1" + KeyGenerator.getKey());
            setUrl("https://www.onliner.by/");
            setExpectedResponseCode(KeyGenerator.getKey());
            setHttpMethod(HttpMethod.GET);
            setCronExpression("0 * * ? * *");
            setRecipients("e.jhon@gmail.com");
            setRequestBody("{'k':'v'}");
            setEnableNotification(true);
            setType(Type.HTTP);
        }
    };


    @Test(enabled = ENABLED)
    public void createMonitor() {
        monitorMapper.createMonitor(MONITOR);
        assertNotEquals(MONITOR.getId(), 0, "Monitor ID must be set up by autogenerated keys");
    }


    @Test(enabled = ENABLED, dependsOnMethods = {"createMonitor"})
    public void getMonitorsCount()
    {
        Integer count = monitorMapper.getMonitorsCount();
        Assert.assertNotNull(count, "");
    }

    @Test(enabled = ENABLED, dependsOnMethods = {"getMonitorsCount"})
    public void getMonitorByMonitorName() {
        checkMonitor(monitorMapper.getMonitorByMonitorName(MONITOR.getName()));
    }


    @Test(enabled = ENABLED, dependsOnMethods = {"getMonitorByMonitorName"})
    public void getMonitorById() {
        checkMonitor(monitorMapper.getMonitorById(MONITOR.getId()));
    }


    @Test(enabled = ENABLED, dependsOnMethods = { "getMonitorById" })
    public void updateMonitor()
    {
        MONITOR.setName("n1" + KeyGenerator.getKey());
        MONITOR.setUrl("https://mail.google.com/");
        MONITOR.setExpectedResponseCode(KeyGenerator.getKey());
        MONITOR.setHttpMethod(Monitor.HttpMethod.POST);
        MONITOR.setCronExpression("0 */3 * ? * *");
        MONITOR.setRecipients("paul@gmail.com");
        MONITOR.setRequestBody("{'kkk':'vvvv'}");
        MONITOR.setEnableNotification(false);
        MONITOR.setType(Monitor.Type.PING);
        monitorMapper.updateMonitor(MONITOR);
        checkMonitor(monitorMapper.getMonitorById(MONITOR.getId()));
    }


    @Test(enabled = ENABLED, dependsOnMethods = {"updateMonitor"})
    public void getAllMonitors()
    {
        List<Monitor> monitorList = monitorMapper.getAllMonitors();
        checkMonitor(monitorList.get(monitorList.size() - 1));
    }


    /**
     * If true, then <code>deleteMonitor</code> will be used to delete monitor after all tests, otherwise -
     * <code>deleteMonitorById</code>
     */
    private static final boolean DELETE_MONITOR = true;

    @Test(enabled = ENABLED  && !DELETE_MONITOR,
            dependsOnMethods = {"createMonitor","getMonitorByMonitorName","getMonitorById","updateMonitor","getAllMonitors"})
    public void deleteMonitorById() {
        monitorMapper.deleteMonitorById(MONITOR.getId());
        assertNull(monitorMapper.getMonitorById(MONITOR.getId()));
    }


    @Test(enabled = ENABLED  && DELETE_MONITOR,
            dependsOnMethods = {"createMonitor","getMonitorByMonitorName","getMonitorById","updateMonitor","getAllMonitors"})
    public void deleteMonitor() {
        monitorMapper.deleteMonitor(MONITOR);
        assertNull(monitorMapper.getMonitorById(MONITOR.getId()));
    }

    private void checkMonitor(Monitor monitor) {
        assertEquals(monitor.getName(), MONITOR.getName(), "Name must match");
        assertEquals(monitor.getUrl(), MONITOR.getUrl(), "URL must match");
        assertEquals(monitor.getCronExpression(), MONITOR.getCronExpression(), "Cron expression must match");
        assertEquals(monitor.getRecipients(), MONITOR.getRecipients(), "Emails must match");
        assertEquals(monitor.getExpectedResponseCode(), MONITOR.getExpectedResponseCode(), "Expected response status must match");
        assertEquals(monitor.getRequestBody(), MONITOR.getRequestBody(), "Request body must match");
        assertEquals(monitor.getType(), MONITOR.getType(), "Type must match");
        assertEquals(monitor.isEnableNotification(), MONITOR.isEnableNotification(), "Enable notification must match");
        assertEquals(monitor.getHttpMethod(), MONITOR.getHttpMethod(), "HTTP method must match");
    }

}
