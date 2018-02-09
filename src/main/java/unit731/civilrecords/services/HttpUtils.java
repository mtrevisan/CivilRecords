package unit731.civilrecords.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.util.EntityUtils;


public class HttpUtils{

	public static final int CONNECT_TIMEOUT = 60 * 1000;
	public static final int SOCKET_TIMEOUT = 60 * 1000;

	private static final Charset CHARSET_DEFAULT = StandardCharsets.UTF_8;

	private static final CloseableHttpClient CLIENT;
	static{
		HttpClientBuilder clientBuilder = HttpClientBuilder.create();
		clientBuilder.setRedirectStrategy(new LaxRedirectStrategy());
		CLIENT = clientBuilder.build();
	}

	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();


	private static final ResponseHandler<JsonNode> JSONNODE_CONTENT_HANDLER = (HttpResponse response) -> {
		StatusLine statusLine = response.getStatusLine();
		if(statusLine.getStatusCode() >= 300)
			throw new HttpResponseException(statusLine.getStatusCode(), statusLine.getReasonPhrase());

		HttpEntity entity = response.getEntity();
		if(entity == null)
			throw new ClientProtocolException("Response contains no content");

		String content = EntityUtils.toString(entity, CHARSET_DEFAULT.name());
		return JSON_MAPPER.readTree(content);
	};


	public static String cleanURLFromParameters(String url) throws URISyntaxException{
		if(url != null)
			url = new URIBuilder(url)
				.clearParameters()
				.build()
				.toString();
		return url;
	}

	public static Content getRequestAsContent(String url) throws IOException{
		try{
			Executor exec = Executor.newInstance(CLIENT);
			return exec.execute(Request.Get(url)
				.connectTimeout(CONNECT_TIMEOUT)
				.socketTimeout(SOCKET_TIMEOUT))
				.returnContent();
		}
		catch(IOException e){
			throw e;
		}
	}

	public static Content postWithBodyAsRawRequestAsContent(String url, String body) throws IOException{
		try{
			Executor exec = Executor.newInstance(CLIENT);
			return exec.execute(Request.Post(url)
				.connectTimeout(CONNECT_TIMEOUT)
				.socketTimeout(SOCKET_TIMEOUT)
				.bodyString(body, ContentType.APPLICATION_FORM_URLENCODED.withCharset(CHARSET_DEFAULT)))
				.returnContent();
		}
		catch(IOException e){
			throw e;
		}
	}

	public static JsonNode postWithBodyAsJsonRequestAsJson(String url, String body) throws IOException{
		try{
			Executor exec = Executor.newInstance(CLIENT);
			return exec.execute(Request.Post(url)
				.connectTimeout(CONNECT_TIMEOUT)
				.socketTimeout(SOCKET_TIMEOUT)
				.bodyString(body, ContentType.APPLICATION_JSON))
				.handleResponse(JSONNODE_CONTENT_HANDLER);
		}
		catch(IOException e){
			throw e;
		}
	}

}
