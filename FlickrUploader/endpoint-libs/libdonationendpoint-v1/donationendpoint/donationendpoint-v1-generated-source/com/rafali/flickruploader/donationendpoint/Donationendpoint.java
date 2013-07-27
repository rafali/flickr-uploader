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
 * on 2013-07-27 at 01:00:53 UTC 
 * Modify at your own risk.
 */

package com.rafali.flickruploader.donationendpoint;

/**
 * Service definition for Donationendpoint (v1).
 *
 * <p>
 * This is an API
 * </p>
 *
 * <p>
 * For more information about this service, see the
 * <a href="" target="_blank">API Documentation</a>
 * </p>
 *
 * <p>
 * This service uses {@link DonationendpointRequestInitializer} to initialize global parameters via its
 * {@link Builder}.
 * </p>
 *
 * @since 1.3
 * @author Google, Inc.
 */
@SuppressWarnings("javadoc")
public class Donationendpoint extends com.google.api.client.googleapis.services.json.AbstractGoogleJsonClient {

  // Note: Leave this static initializer at the top of the file.
  static {
    com.google.api.client.util.Preconditions.checkState(
        com.google.api.client.googleapis.GoogleUtils.MAJOR_VERSION == 1 &&
        com.google.api.client.googleapis.GoogleUtils.MINOR_VERSION >= 15,
        "You are currently running with version %s of google-api-client. " +
        "You need at least version 1.15 of google-api-client to run version " +
        "1.15.0-rc of the  library.", com.google.api.client.googleapis.GoogleUtils.VERSION);
  }

  /**
   * The default encoded root URL of the service. This is determined when the library is generated
   * and normally should not be changed.
   *
   * @since 1.7
   */
  public static final String DEFAULT_ROOT_URL = "https://ra-fa-li.appspot.com/_ah/api/";

  /**
   * The default encoded service path of the service. This is determined when the library is
   * generated and normally should not be changed.
   *
   * @since 1.7
   */
  public static final String DEFAULT_SERVICE_PATH = "donationendpoint/v1/";

  /**
   * The default encoded base URL of the service. This is determined when the library is generated
   * and normally should not be changed.
   */
  public static final String DEFAULT_BASE_URL = DEFAULT_ROOT_URL + DEFAULT_SERVICE_PATH;

  /**
   * Constructor.
   *
   * <p>
   * Use {@link Builder} if you need to specify any of the optional parameters.
   * </p>
   *
   * @param transport HTTP transport, which should normally be:
   *        <ul>
   *        <li>Google App Engine:
   *        {@code com.google.api.client.extensions.appengine.http.UrlFetchTransport}</li>
   *        <li>Android: {@code newCompatibleTransport} from
   *        {@code com.google.api.client.extensions.android.http.AndroidHttp}</li>
   *        <li>Java: {@link com.google.api.client.googleapis.javanet.GoogleNetHttpTransport#newTrustedTransport()}
   *        </li>
   *        </ul>
   * @param jsonFactory JSON factory, which may be:
   *        <ul>
   *        <li>Jackson: {@code com.google.api.client.json.jackson2.JacksonFactory}</li>
   *        <li>Google GSON: {@code com.google.api.client.json.gson.GsonFactory}</li>
   *        <li>Android Honeycomb or higher:
   *        {@code com.google.api.client.extensions.android.json.AndroidJsonFactory}</li>
   *        </ul>
   * @param httpRequestInitializer HTTP request initializer or {@code null} for none
   * @since 1.7
   */
  public Donationendpoint(com.google.api.client.http.HttpTransport transport, com.google.api.client.json.JsonFactory jsonFactory,
      com.google.api.client.http.HttpRequestInitializer httpRequestInitializer) {
    this(new Builder(transport, jsonFactory, httpRequestInitializer));
  }

  /**
   * @param builder builder
   */
  Donationendpoint(Builder builder) {
    super(builder);
  }

  @Override
  protected void initialize(com.google.api.client.googleapis.services.AbstractGoogleClientRequest<?> httpClientRequest) throws java.io.IOException {
    super.initialize(httpClientRequest);
  }

  /**
   * Create a request for the method "getDonation".
   *
   * This request holds the parameters needed by the the donationendpoint server.  After setting any
   * optional parameters, call the {@link GetDonation#execute()} method to invoke the remote
   * operation.
   *
   * @param id
   * @return the request
   */
  public GetDonation getDonation(java.lang.Long id) throws java.io.IOException {
    GetDonation result = new GetDonation(id);
    initialize(result);
    return result;
  }

