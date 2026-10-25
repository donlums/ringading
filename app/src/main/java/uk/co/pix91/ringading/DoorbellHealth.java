package uk.co.pix91.ringading;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Conservative parsing of the Blink homescreen doorbells collection.
 * Only explicitly reported battery/connection state is acted on.
 * An absent field, disabled motion or sleeping camera is NOT considered offline.
 */
final class DoorbellHealth {
    enum State { LOW, OK, OFFLINE, ONLINE, UNKNOWN }
    static final class Reading {
        final String id, name;
        final State battery, connection;
        Reading(String id, String name, State battery, State connection) {
            this.id=id;
            this.name=name;
            this.battery=battery;
            this.connection=connection;
        }
    }
    private DoorbellHealth() {}

    static List<Reading> fromHomescreen(JSONObject screen) {
        List<Reading> result=new ArrayList<>();
        JSONArray devices=screen.optJSONArray("doorbells");
        if(devices==null)return result;
        for(int i=0;i<devices.length();i++){
            JSONObject device=devices.optJSONObject(i);
            if(device==null)continue;
            String id=string(device,"id","doorbell_id","device_id");
            String network=string(device,"network_id");
            // Do not mix reports for two homes or two distinct doorbells.
            if(!id.matches("[0-9]{1,18}") || !network.matches("[0-9]{1,18}"))continue;
            String name=string(device,"name");
            if(name.isEmpty()) name="Blink doorbell";
            name=name.replaceAll("[\\r\\n\\t]"," ").trim();
            if(name.length()>48)name=name.substring(0,48);
            JSONObject signals=device.optJSONObject("signals");
            String battery=string(device,"battery_state","battery_status","battery");
            if(battery.isEmpty() && signals!=null) battery=string(signals,"battery_status","battery");
            State b=decodeBattery(battery);
            if(b==State.UNKNOWN && (device.opt("battery_low") instanceof Boolean
                || device.opt("low_battery") instanceof Boolean)) {
                b=(device.optBoolean("battery_low",false) || device.optBoolean("low_battery",false))
                  ? State.LOW : State.OK;
            }
            String connection=string(device,"status","connection_status","connectivity_status");
            State c=decodeConnection(connection);
            if(c==State.UNKNOWN) {
                for(String key:new String[]{"online","is_online","connected"}){
                    Object raw=device.opt(key);
                    if(raw instanceof Boolean) {c=(Boolean)raw?State.ONLINE:State.OFFLINE;break;}
                }
            }
            result.add(new Reading(network+"_"+id,name,b,c));
        }
        return result;
    }
    private static String string(JSONObject object,String... keys) {
        for(String key:keys){
            Object raw=object.opt(key);
            if(raw instanceof String && !((String)raw).trim().isEmpty())return ((String)raw).trim();
        }
        return "";
    }
    static State decodeBattery(String raw) {
        if(raw==null)return State.UNKNOWN;
        String v=raw.trim().toLowerCase(Locale.ROOT).replace('-','_').replace(' ','_');
        switch(v){
            case "low":case "critical":case "very_low":case "battery_low":
            case "replace":case "replace_battery":case "replace_batteries":case "dead":
                return State.LOW;
            case "ok":case "good":case "normal":case "high":case "full":
            case "medium":case "healthy":case "battery_ok":
                return State.OK;
            default:return State.UNKNOWN;
        }
    }
    static State decodeConnection(String raw) {
        if(raw==null)return State.UNKNOWN;
        String v=raw.trim().toLowerCase(Locale.ROOT).replace('-','_').replace(' ','_');
        switch(v){
            case "offline":case "disconnected":case "not_connected":return State.OFFLINE;
            case "online":case "connected":return State.ONLINE;
            default:return State.UNKNOWN;
        }
    }
}
