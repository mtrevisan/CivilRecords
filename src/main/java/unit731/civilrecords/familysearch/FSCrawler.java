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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import unit731.civilrecords.services.AbstractCrawler;
import unit731.civilrecords.services.HttpUtils;


public class FSCrawler extends AbstractCrawler{

	//[ms]
	private static final int ERROR_WAIT_TIME_DEFAULT = 10_000;

	private static final String URL_FAMILYSEARCH = "https://www.familysearch.org";
	private static final String URL_FAMILYSEARCH_ARCHIVE = URL_FAMILYSEARCH + "/ark:/61903/3:1:";
//	private static final String URL_FAMILYSEARCH_CATALOG = URL_FAMILYSEARCH + "/search/catalog/";
	private static final String URL_FAMILYSEARCH_DATA = URL_FAMILYSEARCH + "/search/filmdatainfo";
	private static final String URL_FAMILYSEARCH_PRE_LOGIN = URL_FAMILYSEARCH + "/auth/familysearch/login?ldsauth=false";
	private static final String URL_FAMILYSEARCH_LOGIN = "https://ident.familysearch.org/cis-web/oauth2/v3/authorization";

	private static final Matcher FAMILYSEARCH_URL_CLEANER = Pattern.compile("https?://(?:www.)?familysearch.org/ark:/61903/3:1:([^/]+)(?:/image.xml)?").matcher("");

	private static final String RESOURCE_TYPE = "http://gedcomx.org";
	private static final String RESOURCE_TYPE_PRIMARY = RESOURCE_TYPE + "/Primary";
	private static final String RESOURCE_TYPE_COLLECTION = RESOURCE_TYPE + "/Collection";
	private static final String RESOURCE_TYPE_DIGITAL_ARTIFACT = RESOURCE_TYPE + "/DigitalArtifact";


	private List<String> urls;

	private long currentPageIndex;
	private long totalPages;

	private boolean loggedIn;


	public FSCrawler(){
		super(ERROR_WAIT_TIME_DEFAULT);
	}

	@Override
	protected boolean login(String username, String password) throws IOException{
		if(!loggedIn){
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
			String responseContent = HttpUtils.postWithBodyAsRawRequestAsContent(URL_FAMILYSEARCH_LOGIN, body)
				.asString(StandardCharsets.UTF_8);
			doc = Jsoup.parse(responseContent);
			Elements loginResponse = doc.select("div[id=errorAuthentication]");
			loggedIn = loginResponse.isEmpty();

			System.out.format((loggedIn? "Login done": "Login error: " + loginResponse.get(0).ownText()) + LINE_SEPARATOR);
		}

		return loggedIn;
	}

	@Override
	protected String extractPage(String url, Document document, PdfWriter writer) throws IOException{
		//URL_FAMILYSEARCH_CATALOG
		url = URL_FAMILYSEARCH_ARCHIVE + url;

		extractImage(url + "/dist.jpg", document, writer);

		return extractNextURL(url);
	}

	@Override
	protected String getNextURL(String url) throws URISyntaxException, IOException{
		if(urls == null){
			HttpUtils.RequestBody data = FSRequest.createImageRequest(url);
			JsonNode response = HttpUtils.postWithBodyAsJsonRequestAsJson(URL_FAMILYSEARCH_DATA, data);

			String filmNumber = null;
			ArrayNode sourceDescriptions = (ArrayNode)response.path("meta").path("sourceDescriptions");
			for(JsonNode sourceDescription : sourceDescriptions)
				if(sourceDescription.has("rights")){
					String resourceType = sourceDescription.path("resourceType").asText(null);
					if(RESOURCE_TYPE_COLLECTION.equals(resourceType)){
						filmNumber = sourceDescription.path("identifiers").path(RESOURCE_TYPE_PRIMARY).get(0).asText(null);
						break;
					}
					else if(RESOURCE_TYPE_DIGITAL_ARTIFACT.equals(resourceType)){
						filmNumber = response.path("dgsNum").asText(null);
						break;
					}
				}
			if(filmNumber == null)
				throw new IOException("Cannot find next URL from '" + sourceDescriptions.toString() + "'");


			data = FSRequest.createFilmRequest(filmNumber);
			response = HttpUtils.postWithBodyAsJsonRequestAsJson(URL_FAMILYSEARCH_DATA, data);

			ArrayNode images = (ArrayNode)response.path("images");
			urls = StreamSupport.stream(images.spliterator(), false)
				.map(JsonNode::asText)
				.map(str -> FAMILYSEARCH_URL_CLEANER.reset(str).replaceFirst("$1"))
				.collect(Collectors.toList());
			totalPages = urls.size();
		}

		currentPageIndex = urls.indexOf(FAMILYSEARCH_URL_CLEANER.reset(url).replaceFirst("$1"));
		if(currentPageIndex < 0)
			currentPageIndex = 0;

		return (currentPageIndex < totalPages - 1? urls.get((int)(currentPageIndex + 1)): null);
	}

	@Override
	protected void logPageStats(DescriptiveStatistics stats){
		//[s/page]
		double speed = stats.getMean();
		//[min]
		int estimatedTimeToComplete = (int)Math.ceil((totalPages - currentPageIndex) * speed / 60.);
		System.out.format(Locale.ENGLISH, "Page %s/%s (%2d%%) downloaded and added to PDF (%3.1f s/page, ETA %02d:%02d)      \r",
			currentPageIndex + 1, (totalPages > 0? totalPages: "?"), (int)Math.floor((currentPageIndex + 1) * 100. / totalPages),
			speed, estimatedTimeToComplete / 60, estimatedTimeToComplete % 60);
	}

}
