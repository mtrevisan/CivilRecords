package unit731.civilrecords.familysearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import unit731.civilrecords.services.AbstractCrawler;
import unit731.civilrecords.services.HttpUtils;


public class FSCrawler extends AbstractCrawler{

	protected static final String URL_FAMILYSEARCH = "https://www.familysearch.org";

	protected List<String> urls;

	protected long currentPageIndex;
	protected long totalPages;


	public FSCrawler(){
		super(URL_FAMILYSEARCH + "/ark:/", AbstractCrawler.WAIT_TIME);
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
