package com.fooddelivery.identity.application;

/**
 * Where a session was started or continued, stored with the refresh token for the session list
 * (REQ-AUTH-004 AC2).
 *
 * @param deviceInfo a short client description (User-Agent), at most 255 characters
 * @param ip the client address as seen after trusted proxies, or {@code null}
 */
public record ClientContext(String deviceInfo, String ip) {

  public static final int MAX_DEVICE_INFO = 255;

  public ClientContext {
    if (deviceInfo != null) {
      deviceInfo = deviceInfo.replaceAll("\\p{Cntrl}", " ").strip();
      if (deviceInfo.length() > MAX_DEVICE_INFO) {
        deviceInfo = deviceInfo.substring(0, MAX_DEVICE_INFO);
      }
      if (deviceInfo.isEmpty()) {
        deviceInfo = null;
      }
    }
  }
}
