package unit731.civilrecords.familysearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Locale;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import unit731.civilrecords.services.AbstractCrawler;
import unit731.civilrecords.services.HttpUtils;


public class FSCrawler extends AbstractCrawler{

	protected static final String URL_FAMILYSEARCH = "https://familysearch.org";

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
		String data = "{\"type\":\"image-data\",\"args\":{\"imageURL\":\"" + url + "\",\"state\":{}}}";
		JsonNode response = HttpUtils.postWithBodyAsJsonRequestAsJson(URL_FAMILYSEARCH + "/search/filmdatainfo", data);

		JsonNode links = response.path("meta").path("links");
		JsonNode self = links.path("self");
		if(totalPages == 0)
			totalPages = self.path("results").asLong(0);
		currentPageIndex = self.path("offset").asLong(0);

		return HttpUtils.cleanURLFromParameters(links.path("next").path("href").asText(null));
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