  public class GetDonation extends DonationendpointRequest<com.rafali.flickruploader.donationendpoint.model.Donation> {

    private static final String REST_PATH = "donation/{id}";

    /**
     * Create a request for the method "getDonation".
     *
     * This request holds the parameters needed by the the donationendpoint server.  After setting any
     * optional parameters, call the {@link GetDonation#execute()} method to invoke the remote
     * operation. <p> {@link
     * GetDonation#initialize(com.google.api.client.googleapis.services.AbstractGoogleClientRequest)}
     * must be called to initialize this instance immediately after invoking the constructor. </p>
     *
     * @param id
     * @since 1.13
     */
    protected GetDonation(java.lang.Long id) {
      super(Donationendpoint.this, "GET", REST_PATH, null, com.rafali.flickruploader.donationendpoint.model.Donation.class);
      this.id = com.google.api.client.util.Preconditions.checkNotNull(id, "Required parameter id must be specified.");
    }

    @Override
    public com.google.api.client.http.HttpResponse executeUsingHead() throws java.io.IOException {
      return super.executeUsingHead();
    }

    @Override
    public com.google.api.client.http.HttpRequest buildHttpRequestUsingHead() throws java.io.IOException {
      return super.buildHttpRequestUsingHead();
    }

    @Override
    public GetDonation setAlt(java.lang.String alt) {
      return (GetDonation) super.setAlt(alt);
    }

    @Override
    public GetDonation setFields(java.lang.String fields) {
      return (GetDonation) super.setFields(fields);
    }

    @Override
    public GetDonation setKey(java.lang.String key) {
      return (GetDonation) super.setKey(key);
    }

    @Override
    public GetDonation setOauthToken(java.lang.String oauthToken) {
      return (GetDonation) super.setOauthToken(oauthToken);
    }

    @Override
    public GetDonation setPrettyPrint(java.lang.Boolean prettyPrint) {
      return (GetDonation) super.setPrettyPrint(prettyPrint);
    }

    @Override
    public GetDonation setQuotaUser(java.lang.String quotaUser) {
      return (GetDonation) super.setQuotaUser(quotaUser);
    }

    @Override
    public GetDonation setUserIp(java.lang.String userIp) {
      return (GetDonation) super.setUserIp(userIp);
    }

    @com.google.api.client.util.Key
    private java.lang.Long id;

    /**

     */
    public java.lang.Long getId() {
      return id;
    }

    public GetDonation setId(java.lang.Long id) {
      this.id = id;
      return this;
    }

    @Override
    public GetDonation set(String parameterName, Object value) {
      return (GetDonation) super.set(parameterName, value);
    }
  }

  /**
   * Create a request for the method "insertDonation".
   *
   * This request holds the parameters needed by the the donationendpoint server.  After setting any
   * optional parameters, call the {@link InsertDonation#execute()} method to invoke the remote
   * operation.
   *
   * @param content the {@link com.rafali.flickruploader.donationendpoint.model.Donation}
   * @return the request
   */
  public InsertDonation insertDonation(com.rafali.flickruploader.donationendpoint.model.Donation content) throws java.io.IOException {
    InsertDonation result = new InsertDonation(content);
    initialize(result);
    return result;
  }

  public class InsertDonation extends DonationendpointRequest<com.rafali.flickruploader.donationendpoint.model.Donation> {

    private static final String REST_PATH = "donation";

    /**
     * Create a request for the method "insertDonation".
     *
     * This request holds the parameters needed by the the donationendpoint server.  After setting any
     * optional parameters, call the {@link InsertDonation#execute()} method to invoke the remote
     * operation. <p> {@link InsertDonation#initialize(com.google.api.client.googleapis.services.Abstr
     * actGoogleClientRequest)} must be called to initialize this instance immediately after invoking
     * the constructor. </p>
     *
     * @param content the {@link com.rafali.flickruploader.donationendpoint.model.Donation}
     * @since 1.13
     */
    protected InsertDonation(com.rafali.flickruploader.donationendpoint.model.Donation content) {
      super(Donationendpoint.this, "POST", REST_PATH, content, com.rafali.flickruploader.donationendpoint.model.Donation.class);
    }

    @Override
    public InsertDonation setAlt(java.lang.String alt) {
      return (InsertDonation) super.setAlt(alt);
    }

    @Override
    public InsertDonation setFields(java.lang.String fields) {
      return (InsertDonation) super.setFields(fields);
    }

