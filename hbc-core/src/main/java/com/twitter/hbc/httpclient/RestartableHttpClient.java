/**
 * Copyright 2013 Twitter, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package com.twitter.hbc.httpclient;

import com.google.common.base.Preconditions;
import com.twitter.hbc.httpclient.auth.Authentication;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DecompressingHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.util.EntityUtils;

/**
 * There's currently a bug in DecompressingHttpClient that does not allow it to properly abort requests.
 * This class is a hacky workaround to make things work.
 */
public class RestartableHttpClient implements HttpClient {

  private final AtomicReference<HttpClient> underlying;
  private final Authentication auth;
  private final HttpParams params;
  private final boolean enableGZip;
  private final SchemeRegistry schemeRegistry;

  public RestartableHttpClient(Authentication auth, boolean enableGZip, HttpParams params, SchemeRegistry schemeRegistry) {
    this.auth = Preconditions.checkNotNull(auth);
    this.enableGZip = enableGZip;
    this.params = Preconditions.checkNotNull(params);
    this.schemeRegistry = Preconditions.checkNotNull(schemeRegistry);

    this.underlying = new AtomicReference<HttpClient>();
  }

  public void setup() {
    DefaultHttpClient defaultClient = new DefaultHttpClient(new PoolingClientConnectionManager(schemeRegistry), params);

	System.out.println("checking proxy settings ...");
	
	Boolean isProxyEnabled = Boolean.parseBoolean(System.getProperty("socialbus.proxy.support.enabled"));
	System.out.println("proxy enabled? " + isProxyEnabled);
	
	if(isProxyEnabled){
		System.out.println("loading proxy settings ...");
			
		String proxyHost = System.getProperty("socialbus.proxy.host");
		Integer proxyPort = Integer.parseInt(System.getProperty("socialbus.proxy.port"));
		String proxyUser = System.getProperty("socialbus.proxy.username");
		String proxyPass = System.getProperty("socialbus.proxy.password");
		
		System.out.println("proxy host: " + proxyHost);
		System.out.println("proxy port: " + proxyPort);
		System.out.println("proxy user: " + proxyUser);
		
	    CredentialsProvider credsProvider = new BasicCredentialsProvider();
	          credsProvider.setCredentials(
	                  new AuthScope(proxyHost, proxyPort),
	                  new UsernamePasswordCredentials(proxyUser, proxyPass));
		
		defaultClient.setCredentialsProvider(credsProvider);
		
		// NTCredentials ntCreds = new NTCredentials(ntUsername, ntPassword,localMachineName, domainName );
		//
		// CredentialsProvider credsProvider = new BasicCredentialsProvider();
		// credsProvider.setCredentials( new AuthScope(proxyHost,proxyPort), ntCreds );
		// HttpClientBuilder clientBuilder = HttpClientBuilder.create();
		//
		// clientBuilder.useSystemProperties();
		// clientBuilder.setProxy(new HttpHost(pxInfo.getProxyURL(), pxInfo.getProxyPort()));
		// clientBuilder.setDefaultCredentialsProvider(credsProvider);
		// clientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
		
		// defaultClient.getCredentialsProvider().setCredentials(
// 		    new AuthScope(proxyHost, proxyPort),
// 		    new UsernamePasswordCredentials(proxyUser, proxyPass));
		
		System.out.println("proxy setup done.");
	}	

    auth.setupConnection(defaultClient);

    if (enableGZip) {
      underlying.set(new DecompressingHttpClient(defaultClient));
    } else {
      underlying.set(defaultClient);
    }
  }

  public void restart() {
    HttpClient old = underlying.get();
    if (old != null) {
      // this will kill all of the connections and release the resources for our old client
      old.getConnectionManager().shutdown();
    }
    setup();
  }

  @Override
  public HttpParams getParams() {
    return underlying.get().getParams();
  }

  @Override
  public ClientConnectionManager getConnectionManager() {
    return underlying.get().getConnectionManager();
  }

  @Override
  public HttpResponse execute(HttpUriRequest request) throws IOException, ClientProtocolException {
    return underlying.get().execute(request);
  }

  @Override
  public HttpResponse execute(HttpUriRequest request, HttpContext context) throws IOException, ClientProtocolException {
    return underlying.get().execute(request, context);
  }

  @Override
  public HttpResponse execute(HttpHost target, HttpRequest request) throws IOException, ClientProtocolException {
    return underlying.get().execute(target, request);
  }

  @Override
  public HttpResponse execute(HttpHost target, HttpRequest request, HttpContext context) throws IOException, ClientProtocolException {
    return underlying.get().execute(target, request, context);
  }

  @Override
  public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
    return underlying.get().execute(request, responseHandler);
  }

  @Override
  public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler, HttpContext context) throws IOException, ClientProtocolException {
    return underlying.get().execute(request, responseHandler, context);
  }

  @Override
  public <T> T execute(HttpHost target, HttpRequest request, ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
    return underlying.get().execute(target, request, responseHandler);
  }

  @Override
  public <T> T execute(HttpHost target, HttpRequest request, ResponseHandler<? extends T> responseHandler, HttpContext context) throws IOException, ClientProtocolException {
    return underlying.get().execute(target, request, responseHandler, context);
  }
}