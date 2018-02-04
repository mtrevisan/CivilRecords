package unit731.civilrecords.san;

import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import unit731.civilrecords.services.AbstractCrawler;
import unit731.civilrecords.services.HttpUtils;


public class SANCrawler extends AbstractCrawler{

	private static final String URL_SAN = "http://www.antenati.san.beniculturali.it";


	public SANCrawler(){
		super(URL_SAN + "/v/", AbstractCrawler.WAIT_TIME);
	}

	@Override
	protected String extractPage(String url, String filmNumber, Document document, PdfWriter writer) throws IOException{
		String nextURL = null;
		while(!shutdown){
			try{
				String content = HttpUtils.getRequestAsContent(url)
					.asString(StandardCharsets.UTF_8);
				Element doc = Jsoup.parse(content);

				Elements imageLink = doc.select("a.cloud-zoom");
				String imageURL = (imageLink != null && !imageLink.isEmpty()? imageLink.get(0).absUrl("href"): null);
				extractImage(imageURL, document, writer);

				Elements nextLink = doc.select("a.next");
				nextURL = (nextLink != null && !nextLink.isEmpty()? HttpUtils.cleanURLFromParameters(URL_SAN + nextLink.get(0).attr("href")): null);

				break;
			}
			catch(IOException | URISyntaxException e){
				if(!(e instanceof SocketTimeoutException))
					Logger.getLogger(AbstractCrawler.class.getName()).log(Level.SEVERE, null, e);

				if(shutdown)
					break;

				try{ Thread.sleep(WAIT_TIME); }
				catch(InterruptedException ie){}
			}
		}
		return nextURL;
	}

	@Override
	protected void logPageStats(DescriptiveStatistics stats){
		System.out.format(Locale.ENGLISH, "Page downloaded and added to PDF (%3.1f s/page)   \r", stats.getMean());
	}

}