    @Override
    public InsertDonation setKey(java.lang.String key) {
      return (InsertDonation) super.setKey(key);
    }

    @Override
    public InsertDonation setOauthToken(java.lang.String oauthToken) {
      return (InsertDonation) super.setOauthToken(oauthToken);
    }

    @Override
    public InsertDonation setPrettyPrint(java.lang.Boolean prettyPrint) {
      return (InsertDonation) super.setPrettyPrint(prettyPrint);
    }

    @Override
    public InsertDonation setQuotaUser(java.lang.String quotaUser) {
      return (InsertDonation) super.setQuotaUser(quotaUser);
    }

    @Override
    public InsertDonation setUserIp(java.lang.String userIp) {
      return (InsertDonation) super.setUserIp(userIp);
    }

    @Override
    public InsertDonation set(String parameterName, Object value) {
      return (InsertDonation) super.set(parameterName, value);
    }
  }

  /**
   * Create a request for the method "listDonation".
   *
   * This request holds the parameters needed by the the donationendpoint server.  After setting any
   * optional parameters, call the {@link ListDonation#execute()} method to invoke the remote
   * operation.
   *
   * @return the request
   */
  public ListDonation listDonation() throws java.io.IOException {
    ListDonation result = new ListDonation();
    initialize(result);
    return result;
  }

  public class ListDonation extends DonationendpointRequest<com.rafali.flickruploader.donationendpoint.model.CollectionResponseDonation> {

    private static final String REST_PATH = "donation";

    /**
     * Create a request for the method "listDonation".
     *
     * This request holds the parameters needed by the the donationendpoint server.  After setting any
     * optional parameters, call the {@link ListDonation#execute()} method to invoke the remote
     * operation. <p> {@link
     * ListDonation#initialize(com.google.api.client.googleapis.services.AbstractGoogleClientRequest)}
     * must be called to initialize this instance immediately after invoking the constructor. </p>
     *
     * @since 1.13
     */
    protected ListDonation() {
      super(Donationendpoint.this, "GET", REST_PATH, null, com.rafali.flickruploader.donationendpoint.model.CollectionResponseDonation.class);
    }

    @Override
    public com.google.api.client.http.HttpResponse executeUsingHead() throws java.io.IOException {
      return super.executeUsingHead();
    }

    @Override
    public com.google.api.client.http.HttpRequest buildHttpRequestUsingHead() throws java.io.IOException {
      return super.buildHttpRequestUsingHead();
    }

    @Override
    public ListDonation setAlt(java.lang.String alt) {
      return (ListDonation) super.setAlt(alt);
    }

    @Override
    public ListDonation setFields(java.lang.String fields) {
      return (ListDonation) super.setFields(fields);
    }

    @Override
    public ListDonation setKey(java.lang.String key) {
      return (ListDonation) super.setKey(key);
    }

    @Override
    public ListDonation setOauthToken(java.lang.String oauthToken) {
      return (ListDonation) super.setOauthToken(oauthToken);
    }

    @Override
    public ListDonation setPrettyPrint(java.lang.Boolean prettyPrint) {
      return (ListDonation) super.setPrettyPrint(prettyPrint);
    }

    @Override
    public ListDonation setQuotaUser(java.lang.String quotaUser) {
      return (ListDonation) super.setQuotaUser(quotaUser);
    }

    @Override
    public ListDonation setUserIp(java.lang.String userIp) {
      return (ListDonation) super.setUserIp(userIp);
    }

    @com.google.api.client.util.Key
    private java.lang.String cursor;

    /**

     */
    public java.lang.String getCursor() {
      return cursor;
    }

    public ListDonation setCursor(java.lang.String cursor) {
      this.cursor = cursor;
      return this;
    }

    @com.google.api.client.util.Key
    private java.lang.Integer limit;

    /**

     */
    public java.lang.Integer getLimit() {
      return limit;
    }

    public ListDonation setLimit(java.lang.Integer limit) {
      this.limit = limit;
      return this;
    }

    @Override
    public ListDonation set(String parameterName, Object value) {
      return (ListDonation) super.set(parameterName, value);
    }
  }

  /**
   * Create a request for the method "removeDonation".
   *
   * This request holds the parameters needed by the the donationendpoint server.  After setting any
   * optional parameters, call the {@link RemoveDonation#execute()} method to invoke the remote
   * operation.
   *
   * @param id
   * @return the request
   */
  public RemoveDonation removeDonation(java.lang.Long id) throws java.io.IOException {
    RemoveDonation result = new RemoveDonation(id);
    initialize(result);
    return result;
  }

