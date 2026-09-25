package org.thesandbox.core.util;


import java.util.List;

public final class GenericDataKeys {

    public static final String WATER_FLOW = "Booleans.waterFlow.state";
    public static final Boolean WATER_FLOW_DEFAULT = false;

    // command messages :
    public static final String CMD_MSGS_SERVER = "&c&lServer &8• &c";
    public static final String CMD_MSGS_INFO = "&r&lInfo &8» &c";
    public static final String CMD_MSGS_ERROR = "&c&lError &8» &c";
    public static final String CMD_MSGS_USAGE = "&c&lUsage &8» &c";
    public static final String CMD_MSGS_COMMAND = "&7&lCommand &8» &7";

    // chat filter

    public static final List<String> BADWORDS = List.of("nigger", "nigga", "faggot");
    public static final String CHATFILTER = "chatfilter.blocked-words";

    // config

    public static final String SERVER = "server";
    public static final String FREEBUILD = "freebuild";
    public static final String SURVIVAL = "survival";

    // ================== //


    private GenericDataKeys() {
    }
}