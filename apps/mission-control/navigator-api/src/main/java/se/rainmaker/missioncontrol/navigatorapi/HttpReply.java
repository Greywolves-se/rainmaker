package se.rainmaker.missioncontrol.navigatorapi;

/**
 * One reply of the API: an HTTP status and a JSON body that matches {@code
 * contract/navigator-ask.schema.json}.
 */
public record HttpReply(int status, String body) {}
