package org.streampipes.empire.test;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.net.URI;


@SuppressWarnings("deprecation")
public class HttpJsonParser {

	public static String getContentFromUrl(URI uri) throws ClientProtocolException, IOException
	{
		return getContentFromUrl(uri, null);
	}
	
	public static String getContentFromUrl(URI uri, String header) throws ClientProtocolException, IOException
	{
		HttpGet request = new HttpGet(uri);
		if (header != null) request.addHeader("Accept", header);
		
		@SuppressWarnings("resource")
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(request);
		
		String pageContent = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
		return pageContent;
		
	}
}
