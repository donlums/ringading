package uk.co.pix91.ringading;

import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import java.util.HashMap;
import static org.junit.Assert.*;

public class DoorbellHealthTest {
    @Test public void explicitLowBatteryAndOffline() throws Exception {
        JSONObject json=new JSONObject("{\"doorbells\":[{\"id\":7,\"network_id\":4,\"name\":\"Front door\",\"battery_state\":\"low\",\"status\":\"offline\"}]}");
        List<DoorbellHealth.Reading> result=DoorbellHealth.fromHomescreen(json);
        assertEquals(1,result.size());
        assertEquals("4_7",result.get(0).id);
        assertEquals(DoorbellHealth.State.LOW,result.get(0).battery);
        assertEquals(DoorbellHealth.State.OFFLINE,result.get(0).connection);
    }
    @Test public void missingSignalsAreUnknownNotOffline() throws Exception {
        JSONObject json=new JSONObject("{\"doorbells\":[{\"id\":7,\"network_id\":4,\"enabled\":false}]}");
        DoorbellHealth.Reading r=DoorbellHealth.fromHomescreen(json).get(0);
        assertEquals(DoorbellHealth.State.UNKNOWN,r.battery);
        assertEquals(DoorbellHealth.State.UNKNOWN,r.connection);
    }
    @Test public void onlineBooleanOverridesUnknownStatus() throws Exception {
        JSONObject json=new JSONObject("{\"doorbells\":[{\"id\":7,\"network_id\":4,\"status\":\"sleeping\",\"online\":true,\"battery_status\":\"good\"}]}");
        DoorbellHealth.Reading r=DoorbellHealth.fromHomescreen(json).get(0);
        assertEquals(DoorbellHealth.State.ONLINE,r.connection);
        assertEquals(DoorbellHealth.State.OK,r.battery);
    }
    @Test public void numericBatteryDoesNotBecomeLow() throws Exception {
        JSONObject json=new JSONObject("{\"doorbells\":[{\"id\":7,\"network_id\":4,\"signals\":{\"battery\":0}}]}");
        DoorbellHealth.Reading r=DoorbellHealth.fromHomescreen(json).get(0);
        assertEquals(DoorbellHealth.State.UNKNOWN,r.battery);
    }
    @Test public void pushClassificationOnlyForExplicitTypes() {
        HashMap<String,String> map=new HashMap<>();
        map.put("event_type","doorbell_offline");
        assertTrue(DoorbellHealthMonitor.isHealthPush(map));
        map.put("event_type","motion");
        assertFalse(DoorbellHealthMonitor.isHealthPush(map));
        map.put("event_type","battery_low");
        assertTrue(DoorbellHealthMonitor.isHealthPush(map));
    }
    @Test public void absentAndUnrecognisedStatesAreNotAlarms() {
        assertEquals(DoorbellHealth.State.UNKNOWN,DoorbellHealth.decodeConnection("sleeping"));
        assertEquals(DoorbellHealth.State.UNKNOWN,DoorbellHealth.decodeBattery("1"));
        assertEquals(DoorbellHealth.State.LOW,DoorbellHealth.decodeBattery("critical"));
    }
}
