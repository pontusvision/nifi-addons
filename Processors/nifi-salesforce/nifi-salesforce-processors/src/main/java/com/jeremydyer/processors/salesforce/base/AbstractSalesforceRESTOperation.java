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

package com.jeremydyer.processors.salesforce.base;

import com.jeremydyer.nifi.salesforce.SalesforceUserPassAuthentication;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

/**
 * Created by jdyer on 8/4/16.
 */
public  class AbstractSalesforceRESTOperation
    extends AbstractProcessor
{

  public static final PropertyDescriptor SALESFORCE_AUTH_SERVICE = new PropertyDescriptor
      .Builder().name("Salesforce.com Authentication Controller Service")
                .description("Your Salesforce.com authentication service for authenticating against Salesforce.com")
                .required(true)
                .identifiesControllerService(SalesforceUserPassAuthentication.class)
                .build();

  public static final Relationship REL_SUCCESS = new Relationship.Builder()
      .name("success")
      .description("Operation completed successfully")
      .build();

  public static final Relationship REL_FAILURE = new Relationship.Builder()
      .name("failure")
      .description("Operation failed")
      .build();

  public static final String DEFAULT_SALESFORCE_URL_BASE = "https://test.salesforce.com/";

  public static final PropertyDescriptor SALESFORCE_URL_BASE = new PropertyDescriptor.Builder()
      .name("Salesforce Base URL")
      .description("URL for API Calls (post authentication)")
      .required(true)
      .defaultValue(DEFAULT_SALESFORCE_URL_BASE)
      .addValidator(StandardValidators.createURLorFileValidator())
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .build();

  public static final String             DEFAULT_SALESFORCE_VERSION = "v45.0";
  public static final PropertyDescriptor SALESFORCE_VERSION         = new PropertyDescriptor.Builder()
      .name("Salesforce API Version")
      .description("API Version")
      .required(true)
      .defaultValue(DEFAULT_SALESFORCE_VERSION)
      .addValidator(StandardValidators.createURLorFileValidator())
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .build();

  public static final String RESPONSE_JSON = "json";
  public static final String RESPONSE_XML  = "xml";

  protected String baseURL = null;
  protected String apiVer  = null;
  protected List<PropertyDescriptor> descriptors;

  protected Set<Relationship> relationships;

  private final String salesforceOp = "limits";

  protected String getEndPoint(ProcessContext context, FlowFile flowFile)
  {
    return salesforceOp;
  }

  @Override
  public Set<Relationship> getRelationships() {
    return this.relationships;
  }

  @Override
  public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
    return descriptors;
  }

  @Override
  public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
    final FlowFile flowFile = session.get();
    if ( flowFile == null ) {
      return;
    }

    final SalesforceUserPassAuthentication sfAuthService = context.getProperty(SALESFORCE_AUTH_SERVICE)
                                                                  .asControllerService(SalesforceUserPassAuthentication.class);
    getLogger().info("Call Salesforce.com REST API.");
    try {

      final String responseJson = sendGet(sfAuthService.getSalesforceAccessToken(), RESPONSE_JSON, generateSalesforceURL(context,flowFile));

      FlowFile ff = session.write(flowFile, new OutputStreamCallback() {
        @Override
        public void process(OutputStream outputStream) throws IOException {
          outputStream.write(responseJson.getBytes());
        }
      });
      session.transfer(ff, REL_SUCCESS);
    } catch (Exception ex) {
      getLogger().error(ex.getMessage());
      session.transfer(flowFile, REL_FAILURE);
    }
  }
  @Override
  protected void init(final ProcessorInitializationContext context) {
    final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
    descriptors.add(SALESFORCE_AUTH_SERVICE);
    descriptors.add(SALESFORCE_URL_BASE);
    descriptors.add(SALESFORCE_VERSION);

    this.descriptors = Collections.unmodifiableList(descriptors);

    final Set<Relationship> relationships = new HashSet<Relationship>();
    relationships.add(REL_SUCCESS);
    relationships.add(REL_FAILURE);
    this.relationships = Collections.unmodifiableSet(relationships);
  }
  // HTTP GET request
  protected String sendGet(String accessToken,
                           String responseFormat, String url) throws Exception
  {

    URL                obj = new URL(url);
    HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

    // optional default is GET
    con.setRequestMethod("GET");

    //Add headers
    con.setRequestProperty("Authorization: Bearer ", accessToken);
    con.setRequestProperty("Content-Type",
        "application/x-www-form-urlencoded");
    con.setRequestProperty("Accept", responseFormat);

    int responseCode = con.getResponseCode();
    getLogger().info("\nSending 'GET' request to URL : " + url);
    getLogger().info("Response Code : " + responseCode);

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
    getLogger().info(response.toString());
    return response.toString();

  }

  // HTTP POST request
  protected String sendPost(String accessToken, String responseFormat, String url) throws Exception
  {

    URL                obj = new URL(url);
    HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

    //add reuqest header
    con.setRequestMethod("POST");
    con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
    con.setRequestProperty("Authorization: Bearer ", accessToken);
    con.setRequestProperty("Content-Type",
        "application/x-www-form-urlencoded");

    String urlParameters = "sn=C02G8416DRJM&cn=&locale=&caller=&num=12345";

    // Send post request
    con.setDoOutput(true);
    DataOutputStream wr = new DataOutputStream(con.getOutputStream());
    wr.writeBytes(urlParameters);
    wr.flush();
    wr.close();

    int responseCode = con.getResponseCode();
    System.out.println("\nSending 'POST' request to URL : " + url);
    System.out.println("Post parameters : " + urlParameters);
    System.out.println("Response Code : " + responseCode);

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
    System.out.println(response.toString());
    return response.toString();
  }

  public static String getBaseURL(ProcessContext context, FlowFile flowFile) {
    return context.getProperty(SALESFORCE_URL_BASE).evaluateAttributeExpressions(flowFile).getValue();
  }

  public static String getAPIVer(ProcessContext context, FlowFile flowFile) {
    return context.getProperty(SALESFORCE_VERSION).evaluateAttributeExpressions(flowFile).getValue();
  }


  protected String generateSalesforceURL(ProcessContext context, FlowFile flowFile)
      throws UnsupportedEncodingException
  {
    if (baseURL == null)
    {
      baseURL = getBaseURL(context, flowFile);
    }

    if (apiVer == null)
    {
      apiVer = getAPIVer(context,flowFile);
    }

    StringBuilder url = new StringBuilder();
    url.append(baseURL);
    url.append(apiVer);
    url.append("/");
    url.append(getEndPoint(context,flowFile));

    return URLEncoder.encode(url.toString(), "UTF-8");

  }
}
