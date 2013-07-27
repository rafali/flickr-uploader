/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * This code was generated by https://code.google.com/p/google-apis-client-generator/
 * (build: 2013-06-26 16:27:34 UTC)
 * on 2013-07-27 at 01:00:36 UTC 
 * Modify at your own risk.
 */

package com.rafali.flickruploader.appinstallendpoint.model;

/**
 * Model definition for AppInstall.
 *
 * <p> This is the Java data model class that specifies how to parse/serialize into the JSON that is
 * transmitted over HTTP when working with the . For a detailed explanation see:
 * <a href="http://code.google.com/p/google-http-java-client/wiki/JSON">http://code.google.com/p/google-http-java-client/wiki/JSON</a>
 * </p>
 *
 * @author Google, Inc.
 */
@SuppressWarnings("javadoc")
public final class AppInstall extends com.google.api.client.json.GenericJson {

  /**
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private AndroidDevice androidDevice;

  /**
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.lang.String customSku;

  /**
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private com.google.api.client.util.DateTime dateCreation;

  /**
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.lang.String deviceId;

  /**
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.util.List<java.lang.String> emails;

  /**
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.util.List<java.lang.String> ownerIds;

  /**
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.lang.Boolean premium;

  /**
   * @return value or {@code null} for none
   */
  public AndroidDevice getAndroidDevice() {
    return androidDevice;
  }

  /**
   * @param androidDevice androidDevice or {@code null} for none
   */
  public AppInstall setAndroidDevice(AndroidDevice androidDevice) {
    this.androidDevice = androidDevice;
    return this;
  }

  /**
   * @return value or {@code null} for none
   */
  public java.lang.String getCustomSku() {
    return customSku;
  }

  /**
   * @param customSku customSku or {@code null} for none
   */
  public AppInstall setCustomSku(java.lang.String customSku) {
    this.customSku = customSku;
    return this;
  }

  /**
   * @return value or {@code null} for none
   */
  public com.google.api.client.util.DateTime getDateCreation() {
    return dateCreation;
  }

  /**
   * @param dateCreation dateCreation or {@code null} for none
   */
  public AppInstall setDateCreation(com.google.api.client.util.DateTime dateCreation) {
    this.dateCreation = dateCreation;
    return this;
  }

  /**
   * @return value or {@code null} for none
   */
  public java.lang.String getDeviceId() {
    return deviceId;
  }

  /**
   * @param deviceId deviceId or {@code null} for none
   */
  public AppInstall setDeviceId(java.lang.String deviceId) {
    this.deviceId = deviceId;
    return this;
  }

  /**
   * @return value or {@code null} for none
   */
  public java.util.List<java.lang.String> getEmails() {
    return emails;
  }

  /**
   * @param emails emails or {@code null} for none
   */
  public AppInstall setEmails(java.util.List<java.lang.String> emails) {
    this.emails = emails;
    return this;
  }

  /**
   * @return value or {@code null} for none
   */
  public java.util.List<java.lang.String> getOwnerIds() {
    return ownerIds;
  }

  /**
   * @param ownerIds ownerIds or {@code null} for none
   */
  public AppInstall setOwnerIds(java.util.List<java.lang.String> ownerIds) {
    this.ownerIds = ownerIds;
    return this;
  }

  /**
   * @return value or {@code null} for none
   */
  public java.lang.Boolean getPremium() {
    return premium;
  }

  /**
   * @param premium premium or {@code null} for none
   */
  public AppInstall setPremium(java.lang.Boolean premium) {
    this.premium = premium;
    return this;
  }

  @Override
  public AppInstall set(String fieldName, Object value) {
    return (AppInstall) super.set(fieldName, value);
  }

  @Override
  public AppInstall clone() {
    return (AppInstall) super.clone();
  }

}
