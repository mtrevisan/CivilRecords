package unit731.civilrecords.familysearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import unit731.civilrecords.services.HttpUtils;


public class FSFilmCrawler extends FSCrawler{

	@Override
	protected String getNextURL(String url, String filmNumber) throws URISyntaxException, IOException{
		if(urls == null){
			String data = "{\"type\":\"film-data\",\"args\":{\"dgsNum\":\"" + filmNumber + "\",\"state\":{},\"locale\":\"en\"}}";
			JsonNode response = HttpUtils.postWithBodyAsJsonRequestAsJson(URL_FAMILYSEARCH + "/search/filmdatainfo", data);

			ArrayNode images = (ArrayNode)response.path("images");
			urls = StreamSupport.stream(images.spliterator(), false)
				.map(JsonNode::asText)
				.map(str -> str.substring(0, str.length() - "/image.xml".length()))
				.collect(Collectors.toList());
			totalPages = urls.size();
		}
		currentPageIndex = urls.indexOf(url);

		return (currentPageIndex < totalPages - 1? urls.get((int)(currentPageIndex + 1)): null);
	}

}
