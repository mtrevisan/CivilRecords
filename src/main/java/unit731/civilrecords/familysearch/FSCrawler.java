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
import org.apache.http.client.fluent.Content;

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
	protected void login(String username, String password) throws IOException{
		String loginUrl = "https://ident.familysearch.org/cis-web/oauth2/v3/authorization";
		String body = "{\"userName\":\"" + username + "\",\"password\":\"" + password + "\",\"privateComputer\":\"on\",\"params\":\"EzMupujcElotrYrj8jvzJLSgz6MVggBAzR8PrLNVn858Oave6sP6_yMSU91n_FwuMS4A_6lp-9bZ_zjPaPB4V5M0JaOfuIfKwEIh6R5kuvUaImaxTvd7_0wPSHqvcsZcsDvoAk34vowAfqOS9nnoTOmawp9E7mEqFEjZAnWvwAJ4a-8g-nstMTV3uoET9Lw9wO3sUD4iLv6bTrM18QSZmCqQdIcPl2A9JW4w709GR1kZmBhe7jt9helhhBddCVLx-7-5d6114F8aLa8C_GiaD4Li2aqKuv9vc4aT1zcLxttEh_HIGhewgTT9rv8hXbMwerqYF6_PNmvgRKTprRFTlPO4qUiVRFmXL6ZWeUbV27_ImqXLaKJt3TyDrvL9kPmmiogHDZEh_-RY775U7ycVSnIoKU-eJSpYcYlphZZ_ffYoqBhoflv2R7asHbaiq-s4biULbge1e--vXMx3-L2TlL2VsRpQfKTgr9bk3TcIIm8G54CaBsX7H0m50bVRRSEiNUJ6iku8OmbfectLOZ7e6y4IfYHHHgDQvUiWHWqzPXPFSGEf7E5vUZihWoYqcMPSdpowUJ52Q15R-D_tWm6S32eptlujsMdOPc2KGD-n_tnQF84QXlVvc-lhA0AqzwYbFu2Yrd5_sNkC1PQwd_hAFZiYKZzqdkU2YMWnBEdcynC34HNOEUCkZuYK1zSAjCVR-grhtlAx7XKp6FOkdGaHF2mcEbCLp71kdLcsl0rlzV1zor5pIPNbkXXnhQWOLmytM1IpZBP2WA0vgScayQTiNKtBRgtJYOCPa_zw66jTaitah_4y3qXyC3myIeNSa5lXiRegYUv0nC0=\"}";
		HttpUtils.postWithBodyAsRawRequestAsContent(loginUrl, body);

		System.out.format("Login done");
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
