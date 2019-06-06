/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jeremydyer.nifi.salesforce;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnDisabled;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Tags({ "Salesforce.com", "username-password", "oauth", "authentication" })
@CapabilityDescription("Service to provide authentication services against Salesforce.com")
public class SalesforceUserPassAuthenticationService
    extends AbstractControllerService implements SalesforceUserPassAuthentication
{

  //Salesforce.com Documentation around this authentication flow
  //https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/intro_understanding_username_password_oauth_flow.htm

  private final String GRANT_TYPE  = "password";
  private JSONObject sfResponse;

  //TODO: create a custom validator. Make sure the user is entering a URL and it is using HTTPS which is required by Salesforce.
  public static final PropertyDescriptor AUTH_ENDPOINT = new PropertyDescriptor
      .Builder().name("Salesforce REST Authentication Endpoint")
                .description("The URL for the authentication endpoint for Salesforce.com")
                .required(true)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .defaultValue("https://login.salesforce.com/services/oauth2/token")
                .build();

  public static final PropertyDescriptor CLIENT_ID = new PropertyDescriptor
      .Builder().name("Salesforce.com ClientID (Consumer Key)")
                .description("The 'Consumer Key' from the connected app definition.")
                .required(true)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .build();

  public static final PropertyDescriptor CLIENT_SECRET = new PropertyDescriptor
      .Builder().name("Salesforce.com ClientSecret (Consumer Secret)")
                .description("The 'Consumer Secret' from the connected app definition.")
                .required(true)
                .sensitive(true)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .build();

  public static final PropertyDescriptor USERNAME = new PropertyDescriptor
      .Builder().name("Salesforce.com Username")
                .description(
                    "End-user's username -- usually looks like an e-mail.  Note that this must be provided for Oauth2 to work with apps without user input.")
                .required(true)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .build();

  public static final PropertyDescriptor PASSWORD = new PropertyDescriptor
      .Builder().name("Salesforce.com Password (plus security token appended).")
                .description("End-user's password.   Note that this must be provided for Oauth2 to " +
                    "work with apps without user input.  You must append the user’s security " +
                    "token to their password A security token is an automatically-generated key " +
                    "from Salesforce. For example, if a user's password is mypassword, and their " +
                    "security token is XXXXXXXXXX, then the value provided for this parmeter must " +
                    "be mypasswordXXXXXXXXXX. For more information on security tokens see “Reset Your " +
                    "Security Token” in the online help.  Hint: you can also change your password, and " +
                    "Salesforce.com will send you an e-mail with the new security token.")
                .required(true)
                .sensitive(true)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .build();

  private static final List<PropertyDescriptor> properties;

  static
  {
    final List<PropertyDescriptor> props = new ArrayList<>();
    props.add(AUTH_ENDPOINT);
    props.add(CLIENT_ID);
    props.add(CLIENT_SECRET);
    props.add(USERNAME);
    props.add(PASSWORD);
    properties = Collections.unmodifiableList(props);
  }

  private String clientId;
  private String clientSecret;
  private String userName;
  private String password;
  private String authEndpoint;

  @Override
  protected List<PropertyDescriptor> getSupportedPropertyDescriptors()
  {
    return properties;
  }

  /**
   * @param context the configuration context
   * @throws InitializationException if unable to create a database connection
   */
  @OnEnabled
  public void onEnabled(final ConfigurationContext context) throws InitializationException
  {

    this.clientId = context.getProperty(CLIENT_ID).evaluateAttributeExpressions().getValue();
    this.clientSecret = context.getProperty(CLIENT_SECRET).evaluateAttributeExpressions().getValue();

    try
    {
      this.userName = URLEncoder
          .encode(context.getProperty(USERNAME).evaluateAttributeExpressions().getValue(), "UTF-8");
    }
    catch (UnsupportedEncodingException use)
    {
      getLogger().error(use.getMessage());
      this.userName = context.getProperty(USERNAME).evaluateAttributeExpressions().getValue();
    }

    this.password = context.getProperty(PASSWORD).evaluateAttributeExpressions().getValue();
    this.authEndpoint = context.getProperty(AUTH_ENDPOINT).evaluateAttributeExpressions().getValue();

    authenticate();
  }

  @OnDisabled
  public void shutdown()
  {
    //TODO: Invalidate the access token here and "logout"
  }

  public void authenticate() throws InitializationException
  {

    StringBuilder requestBody = new StringBuilder();
    requestBody.append("grant_type=");
    requestBody.append(GRANT_TYPE);
    requestBody.append("&client_id=");
    requestBody.append(this.clientId);
    requestBody.append("&client_secret=");
    requestBody.append(this.clientSecret);
    requestBody.append("&username=");
    requestBody.append(this.userName);

    requestBody.append("&password=");
    requestBody.append(this.password);
    //requestBody.append(context.getProperty(USER_SECURITY_TOKEN).evaluateAttributeExpressions().getValue());

    try
    {
      URL                obj = new URL(this.authEndpoint);
      HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

      //add request header
      con.setRequestMethod("POST");
      con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

      // Send post request
      con.setDoOutput(true);
      DataOutputStream wr = new DataOutputStream(con.getOutputStream());
      wr.writeBytes(requestBody.toString());
      wr.flush();
      wr.close();

      int responseCode = con.getResponseCode();

      if (responseCode == 200)
      {

        BufferedReader in = new BufferedReader(
            new InputStreamReader(con.getInputStream()));
        String       inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null)
        {
          response.append(inputLine);
        }
        in.close();

        //print result
        //            System.out.println(response.toString());
        //            getLogger().info("Salesforce.com Auth Response: " + response.toString());

        //Parse the response and attempt to get the Salesforce.com access_token
        sfResponse = new JSONObject(response.toString());

        if (sfResponse.get("access_token") != null)
        {
          getLogger().info("Salesforce.com Access Token received.");
        }
        else
        {
          throw new Exception("Salesforce.com: Failed to find  access_token  " );
        }
      }
      else
      {
        throw new Exception("Salesforce.com:  Invalid Response; response code  " + responseCode + " " + con.getResponseMessage());
      }

    }
    catch (Exception ex)
    {
      getLogger().error(ex.getMessage());
      throw new InitializationException(ex);
    }
  }

  public String getSalesforceAccessToken() throws ProcessException
  {
    return getResponseAttrib("access_token");
  }

  public String getResponseAttrib(String attrib) throws ProcessException
  {
    if (this.sfResponse == null)
    {
      throw new ProcessException("Salesforce.com:  Invalid response; please re-try authentication.");
    }
    if (this.sfResponse.get(attrib) == null)
    {
      throw new ProcessException("Salesforce.com:  Unable to find attribute " + attrib);
    }
    return this.sfResponse.getString(attrib);
  }

}
