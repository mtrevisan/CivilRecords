package unit731.civilrecords.familysearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import unit731.civilrecords.services.AbstractCrawler;
import unit731.civilrecords.services.HttpUtils;


public class FSCrawler extends AbstractCrawler{

	protected static final String URL_FAMILYSEARCH = "https://www.familysearch.org";
	protected static final String URL_FAMILYSEARCH_PRE_LOGIN = "https://www.familysearch.org/auth/familysearch/login?ldsauth=false";
	protected static final String URL_FAMILYSEARCH_LOGIN = "https://ident.familysearch.org/cis-web/oauth2/v3/authorization";

//	private static final Object LOCK = new Object();
//	//[ms]
//	private static final long LOGIN_WAIT_TIME = 30_000l;

	protected List<String> urls;

	protected long currentPageIndex;
	protected long totalPages;


	public FSCrawler(){
		super(URL_FAMILYSEARCH + "/ark:/", AbstractCrawler.WAIT_TIME);
	}

	@Override
	protected void login(String username, String password) throws IOException{
		String preLoginContent = HttpUtils.getRequestAsContent(URL_FAMILYSEARCH_PRE_LOGIN)
			.asString(StandardCharsets.UTF_8);
		Element doc = Jsoup.parse(preLoginContent);
		Elements inputParams = doc.select("input[name=params]");
		String params = (inputParams != null && !inputParams.isEmpty()? inputParams.get(0).attr("value"): null);
		if(params == null)
			throw new IOException("Cannot extract authorization key");

		boolean privateComputer = true;
		List<BasicNameValuePair> bodyParams = new ArrayList<>();
		bodyParams.add(new BasicNameValuePair("userName", username));
		bodyParams.add(new BasicNameValuePair("password", password));
		bodyParams.add(new BasicNameValuePair("params", params));
		if(privateComputer)
			bodyParams.add(new BasicNameValuePair("privateComputer", "on"));
		String body = URLEncodedUtils.format(bodyParams, StandardCharsets.UTF_8.name());
		Content response = HttpUtils.postWithBodyAsRawRequestAsContent(URL_FAMILYSEARCH_LOGIN, body);

//		synchronized(LOCK){
//			try{
//				LOCK.wait(LOGIN_WAIT_TIME);
//			}
//			catch(InterruptedException ex){
//				throw new IOException("Cannot wait for login");
//			}
//		}

		System.out.format("Login done: " + params + LINE_SEPARATOR);
System.out.format(response.asString(StandardCharsets.UTF_8) + LINE_SEPARATOR);
	}

	@Override
	protected String extractPage(String url, String filmNumber, Document document, PdfWriter writer) throws IOException{
		extractImage(url + "/dist.jpg", document, writer);

		return extractNextURL(url, filmNumber);
	}

	@Override
	protected String getNextURL(String url, String filmNumber) throws URISyntaxException, IOException{
		if(urls == null){
			String data = "{\"type\":\"image-data\",\"args\":{\"imageURL\":\"" + url + "\",\"state\":{}}}";
			JsonNode response = HttpUtils.postWithBodyAsJsonRequestAsJson(URL_FAMILYSEARCH + "/search/filmdatainfo", data);

			String self = null;
			ArrayNode sourceDescriptions = (ArrayNode)response.path("meta").path("sourceDescriptions");
			for(JsonNode sourceDescription : sourceDescriptions)
				if("http://gedcomx.org/Collection".equals(sourceDescription.path("resourceType").asText(null))){
					self = sourceDescription.path("identifiers").path("http://gedcomx.org/Primary").get(0).asText(null);
					break;
				}

			data = "{\"type\":\"waypoint-data\",\"args\":{\"waypointURL\":\"" + self + "\",\"state\":{},\"locale\":\"en\"}}";
			response = HttpUtils.postWithBodyAsJsonRequestAsJson(URL_FAMILYSEARCH + "/search/filmdatainfo", data);

			ArrayNode images = (ArrayNode)response.path("images");
			urls = StreamSupport.stream(images.spliterator(), false)
				.map(JsonNode::asText)
				.map(str -> str.substring(0, str.indexOf('?')))
				.collect(Collectors.toList());
			totalPages = urls.size();
		}
		currentPageIndex = urls.indexOf(url);

		return (currentPageIndex < totalPages - 1? urls.get((int)(currentPageIndex + 1)): null);
	}

	@Override
	protected void logPageStats(DescriptiveStatistics stats){
		//[s/page]
		double speed = stats.getMean();
		//[min]
		int estimatedTimeToComplete = (int)Math.ceil((totalPages - currentPageIndex) * speed / 60.);
		System.out.format(Locale.ENGLISH, "Page %s/%s (%2d%%) downloaded and added to PDF (%3.1f s/page, ETA %02d:%02d)      \r",
			(currentPageIndex > 0? currentPageIndex: "?"), (totalPages > 0? totalPages: "?"), (int)Math.floor(currentPageIndex * 100. / totalPages),
			speed, estimatedTimeToComplete / 60, estimatedTimeToComplete % 60);
	}

}