  public class RemoveDonation extends DonationendpointRequest<com.rafali.flickruploader.donationendpoint.model.Donation> {

    private static final String REST_PATH = "donation/{id}";

    /**
     * Create a request for the method "removeDonation".
     *
     * This request holds the parameters needed by the the donationendpoint server.  After setting any
     * optional parameters, call the {@link RemoveDonation#execute()} method to invoke the remote
     * operation. <p> {@link RemoveDonation#initialize(com.google.api.client.googleapis.services.Abstr
     * actGoogleClientRequest)} must be called to initialize this instance immediately after invoking
     * the constructor. </p>
     *
     * @param id
     * @since 1.13
     */
    protected RemoveDonation(java.lang.Long id) {
      super(Donationendpoint.this, "DELETE", REST_PATH, null, com.rafali.flickruploader.donationendpoint.model.Donation.class);
      this.id = com.google.api.client.util.Preconditions.checkNotNull(id, "Required parameter id must be specified.");
    }

    @Override
    public RemoveDonation setAlt(java.lang.String alt) {
      return (RemoveDonation) super.setAlt(alt);
    }

    @Override
    public RemoveDonation setFields(java.lang.String fields) {
      return (RemoveDonation) super.setFields(fields);
    }

    @Override
    public RemoveDonation setKey(java.lang.String key) {
      return (RemoveDonation) super.setKey(key);
    }

    @Override
    public RemoveDonation setOauthToken(java.lang.String oauthToken) {
      return (RemoveDonation) super.setOauthToken(oauthToken);
    }

    @Override
    public RemoveDonation setPrettyPrint(java.lang.Boolean prettyPrint) {
      return (RemoveDonation) super.setPrettyPrint(prettyPrint);
    }

    @Override
    public RemoveDonation setQuotaUser(java.lang.String quotaUser) {
      return (RemoveDonation) super.setQuotaUser(quotaUser);
    }

    @Override
    public RemoveDonation setUserIp(java.lang.String userIp) {
      return (RemoveDonation) super.setUserIp(userIp);
    }

    @com.google.api.client.util.Key
    private java.lang.Long id;

    /**

     */
    public java.lang.Long getId() {
      return id;
    }

    public RemoveDonation setId(java.lang.Long id) {
      this.id = id;
      return this;
    }

    @Override
    public RemoveDonation set(String parameterName, Object value) {
      return (RemoveDonation) super.set(parameterName, value);
    }
  }

  /**
   * Create a request for the method "updateDonation".
   *
   * This request holds the parameters needed by the the donationendpoint server.  After setting any
   * optional parameters, call the {@link UpdateDonation#execute()} method to invoke the remote
   * operation.
   *
   * @param content the {@link com.rafali.flickruploader.donationendpoint.model.Donation}
   * @return the request
   */
  public UpdateDonation updateDonation(com.rafali.flickruploader.donationendpoint.model.Donation content) throws java.io.IOException {
    UpdateDonation result = new UpdateDonation(content);
    initialize(result);
    return result;
  }

  public class UpdateDonation extends DonationendpointRequest<com.rafali.flickruploader.donationendpoint.model.Donation> {

    private static final String REST_PATH = "donation";

    /**
     * Create a request for the method "updateDonation".
     *
     * This request holds the parameters needed by the the donationendpoint server.  After setting any
     * optional parameters, call the {@link UpdateDonation#execute()} method to invoke the remote
     * operation. <p> {@link UpdateDonation#initialize(com.google.api.client.googleapis.services.Abstr
     * actGoogleClientRequest)} must be called to initialize this instance immediately after invoking
     * the constructor. </p>
     *
     * @param content the {@link com.rafali.flickruploader.donationendpoint.model.Donation}
     * @since 1.13
     */
    protected UpdateDonation(com.rafali.flickruploader.donationendpoint.model.Donation content) {
      super(Donationendpoint.this, "PUT", REST_PATH, content, com.rafali.flickruploader.donationendpoint.model.Donation.class);
    }

    @Override
    public UpdateDonation setAlt(java.lang.String alt) {
      return (UpdateDonation) super.setAlt(alt);
    }

    @Override
    public UpdateDonation setFields(java.lang.String fields) {
      return (UpdateDonation) super.setFields(fields);
    }

    @Override
    public UpdateDonation setKey(java.lang.String key) {
      return (UpdateDonation) super.setKey(key);
    }

    @Override
    public UpdateDonation setOauthToken(java.lang.String oauthToken) {
      return (UpdateDonation) super.setOauthToken(oauthToken);
    }

    @Override
    public UpdateDonation setPrettyPrint(java.lang.Boolean prettyPrint) {
      return (UpdateDonation) super.setPrettyPrint(prettyPrint);
    }

    @Override
    public UpdateDonation setQuotaUser(java.lang.String quotaUser) {
      return (UpdateDonation) super.setQuotaUser(quotaUser);
    }

    @Override
    public UpdateDonation setUserIp(java.lang.String userIp) {
      return (UpdateDonation) super.setUserIp(userIp);
    }

    @Override
    public UpdateDonation set(String parameterName, Object value) {
      return (UpdateDonation) super.set(parameterName, value);
    }
  }

  /**
   * Builder for {@link Donationendpoint}.
   *
   * <p>
   * Implementation is not thread-safe.
   * </p>
   *
   * @since 1.3.0
   */
  public static final class Builder extends com.google.api.client.googleapis.services.json.AbstractGoogleJsonClient.Builder {

    /**
     * Returns an instance of a new builder.
     *
     * @param transport HTTP transport, which should normally be:
     *        <ul>
     *        <li>Google App Engine:
     *        {@code com.google.api.client.extensions.appengine.http.UrlFetchTransport}</li>
     *        <li>Android: {@code newCompatibleTransport} from
     *        {@code com.google.api.client.extensions.android.http.AndroidHttp}</li>
     *        <li>Java: {@link com.google.api.client.googleapis.javanet.GoogleNetHttpTransport#newTrustedTransport()}
     *        </li>
     *        </ul>
     * @param jsonFactory JSON factory, which may be:
     *        <ul>
     *        <li>Jackson: {@code com.google.api.client.json.jackson2.JacksonFactory}</li>
     *        <li>Google GSON: {@code com.google.api.client.json.gson.GsonFactory}</li>
     *        <li>Android Honeycomb or higher:
     *        {@code com.google.api.client.extensions.android.json.AndroidJsonFactory}</li>
     *        </ul>
     * @param httpRequestInitializer HTTP request initializer or {@code null} for none
     * @since 1.7
     */
    public Builder(com.google.api.client.http.HttpTransport transport, com.google.api.client.json.JsonFactory jsonFactory,
        com.google.api.client.http.HttpRequestInitializer httpRequestInitializer) {
      super(
          transport,
          jsonFactory,
          DEFAULT_ROOT_URL,
          DEFAULT_SERVICE_PATH,
          httpRequestInitializer,
          false);
    }

    /** Builds a new instance of {@link Donationendpoint}. */
    @Override
    public Donationendpoint build() {
      return new Donationendpoint(this);
    }

    @Override
    public Builder setRootUrl(String rootUrl) {
      return (Builder) super.setRootUrl(rootUrl);
    }

    @Override
    public Builder setServicePath(String servicePath) {
      return (Builder) super.setServicePath(servicePath);
    }

    @Override
    public Builder setHttpRequestInitializer(com.google.api.client.http.HttpRequestInitializer httpRequestInitializer) {
      return (Builder) super.setHttpRequestInitializer(httpRequestInitializer);
    }

    @Override
    public Builder setApplicationName(String applicationName) {
      return (Builder) super.setApplicationName(applicationName);
    }

    @Override
    public Builder setSuppressPatternChecks(boolean suppressPatternChecks) {
      return (Builder) super.setSuppressPatternChecks(suppressPatternChecks);
    }

    @Override
    public Builder setSuppressRequiredParameterChecks(boolean suppressRequiredParameterChecks) {
      return (Builder) super.setSuppressRequiredParameterChecks(suppressRequiredParameterChecks);
    }

    @Override
    public Builder setSuppressAllChecks(boolean suppressAllChecks) {
      return (Builder) super.setSuppressAllChecks(suppressAllChecks);
    }

    /**
     * Set the {@link DonationendpointRequestInitializer}.
     *
     * @since 1.12
     */
    public Builder setDonationendpointRequestInitializer(
        DonationendpointRequestInitializer donationendpointRequestInitializer) {
      return (Builder) super.setGoogleClientRequestInitializer(donationendpointRequestInitializer);
    }

    @Override
    public Builder setGoogleClientRequestInitializer(
        com.google.api.client.googleapis.services.GoogleClientRequestInitializer googleClientRequestInitializer) {
      return (Builder) super.setGoogleClientRequestInitializer(googleClientRequestInitializer);
    }
  }
}
